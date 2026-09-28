package com.example.service

import com.example.analytics.AnalyticsFilterContext
import com.example.core.BusinessDate
import com.example.model.ManufacturingModel
import com.example.model.StockQuantity
import com.example.model.StockRecord
import com.example.repository.StockRepository
import java.util.UUID

class StockService(
    private val stockRepository: StockRepository
) {
    /**
     * Records manually entered stock values.
     * Opening quantity is NEVER automatically derived from yesterday's closing.
     */
    suspend fun recordManualStock(
        date: BusinessDate,
        model: ManufacturingModel,
        openingQuantity: Int,
        closingQuantity: Int,
        notes: String? = null,
        id: String = UUID.randomUUID().toString()
    ): StockRecord {
        val stockQty = StockQuantity(openingQuantity, closingQuantity)
        val record = StockRecord(
            id = id,
            date = date,
            model = model,
            openingQuantity = stockQty.opening,
            closingQuantity = stockQty.closing,
            notes = notes
        )
        stockRepository.insert(record)
        return record
    }

    suspend fun getStock(filter: AnalyticsFilterContext): List<StockRecord> {
        return stockRepository.getByFilter(filter)
    }

    suspend fun getStockByDateRange(from: BusinessDate, to: BusinessDate): List<StockRecord> {
        return stockRepository.getByDateRange(from, to)
    }
}
