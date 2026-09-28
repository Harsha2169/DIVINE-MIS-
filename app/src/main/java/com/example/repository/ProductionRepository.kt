package com.example.repository

import com.example.analytics.AnalyticsFilterContext
import com.example.core.BusinessDate
import com.example.model.ProductionRecord

/**
 * Authoritative Repository Interface for Production Data.
 *
 * All implementations (Firestore, Room, In-Memory) and test fixtures
 * MUST strictly implement this interface.
 */
interface ProductionRepository {
    suspend fun insert(record: ProductionRecord)
    suspend fun insertAll(records: List<ProductionRecord>)
    suspend fun getById(id: String): ProductionRecord?
    suspend fun getByFilter(filter: AnalyticsFilterContext): List<ProductionRecord>
    suspend fun getByDateRange(from: BusinessDate, to: BusinessDate): List<ProductionRecord>
    suspend fun deleteById(id: String): Boolean
}
