package com.example.model

/**
 * Authoritative Canonical Rejection Master.
 *
 * Sources:
 * - KAMAL
 * - MRN
 * - DSPL
 *
 * Business Areas:
 * - KAMAL
 * - GABRIEL
 * - DSPL
 *
 * Rejection Sides:
 * - LH
 * - RH
 * Note: BOTH is strictly invalid.
 * Note: Production does NOT use LH/RH.
 *
 * Rebuffing Rule:
 * REBUFFING = KAMAL rejection ONLY.
 * MRN and DSPL rejection must never automatically become rebuffing.
 */
enum class RejectionSource(val code: String, val displayName: String) {
    KAMAL("KAMAL", "KAMAL"),
    MRN("MRN", "MRN"),
    DSPL("DSPL", "DSPL");

    val isRebuffable: Boolean get() = this == KAMAL

    companion object {
        fun fromCode(code: String): RejectionSource {
            return entries.firstOrNull {
                it.code.equals(code.trim(), ignoreCase = true) ||
                it.displayName.equals(code.trim(), ignoreCase = true)
            } ?: throw IllegalArgumentException(
                "Unknown rejection source: '$code'. Canonical sources are: ${entries.joinToString { it.code }}"
            )
        }
    }
}

enum class BusinessArea(val code: String, val displayName: String) {
    KAMAL("KAMAL", "KAMAL"),
    GABRIEL("GABRIEL", "GABRIEL"),
    DSPL("DSPL", "DSPL");

    companion object {
        fun fromCode(code: String): BusinessArea {
            return entries.firstOrNull {
                it.code.equals(code.trim(), ignoreCase = true) ||
                it.displayName.equals(code.trim(), ignoreCase = true)
            } ?: throw IllegalArgumentException(
                "Unknown business area: '$code'. Canonical business areas are: ${entries.joinToString { it.code }}"
            )
        }
    }
}

enum class RejectionSide(val code: String, val displayName: String) {
    LH("LH", "LH"),
    RH("RH", "RH");

    companion object {
        fun parse(value: String): RejectionSide {
            val trimmed = value.trim().uppercase()
            if (trimmed == "BOTH") {
                throw IllegalArgumentException("Rejection side 'BOTH' is strictly invalid. Only LH or RH is permitted.")
            }
            return entries.firstOrNull { it.code == trimmed || it.name == trimmed }
                ?: throw IllegalArgumentException("Invalid rejection side: '$value'. Must be LH or RH.")
        }

        fun parseOrNull(value: String?): RejectionSide? {
            if (value == null || value.trim().uppercase() == "BOTH") return null
            val trimmed = value.trim().uppercase()
            return entries.firstOrNull { it.code == trimmed || it.name == trimmed }
        }
    }
}

object RebuffingRule {
    /**
     * Rebuffing is strictly limited to KAMAL rejection source ONLY.
     */
    fun canBeRebuffed(source: RejectionSource): Boolean = source == RejectionSource.KAMAL

    fun validateRebuffingEligibility(source: RejectionSource) {
        if (!canBeRebuffed(source)) {
            throw IllegalArgumentException(
                "Rebuffing is allowed for KAMAL rejection ONLY. Source '${source.displayName}' cannot be rebuffed."
            )
        }
    }
}
