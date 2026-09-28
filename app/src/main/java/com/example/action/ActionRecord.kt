package com.example.action

import com.example.core.BusinessDate
import com.example.model.Department

/**
 * Authoritative Canonical Action Domain Model for Divine Stamp Manufacturing MIS.
 *
 * Invariants:
 * - id cannot be blank
 * - title must be at least 3 characters long
 * - description cannot be blank
 * - createdBy cannot be blank
 * - status must follow ActionStatus enum
 * - priority must follow ActionPriority enum
 * - businessDate must be valid BusinessDate (YYYY-MM-DD)
 * - assigneeId and assigneeName are mandatory when status is ASSIGNED, IN_PROGRESS, or COMPLETED
 * - holdReason is mandatory when status is ON_HOLD
 * - cancellationReason is mandatory when status is CANCELLED
 */
data class ActionRecord(
    val id: String,
    val title: String,
    val description: String,
    val department: Department,
    val status: ActionStatus = ActionStatus.OPEN,
    val priority: ActionPriority = ActionPriority.MEDIUM,
    val businessDate: BusinessDate,
    val dueDate: BusinessDate? = null,
    val createdBy: String,
    val assigneeId: String? = null,
    val assigneeName: String? = null,
    val holdReason: String? = null,
    val previousStateBeforeHold: ActionStatus? = null,
    val cancellationReason: String? = null,
    val closureNotes: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val completedAt: Long? = null,
    val closedAt: Long? = null
) {
    init {
        require(id.isNotBlank()) { "Action ID cannot be blank" }
        require(title.isNotBlank()) { "Action title cannot be blank" }
        require(title.trim().length >= 3) { "Action title must be at least 3 characters long: '$title'" }
        require(description.isNotBlank()) { "Action description cannot be blank" }
        require(createdBy.isNotBlank()) { "Action createdBy cannot be blank" }

        if (status == ActionStatus.ASSIGNED || status == ActionStatus.IN_PROGRESS || status == ActionStatus.COMPLETED) {
            require(!assigneeId.isNullOrBlank()) { "Assignee ID is required when action status is '$status'" }
            require(!assigneeName.isNullOrBlank()) { "Assignee name is required when action status is '$status'" }
        }

        if (status == ActionStatus.ON_HOLD) {
            require(!holdReason.isNullOrBlank()) { "Hold reason is required when action status is ON_HOLD" }
        }

        if (status == ActionStatus.CANCELLED) {
            require(!cancellationReason.isNullOrBlank()) { "Cancellation reason is required when action status is CANCELLED" }
        }
    }
}
