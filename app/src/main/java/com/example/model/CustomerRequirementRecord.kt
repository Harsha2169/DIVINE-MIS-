package com.example.model

import com.example.core.BusinessDate

/**
 * Authoritative Canonical Customer Requirement Record (Customer End Plan).
 *
 * Invariants:
 * - Customer requirement != Department execution plan != Daily plan != Actual production != GIL delivery.
 * - Customer name cannot be blank.
 * - Model must be a canonical ManufacturingModel (ALL is strictly filter-only and cannot be stored).
 * - requiredQuantity >= 0 (0 is valid data; missing data is NO DATA).
 * - Business date is timezone-safe YYYY-MM-DD.
 */
data class CustomerRequirementRecord(
    val id: String,
    val customerName: String,
    val date: BusinessDate,
    val model: ManufacturingModel,
    val requiredQuantity: Int,
    val notes: String? = null,
    val createdAt: Long = System.currentTimeMillis()
) {
    init {
        require(id.isNotBlank()) { "Customer requirement ID cannot be blank" }
        require(customerName.isNotBlank()) { "Customer name cannot be blank" }
        require(requiredQuantity >= 0) { "Required quantity must be non-negative, but was: $requiredQuantity" }
    }
}
