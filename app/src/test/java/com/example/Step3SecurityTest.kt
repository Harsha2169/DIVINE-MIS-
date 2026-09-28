package com.example

import com.example.analytics.AnalyticsFilterContext
import com.example.auth.AccessDeniedSecurityException
import com.example.auth.AuthContext
import com.example.auth.FirestoreSchema
import com.example.auth.InMemoryFirebaseAuthBoundary
import com.example.auth.InMemoryUserRepository
import com.example.auth.Permission
import com.example.auth.SecurityEnforcer
import com.example.auth.SecurityPolicy
import com.example.auth.UserGovernanceService
import com.example.auth.UserProfile
import com.example.auth.UserRole
import com.example.core.BusinessDate
import com.example.fixture.Step1TestFixture
import com.example.model.BusinessArea
import com.example.model.Department
import com.example.model.ManufacturingModel
import com.example.model.RejectionSide
import com.example.model.RejectionSource
import com.example.service.SecureProductionService
import com.example.service.SecureRejectionService
import com.example.service.SecureStockService
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
 * Step 3 Verification Test Suite: Authentication, RBAC, and Security Architecture.
 */
class Step3SecurityTest {

    private lateinit var fixture: Step1TestFixture
    private lateinit var userRepository: InMemoryUserRepository
    private lateinit var securityEnforcer: SecurityEnforcer
    private lateinit var authBoundary: InMemoryFirebaseAuthBoundary
    private lateinit var userGovernanceService: UserGovernanceService

    private lateinit var secureProductionService: SecureProductionService
    private lateinit var secureRejectionService: SecureRejectionService
    private lateinit var secureStockService: SecureStockService

    private lateinit var ceoProfile: UserProfile
    private lateinit var adminProfile: UserProfile
    private lateinit var managerProfile: UserProfile
    private lateinit var castingSupervisorProfile: UserProfile
    private lateinit var unassignedSupervisorProfile: UserProfile

    @Before
    fun setUp() = runBlocking {
        fixture = Step1TestFixture()
        userRepository = InMemoryUserRepository()
        securityEnforcer = SecurityEnforcer(userRepository)
        authBoundary = InMemoryFirebaseAuthBoundary(userRepository)
        userGovernanceService = UserGovernanceService(userRepository, securityEnforcer)

        secureProductionService = SecureProductionService(fixture.productionService, securityEnforcer)
        secureRejectionService = SecureRejectionService(fixture.rejectionService, securityEnforcer)
        secureStockService = SecureStockService(fixture.stockService, securityEnforcer)

        // Seed authoritative profiles
        ceoProfile = UserProfile(
            uid = "uid-ceo-001",
            email = "ceo@divinestamp.com",
            displayName = "Executive Director",
            role = UserRole.CEO
        )
        adminProfile = UserProfile(
            uid = "uid-admin-001",
            email = "admin@divinestamp.com",
            displayName = "System Admin",
            role = UserRole.ADMIN
        )
        managerProfile = UserProfile(
            uid = "uid-mgr-001",
            email = "manager@divinestamp.com",
            displayName = "Plant Operations Manager",
            role = UserRole.MANAGER
        )
        castingSupervisorProfile = UserProfile(
            uid = "uid-sup-casting-001",
            email = "sup.casting@divinestamp.com",
            displayName = "Casting Supervisor",
            role = UserRole.SUPERVISOR,
            assignedDepartments = setOf(Department.CASTING, Department.POST_CASTING)
        )
        unassignedSupervisorProfile = UserProfile(
            uid = "uid-sup-unassigned-001",
            email = "sup.new@divinestamp.com",
            displayName = "New Supervisor",
            role = UserRole.SUPERVISOR,
            assignedDepartments = emptySet()
        )

        userRepository.save(ceoProfile)
        userRepository.save(adminProfile)
        userRepository.save(managerProfile)
        userRepository.save(castingSupervisorProfile)
        userRepository.save(unassignedSupervisorProfile)
    }

    // 1. Authentication Required
    @Test
    fun testAuthenticationRequired() = runBlocking {
        val unauthenticated = AuthContext.Unauthenticated
        assertFalse(unauthenticated.isAuthenticated)
        assertNull(unauthenticated.currentUser)

        // Production entry must fail when unauthenticated
        try {
            secureProductionService.recordProduction(
                authContext = unauthenticated,
                date = BusinessDate.parse("2026-09-01"),
                model = ManufacturingModel.U86,
                department = Department.CASTING,
                quantity = 100
            )
            fail("Unauthenticated production entry must throw AccessDeniedSecurityException")
        } catch (e: AccessDeniedSecurityException) {
            assertTrue(e.reason.contains("Unauthenticated", ignoreCase = true))
        }

        // Rejection entry must fail when unauthenticated
        try {
            secureRejectionService.recordRejection(
                authContext = unauthenticated,
                date = BusinessDate.parse("2026-09-01"),
                model = ManufacturingModel.U86,
                department = Department.CASTING,
                defectName = "Blowhole",
                source = RejectionSource.KAMAL,
                businessArea = BusinessArea.KAMAL,
                side = RejectionSide.LH,
                quantity = 5
            )
            fail("Unauthenticated rejection entry must throw AccessDeniedSecurityException")
        } catch (e: AccessDeniedSecurityException) {
            assertTrue(e.reason.contains("Unauthenticated", ignoreCase = true))
        }

        // Stock entry must fail when unauthenticated
        try {
            secureStockService.recordManualStock(
                authContext = unauthenticated,
                date = BusinessDate.parse("2026-09-01"),
                model = ManufacturingModel.U86,
                openingQuantity = 200,
                closingQuantity = 180
            )
            fail("Unauthenticated stock entry must throw AccessDeniedSecurityException")
        } catch (e: AccessDeniedSecurityException) {
            assertTrue(e.reason.contains("Unauthenticated", ignoreCase = true))
        }
    }

    // 2. Exact Four Roles
    @Test
    fun testExactFourRoles() {
        val expectedRoles = listOf("CEO", "ADMIN", "MANAGER", "SUPERVISOR")
        val actualRoles = UserRole.entries.map { it.code }

        assertEquals(4, UserRole.entries.size)
        assertEquals(expectedRoles, actualRoles)

        assertEquals("CEO", UserRole.CEO.code)
        assertEquals("Chief Executive Officer", UserRole.CEO.displayName)

        assertEquals("ADMIN", UserRole.ADMIN.code)
        assertEquals("System Administrator", UserRole.ADMIN.displayName)

        assertEquals("MANAGER", UserRole.MANAGER.code)
        assertEquals("Operations Manager", UserRole.MANAGER.displayName)

        assertEquals("SUPERVISOR", UserRole.SUPERVISOR.code)
        assertEquals("Department Supervisor", UserRole.SUPERVISOR.displayName)
    }

    // 3. CEO Read-Only Contract
    @Test
    fun testCeoReadOnly() = runBlocking {
        val ceoContext = AuthContext.Authenticated(ceoProfile)

        // CEO has READ_ALL_DATA
        assertTrue(SecurityPolicy.hasBasePermission(UserRole.CEO, Permission.READ_ALL_DATA))
        assertFalse(SecurityPolicy.hasBasePermission(UserRole.CEO, Permission.RECORD_PRODUCTION))
        assertFalse(SecurityPolicy.hasBasePermission(UserRole.CEO, Permission.RECORD_REJECTION))
        assertFalse(SecurityPolicy.hasBasePermission(UserRole.CEO, Permission.RECORD_STOCK))
        assertFalse(SecurityPolicy.hasBasePermission(UserRole.CEO, Permission.MANAGE_USERS))

        // CEO can read
        val filter = fixture.defaultFilterContext()
        val prods = secureProductionService.getProduction(ceoContext, filter)
        assertNotNull(prods)

        // CEO write prohibition: Production
        try {
            secureProductionService.recordProduction(
                authContext = ceoContext,
                date = BusinessDate.parse("2026-09-01"),
                model = ManufacturingModel.U86,
                department = Department.CASTING,
                quantity = 50
            )
            fail("CEO write operation must throw AccessDeniedSecurityException")
        } catch (e: AccessDeniedSecurityException) {
            assertTrue(e.reason.contains("read-only", ignoreCase = true))
        }

        // CEO write prohibition: Stock
        try {
            secureStockService.recordManualStock(
                authContext = ceoContext,
                date = BusinessDate.parse("2026-09-01"),
                model = ManufacturingModel.U86,
                openingQuantity = 100,
                closingQuantity = 90
            )
            fail("CEO stock recording must throw AccessDeniedSecurityException")
        } catch (e: AccessDeniedSecurityException) {
            assertTrue(e.reason.contains("read-only", ignoreCase = true))
        }
    }

    // 4. ADMIN Governance Access
    @Test
    fun testAdminGovernanceAccess() = runBlocking {
        val adminContext = AuthContext.Authenticated(adminProfile)

        assertTrue(SecurityPolicy.hasBasePermission(UserRole.ADMIN, Permission.MANAGE_USERS))
        assertTrue(SecurityPolicy.hasBasePermission(UserRole.ADMIN, Permission.MANAGE_SYSTEM_SETTINGS))
        assertTrue(SecurityPolicy.hasBasePermission(UserRole.ADMIN, Permission.VIEW_GOVERNANCE_AUDIT))
        assertTrue(SecurityPolicy.hasBasePermission(UserRole.ADMIN, Permission.READ_ALL_DATA))

        // Admin can create a new user profile
        val created = userGovernanceService.createUser(
            actorContext = adminContext,
            uid = "uid-new-user-002",
            email = "operator@divinestamp.com",
            displayName = "Machining Operator",
            role = UserRole.SUPERVISOR,
            assignedDepartments = setOf(Department.MACHINING)
        )
        assertNotNull(created)
        assertEquals("uid-new-user-002", created.uid)
        assertEquals(UserRole.SUPERVISOR, created.role)
        assertTrue(created.hasDepartmentAssignment(Department.MACHINING))

        // Admin can update roles
        val updated = userGovernanceService.updateUserRole(
            actorContext = adminContext,
            targetUid = created.uid,
            newRole = UserRole.MANAGER
        )
        assertEquals(UserRole.MANAGER, updated.role)
        assertEquals(emptySet<Department>(), updated.assignedDepartments)

        // Non-admin (Manager) cannot manage users
        val managerContext = AuthContext.Authenticated(managerProfile)
        try {
            userGovernanceService.createUser(
                actorContext = managerContext,
                uid = "uid-illegal",
                email = "illegal@divinestamp.com",
                displayName = "Illegal",
                role = UserRole.SUPERVISOR
            )
            fail("Manager cannot manage users")
        } catch (e: AccessDeniedSecurityException) {
            assertTrue(e.reason.contains("MANAGE_USERS", ignoreCase = true))
        }
    }

    // 5. MANAGER Operational Permissions
    @Test
    fun testManagerOperationalPermissions() = runBlocking {
        val managerContext = AuthContext.Authenticated(managerProfile)

        assertTrue(SecurityPolicy.hasBasePermission(UserRole.MANAGER, Permission.RECORD_PRODUCTION))
        assertTrue(SecurityPolicy.hasBasePermission(UserRole.MANAGER, Permission.RECORD_REJECTION))
        assertTrue(SecurityPolicy.hasBasePermission(UserRole.MANAGER, Permission.RECORD_STOCK))
        assertTrue(SecurityPolicy.hasBasePermission(UserRole.MANAGER, Permission.RECORD_PLAN))
        assertTrue(SecurityPolicy.hasBasePermission(UserRole.MANAGER, Permission.APPROVE_PLAN))
        assertTrue(SecurityPolicy.hasBasePermission(UserRole.MANAGER, Permission.READ_ALL_DATA))
        assertFalse(SecurityPolicy.hasBasePermission(UserRole.MANAGER, Permission.MANAGE_USERS))

        // Manager can record production across any applicable department
        val prod1 = secureProductionService.recordProduction(
            authContext = managerContext,
            date = BusinessDate.parse("2026-09-01"),
            model = ManufacturingModel.U86,
            department = Department.CASTING,
            quantity = 500
        )
        assertEquals(500, prod1.quantity.value)

        val prod2 = secureProductionService.recordProduction(
            authContext = managerContext,
            date = BusinessDate.parse("2026-09-01"),
            model = ManufacturingModel.U86,
            department = Department.MACHINING,
            quantity = 480
        )
        assertEquals(480, prod2.quantity.value)

        // Manager can record stock
        val stock = secureStockService.recordManualStock(
            authContext = managerContext,
            date = BusinessDate.parse("2026-09-01"),
            model = ManufacturingModel.U86,
            openingQuantity = 1000,
            closingQuantity = 950
        )
        assertEquals(1000, stock.openingQuantity)
        assertEquals(950, stock.closingQuantity)
    }

    // 6. SUPERVISOR Assigned Department Restriction
    @Test
    fun testSupervisorAssignedDepartmentRestriction() = runBlocking {
        val supContext = AuthContext.Authenticated(castingSupervisorProfile)

        // Supervisor can record production in CASTING
        val prod = secureProductionService.recordProduction(
            authContext = supContext,
            date = BusinessDate.parse("2026-09-01"),
            model = ManufacturingModel.U86,
            department = Department.CASTING,
            quantity = 150
        )
        assertEquals(150, prod.quantity.value)

        // Supervisor can record rejection in CASTING
        val rej = secureRejectionService.recordRejection(
            authContext = supContext,
            date = BusinessDate.parse("2026-09-01"),
            model = ManufacturingModel.U86,
            department = Department.CASTING,
            defectName = "Blowhole",
            source = RejectionSource.KAMAL,
            businessArea = BusinessArea.KAMAL,
            side = RejectionSide.LH,
            quantity = 3
        )
        assertEquals(3, rej.quantity)
    }

    // 7. SUPERVISOR Unassigned Department Denial
    @Test
    fun testSupervisorUnassignedDepartmentDenial() = runBlocking {
        val supContext = AuthContext.Authenticated(castingSupervisorProfile)

        // Casting supervisor attempting MACHINING must fail
        try {
            secureProductionService.recordProduction(
                authContext = supContext,
                date = BusinessDate.parse("2026-09-01"),
                model = ManufacturingModel.U86,
                department = Department.MACHINING,
                quantity = 50
            )
            fail("Supervisor must be denied access to unassigned department MACHINING")
        } catch (e: AccessDeniedSecurityException) {
            assertTrue(e.reason.contains("not assigned", ignoreCase = true))
        }

        // Supervisor cannot record stock
        try {
            secureStockService.recordManualStock(
                authContext = supContext,
                date = BusinessDate.parse("2026-09-01"),
                model = ManufacturingModel.U86,
                openingQuantity = 50,
                closingQuantity = 40
            )
            fail("Supervisor must be denied recording stock")
        } catch (e: AccessDeniedSecurityException) {
            assertTrue(e.reason.contains("RECORD_STOCK", ignoreCase = true))
        }

        // Unassigned supervisor (empty departments) must be denied
        val unassignedContext = AuthContext.Authenticated(unassignedSupervisorProfile)
        try {
            secureProductionService.recordProduction(
                authContext = unassignedContext,
                date = BusinessDate.parse("2026-09-01"),
                model = ManufacturingModel.U86,
                department = Department.CASTING,
                quantity = 25
            )
            fail("Unassigned supervisor must be denied data entry")
        } catch (e: AccessDeniedSecurityException) {
            assertTrue(e.reason.contains("no assigned departments", ignoreCase = true))
        }
    }

    // 8. Invalid and Missing Role Denial
    @Test
    fun testInvalidAndMissingRoleDenial() {
        assertNull(UserRole.fromCodeOrNull("GUEST"))
        assertNull(UserRole.fromCodeOrNull(""))
        assertNull(UserRole.fromCodeOrNull(null))

        try {
            UserRole.fromCode("ANONYMOUS")
            fail("UserRole.fromCode('ANONYMOUS') must throw IllegalArgumentException")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message?.contains("invalid", ignoreCase = true) == true)
        }
    }

    // 9. Missing Profile Denial
    @Test
    fun testMissingProfileDenial() = runBlocking {
        // Authenticated token referencing non-existent UID in database
        val ghostProfile = UserProfile(
            uid = "ghost-uid-404",
            email = "ghost@example.com",
            displayName = "Ghost User",
            role = UserRole.MANAGER
        )
        val ghostContext = AuthContext.Authenticated(ghostProfile)

        try {
            secureProductionService.recordProduction(
                authContext = ghostContext,
                date = BusinessDate.parse("2026-09-01"),
                model = ManufacturingModel.U86,
                department = Department.CASTING,
                quantity = 100
            )
            fail("Context with missing authoritative profile must throw AccessDeniedSecurityException")
        } catch (e: AccessDeniedSecurityException) {
            assertTrue(e.reason.contains("does not exist", ignoreCase = true))
        }
    }

    // 10. Client Role Escalation Denial
    @Test
    fun testClientRoleEscalationDenial() = runBlocking {
        // A client sends an auth context claiming to be ADMIN, but authoritative storage has them as SUPERVISOR
        val tamperedUser = castingSupervisorProfile.copy(role = UserRole.ADMIN)
        val tamperedContext = AuthContext.Authenticated(tamperedUser)

        try {
            secureProductionService.recordProduction(
                authContext = tamperedContext,
                date = BusinessDate.parse("2026-09-01"),
                model = ManufacturingModel.U86,
                department = Department.MACHINING, // Supervisor has no access to MACHINING
                quantity = 100
            )
            fail("Role escalation must throw AccessDeniedSecurityException")
        } catch (e: AccessDeniedSecurityException) {
            assertTrue(e.reason.contains("escalation", ignoreCase = true))
        }
    }

    // 11. Repository and Security Contract Consistency
    @Test
    fun testRepositoryAndSecurityContractConsistency() = runBlocking {
        // Verify Firestore schema collection constants
        assertEquals("users", FirestoreSchema.USERS)
        assertEquals("production", FirestoreSchema.PRODUCTION)
        assertEquals("rejection", FirestoreSchema.REJECTION)
        assertEquals("stock", FirestoreSchema.STOCK)
        assertEquals("planning", FirestoreSchema.PLANNING)
        assertEquals("audit_logs", FirestoreSchema.AUDIT_LOGS)

        // Verify Firebase Auth Boundary operations
        val authResult = authBoundary.signIn("uid-mgr-001")
        assertTrue(authResult.isAuthenticated)
        assertEquals(UserRole.MANAGER, authResult.currentUser?.role)

        authBoundary.signOut()
        assertFalse(authBoundary.getCurrentAuthContext().isAuthenticated)

        // Verify Deactivated User Denial
        val deactivatedProfile = managerProfile.copy(isActive = false)
        userRepository.save(deactivatedProfile)

        try {
            authBoundary.signIn("uid-mgr-001")
            fail("Deactivated user sign-in must fail")
        } catch (e: AccessDeniedSecurityException) {
            assertTrue(e.reason.contains("deactivated", ignoreCase = true))
        }
    }
}
