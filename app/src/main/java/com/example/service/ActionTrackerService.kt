package com.example.service

import com.example.action.ActionPriority
import com.example.action.ActionRecord
import com.example.action.ActionStatus
import com.example.action.ActionWorkflowValidator
import com.example.auth.AccessDeniedSecurityException
import com.example.auth.AuthContext
import com.example.auth.SecurityEnforcer
import com.example.auth.UserRole
import com.example.core.BusinessDate
import com.example.model.Department
import com.example.repository.ActionTrackerRepository
import java.util.UUID

/**
 * Authoritative Operational Service for Divine Stamp Action Tracker.
 *
 * Implements canonical workflow:
 * OPEN -> ASSIGNED -> IN_PROGRESS -> COMPLETED -> CLOSED
 *
 * Enforces:
 * - Deterministic workflow state transitions
 * - Role-Based Access Control (RBAC) via Step 3 SecurityEnforcer
 * - CEO executive read-only oversight
 * - Supervisor department scoping and assignment restrictions
 * - ON_HOLD and CANCELLED terminal constraints
 */
class ActionTrackerService(
    private val actionRepository: ActionTrackerRepository,
    private val securityEnforcer: SecurityEnforcer
) {

    /**
     * Creates a new action record.
     * If an assignee is provided, status is ASSIGNED; otherwise OPEN.
     */
    suspend fun createAction(
        context: AuthContext,
        title: String,
        description: String,
        department: Department,
        priority: ActionPriority = ActionPriority.MEDIUM,
        businessDate: BusinessDate,
        dueDate: BusinessDate? = null,
        assigneeId: String? = null,
        assigneeName: String? = null,
        id: String = UUID.randomUUID().toString()
    ): ActionRecord {
        securityEnforcer.enforceActionManagement(context, department)

        val authenticated = context as AuthContext.Authenticated
        if (authenticated.user.role == UserRole.SUPERVISOR && assigneeId != null) {
            // Supervisor assignment restriction: must have assignment to department
            if (!authenticated.user.hasDepartmentAssignment(department)) {
                throw AccessDeniedSecurityException(
                    "Supervisor '${authenticated.user.displayName}' cannot assign actions outside assigned departments."
                )
            }
        }

        val initialStatus = if (assigneeId != null) {
            require(!assigneeName.isNullOrBlank()) { "Assignee name is required when assignee ID is provided" }
            ActionStatus.ASSIGNED
        } else {
            ActionStatus.OPEN
        }

        val action = ActionRecord(
            id = id,
            title = title,
            description = description,
            department = department,
            status = initialStatus,
            priority = priority,
            businessDate = businessDate,
            dueDate = dueDate,
            createdBy = authenticated.user.displayName,
            assigneeId = assigneeId,
            assigneeName = assigneeName
        )

        actionRepository.insert(action)
        return action
    }

    /**
     * Assigns or reassigns an action to a designated owner.
     */
    suspend fun assignAction(
        context: AuthContext,
        actionId: String,
        assigneeId: String,
        assigneeName: String,
        assigneeDepartment: Department? = null
    ): ActionRecord {
        val existing = actionRepository.getById(actionId)
            ?: throw NoSuchElementException("Action record not found: '$actionId'")

        securityEnforcer.enforceActionManagement(context, existing.department)

        val authenticated = context as AuthContext.Authenticated
        if (authenticated.user.role == UserRole.SUPERVISOR) {
            if (!authenticated.user.hasDepartmentAssignment(existing.department)) {
                throw AccessDeniedSecurityException(
                    "Supervisor '${authenticated.user.displayName}' cannot assign actions in unassigned department '${existing.department.displayName}'."
                )
            }
            if (assigneeDepartment != null && !authenticated.user.hasDepartmentAssignment(assigneeDepartment)) {
                throw AccessDeniedSecurityException(
                    "Supervisor cannot assign action to department '${assigneeDepartment.displayName}' outside their assignment."
                )
            }
        }

        require(assigneeId.isNotBlank()) { "Assignee ID cannot be blank" }
        require(assigneeName.isNotBlank()) { "Assignee name cannot be blank" }

        ActionWorkflowValidator.validateTransition(existing.status, ActionStatus.ASSIGNED)

        val updated = existing.copy(
            assigneeId = assigneeId,
            assigneeName = assigneeName,
            status = ActionStatus.ASSIGNED,
            updatedAt = System.currentTimeMillis()
        )

        actionRepository.update(updated)
        return updated
    }

    /**
     * Transitions an action from ASSIGNED (or resumed from ON_HOLD) to IN_PROGRESS.
     */
    suspend fun startProgress(
        context: AuthContext,
        actionId: String
    ): ActionRecord {
        val existing = actionRepository.getById(actionId)
            ?: throw NoSuchElementException("Action record not found: '$actionId'")

        securityEnforcer.enforceActionManagement(context, existing.department)
        ActionWorkflowValidator.validateTransition(existing.status, ActionStatus.IN_PROGRESS)

        val updated = existing.copy(
            status = ActionStatus.IN_PROGRESS,
            updatedAt = System.currentTimeMillis()
        )

        actionRepository.update(updated)
        return updated
    }

    /**
     * Suspends an active action with an authoritative reason.
     */
    suspend fun putOnHold(
        context: AuthContext,
        actionId: String,
        reason: String
    ): ActionRecord {
        require(reason.isNotBlank()) { "Hold reason cannot be blank" }

        val existing = actionRepository.getById(actionId)
            ?: throw NoSuchElementException("Action record not found: '$actionId'")

        securityEnforcer.enforceActionManagement(context, existing.department)
        ActionWorkflowValidator.validateTransition(existing.status, ActionStatus.ON_HOLD)

        val updated = existing.copy(
            status = ActionStatus.ON_HOLD,
            holdReason = reason,
            previousStateBeforeHold = existing.status,
            updatedAt = System.currentTimeMillis()
        )

        actionRepository.update(updated)
        return updated
    }

    /**
     * Resumes an ON_HOLD action to an appropriate active state.
     */
    suspend fun resumeAction(
        context: AuthContext,
        actionId: String,
        targetStatus: ActionStatus? = null
    ): ActionRecord {
        val existing = actionRepository.getById(actionId)
            ?: throw NoSuchElementException("Action record not found: '$actionId'")

        if (existing.status != ActionStatus.ON_HOLD) {
            throw IllegalStateException("Cannot resume action in status '${existing.status}'. Action must be ON_HOLD.")
        }

        val target = targetStatus ?: (
            existing.previousStateBeforeHold ?: (
                if (!existing.assigneeId.isNullOrBlank()) ActionStatus.IN_PROGRESS else ActionStatus.OPEN
            )
        )

        securityEnforcer.enforceActionManagement(context, existing.department)
        ActionWorkflowValidator.validateTransition(ActionStatus.ON_HOLD, target)

        val updated = existing.copy(
            status = target,
            holdReason = null,
            previousStateBeforeHold = null,
            updatedAt = System.currentTimeMillis()
        )

        actionRepository.update(updated)
        return updated
    }

    /**
     * Completes execution of an action, awaiting formal closure.
     */
    suspend fun completeAction(
        context: AuthContext,
        actionId: String,
        notes: String? = null
    ): ActionRecord {
        val existing = actionRepository.getById(actionId)
            ?: throw NoSuchElementException("Action record not found: '$actionId'")

        securityEnforcer.enforceActionManagement(context, existing.department)
        ActionWorkflowValidator.validateTransition(existing.status, ActionStatus.COMPLETED)

        val updated = existing.copy(
            status = ActionStatus.COMPLETED,
            completedAt = System.currentTimeMillis(),
            closureNotes = notes,
            updatedAt = System.currentTimeMillis()
        )

        actionRepository.update(updated)
        return updated
    }

    /**
     * Formally closes an action after verification. Terminal state.
     */
    suspend fun closeAction(
        context: AuthContext,
        actionId: String,
        closureNotes: String? = null
    ): ActionRecord {
        val existing = actionRepository.getById(actionId)
            ?: throw NoSuchElementException("Action record not found: '$actionId'")

        securityEnforcer.enforceActionManagement(context, existing.department)
        ActionWorkflowValidator.validateTransition(existing.status, ActionStatus.CLOSED)

        val updated = existing.copy(
            status = ActionStatus.CLOSED,
            closedAt = System.currentTimeMillis(),
            closureNotes = closureNotes ?: existing.closureNotes,
            updatedAt = System.currentTimeMillis()
        )

        actionRepository.update(updated)
        return updated
    }

    /**
     * Terminates an action prematurely. Terminal state.
     */
    suspend fun cancelAction(
        context: AuthContext,
        actionId: String,
        reason: String
    ): ActionRecord {
        require(reason.isNotBlank()) { "Cancellation reason cannot be blank" }

        val existing = actionRepository.getById(actionId)
            ?: throw NoSuchElementException("Action record not found: '$actionId'")

        securityEnforcer.enforceActionManagement(context, existing.department)
        ActionWorkflowValidator.validateTransition(existing.status, ActionStatus.CANCELLED)

        val updated = existing.copy(
            status = ActionStatus.CANCELLED,
            cancellationReason = reason,
            updatedAt = System.currentTimeMillis()
        )

        actionRepository.update(updated)
        return updated
    }

    /**
     * Retrieves an action by ID with department read enforcement.
     */
    suspend fun getActionById(
        context: AuthContext,
        actionId: String
    ): ActionRecord? {
        val action = actionRepository.getById(actionId) ?: return null
        securityEnforcer.enforceDepartmentRead(context, action.department)
        return action
    }

    /**
     * Retrieves actions scoped to the caller's authorized permissions.
     */
    suspend fun getActions(
        context: AuthContext,
        department: Department? = null
    ): List<ActionRecord> {
        securityEnforcer.enforceDepartmentRead(context, department)

        val authenticated = context as AuthContext.Authenticated
        return if (department != null) {
            actionRepository.getByDepartment(department)
        } else {
            if (authenticated.user.role == UserRole.SUPERVISOR) {
                val assigned = authenticated.user.assignedDepartments
                actionRepository.getAll().filter { it.department in assigned }
            } else {
                actionRepository.getAll()
            }
        }
    }
}
