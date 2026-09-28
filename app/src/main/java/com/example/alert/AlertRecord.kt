package com.example.alert

import com.example.core.BusinessDate
import com.example.model.Department
import com.example.model.ManufacturingModel

/**
 * Authoritative Alert Record Domain Model for Divine Stamp Manufacturing MIS.
 *
 * Invariants:
 * - id cannot be blank
 * - message cannot be blank
 * - businessDate must be valid
 * - state transitions strictly follow AlertWorkflowValidator
 */
data class AlertRecord(
    val id: String,
    val type: AlertType,
    val severity: AlertSeverity,
    val businessDate: BusinessDate,
    val department: Department? = null,
    val model: ManufacturingModel? = null,
    val reference: String? = null,
    val message: String,
    val status: AlertStatus = AlertStatus.OPEN,
    val createdAt: String,
    val acknowledgedBy: String? = null,
    val acknowledgedAt: String? = null,
    val resolvedBy: String? = null,
    val resolvedAt: String? = null,
    val resolutionNote: String? = null,
    val dismissedBy: String? = null,
    val dismissedAt: String? = null,
    val dismissalReason: String? = null
) {
    init {
        require(id.isNotBlank()) { "Alert ID cannot be blank" }
        require(message.isNotBlank()) { "Alert message cannot be blank" }

        if (status == AlertStatus.ACKNOWLEDGED) {
            require(!acknowledgedBy.isNullOrBlank()) { "acknowledgedBy is required when status is ACKNOWLEDGED" }
            require(!acknowledgedAt.isNullOrBlank()) { "acknowledgedAt is required when status is ACKNOWLEDGED" }
        }
        if (status == AlertStatus.RESOLVED) {
            require(!resolvedBy.isNullOrBlank()) { "resolvedBy is required when status is RESOLVED" }
            require(!resolvedAt.isNullOrBlank()) { "resolvedAt is required when status is RESOLVED" }
        }
        if (status == AlertStatus.DISMISSED) {
            require(!dismissedBy.isNullOrBlank()) { "dismissedBy is required when status is DISMISSED" }
            require(!dismissedAt.isNullOrBlank()) { "dismissedAt is required when status is DISMISSED" }
        }
    }

    /**
     * Acknowledges the alert.
     */
    fun acknowledge(userId: String, timestamp: String): AlertRecord {
        AlertWorkflowValidator.validateTransition(status, AlertStatus.ACKNOWLEDGED)
        return copy(
            status = AlertStatus.ACKNOWLEDGED,
            acknowledgedBy = userId,
            acknowledgedAt = timestamp
        )
    }

    /**
     * Resolves the alert.
     */
    fun resolve(userId: String, timestamp: String, note: String? = null): AlertRecord {
        AlertWorkflowValidator.validateTransition(status, AlertStatus.RESOLVED)
        return copy(
            status = AlertStatus.RESOLVED,
            resolvedBy = userId,
            resolvedAt = timestamp,
            resolutionNote = note
        )
    }

    /**
     * Dismisses the alert.
     */
    fun dismiss(userId: String, timestamp: String, reason: String? = null): AlertRecord {
        AlertWorkflowValidator.validateTransition(status, AlertStatus.DISMISSED)
        return copy(
            status = AlertStatus.DISMISSED,
            dismissedBy = userId,
            dismissedAt = timestamp,
            dismissalReason = reason
        )
    }
}
