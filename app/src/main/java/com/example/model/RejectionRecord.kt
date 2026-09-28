package com.example.model

import com.example.core.BusinessDate

/**
 * Authoritative Canonical Rejection Record.
 *
 * Invariants:
 * - date must be valid BusinessDate (YYYY-MM-DD)
 * - model applicability to department is strictly validated
 * - side must be LH or RH (BOTH is invalid)
 * - quantity must be non-negative
 * - isRebuffed is ONLY permitted if source is KAMAL
 */
data class RejectionRecord(
    val id: String,
    val date: BusinessDate,
    val model: ManufacturingModel,
    val department: Department,
    val defectName: String,
    val source: RejectionSource,
    val businessArea: BusinessArea,
    val side: RejectionSide,
    val quantity: Int,
    val isRebuffed: Boolean = false,
    val notes: String? = null,
    val createdAt: Long = System.currentTimeMillis()
) {
    init {
        require(id.isNotBlank()) { "Rejection record ID cannot be blank" }
        DefectMaster.validateDefectName(defectName)
        require(quantity >= 0) { "Rejection quantity must be non-negative, but was: $quantity" }
        ModelApplicabilityValidator.validateApplicability(model, department)
        if (isRebuffed) {
            RebuffingRule.validateRebuffingEligibility(source)
        }
    }
}
