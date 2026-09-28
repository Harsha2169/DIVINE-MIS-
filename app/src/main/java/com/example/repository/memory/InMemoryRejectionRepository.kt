package com.example.repository.memory

import com.example.analytics.AnalyticsFilterContext
import com.example.core.BusinessDate
import com.example.model.RejectionRecord
import com.example.repository.RejectionRepository
import java.util.concurrent.ConcurrentHashMap

class InMemoryRejectionRepository : RejectionRepository {
    private val records = ConcurrentHashMap<String, RejectionRecord>()

    override suspend fun insert(record: RejectionRecord) {
        records[record.id] = record
    }

    override suspend fun insertAll(records: List<RejectionRecord>) {
        records.forEach { insert(it) }
    }

    override suspend fun getById(id: String): RejectionRecord? = records[id]

    override suspend fun getByFilter(filter: AnalyticsFilterContext): List<RejectionRecord> {
        return records.values.filter { record ->
            record.date in filter.fromDate..filter.toDate &&
            (filter.selectedDate == null || record.date == filter.selectedDate) &&
            filter.model.matches(record.model) &&
            filter.department.matches(record.department) &&
            filter.rejectionSource.matches(record.source) &&
            filter.businessArea.matches(record.businessArea) &&
            filter.defect.matches(record.defectName) &&
            filter.side.matches(record.side)
        }.sortedBy { it.date }
    }

    override suspend fun getByDateRange(from: BusinessDate, to: BusinessDate): List<RejectionRecord> {
        return records.values.filter { it.date in from..to }.sortedBy { it.date }
    }

    override suspend fun deleteById(id: String): Boolean = records.remove(id) != null

    fun clear() {
        records.clear()
    }
}
