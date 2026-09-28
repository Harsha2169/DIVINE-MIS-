package com.example.repository

import com.example.analytics.AnalyticsFilterContext
import com.example.core.BusinessDate
import com.example.model.RejectionRecord

/**
 * Authoritative Repository Interface for Rejection Data.
 *
 * All implementations and test fixtures MUST strictly implement this interface.
 */
interface RejectionRepository {
    suspend fun insert(record: RejectionRecord)
    suspend fun insertAll(records: List<RejectionRecord>)
    suspend fun getById(id: String): RejectionRecord?
    suspend fun getByFilter(filter: AnalyticsFilterContext): List<RejectionRecord>
    suspend fun getByDateRange(from: BusinessDate, to: BusinessDate): List<RejectionRecord>
    suspend fun deleteById(id: String): Boolean
}
