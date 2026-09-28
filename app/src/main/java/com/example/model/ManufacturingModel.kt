package com.example.model

/**
 * Authoritative Canonical Manufacturing Model Master.
 *
 * Exactly 7 models:
 * - U86
 * - U180
 * - U244
 * - MAXR
 * - N282-DISC
 * - DRUM
 * - N360
 *
 * CRITICAL RULE:
 * ALL is FILTER-ONLY and is NEVER a member of ManufacturingModel.
 * ALL MUST NEVER be stored in Firestore.
 */
enum class ManufacturingModel(val code: String, val displayName: String) {
    U86("U86", "U86"),
    U180("U180", "U180"),
    U244("U244", "U244"),
    MAXR("MAXR", "MAXR"),
    N282_DISC("N282-DISC", "N282-DISC"),
    DRUM("DRUM", "DRUM"),
    N360("N360", "N360");

    companion object {
        val ALL_MODELS: Set<ManufacturingModel> = entries.toSet()

        fun fromCode(code: String): ManufacturingModel {
            val normalized = code.trim().uppercase()
            if (normalized == "ALL") {
                throw IllegalArgumentException("ALL is filter-only and cannot be resolved as a ManufacturingModel entity.")
            }
            return entries.firstOrNull {
                it.code.equals(code.trim(), ignoreCase = true) ||
                it.name.equals(code.trim(), ignoreCase = true)
            } ?: throw IllegalArgumentException(
                "Unknown model: '$code'. Valid canonical models are: ${entries.joinToString { it.code }}"
            )
        }

        fun fromCodeOrNull(code: String?): ManufacturingModel? {
            if (code == null || code.trim().uppercase() == "ALL") return null
            return entries.firstOrNull {
                it.code.equals(code.trim(), ignoreCase = true) ||
                it.name.equals(code.trim(), ignoreCase = true)
            }
        }
    }
}
