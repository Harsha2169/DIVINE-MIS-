package com.example.model

import com.example.core.BusinessDate

/**
 * Authoritative Canonical Stock Record.
 *
 * Invariants:
 * - date must be valid BusinessDate (YYYY-MM-DD)
 * - openingQuantity >= 0 (manually entered, NEVER derived from yesterday's closing)
 * - closingQuantity >= 0 (manually entered)
 */
data class StockRecord(
    val id: String,
    val date: BusinessDate,
    val model: ManufacturingModel,
    val openingQuantity: Int,
    val closingQuantity: Int,
    val notes: String? = null,
    val createdAt: Long = System.currentTimeMillis()
) {
    init {
        require(id.isNotBlank()) { "Stock record ID cannot be blank" }
        require(openingQuantity >= 0) { "Opening quantity must be non-negative, but was: $openingQuantity" }
        require(closingQuantity >= 0) { "Closing quantity must be non-negative, but was: $closingQuantity" }
    }
}
