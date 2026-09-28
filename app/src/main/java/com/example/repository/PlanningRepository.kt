package com.example.repository

import com.example.analytics.AnalyticsFilterContext
import com.example.core.BusinessDate
import com.example.model.CustomerRequirementRecord
import com.example.model.MonthlyDepartmentPlanRecord
import com.example.model.PlanRecord

/**
 * Authoritative Repository Interface for Planning Data.
 *
 * Covers all three planning areas:
 * 1. Daily Department Plan (PlanRecord)
 * 2. Customer End Plan (CustomerRequirementRecord)
 * 3. Monthly Department Plan (MonthlyDepartmentPlanRecord)
 *
 * All implementations and test fixtures MUST strictly implement this interface.
 */
interface PlanningRepository {
    // 1. Daily Department Plan
    suspend fun insert(record: PlanRecord)
    suspend fun insertAll(records: List<PlanRecord>)
    suspend fun getById(id: String): PlanRecord?
    suspend fun getByFilter(filter: AnalyticsFilterContext): List<PlanRecord>
    suspend fun getByDateRange(from: BusinessDate, to: BusinessDate): List<PlanRecord>
    suspend fun deleteById(id: String): Boolean

    // 2. Customer End Plan
    suspend fun insertCustomerRequirement(record: CustomerRequirementRecord)
    suspend fun insertAllCustomerRequirements(records: List<CustomerRequirementRecord>)
    suspend fun getCustomerRequirementById(id: String): CustomerRequirementRecord?
    suspend fun getCustomerRequirementsByFilter(filter: AnalyticsFilterContext): List<CustomerRequirementRecord>
    suspend fun getCustomerRequirementsByDateRange(from: BusinessDate, to: BusinessDate): List<CustomerRequirementRecord>
    suspend fun deleteCustomerRequirementById(id: String): Boolean

    // 3. Monthly Department Plan
    suspend fun insertMonthlyPlan(record: MonthlyDepartmentPlanRecord)
    suspend fun insertAllMonthlyPlans(records: List<MonthlyDepartmentPlanRecord>)
    suspend fun getMonthlyPlanById(id: String): MonthlyDepartmentPlanRecord?
    suspend fun getMonthlyPlansByYearMonth(yearMonth: String): List<MonthlyDepartmentPlanRecord>
    suspend fun deleteMonthlyPlanById(id: String): Boolean
}
