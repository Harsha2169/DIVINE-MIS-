package com.example.action

/**
 * Authoritative Canonical Action Status for Divine Stamp Manufacturing MIS.
 *
 * Exactly seven statuses:
 * - OPEN: Created, not yet assigned.
 * - ASSIGNED: Assigned to an owner/assignee, awaiting commencement.
 * - IN_PROGRESS: Work actively under execution.
 * - ON_HOLD: Temporarily suspended due to blockers or dependency.
 * - COMPLETED: Work execution finished, awaiting verification and closure.
 * - CLOSED: Formally accepted, closed, and finalized (Terminal).
 * - CANCELLED: Terminated before completion (Terminal).
 */
enum class ActionStatus(val code: String, val displayName: String) {
    OPEN("OPEN", "Open"),
    ASSIGNED("ASSIGNED", "Assigned"),
    IN_PROGRESS("IN_PROGRESS", "In Progress"),
    ON_HOLD("ON_HOLD", "On Hold"),
    COMPLETED("COMPLETED", "Completed"),
    CLOSED("CLOSED", "Closed"),
    CANCELLED("CANCELLED", "Cancelled");

    val isTerminal: Boolean get() = this == CLOSED || this == CANCELLED
    val isActive: Boolean get() = this == OPEN || this == ASSIGNED || this == IN_PROGRESS

    companion object {
        fun fromCode(code: String): ActionStatus {
            val trimmed = code.trim().uppercase()
            return entries.firstOrNull { it.name == trimmed || it.code == trimmed }
                ?: throw IllegalArgumentException(
                    "Unknown action status: '$code'. Canonical statuses are: ${entries.map { it.code }}"
                )
        }
    }
}
