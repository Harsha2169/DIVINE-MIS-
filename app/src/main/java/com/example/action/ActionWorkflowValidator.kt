package com.example.action

/**
 * Authoritative Canonical Workflow Transition Validator for Action Tracker.
 *
 * Canonical Workflow:
 * OPEN -> ASSIGNED -> IN_PROGRESS -> COMPLETED -> CLOSED
 *
 * Special State Rules:
 * - ON_HOLD: Entered from any active state (OPEN, ASSIGNED, IN_PROGRESS); resumes to an active state.
 * - COMPLETED -> CLOSED: Verification and formal closure.
 * - CANCELLED: Terminal. Entered from any non-terminal state.
 * - CLOSED: Terminal.
 *
 * Arbitrary transitions (e.g. OPEN -> CLOSED, CLOSED -> IN_PROGRESS, CANCELLED -> OPEN)
 * are strictly and deterministically rejected.
 */
object ActionWorkflowValidator {

    fun validateTransition(current: ActionStatus, target: ActionStatus) {
        if (current.isTerminal) {
            throw IllegalStateException(
                "CRITICAL WORKFLOW VIOLATION: Cannot transition from terminal state '$current' to '$target'. State '$current' is terminal."
            )
        }

        if (current == target) {
            // Reassignment in ASSIGNED state is valid; arbitrary self-transitions elsewhere are rejected
            if (current != ActionStatus.ASSIGNED) {
                throw IllegalStateException("Redundant workflow transition: Action is already in state '$current'.")
            }
            return
        }

        val isValid = when (current) {
            ActionStatus.OPEN -> target in listOf(
                ActionStatus.ASSIGNED,
                ActionStatus.ON_HOLD,
                ActionStatus.CANCELLED
            )
            ActionStatus.ASSIGNED -> target in listOf(
                ActionStatus.IN_PROGRESS,
                ActionStatus.ASSIGNED,
                ActionStatus.ON_HOLD,
                ActionStatus.CANCELLED
            )
            ActionStatus.IN_PROGRESS -> target in listOf(
                ActionStatus.COMPLETED,
                ActionStatus.ON_HOLD,
                ActionStatus.CANCELLED
            )
            ActionStatus.ON_HOLD -> target in listOf(
                ActionStatus.IN_PROGRESS,
                ActionStatus.ASSIGNED,
                ActionStatus.OPEN,
                ActionStatus.CANCELLED
            )
            ActionStatus.COMPLETED -> target in listOf(
                ActionStatus.CLOSED,
                ActionStatus.IN_PROGRESS,
                ActionStatus.CANCELLED
            )
            ActionStatus.CLOSED -> false
            ActionStatus.CANCELLED -> false
        }

        if (!isValid) {
            throw IllegalStateException(
                "CRITICAL WORKFLOW VIOLATION: Invalid transition from '$current' to '$target'. Canonical workflow is: OPEN -> ASSIGNED -> IN_PROGRESS -> COMPLETED -> CLOSED."
            )
        }
    }
}
