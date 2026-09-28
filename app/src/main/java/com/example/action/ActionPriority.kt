package com.example.action

/**
 * Authoritative Canonical Action Priority for Divine Stamp Manufacturing MIS.
 *
 * Exactly four priorities:
 * - CRITICAL: Immediate stoppage/safety risk, highest attention.
 * - HIGH: Significant operational or quality impact.
 * - MEDIUM: Standard operational action.
 * - LOW: Minor improvement or housekeeping.
 */
enum class ActionPriority(val code: String, val displayName: String, val weight: Int) {
    CRITICAL("CRITICAL", "Critical", 4),
    HIGH("HIGH", "High", 3),
    MEDIUM("MEDIUM", "Medium", 2),
    LOW("LOW", "Low", 1);

    companion object {
        fun fromCode(code: String): ActionPriority {
            val trimmed = code.trim().uppercase()
            return entries.firstOrNull { it.name == trimmed || it.code == trimmed }
                ?: throw IllegalArgumentException(
                    "Unknown action priority: '$code'. Canonical priorities are: ${entries.map { it.code }}"
                )
        }
    }
}
