package com.example.alert

class InvalidAlertTransitionException(
    val from: AlertStatus,
    val to: AlertStatus,
    override val message: String = "Invalid alert status transition from '$from' to '$to'."
) : IllegalStateException(message)

/**
 * Authoritative Validator for Alert State Transitions.
 *
 * Invariants:
 * - Terminal states (RESOLVED, DISMISSED) cannot transition to any other status.
 * - Same-state transitions (e.g. OPEN -> OPEN) are invalid.
 * - ACKNOWLEDGED cannot transition back to OPEN.
 * - OPEN can transition to ACKNOWLEDGED, RESOLVED, or DISMISSED.
 * - ACKNOWLEDGED can transition to RESOLVED or DISMISSED.
 */
object AlertWorkflowValidator {

    fun isValidTransition(from: AlertStatus, to: AlertStatus): Boolean {
        if (from == to) return false
        return when (from) {
            AlertStatus.OPEN -> to == AlertStatus.ACKNOWLEDGED || to == AlertStatus.RESOLVED || to == AlertStatus.DISMISSED
            AlertStatus.ACKNOWLEDGED -> to == AlertStatus.RESOLVED || to == AlertStatus.DISMISSED
            AlertStatus.RESOLVED, AlertStatus.DISMISSED -> false
        }
    }

    fun validateTransition(from: AlertStatus, to: AlertStatus) {
        if (!isValidTransition(from, to)) {
            throw InvalidAlertTransitionException(from, to)
        }
    }
}
