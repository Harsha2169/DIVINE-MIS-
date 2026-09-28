package com.example.analytics

import com.example.core.BusinessDate
import com.example.model.BusinessArea
import com.example.model.Department
import com.example.model.ManufacturingModel
import com.example.model.RejectionSide
import com.example.model.RejectionSource

/**
 * Common Analytics Filter hierarchy.
 *
 * ALL is strictly filter-only and NEVER a stored domain entity.
 */
sealed interface ModelFilter {
    data object All : ModelFilter
    data class Specific(val model: ManufacturingModel) : ModelFilter

    fun matches(candidate: ManufacturingModel): Boolean = when (this) {
        is All -> true
        is Specific -> this.model == candidate
    }
}

sealed interface DepartmentFilter {
    data object All : DepartmentFilter
    data class Specific(val department: Department) : DepartmentFilter

    fun matches(candidate: Department): Boolean = when (this) {
        is All -> true
        is Specific -> this.department == candidate
    }
}

sealed interface RejectionSourceFilter {
    data object All : RejectionSourceFilter
    data class Specific(val source: RejectionSource) : RejectionSourceFilter

    fun matches(candidate: RejectionSource): Boolean = when (this) {
        is All -> true
        is Specific -> this.source == candidate
    }
}

sealed interface BusinessAreaFilter {
    data object All : BusinessAreaFilter
    data class Specific(val area: BusinessArea) : BusinessAreaFilter

    fun matches(candidate: BusinessArea): Boolean = when (this) {
        is All -> true
        is Specific -> this.area == candidate
    }
}

sealed interface DefectFilter {
    data object All : DefectFilter
    data class Specific(val defectName: String) : DefectFilter

    fun matches(candidate: String): Boolean = when (this) {
        is All -> true
        is Specific -> this.defectName.equals(candidate, ignoreCase = true)
    }
}

sealed interface SideFilter {
    data object All : SideFilter
    data class Specific(val side: RejectionSide) : SideFilter

    fun matches(candidate: RejectionSide): Boolean = when (this) {
        is All -> true
        is Specific -> this.side == candidate
    }
}

/**
 * Authoritative Canonical Filter Context for ALL future analytics, dashboards, and reports.
 *
 * Prevents divergent, ad-hoc filter definitions across future modules.
 */
data class AnalyticsFilterContext(
    val fromDate: BusinessDate,
    val toDate: BusinessDate,
    val selectedDate: BusinessDate? = null,
    val model: ModelFilter = ModelFilter.All,
    val department: DepartmentFilter = DepartmentFilter.All,
    val rejectionSource: RejectionSourceFilter = RejectionSourceFilter.All,
    val businessArea: BusinessAreaFilter = BusinessAreaFilter.All,
    val defect: DefectFilter = DefectFilter.All,
    val side: SideFilter = SideFilter.All
) {
    init {
        require(fromDate <= toDate) {
            "Filter context fromDate ($fromDate) cannot be after toDate ($toDate)"
        }
        if (selectedDate != null) {
            require(selectedDate in fromDate..toDate) {
                "selectedDate ($selectedDate) must be within [fromDate ($fromDate) .. toDate ($toDate)]"
            }
        }
    }

    /**
     * Checks if the filter's model and department combination is valid according to domain rules.
     */
    fun isApplicable(): Boolean {
        return if (model is ModelFilter.Specific && department is DepartmentFilter.Specific) {
            com.example.model.ModelApplicabilityValidator.isApplicable(model.model, department.department)
        } else {
            true
        }
    }

    /**
     * Validates that the filter's model and department combination complies with domain rules.
     * Throws IllegalArgumentException if invalid (e.g. U180 or MAXR outside of GIL DELIVERY).
     */
    fun validateApplicability() {
        if (model is ModelFilter.Specific && department is DepartmentFilter.Specific) {
            com.example.model.ModelApplicabilityValidator.validateApplicability(model.model, department.department)
        }
    }

    /**
     * Helper to create a single-day filter context.
     */
    companion object {
        fun singleDay(
            date: BusinessDate,
            model: ModelFilter = ModelFilter.All,
            department: DepartmentFilter = DepartmentFilter.All
        ): AnalyticsFilterContext = AnalyticsFilterContext(
            fromDate = date,
            toDate = date,
            selectedDate = date,
            model = model,
            department = department
        )
    }
}
