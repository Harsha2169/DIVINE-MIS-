package com.example.model

import com.example.core.BusinessDate

/**
 * Authoritative Canonical Production Record.
 *
 * Invariants:
 * - date must be valid BusinessDate (YYYY-MM-DD)
 * - model applicability to department is strictly enforced
 * - quantity is non-negative and has NO LH/RH concept
 */
data class ProductionRecord(
    val id: String,
    val date: BusinessDate,
    val model: ManufacturingModel,
    val department: Department,
    val quantity: ProductionQuantity,
    val shift: String? = null,
    val notes: String? = null,
    val createdAt: Long = System.currentTimeMillis()
) {
    init {
        require(id.isNotBlank()) { "Production record ID cannot be blank" }
        ModelApplicabilityValidator.validateApplicability(model, department)
    }
}
