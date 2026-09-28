package com.example.repository

import com.example.analytics.AnalyticsFilterContext
import com.example.core.BusinessDate
import com.example.model.StockRecord

/**
 * Authoritative Repository Interface for Stock Data.
 *
 * All implementations and test fixtures MUST strictly implement this interface.
 */
interface StockRepository {
    suspend fun insert(record: StockRecord)
    suspend fun insertAll(records: List<StockRecord>)
    suspend fun getById(id: String): StockRecord?
    suspend fun getByFilter(filter: AnalyticsFilterContext): List<StockRecord>
    suspend fun getByDateRange(from: BusinessDate, to: BusinessDate): List<StockRecord>
    suspend fun deleteById(id: String): Boolean
}
