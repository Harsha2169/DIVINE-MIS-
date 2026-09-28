package com.example.repository.memory

import com.example.analytics.AnalyticsFilterContext
import com.example.core.BusinessDate
import com.example.model.ProductionRecord
import com.example.repository.ProductionRepository
import java.util.concurrent.ConcurrentHashMap

class InMemoryProductionRepository : ProductionRepository {
    private val records = ConcurrentHashMap<String, ProductionRecord>()

    override suspend fun insert(record: ProductionRecord) {
        records[record.id] = record
    }

    override suspend fun insertAll(records: List<ProductionRecord>) {
        records.forEach { insert(it) }
    }

    override suspend fun getById(id: String): ProductionRecord? = records[id]

    override suspend fun getByFilter(filter: AnalyticsFilterContext): List<ProductionRecord> {
        return records.values.filter { record ->
            record.date in filter.fromDate..filter.toDate &&
            (filter.selectedDate == null || record.date == filter.selectedDate) &&
            filter.model.matches(record.model) &&
            filter.department.matches(record.department)
        }.sortedBy { it.date }
    }

    override suspend fun getByDateRange(from: BusinessDate, to: BusinessDate): List<ProductionRecord> {
        return records.values.filter { it.date in from..to }.sortedBy { it.date }
    }

    override suspend fun deleteById(id: String): Boolean = records.remove(id) != null

    fun clear() {
        records.clear()
    }
}
