package com.example.repository.memory

import com.example.analytics.AnalyticsFilterContext
import com.example.core.BusinessDate
import com.example.model.CustomerRequirementRecord
import com.example.model.MonthlyDepartmentPlanRecord
import com.example.model.PlanRecord
import com.example.repository.PlanningRepository
import java.util.concurrent.ConcurrentHashMap

class InMemoryPlanningRepository : PlanningRepository {
    private val records = ConcurrentHashMap<String, PlanRecord>()
    private val customerRequirements = ConcurrentHashMap<String, CustomerRequirementRecord>()
    private val monthlyPlans = ConcurrentHashMap<String, MonthlyDepartmentPlanRecord>()

    // 1. Daily Department Plan
    override suspend fun insert(record: PlanRecord) {
        records[record.id] = record
    }

    override suspend fun insertAll(records: List<PlanRecord>) {
        records.forEach { insert(it) }
    }

    override suspend fun getById(id: String): PlanRecord? = records[id]

    override suspend fun getByFilter(filter: AnalyticsFilterContext): List<PlanRecord> {
        return records.values.filter { record ->
            record.date in filter.fromDate..filter.toDate &&
            (filter.selectedDate == null || record.date == filter.selectedDate) &&
            filter.model.matches(record.model) &&
            filter.department.matches(record.department)
        }.sortedBy { it.date }
    }

    override suspend fun getByDateRange(from: BusinessDate, to: BusinessDate): List<PlanRecord> {
        return records.values.filter { it.date in from..to }.sortedBy { it.date }
    }

    override suspend fun deleteById(id: String): Boolean = records.remove(id) != null

    // 2. Customer End Plan
    override suspend fun insertCustomerRequirement(record: CustomerRequirementRecord) {
        customerRequirements[record.id] = record
    }

    override suspend fun insertAllCustomerRequirements(records: List<CustomerRequirementRecord>) {
        records.forEach { insertCustomerRequirement(it) }
    }

    override suspend fun getCustomerRequirementById(id: String): CustomerRequirementRecord? {
        return customerRequirements[id]
    }

    override suspend fun getCustomerRequirementsByFilter(filter: AnalyticsFilterContext): List<CustomerRequirementRecord> {
        return customerRequirements.values.filter { req ->
            req.date in filter.fromDate..filter.toDate &&
            (filter.selectedDate == null || req.date == filter.selectedDate) &&
            filter.model.matches(req.model)
        }.sortedBy { it.date }
    }

    override suspend fun getCustomerRequirementsByDateRange(
        from: BusinessDate,
        to: BusinessDate
    ): List<CustomerRequirementRecord> {
        return customerRequirements.values.filter { it.date in from..to }.sortedBy { it.date }
    }

    override suspend fun deleteCustomerRequirementById(id: String): Boolean {
        return customerRequirements.remove(id) != null
    }

    // 3. Monthly Department Plan
    override suspend fun insertMonthlyPlan(record: MonthlyDepartmentPlanRecord) {
        monthlyPlans[record.id] = record
    }

    override suspend fun insertAllMonthlyPlans(records: List<MonthlyDepartmentPlanRecord>) {
        records.forEach { insertMonthlyPlan(it) }
    }

    override suspend fun getMonthlyPlanById(id: String): MonthlyDepartmentPlanRecord? {
        return monthlyPlans[id]
    }

    override suspend fun getMonthlyPlansByYearMonth(yearMonth: String): List<MonthlyDepartmentPlanRecord> {
        return monthlyPlans.values.filter { it.yearMonth == yearMonth }.sortedWith(
            compareBy({ it.department.sequenceNumber }, { it.model.ordinal })
        )
    }

    override suspend fun deleteMonthlyPlanById(id: String): Boolean {
        return monthlyPlans.remove(id) != null
    }

    fun clear() {
        records.clear()
        customerRequirements.clear()
        monthlyPlans.clear()
    }
}
