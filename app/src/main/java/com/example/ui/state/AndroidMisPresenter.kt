package com.example.ui.state

import com.example.action.ActionRecord
import com.example.action.ActionStatus
import com.example.alert.AlertConfiguration
import com.example.alert.AlertRecord
import com.example.auth.AuthContext
import com.example.auth.Permission
import com.example.auth.UserRole
import com.example.core.MetricData
import com.example.shared.CommonFilterState
import com.example.shared.SharedAppDataAccess

/**
 * Android Presenter connecting [SharedAppDataAccess] with Android UI Screen States.
 *
 * Enforces:
 * - Direct consumption of shared repositories and services.
 * - Consistent common filters.
 * - Service-layer RBAC enforcement.
 * - Explicit loading, error, and NO DATA states.
 */
class AndroidMisPresenter(
    val sharedData: SharedAppDataAccess
) {

    suspend fun loadProductionScreen(
        context: AuthContext,
        filter: CommonFilterState
    ): ProductionScreenState {
        // Enforce department read if supervisor
        if (context is AuthContext.Authenticated && context.user.role == UserRole.SUPERVISOR) {
            filter.department?.let { sharedData.securityEnforcer.enforceDepartmentRead(context, it) }
        }

        val records = sharedData.productionRepository.getByFilter(filter.toAnalyticsFilterContext())
        val canAdd = if (context is AuthContext.Authenticated) {
            context.user.role != UserRole.CEO && context.user.isActive
        } else false

        return ProductionScreenState(
            filter = filter,
            records = records,
            isLoading = false,
            isNoData = records.isEmpty(),
            canAddRecord = canAdd
        )
    }

    suspend fun loadRejectionScreen(
        context: AuthContext,
        filter: CommonFilterState
    ): RejectionScreenState {
        if (context is AuthContext.Authenticated && context.user.role == UserRole.SUPERVISOR) {
            filter.department?.let { sharedData.securityEnforcer.enforceDepartmentRead(context, it) }
        }

        val records = sharedData.rejectionRepository.getByFilter(filter.toAnalyticsFilterContext())
        val total = records.sumOf { it.quantity }
        val canAdd = if (context is AuthContext.Authenticated) {
            context.user.role != UserRole.CEO && context.user.isActive
        } else false

        return RejectionScreenState(
            filter = filter,
            records = records,
            totalRejections = total,
            isLoading = false,
            isNoData = records.isEmpty(),
            canAddRecord = canAdd
        )
    }

    suspend fun loadStockScreen(
        context: AuthContext,
        filter: CommonFilterState
    ): StockScreenState {
        val records = sharedData.stockRepository.getByFilter(filter.toAnalyticsFilterContext())
        val canAdd = if (context is AuthContext.Authenticated) {
            context.user.role != UserRole.CEO && context.user.isActive
        } else false

        return StockScreenState(
            filter = filter,
            records = records,
            isLoading = false,
            isNoData = records.isEmpty(),
            canAddRecord = canAdd
        )
    }

    suspend fun loadPlanningScreen(
        context: AuthContext,
        filter: CommonFilterState
    ): PlanningScreenState {
        val filterContext = filter.toAnalyticsFilterContext()
        val daily = sharedData.planningRepository.getByFilter(filterContext)
        val customer = sharedData.planningRepository.getCustomerRequirementsByFilter(filterContext)
        val ym = filter.fromDate.toString().substring(0, 7)
        val monthly = sharedData.planningRepository.getMonthlyPlansByYearMonth(ym)

        val canCreate = if (context is AuthContext.Authenticated) {
            context.user.role != UserRole.CEO && context.user.isActive
        } else false

        val canApprove = if (context is AuthContext.Authenticated) {
            (context.user.role == UserRole.ADMIN || context.user.role == UserRole.MANAGER) && context.user.isActive
        } else false

        val isNoData = daily.isEmpty() && customer.isEmpty() && monthly.isEmpty()

        return PlanningScreenState(
            filter = filter,
            dailyPlans = daily,
            customerPlans = customer,
            monthlyPlans = monthly,
            isLoading = false,
            isNoData = isNoData,
            canCreatePlan = canCreate,
            canApprovePlan = canApprove
        )
    }

    suspend fun loadActionTrackerScreen(
        context: AuthContext,
        filter: CommonFilterState
    ): ActionTrackerScreenState {
        val allActions = sharedData.actionTrackerRepository.getAll()
        val filtered = allActions.filter { action ->
            (filter.department == null || action.department == filter.department) &&
                    (action.businessDate in filter.fromDate..filter.toDate)
        }

        // Supervisor can only view actions in their scope
        val visible = if (context is AuthContext.Authenticated && context.user.role == UserRole.SUPERVISOR) {
            filtered.filter { action -> context.user.hasDepartmentAssignment(action.department) }
        } else {
            filtered
        }

        val canCreate = if (context is AuthContext.Authenticated) {
            context.user.role != UserRole.CEO && context.user.isActive
        } else false

        return ActionTrackerScreenState(
            actions = visible,
            isLoading = false,
            isNoData = visible.isEmpty(),
            canCreateAction = canCreate,
            canModifyAction = canCreate
        )
    }

    suspend fun loadAlertsScreen(
        context: AuthContext,
        filter: CommonFilterState,
        config: AlertConfiguration = AlertConfiguration()
    ): AlertsScreenState {
        val generated = sharedData.alertGeneratorService.generateAlerts(
            businessDate = filter.selectedDate ?: filter.toDate,
            config = config
        )

        // Store or synchronize in alert repository
        sharedData.alertRepository.clear()
        sharedData.alertRepository.insertAll(generated)

        val allAlerts = sharedData.alertRepository.getAll()
        val readableAlerts = sharedData.alertSecurityEnforcer.filterReadableAlerts(context, allAlerts)

        val canModify = if (context is AuthContext.Authenticated) {
            context.user.role != UserRole.CEO && context.user.isActive
        } else false

        return AlertsScreenState(
            alerts = readableAlerts,
            isLoading = false,
            isNoData = readableAlerts.isEmpty(),
            canModifyAlert = canModify
        )
    }

    suspend fun loadReportsScreen(
        context: AuthContext,
        filter: CommonFilterState
    ): ReportsScreenState {
        val pack = sharedData.meetingPackService.generateMeetingPack(filter.toAnalyticsFilterContext())
        val isNoData = pack.executiveSummary.productionSummary is MetricData.NoData &&
                pack.executiveSummary.rejectionSummary is MetricData.NoData

        return ReportsScreenState(
            meetingPack = pack,
            isLoading = false,
            isNoData = isNoData,
            canExport = true
        )
    }
}
