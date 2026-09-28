package com.example.ui.state

import com.example.action.ActionRecord
import com.example.alert.AlertRecord
import com.example.model.CustomerRequirementRecord
import com.example.model.MonthlyDepartmentPlanRecord
import com.example.model.PlanRecord
import com.example.model.ProductionRecord
import com.example.model.RejectionRecord
import com.example.model.StockRecord
import com.example.report.ManagementMeetingPack
import com.example.shared.CommonFilterState

data class ProductionScreenState(
    val filter: CommonFilterState,
    val records: List<ProductionRecord> = emptyList(),
    val isLoading: Boolean = false,
    val isNoData: Boolean = false,
    val errorMessage: String? = null,
    val canAddRecord: Boolean = false
)

data class RejectionScreenState(
    val filter: CommonFilterState,
    val records: List<RejectionRecord> = emptyList(),
    val totalRejections: Int = 0,
    val isLoading: Boolean = false,
    val isNoData: Boolean = false,
    val errorMessage: String? = null,
    val canAddRecord: Boolean = false
)

data class StockScreenState(
    val filter: CommonFilterState,
    val records: List<StockRecord> = emptyList(),
    val isLoading: Boolean = false,
    val isNoData: Boolean = false,
    val errorMessage: String? = null,
    val canAddRecord: Boolean = false
)

data class PlanningScreenState(
    val filter: CommonFilterState,
    val dailyPlans: List<PlanRecord> = emptyList(),
    val customerPlans: List<CustomerRequirementRecord> = emptyList(),
    val monthlyPlans: List<MonthlyDepartmentPlanRecord> = emptyList(),
    val isLoading: Boolean = false,
    val isNoData: Boolean = false,
    val errorMessage: String? = null,
    val canCreatePlan: Boolean = false,
    val canApprovePlan: Boolean = false
)

data class ActionTrackerScreenState(
    val actions: List<ActionRecord> = emptyList(),
    val isLoading: Boolean = false,
    val isNoData: Boolean = false,
    val errorMessage: String? = null,
    val canCreateAction: Boolean = false,
    val canModifyAction: Boolean = false
)

data class AlertsScreenState(
    val alerts: List<AlertRecord> = emptyList(),
    val isLoading: Boolean = false,
    val isNoData: Boolean = false,
    val errorMessage: String? = null,
    val canModifyAlert: Boolean = false
)

data class ReportsScreenState(
    val meetingPack: ManagementMeetingPack? = null,
    val isLoading: Boolean = false,
    val isNoData: Boolean = false,
    val errorMessage: String? = null,
    val canExport: Boolean = true
)
