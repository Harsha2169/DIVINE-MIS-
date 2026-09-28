package com.example.repository.memory

import com.example.analytics.AnalyticsFilterContext
import com.example.core.BusinessDate
import com.example.model.StockRecord
import com.example.repository.StockRepository
import java.util.concurrent.ConcurrentHashMap

class InMemoryStockRepository : StockRepository {
    private val records = ConcurrentHashMap<String, StockRecord>()

    override suspend fun insert(record: StockRecord) {
        records[record.id] = record
    }

    override suspend fun insertAll(records: List<StockRecord>) {
        records.forEach { insert(it) }
    }

    override suspend fun getById(id: String): StockRecord? = records[id]

    override suspend fun getByFilter(filter: AnalyticsFilterContext): List<StockRecord> {
        return records.values.filter { record ->
            record.date in filter.fromDate..filter.toDate &&
            (filter.selectedDate == null || record.date == filter.selectedDate) &&
            filter.model.matches(record.model)
        }.sortedBy { it.date }
    }

    override suspend fun getByDateRange(from: BusinessDate, to: BusinessDate): List<StockRecord> {
        return records.values.filter { it.date in from..to }.sortedBy { it.date }
    }

    override suspend fun deleteById(id: String): Boolean = records.remove(id) != null

    fun clear() {
        records.clear()
    }
}
