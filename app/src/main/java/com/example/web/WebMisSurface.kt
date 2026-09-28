package com.example.web

import com.example.action.ActionRecord
import com.example.action.ActionStatus
import com.example.alert.AlertConfiguration
import com.example.alert.AlertRecord
import com.example.alert.AlertSeverity
import com.example.alert.AlertStatus
import com.example.auth.AccessDeniedSecurityException
import com.example.auth.AuthContext
import com.example.auth.Permission
import com.example.auth.UserRole
import com.example.core.MetricData
import com.example.model.CustomerRequirementRecord
import com.example.model.MonthlyDepartmentPlanRecord
import com.example.model.PlanRecord
import com.example.model.ProductionRecord
import com.example.model.RejectionRecord
import com.example.model.StockRecord
import com.example.report.ManagementMeetingPack
import com.example.shared.CommonFilterState
import com.example.shared.SharedAppDataAccess
import com.example.ui.navigation.MisNavigationPolicy
import com.example.ui.navigation.MisScreen

/**
 * Platform-independent response envelope for Web API surface.
 */
data class WebDataResponse<T>(
    val success: Boolean,
    val data: T?,
    val isNoData: Boolean = false,
    val errorMessage: String? = null,
    val canWrite: Boolean = false
)

data class WebPlanningData(
    val dailyPlans: List<PlanRecord>,
    val customerRequirements: List<CustomerRequirementRecord>,
    val monthlyPlans: List<MonthlyDepartmentPlanRecord>
)

/**
 * Authoritative Web MIS Application Surface.
 *
 * Consumes the exact same:
 * - Shared repositories and data coordinator
 * - Step 3 RBAC rules and security enforcer
 * - Firestore collection names
 * - Common 9-attribute filter state
 * - Analytics, reports, and alert generators
 */
class WebMisSurface(
    val sharedData: SharedAppDataAccess
) {

    /**
     * Authenticates a web user session.
     */
    suspend fun authenticate(email: String, role: UserRole): AuthContext {
        val user = sharedData.userRepository.getByEmail(email)
            ?: throw AccessDeniedSecurityException("Web user not found for email: '$email'")
        if (!user.isActive) {
            throw AccessDeniedSecurityException("Web user account '${user.uid}' is deactivated.")
        }
        return AuthContext.Authenticated(user)
    }

    /**
     * Retrieves navigation routes permitted for the authenticated web user.
     */
    fun getPermittedScreens(context: AuthContext): List<MisScreen> {
        return MisNavigationPolicy.getAvailableScreens(context)
    }

    /**
     * Evaluates whether the web user is permitted to write or edit operational data.
     */
    fun canWrite(context: AuthContext): Boolean {
        return MisNavigationPolicy.canPerformWrite(context)
    }

    /**
     * Queries production data for the Web MIS surface.
     */
    suspend fun getProductionData(
        context: AuthContext,
        filter: CommonFilterState
    ): WebDataResponse<List<ProductionRecord>> {
        if (context is AuthContext.Authenticated && context.user.role == UserRole.SUPERVISOR) {
            filter.department?.let { sharedData.securityEnforcer.enforceDepartmentRead(context, it) }
        }

        val records = sharedData.productionRepository.getByFilter(filter.toAnalyticsFilterContext())
        val canWrite = canWrite(context)

        return WebDataResponse(
            success = true,
            data = records,
            isNoData = records.isEmpty(),
            canWrite = canWrite
        )
    }

    /**
     * Records new production from the Web surface.
     */
    suspend fun recordProduction(
        context: AuthContext,
        record: ProductionRecord
    ) {
        sharedData.securityEnforcer.enforceDepartmentDataEntry(
            context = context,
            department = record.department,
            permission = Permission.RECORD_PRODUCTION
        )
        sharedData.productionRepository.insert(record)
    }

    /**
     * Queries rejection data for the Web MIS surface.
     */
    suspend fun getRejectionData(
        context: AuthContext,
        filter: CommonFilterState
    ): WebDataResponse<List<RejectionRecord>> {
        if (context is AuthContext.Authenticated && context.user.role == UserRole.SUPERVISOR) {
            filter.department?.let { sharedData.securityEnforcer.enforceDepartmentRead(context, it) }
        }

        val records = sharedData.rejectionRepository.getByFilter(filter.toAnalyticsFilterContext())
        return WebDataResponse(
            success = true,
            data = records,
            isNoData = records.isEmpty(),
            canWrite = canWrite(context)
        )
    }

    /**
     * Queries stock data for the Web MIS surface.
     */
    suspend fun getStockData(
        context: AuthContext,
        filter: CommonFilterState
    ): WebDataResponse<List<StockRecord>> {
        val records = sharedData.stockRepository.getByFilter(filter.toAnalyticsFilterContext())
        return WebDataResponse(
            success = true,
            data = records,
            isNoData = records.isEmpty(),
            canWrite = canWrite(context)
        )
    }

    /**
     * Queries planning data for the Web MIS surface.
     */
    suspend fun getPlanningData(
        context: AuthContext,
        filter: CommonFilterState
    ): WebDataResponse<WebPlanningData> {
        val filterContext = filter.toAnalyticsFilterContext()
        val daily = sharedData.planningRepository.getByFilter(filterContext)
        val customer = sharedData.planningRepository.getCustomerRequirementsByFilter(filterContext)
        val ym = filter.fromDate.toString().substring(0, 7)
        val monthly = sharedData.planningRepository.getMonthlyPlansByYearMonth(ym)

        val isNoData = daily.isEmpty() && customer.isEmpty() && monthly.isEmpty()

        return WebDataResponse(
            success = true,
            data = WebPlanningData(daily, customer, monthly),
            isNoData = isNoData,
            canWrite = canWrite(context)
        )
    }

    /**
     * Queries action tracker records for the Web MIS surface.
     */
    suspend fun getActionTrackerData(
        context: AuthContext,
        filter: CommonFilterState
    ): WebDataResponse<List<ActionRecord>> {
        val all = sharedData.actionTrackerRepository.getAll()
        val filtered = all.filter { action ->
            (filter.department == null || action.department == filter.department) &&
                    (action.businessDate in filter.fromDate..filter.toDate)
        }

        val visible = if (context is AuthContext.Authenticated && context.user.role == UserRole.SUPERVISOR) {
            filtered.filter { context.user.hasDepartmentAssignment(it.department) }
        } else {
            filtered
        }

        return WebDataResponse(
            success = true,
            data = visible,
            isNoData = visible.isEmpty(),
            canWrite = canWrite(context)
        )
    }

    /**
     * Creates an action item from the Web surface.
     */
    suspend fun createAction(
        context: AuthContext,
        action: ActionRecord
    ) {
        sharedData.securityEnforcer.enforceActionManagement(context, action.department)
        sharedData.actionTrackerRepository.insert(action)
    }

    /**
     * Queries alerts for the Web MIS surface.
     */
    suspend fun getAlertsData(
        context: AuthContext,
        filter: CommonFilterState,
        config: AlertConfiguration = AlertConfiguration()
    ): WebDataResponse<List<AlertRecord>> {
        val alerts = sharedData.alertGeneratorService.generateAlerts(
            businessDate = filter.selectedDate ?: filter.toDate,
            config = config
        )

        sharedData.alertRepository.clear()
        sharedData.alertRepository.insertAll(alerts)

        val allAlerts = sharedData.alertRepository.getAll()
        val readable = sharedData.alertSecurityEnforcer.filterReadableAlerts(context, allAlerts)

        return WebDataResponse(
            success = true,
            data = readable,
            isNoData = readable.isEmpty(),
            canWrite = canWrite(context)
        )
    }

    /**
     * Acknowledges an alert from the Web surface.
     */
    suspend fun acknowledgeAlert(
        context: AuthContext,
        alertId: String,
        timestamp: String
    ): AlertRecord {
        val alert = sharedData.alertRepository.getById(alertId)
            ?: throw IllegalArgumentException("Alert not found: '$alertId'")

        sharedData.alertSecurityEnforcer.enforceModifyAlert(context, alert)

        val user = (context as AuthContext.Authenticated).user
        val updated = alert.acknowledge(user.uid, timestamp)
        return sharedData.alertRepository.update(updated)
    }

    /**
     * Generates executive meeting pack for the Web MIS surface.
     */
    suspend fun getMeetingPack(
        context: AuthContext,
        filter: CommonFilterState
    ): WebDataResponse<ManagementMeetingPack> {
        val pack = sharedData.meetingPackService.generateMeetingPack(filter.toAnalyticsFilterContext())
        val isNoData = pack.executiveSummary.productionSummary is MetricData.NoData &&
                pack.executiveSummary.rejectionSummary is MetricData.NoData

        return WebDataResponse(
            success = true,
            data = pack,
            isNoData = isNoData,
            canWrite = false // Meeting pack is an analytical report
        )
    }
}
