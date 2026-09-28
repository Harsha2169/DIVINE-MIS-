package com.example

import com.example.action.ActionPriority
import com.example.action.ActionRecord
import com.example.action.ActionStatus
import com.example.action.ActionWorkflowValidator
import com.example.auth.AccessDeniedSecurityException
import com.example.auth.AuthContext
import com.example.auth.InMemoryUserRepository
import com.example.auth.SecurityEnforcer
import com.example.auth.UserProfile
import com.example.auth.UserRepository
import com.example.auth.UserRole
import com.example.core.BusinessDate
import com.example.model.Department
import com.example.model.validation.DomainValidator
import com.example.repository.ActionTrackerRepository
import com.example.repository.memory.InMemoryActionTrackerRepository
import com.example.service.ActionTrackerService
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test

/**
 * Step 6 Verification Test Suite: Action Tracker and Operational Workflow.
 */
class Step6ActionTrackerTest {

    private lateinit var userRepository: UserRepository
    private lateinit var securityEnforcer: SecurityEnforcer
    private lateinit var actionRepository: ActionTrackerRepository
    private lateinit var actionTrackerService: ActionTrackerService

    private lateinit var ceoContext: AuthContext.Authenticated
    private lateinit var adminContext: AuthContext.Authenticated
    private lateinit var managerContext: AuthContext.Authenticated
    private lateinit var castingSupervisorContext: AuthContext.Authenticated
    private lateinit var unassignedSupervisorContext: AuthContext.Authenticated
    private val unauthenticatedContext = AuthContext.Unauthenticated

    @Before
    fun setUp() = runBlocking {
        userRepository = InMemoryUserRepository()
        securityEnforcer = SecurityEnforcer(userRepository)
        actionRepository = InMemoryActionTrackerRepository()
        actionTrackerService = ActionTrackerService(actionRepository, securityEnforcer)

        val ceo = UserProfile("uid-ceo", "ceo@ds.com", "CEO User", UserRole.CEO)
        val admin = UserProfile("uid-admin", "admin@ds.com", "Admin User", UserRole.ADMIN)
        val manager = UserProfile("uid-mgr", "mgr@ds.com", "Plant Manager", UserRole.MANAGER)
        val castingSup = UserProfile(
            uid = "uid-sup-cast",
            email = "sup.cast@ds.com",
            displayName = "Casting Supervisor",
            role = UserRole.SUPERVISOR,
            assignedDepartments = setOf(Department.CASTING)
        )
        val unassignedSup = UserProfile(
            uid = "uid-sup-none",
            email = "sup.none@ds.com",
            displayName = "Unassigned Supervisor",
            role = UserRole.SUPERVISOR,
            assignedDepartments = emptySet()
        )

        userRepository.save(ceo)
        userRepository.save(admin)
        userRepository.save(manager)
        userRepository.save(castingSup)
        userRepository.save(unassignedSup)

        ceoContext = AuthContext.Authenticated(ceo)
        adminContext = AuthContext.Authenticated(admin)
        managerContext = AuthContext.Authenticated(manager)
        castingSupervisorContext = AuthContext.Authenticated(castingSup)
        unassignedSupervisorContext = AuthContext.Authenticated(unassignedSup)
    }

    // 1. Exact Statuses
    @Test
    fun testExactStatuses() {
        val statuses = ActionStatus.entries
        assertEquals("ActionStatus must have exactly 7 statuses", 7, statuses.size)

        val expected = setOf(
            "OPEN",
            "ASSIGNED",
            "IN_PROGRESS",
            "ON_HOLD",
            "COMPLETED",
            "CLOSED",
            "CANCELLED"
        )
        assertEquals(expected, statuses.map { it.code }.toSet())

        // Terminal flags
        assertTrue(ActionStatus.CLOSED.isTerminal)
        assertTrue(ActionStatus.CANCELLED.isTerminal)
        assertFalse(ActionStatus.OPEN.isTerminal)
        assertFalse(ActionStatus.ASSIGNED.isTerminal)
        assertFalse(ActionStatus.IN_PROGRESS.isTerminal)
        assertFalse(ActionStatus.ON_HOLD.isTerminal)
        assertFalse(ActionStatus.COMPLETED.isTerminal)

        // Active flags
        assertTrue(ActionStatus.OPEN.isActive)
        assertTrue(ActionStatus.ASSIGNED.isActive)
        assertTrue(ActionStatus.IN_PROGRESS.isActive)
        assertFalse(ActionStatus.ON_HOLD.isActive)
        assertFalse(ActionStatus.COMPLETED.isActive)
        assertFalse(ActionStatus.CLOSED.isActive)
        assertFalse(ActionStatus.CANCELLED.isActive)

        // Parsing
        assertEquals(ActionStatus.OPEN, ActionStatus.fromCode("OPEN"))
        assertEquals(ActionStatus.IN_PROGRESS, ActionStatus.fromCode("in_progress"))
        assertEquals(ActionStatus.CLOSED, DomainValidator.validateActionStatus("CLOSED"))

        try {
            ActionStatus.fromCode("PENDING_APPROVAL")
            fail("Invented status must throw IllegalArgumentException")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message?.contains("Unknown action status", ignoreCase = true) == true)
        }
    }

    // 2. Exact Priorities
    @Test
    fun testExactPriorities() {
        val priorities = ActionPriority.entries
        assertEquals("ActionPriority must have exactly 4 priorities", 4, priorities.size)

        val expected = setOf("CRITICAL", "HIGH", "MEDIUM", "LOW")
        assertEquals(expected, priorities.map { it.code }.toSet())

        assertEquals(ActionPriority.CRITICAL, ActionPriority.fromCode("CRITICAL"))
        assertEquals(ActionPriority.LOW, ActionPriority.fromCode("low"))
        assertEquals(ActionPriority.HIGH, DomainValidator.validateActionPriority("HIGH"))

        try {
            ActionPriority.fromCode("URGENT")
            fail("Invented priority must throw IllegalArgumentException")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message?.contains("Unknown action priority", ignoreCase = true) == true)
        }
    }

    // 3. Action Creation
    @Test
    fun testActionCreation() = runBlocking {
        val date = BusinessDate.parse("2026-09-10")

        // Unassigned action starts in OPEN status
        val openAction = actionTrackerService.createAction(
            context = managerContext,
            title = "Fix die alignment in Casting Line 1",
            description = "High blowhole defect rate observed due to loose die clamps.",
            department = Department.CASTING,
            priority = ActionPriority.HIGH,
            businessDate = date,
            dueDate = BusinessDate.parse("2026-09-15")
        )

        assertEquals(ActionStatus.OPEN, openAction.status)
        assertEquals(ActionPriority.HIGH, openAction.priority)
        assertEquals(Department.CASTING, openAction.department)
        assertNull(openAction.assigneeId)
        assertNull(openAction.assigneeName)

        // Action created with assignee starts in ASSIGNED status
        val assignedAction = actionTrackerService.createAction(
            context = managerContext,
            title = "Inspect polishing wheels in Buffing",
            description = "Rough buffing defect spike on LH parts.",
            department = Department.BUFFING,
            priority = ActionPriority.MEDIUM,
            businessDate = date,
            assigneeId = "uid-sup-cast",
            assigneeName = "Casting Supervisor"
        )
        assertEquals(ActionStatus.ASSIGNED, assignedAction.status)
        assertEquals("uid-sup-cast", assignedAction.assigneeId)

        // Invalid title length (< 3 chars)
        try {
            actionTrackerService.createAction(
                context = managerContext,
                title = "AB",
                description = "Valid description here",
                department = Department.CASTING,
                businessDate = date
            )
            fail("Short title must throw IllegalArgumentException")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message?.contains("at least 3", ignoreCase = true) == true)
        }

        // Blank description
        try {
            actionTrackerService.createAction(
                context = managerContext,
                title = "Valid Title",
                description = "   ",
                department = Department.CASTING,
                businessDate = date
            )
            fail("Blank description must throw IllegalArgumentException")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message?.contains("description cannot be blank", ignoreCase = true) == true)
        }
    }

    // 4. Assignment and Reassignment
    @Test
    fun testAssignmentAndReassignment() = runBlocking {
        val date = BusinessDate.parse("2026-09-10")
        val action = actionTrackerService.createAction(
            context = managerContext,
            title = "Check thermocouple calibration",
            description = "Furnace temperature fluctuation observed.",
            department = Department.CASTING,
            businessDate = date
        )
        assertEquals(ActionStatus.OPEN, action.status)

        // Assign action
        val assigned = actionTrackerService.assignAction(
            context = managerContext,
            actionId = action.id,
            assigneeId = "uid-sup-cast",
            assigneeName = "Casting Supervisor"
        )
        assertEquals(ActionStatus.ASSIGNED, assigned.status)
        assertEquals("uid-sup-cast", assigned.assigneeId)
        assertEquals("Casting Supervisor", assigned.assigneeName)

        // Reassign to another user
        val reassigned = actionTrackerService.assignAction(
            context = managerContext,
            actionId = action.id,
            assigneeId = "uid-admin",
            assigneeName = "System Admin"
        )
        assertEquals(ActionStatus.ASSIGNED, reassigned.status)
        assertEquals("uid-admin", reassigned.assigneeId)
        assertEquals("System Admin", reassigned.assigneeName)

        // Blank assignee throws
        try {
            actionTrackerService.assignAction(
                context = managerContext,
                actionId = action.id,
                assigneeId = "",
                assigneeName = "Admin"
            )
            fail("Blank assigneeId must throw")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message?.contains("cannot be blank", ignoreCase = true) == true)
        }
    }

    // 5. Valid Transitions (Canonical Workflow)
    @Test
    fun testValidTransitions() = runBlocking {
        val date = BusinessDate.parse("2026-09-11")

        // Step 1: OPEN
        val a1 = actionTrackerService.createAction(
            context = managerContext,
            title = "Replace coolant filter",
            description = "Flow rate below operating limit.",
            department = Department.MACHINING,
            businessDate = date
        )
        assertEquals(ActionStatus.OPEN, a1.status)

        // Step 2: ASSIGNED
        val a2 = actionTrackerService.assignAction(
            context = managerContext,
            actionId = a1.id,
            assigneeId = "uid-mgr",
            assigneeName = "Plant Manager"
        )
        assertEquals(ActionStatus.ASSIGNED, a2.status)

        // Step 3: IN_PROGRESS
        val a3 = actionTrackerService.startProgress(
            context = managerContext,
            actionId = a2.id
        )
        assertEquals(ActionStatus.IN_PROGRESS, a3.status)

        // Step 4: COMPLETED
        val a4 = actionTrackerService.completeAction(
            context = managerContext,
            actionId = a3.id,
            notes = "Filter replaced and flow rate verified at 45 LPM."
        )
        assertEquals(ActionStatus.COMPLETED, a4.status)
        assertNotNull(a4.completedAt)

        // Step 5: CLOSED
        val a5 = actionTrackerService.closeAction(
            context = managerContext,
            actionId = a4.id,
            closureNotes = "Verified by QA lead."
        )
        assertEquals(ActionStatus.CLOSED, a5.status)
        assertNotNull(a5.closedAt)
    }

    // 6. Invalid Transitions Rejection
    @Test
    fun testInvalidTransitions() = runBlocking {
        val date = BusinessDate.parse("2026-09-11")
        val action = actionTrackerService.createAction(
            context = managerContext,
            title = "Check hydraulic pressure",
            description = "Pressure drop on clamp cylinder.",
            department = Department.CASTING,
            businessDate = date
        )
        assertEquals(ActionStatus.OPEN, action.status)

        // 1. OPEN -> IN_PROGRESS (cannot skip ASSIGNED)
        try {
            actionTrackerService.startProgress(managerContext, action.id)
            fail("OPEN -> IN_PROGRESS directly must throw IllegalStateException")
        } catch (e: IllegalStateException) {
            assertTrue(e.message?.contains("CRITICAL WORKFLOW VIOLATION", ignoreCase = true) == true)
        }

        // 2. OPEN -> COMPLETED
        try {
            actionTrackerService.completeAction(managerContext, action.id)
            fail("OPEN -> COMPLETED directly must throw IllegalStateException")
        } catch (e: IllegalStateException) {
            assertTrue(e.message?.contains("CRITICAL WORKFLOW VIOLATION", ignoreCase = true) == true)
        }

        // 3. OPEN -> CLOSED
        try {
            actionTrackerService.closeAction(managerContext, action.id)
            fail("OPEN -> CLOSED directly must throw IllegalStateException")
        } catch (e: IllegalStateException) {
            assertTrue(e.message?.contains("CRITICAL WORKFLOW VIOLATION", ignoreCase = true) == true)
        }

        // Assign action
        val assigned = actionTrackerService.assignAction(managerContext, action.id, "uid-mgr", "Manager")

        // 4. ASSIGNED -> CLOSED (cannot skip IN_PROGRESS and COMPLETED)
        try {
            actionTrackerService.closeAction(managerContext, assigned.id)
            fail("ASSIGNED -> CLOSED must throw IllegalStateException")
        } catch (e: IllegalStateException) {
            assertTrue(e.message?.contains("CRITICAL WORKFLOW VIOLATION", ignoreCase = true) == true)
        }

        // 5. Validator directly rejects illegal paths
        try {
            ActionWorkflowValidator.validateTransition(ActionStatus.IN_PROGRESS, ActionStatus.OPEN)
            fail("IN_PROGRESS -> OPEN must fail")
        } catch (e: IllegalStateException) {
            assertTrue(e.message?.contains("CRITICAL WORKFLOW VIOLATION", ignoreCase = true) == true)
        }
    }

    // 7. ON_HOLD Flow
    @Test
    fun testOnHoldFlow() = runBlocking {
        val date = BusinessDate.parse("2026-09-12")
        val action = actionTrackerService.createAction(
            context = managerContext,
            title = "Install sensor upgrade",
            description = "Upgrade temperature sensor on Furnace 2.",
            department = Department.CASTING,
            businessDate = date,
            assigneeId = "uid-sup-cast",
            assigneeName = "Casting Supervisor"
        )
        val inProgress = actionTrackerService.startProgress(managerContext, action.id)
        assertEquals(ActionStatus.IN_PROGRESS, inProgress.status)

        // Put on hold without reason must throw
        try {
            actionTrackerService.putOnHold(managerContext, inProgress.id, "   ")
            fail("Blank hold reason must throw IllegalArgumentException")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message?.contains("reason", ignoreCase = true) == true)
        }

        // Put on hold with valid reason
        val onHold = actionTrackerService.putOnHold(
            context = managerContext,
            actionId = inProgress.id,
            reason = "Awaiting replacement sensor delivery from vendor."
        )
        assertEquals(ActionStatus.ON_HOLD, onHold.status)
        assertEquals("Awaiting replacement sensor delivery from vendor.", onHold.holdReason)
        assertEquals(ActionStatus.IN_PROGRESS, onHold.previousStateBeforeHold)

        // Resume back to active state (defaults to previous state before hold: IN_PROGRESS)
        val resumed = actionTrackerService.resumeAction(managerContext, onHold.id)
        assertEquals(ActionStatus.IN_PROGRESS, resumed.status)
        assertNull(resumed.holdReason)
        assertNull(resumed.previousStateBeforeHold)

        // Put on hold again and resume to ASSIGNED
        val holdAgain = actionTrackerService.putOnHold(managerContext, resumed.id, "Vendor technician unavailable")
        val resumedAssigned = actionTrackerService.resumeAction(managerContext, holdAgain.id, ActionStatus.ASSIGNED)
        assertEquals(ActionStatus.ASSIGNED, resumedAssigned.status)

        // Cannot jump from ON_HOLD directly to COMPLETED or CLOSED
        val hold3 = actionTrackerService.putOnHold(managerContext, resumedAssigned.id, "Shift end hold")
        try {
            actionTrackerService.completeAction(managerContext, hold3.id)
            fail("ON_HOLD -> COMPLETED directly must throw IllegalStateException")
        } catch (e: IllegalStateException) {
            assertTrue(e.message?.contains("CRITICAL WORKFLOW VIOLATION", ignoreCase = true) == true)
        }
        try {
            actionTrackerService.closeAction(managerContext, hold3.id)
            fail("ON_HOLD -> CLOSED directly must throw IllegalStateException")
        } catch (e: IllegalStateException) {
            assertTrue(e.message?.contains("CRITICAL WORKFLOW VIOLATION", ignoreCase = true) == true)
        }
    }

    // 8. COMPLETED → CLOSED
    @Test
    fun testCompletedToClosed() = runBlocking {
        val date = BusinessDate.parse("2026-09-12")
        val action = actionTrackerService.createAction(
            context = managerContext,
            title = "Calibrate gauge block",
            description = "Annual micrometer calibration.",
            department = Department.MACHINING,
            businessDate = date,
            assigneeId = "uid-mgr",
            assigneeName = "Manager"
        )
        val inProgress = actionTrackerService.startProgress(managerContext, action.id)
        val completed = actionTrackerService.completeAction(managerContext, inProgress.id, "Gauge calibrated within +/- 0.002mm")
        assertEquals(ActionStatus.COMPLETED, completed.status)

        // Formal verification and closure
        val closed = actionTrackerService.closeAction(managerContext, completed.id, "QA Certificate #441 issued")
        assertEquals(ActionStatus.CLOSED, closed.status)
        assertNotNull(closed.closedAt)

        // Reopening / rework flow from COMPLETED back to IN_PROGRESS
        val action2 = actionTrackerService.createAction(
            context = managerContext,
            title = "Tool sharpening",
            description = "Sharpen deburring tools.",
            department = Department.MACHINING,
            businessDate = date,
            assigneeId = "uid-mgr",
            assigneeName = "Manager"
        )
        val inProgress2 = actionTrackerService.startProgress(managerContext, action2.id)
        val completed2 = actionTrackerService.completeAction(managerContext, inProgress2.id)
        // Rework required: return to IN_PROGRESS
        ActionWorkflowValidator.validateTransition(ActionStatus.COMPLETED, ActionStatus.IN_PROGRESS)
    }

    // 9. CANCELLED Terminal Behavior
    @Test
    fun testCancelledTerminalBehavior() = runBlocking {
        val date = BusinessDate.parse("2026-09-13")

        // Cancel from OPEN
        val a1 = actionTrackerService.createAction(
            context = managerContext,
            title = "Test cancellation from OPEN",
            description = "Description",
            department = Department.CASTING,
            businessDate = date
        )
        val cancelled = actionTrackerService.cancelAction(managerContext, a1.id, "Defect was misreported")
        assertEquals(ActionStatus.CANCELLED, cancelled.status)
        assertEquals("Defect was misreported", cancelled.cancellationReason)

        // Any further transition from CANCELLED throws
        try {
            actionTrackerService.startProgress(managerContext, cancelled.id)
            fail("Transition from CANCELLED must throw IllegalStateException")
        } catch (e: IllegalStateException) {
            assertTrue(e.message?.contains("terminal", ignoreCase = true) == true)
        }

        try {
            actionTrackerService.assignAction(managerContext, cancelled.id, "uid-mgr", "Manager")
            fail("Assignment on CANCELLED must throw IllegalStateException")
        } catch (e: IllegalStateException) {
            assertTrue(e.message?.contains("terminal", ignoreCase = true) == true)
        }

        // CLOSED is also terminal and cannot be cancelled
        val a2 = actionTrackerService.createAction(
            context = managerContext,
            title = "Test terminal closed",
            description = "Description",
            department = Department.CASTING,
            businessDate = date,
            assigneeId = "uid-mgr",
            assigneeName = "Manager"
        )
        val prog = actionTrackerService.startProgress(managerContext, a2.id)
        val comp = actionTrackerService.completeAction(managerContext, prog.id)
        val closed = actionTrackerService.closeAction(managerContext, comp.id)

        try {
            actionTrackerService.cancelAction(managerContext, closed.id, "Cannot cancel closed action")
            fail("Cancelling CLOSED action must throw IllegalStateException")
        } catch (e: IllegalStateException) {
            assertTrue(e.message?.contains("terminal", ignoreCase = true) == true)
        }
    }

    // 10. Department Scope Enforcement
    @Test
    fun testDepartmentScopeEnforcement() = runBlocking {
        val date = BusinessDate.parse("2026-09-14")

        // Casting Supervisor can create actions in CASTING
        val castingAction = actionTrackerService.createAction(
            context = castingSupervisorContext,
            title = "Inspect casting mold #4",
            description = "Mold wear pattern check.",
            department = Department.CASTING,
            businessDate = date
        )
        assertNotNull(castingAction)
        assertEquals(Department.CASTING, castingAction.department)

        // Casting Supervisor CANNOT create actions in MACHINING
        try {
            actionTrackerService.createAction(
                context = castingSupervisorContext,
                title = "Machining spindle check",
                description = "Vibration observed on spindle 3.",
                department = Department.MACHINING,
                businessDate = date
            )
            fail("Supervisor must be denied access to unassigned department")
        } catch (e: AccessDeniedSecurityException) {
            assertTrue(e.message?.contains("not authorized", ignoreCase = true) == true)
        }

        // Casting Supervisor cannot read MACHINING actions
        try {
            actionTrackerService.getActions(castingSupervisorContext, Department.MACHINING)
            fail("Supervisor cannot read unassigned department")
        } catch (e: AccessDeniedSecurityException) {
            assertTrue(e.message?.contains("cannot read unassigned department", ignoreCase = true) == true)
        }
    }

    // 11. CEO Read-Only Enforcement
    @Test
    fun testCeoReadOnly() = runBlocking {
        val date = BusinessDate.parse("2026-09-14")

        // Seed an action by manager
        val action = actionTrackerService.createAction(
            context = managerContext,
            title = "Plant-wide energy audit",
            description = "Evaluate compressed air leaks across all bays.",
            department = Department.CASTING,
            businessDate = date
        )

        // CEO CAN read actions across all departments
        val allActions = actionTrackerService.getActions(ceoContext)
        assertTrue(allActions.isNotEmpty())

        val fetched = actionTrackerService.getActionById(ceoContext, action.id)
        assertNotNull(fetched)

        // CEO CANNOT create an action
        try {
            actionTrackerService.createAction(
                context = ceoContext,
                title = "CEO direct task",
                description = "CEO operational write",
                department = Department.CASTING,
                businessDate = date
            )
            fail("CEO write operation must throw AccessDeniedSecurityException")
        } catch (e: AccessDeniedSecurityException) {
            assertTrue(e.message?.contains("read-only", ignoreCase = true) == true)
        }

        // CEO CANNOT assign an action
        try {
            actionTrackerService.assignAction(ceoContext, action.id, "uid-mgr", "Manager")
            fail("CEO assign must throw AccessDeniedSecurityException")
        } catch (e: AccessDeniedSecurityException) {
            assertTrue(e.message?.contains("read-only", ignoreCase = true) == true)
        }

        // CEO CANNOT complete or close an action
        try {
            actionTrackerService.closeAction(ceoContext, action.id)
            fail("CEO close must throw AccessDeniedSecurityException")
        } catch (e: AccessDeniedSecurityException) {
            assertTrue(e.message?.contains("read-only", ignoreCase = true) == true)
        }
    }

    // 12. Supervisor Assignment Restriction
    @Test
    fun testSupervisorAssignmentRestriction() = runBlocking {
        val date = BusinessDate.parse("2026-09-14")

        // Unassigned supervisor cannot create any action
        try {
            actionTrackerService.createAction(
                context = unassignedSupervisorContext,
                title = "Attempt by unassigned supervisor",
                description = "Description",
                department = Department.CASTING,
                businessDate = date
            )
            fail("Unassigned supervisor must throw AccessDeniedSecurityException")
        } catch (e: AccessDeniedSecurityException) {
            assertTrue(e.message?.contains("no assigned departments", ignoreCase = true) == true)
        }

        // Manager creates action in MACHINING
        val machiningAction = actionTrackerService.createAction(
            context = managerContext,
            title = "Machining cutter replacement",
            description = "End mill worn out.",
            department = Department.MACHINING,
            businessDate = date
        )

        // Casting supervisor CANNOT assign the machining action
        try {
            actionTrackerService.assignAction(
                context = castingSupervisorContext,
                actionId = machiningAction.id,
                assigneeId = "uid-sup-cast",
                assigneeName = "Casting Supervisor"
            )
            fail("Supervisor cannot assign action outside assigned department")
        } catch (e: AccessDeniedSecurityException) {
            assertTrue(e.message?.contains("not authorized", ignoreCase = true) == true)
        }

        // Casting supervisor CANNOT assign action to personnel outside their department
        val castingAction = actionTrackerService.createAction(
            context = castingSupervisorContext,
            title = "Casting core vent cleaning",
            description = "Vent blockage check.",
            department = Department.CASTING,
            businessDate = date
        )
        try {
            actionTrackerService.assignAction(
                context = castingSupervisorContext,
                actionId = castingAction.id,
                assigneeId = "uid-machining-sup",
                assigneeName = "Machining Supervisor",
                assigneeDepartment = Department.MACHINING
            )
            fail("Supervisor cannot assign to an unassigned department personnel")
        } catch (e: AccessDeniedSecurityException) {
            assertTrue(e.message?.contains("outside their assignment", ignoreCase = true) == true)
        }
    }

    // 13. Repository Contract Consistency
    @Test
    fun testRepositoryContractConsistency() = runBlocking {
        val date1 = BusinessDate.parse("2026-09-01")
        val date2 = BusinessDate.parse("2026-09-05")

        val action1 = ActionRecord(
            id = "ACT-REP-01",
            title = "Repository test action 1",
            description = "Description 1",
            department = Department.CASTING,
            status = ActionStatus.OPEN,
            priority = ActionPriority.CRITICAL,
            businessDate = date1,
            createdBy = "Tester"
        )
        val action2 = ActionRecord(
            id = "ACT-REP-02",
            title = "Repository test action 2",
            description = "Description 2",
            department = Department.BUFFING,
            status = ActionStatus.ASSIGNED,
            priority = ActionPriority.LOW,
            businessDate = date2,
            createdBy = "Tester",
            assigneeId = "uid-user-2",
            assigneeName = "User Two"
        )

        // Insert
        actionRepository.insert(action1)
        actionRepository.insert(action2)

        // Get by ID
        assertEquals(action1, actionRepository.getById("ACT-REP-01"))
        assertEquals(action2, actionRepository.getById("ACT-REP-02"))
        assertNull(actionRepository.getById("ACT-NON-EXISTENT"))

        // Get all
        val all = actionRepository.getAll()
        assertEquals(2, all.size)

        // Get by department
        val castingOnly = actionRepository.getByDepartment(Department.CASTING)
        assertEquals(1, castingOnly.size)
        assertEquals("ACT-REP-01", castingOnly.first().id)

        // Get by status
        val openOnly = actionRepository.getByStatus(ActionStatus.OPEN)
        assertEquals(1, openOnly.size)
        assertEquals("ACT-REP-01", openOnly.first().id)

        // Get by priority
        val criticalOnly = actionRepository.getByPriority(ActionPriority.CRITICAL)
        assertEquals(1, criticalOnly.size)
        assertEquals("ACT-REP-01", criticalOnly.first().id)

        // Get by assignee
        val user2Actions = actionRepository.getByAssignee("uid-user-2")
        assertEquals(1, user2Actions.size)
        assertEquals("ACT-REP-02", user2Actions.first().id)

        // Get by date range
        val range = actionRepository.getByDateRange(date1, date2)
        assertEquals(2, range.size)

        // Update
        val updated1 = action1.copy(title = "Updated title 1")
        actionRepository.update(updated1)
        assertEquals("Updated title 1", actionRepository.getById("ACT-REP-01")?.title)

        // Delete
        assertTrue(actionRepository.deleteById("ACT-REP-01"))
        assertNull(actionRepository.getById("ACT-REP-01"))
        assertEquals(1, actionRepository.getAll().size)
    }
}
