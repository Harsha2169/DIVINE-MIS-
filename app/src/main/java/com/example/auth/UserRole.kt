package com.example.auth

/**
 * Authoritative Canonical User Roles for Divine Stamp Manufacturing MIS.
 *
 * Exactly four roles:
 * - CEO: Executive read-only oversight across all departments and analytics.
 * - ADMIN: Full system governance, user management, and security settings.
 * - MANAGER: Operational management across all production lines and planning.
 * - SUPERVISOR: Department-scoped data entry limited strictly to assigned department(s).
 */
enum class UserRole(val code: String, val displayName: String) {
    CEO("CEO", "Chief Executive Officer"),
    ADMIN("ADMIN", "System Administrator"),
    MANAGER("MANAGER", "Operations Manager"),
    SUPERVISOR("SUPERVISOR", "Department Supervisor");

    companion object {
        fun fromCode(code: String): UserRole {
            val trimmed = code.trim()
            return entries.firstOrNull { it.code.equals(trimmed, ignoreCase = true) }
                ?: throw IllegalArgumentException(
                    "Unknown or invalid user role: '$code'. Canonical roles are: ${entries.map { it.code }}"
                )
        }

        fun fromCodeOrNull(code: String?): UserRole? {
            if (code.isNullOrBlank()) return null
            val trimmed = code.trim()
            return entries.firstOrNull { it.code.equals(trimmed, ignoreCase = true) }
        }
    }
}
