package com.example.model

/**
 * Authoritative Canonical Department Master.
 *
 * Exact Sequence:
 * 1. CASTING
 * 2. POST CASTING
 * 3. M/C
 * 4. BUFFING
 * 5. FINAL
 * 6. KAMAL OK
 * 7. GIL DELIVERY
 *
 * Alternate department names are strictly prohibited.
 */
enum class Department(
    val code: String,
    val displayName: String,
    val sequenceNumber: Int
) {
    CASTING("CASTING", "CASTING", 1),
    POST_CASTING("POST_CASTING", "POST CASTING", 2),
    MACHINING("M_C", "M/C", 3),
    BUFFING("BUFFING", "BUFFING", 4),
    FINAL("FINAL", "FINAL", 5),
    KAMAL_OK("KAMAL_OK", "KAMAL OK", 6),
    GIL_DELIVERY("GIL_DELIVERY", "GIL DELIVERY", 7);

    companion object {
        val CANONICAL_SEQUENCE: List<Department> = entries.sortedBy { it.sequenceNumber }

        fun fromDisplayName(name: String): Department {
            val trimmed = name.trim()
            if (trimmed.equals("ALL", ignoreCase = true)) {
                throw IllegalArgumentException("ALL is filter-only and cannot be resolved as a Department entity.")
            }
            return entries.firstOrNull {
                it.displayName.equals(trimmed, ignoreCase = true) ||
                it.code.equals(trimmed, ignoreCase = true) ||
                it.name.equals(trimmed, ignoreCase = true)
            } ?: throw IllegalArgumentException(
                "Unknown department: '$name'. Canonical departments are: ${CANONICAL_SEQUENCE.joinToString { it.displayName }}"
            )
        }

        fun fromDisplayNameOrNull(name: String?): Department? {
            if (name == null || name.trim().uppercase() == "ALL") return null
            return entries.firstOrNull {
                it.displayName.equals(name.trim(), ignoreCase = true) ||
                it.code.equals(name.trim(), ignoreCase = true) ||
                it.name.equals(name.trim(), ignoreCase = true)
            }
        }
    }
}
