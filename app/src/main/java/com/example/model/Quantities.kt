package com.example.model

/**
 * Authoritative Quantity contracts for Production, Rejection, and Stock.
 *
 * Invariants:
 * - Production: non-negative integer, NO LH/RH.
 * - Rejection: non-negative integer, MUST specify LH or RH (BOTH is invalid).
 * - Stock: opening and closing are non-negative, manually entered.
 *   Never derived automatically from yesterday's closing.
 */
data class ProductionQuantity(val value: Int) {
    init {
        require(value >= 0) { "Production quantity must be non-negative, but got: $value" }
    }
}

data class RejectionQuantity(
    val value: Int,
    val side: RejectionSide
) {
    init {
        require(value >= 0) { "Rejection quantity must be non-negative, but got: $value" }
    }
}

data class StockQuantity(
    val opening: Int,
    val closing: Int
) {
    init {
        require(opening >= 0) { "Stock opening quantity must be non-negative, but got: $opening" }
        require(closing >= 0) { "Stock closing quantity must be non-negative, but got: $closing" }
    }
}
