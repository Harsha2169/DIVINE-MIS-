package com.example.alert

/**
 * Authoritative Canonical Alert Types for Divine Stamp Manufacturing MIS.
 *
 * Covers:
 * - Production below plan
 * - Rejection threshold exceeded
 * - Critical/high rejection issue
 * - Stock condition requiring attention
 * - Overdue action
 * - Critical action
 * - Missing required operational data
 */
enum class AlertType(val code: String, val displayName: String) {
    PRODUCTION_BELOW_PLAN("PRODUCTION_BELOW_PLAN", "Production Below Plan"),
    REJECTION_THRESHOLD_EXCEEDED("REJECTION_THRESHOLD_EXCEEDED", "Rejection Threshold Exceeded"),
    CRITICAL_HIGH_REJECTION_ISSUE("CRITICAL_HIGH_REJECTION_ISSUE", "Critical/High Rejection Issue"),
    STOCK_CONDITION_ATTENTION("STOCK_CONDITION_ATTENTION", "Stock Condition Requiring Attention"),
    OVERDUE_ACTION("OVERDUE_ACTION", "Overdue Action"),
    CRITICAL_ACTION("CRITICAL_ACTION", "Critical Action"),
    MISSING_REQUIRED_OPERATIONAL_DATA("MISSING_REQUIRED_OPERATIONAL_DATA", "Missing Required Operational Data");

    companion object {
        fun fromCode(code: String): AlertType {
            val trimmed = code.trim()
            return entries.firstOrNull { it.code.equals(trimmed, ignoreCase = true) }
                ?: throw IllegalArgumentException(
                    "Unknown or invalid alert type: '$code'. Canonical types are: ${entries.map { it.code }}"
                )
        }

        fun fromCodeOrNull(code: String?): AlertType? {
            if (code.isNullOrBlank()) return null
            val trimmed = code.trim()
            return entries.firstOrNull { it.code.equals(trimmed, ignoreCase = true) }
        }
    }
}
