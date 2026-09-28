package com.example.shared

import com.example.analytics.AnalyticsFilterContext
import com.example.analytics.BusinessAreaFilter
import com.example.analytics.DefectFilter
import com.example.analytics.DepartmentFilter
import com.example.analytics.ModelFilter
import com.example.analytics.RejectionSourceFilter
import com.example.analytics.SideFilter
import com.example.auth.UserProfile
import com.example.auth.UserRole
import com.example.core.BusinessDate
import com.example.model.BusinessArea
import com.example.model.Department
import com.example.model.ManufacturingModel
import com.example.model.RejectionSide
import com.example.model.RejectionSource

/**
 * Authoritative Canonical Filter State shared across Web and Android.
 *
 * Covers all 9 common filters:
 * 1. From Date
 * 2. To Date
 * 3. Selected Date
 * 4. Model
 * 5. Department
 * 6. Rejection Source
 * 7. Business Area
 * 8. Defect
 * 9. Side
 */
data class CommonFilterState(
    val fromDate: BusinessDate,
    val toDate: BusinessDate,
    val selectedDate: BusinessDate? = null,
    val model: ManufacturingModel? = null,
    val department: Department? = null,
    val rejectionSource: RejectionSource? = null,
    val businessArea: BusinessArea? = null,
    val defect: String? = null,
    val side: RejectionSide? = null
) {
    init {
        require(fromDate <= toDate) { "fromDate ($fromDate) cannot be after toDate ($toDate)" }
        if (selectedDate != null) {
            require(selectedDate in fromDate..toDate) {
                "selectedDate ($selectedDate) must be within [fromDate ($fromDate) .. toDate ($toDate)]"
            }
        }
    }

    /**
     * Converts common filter state into the canonical [AnalyticsFilterContext].
     */
    fun toAnalyticsFilterContext(): AnalyticsFilterContext {
        return AnalyticsFilterContext(
            fromDate = fromDate,
            toDate = toDate,
            selectedDate = selectedDate,
            model = model?.let { ModelFilter.Specific(it) } ?: ModelFilter.All,
            department = department?.let { DepartmentFilter.Specific(it) } ?: DepartmentFilter.All,
            rejectionSource = rejectionSource?.let { RejectionSourceFilter.Specific(it) } ?: RejectionSourceFilter.All,
            businessArea = businessArea?.let { BusinessAreaFilter.Specific(it) } ?: BusinessAreaFilter.All,
            defect = defect?.takeIf { it.isNotBlank() }?.let { DefectFilter.Specific(it) } ?: DefectFilter.All,
            side = side?.let { SideFilter.Specific(it) } ?: SideFilter.All
        )
    }

    /**
     * Enforces supervisor department scoping.
     * If user is a supervisor, the department filter is restricted to their assigned department.
     */
    fun withSupervisorScope(user: UserProfile): CommonFilterState {
        if (user.role == UserRole.SUPERVISOR) {
            val assigned = user.assignedDepartments.firstOrNull()
            return copy(department = assigned)
        }
        return this
    }

    companion object {
        fun forDate(date: BusinessDate): CommonFilterState = CommonFilterState(
            fromDate = date,
            toDate = date,
            selectedDate = date
        )

        fun forRange(from: BusinessDate, to: BusinessDate): CommonFilterState = CommonFilterState(
            fromDate = from,
            toDate = to,
            selectedDate = to
        )
    }
}
