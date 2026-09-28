package com.example.model

/**
 * Authoritative Canonical Defect Master for Divine Stamp Manufacturing MIS.
 *
 * Defines standard defect categories recognized across casting, machining,
 * buffing, and final inspection.
 */
object DefectMaster {

    val STANDARD_DEFECTS: List<String> = listOf(
        "BLOWHOLE",
        "CRACK",
        "DENT",
        "PIN HOLE",
        "POROSITY",
        "SLAG INCLUSION",
        "SHRINKAGE",
        "UNDER FILL",
        "MACHINING DAMAGE",
        "ROUGH BUFFING",
        "BEND",
        "OTHER"
    )

    fun validateDefectName(name: String) {
        require(name.isNotBlank()) { "Defect name cannot be blank" }
        require(name.trim().length >= 2) { "Defect name must be at least 2 characters long: '$name'" }
    }

    fun normalizeDefectName(name: String): String {
        validateDefectName(name)
        val trimmed = name.trim()
        val match = STANDARD_DEFECTS.firstOrNull { it.equals(trimmed, ignoreCase = true) }
        return match ?: trimmed
    }

    fun isStandardDefect(name: String): Boolean {
        if (name.isBlank()) return false
        val trimmed = name.trim()
        return STANDARD_DEFECTS.any { it.equals(trimmed, ignoreCase = true) }
    }

    fun assertStandardDefect(name: String): String {
        validateDefectName(name)
        val trimmed = name.trim()
        return STANDARD_DEFECTS.firstOrNull { it.equals(trimmed, ignoreCase = true) }
            ?: throw IllegalArgumentException(
                "CRITICAL INVARIANT VIOLATION: Invented defect name: '$name'. Defect must be defined in DefectMaster: $STANDARD_DEFECTS"
            )
    }
}
