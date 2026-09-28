package com.example.report

import com.example.action.ActionPriority
import com.example.action.ActionRecord
import com.example.action.ActionStatus
import com.example.analytics.AnalyticsFilterContext
import com.example.analytics.DepartmentFilter
import com.example.analytics.ManufacturingAnalyticsService
import com.example.analytics.PlanningAnalyticsService
import com.example.core.BusinessDate
import com.example.core.MetricData
import com.example.model.Department
import com.example.model.RejectionRecord
import com.example.model.RejectionSource
import com.example.repository.ActionTrackerRepository
import com.example.repository.PlanningRepository
import com.example.repository.ProductionRepository
import com.example.repository.RejectionRepository
import com.example.repository.StockRepository
import java.math.BigDecimal
import java.math.RoundingMode

/**
 * Authoritative Service for Generating Management Reports and Meeting Packs.
 *
 * All business calculations are performed deterministically in this service layer.
 * Strictly enforces:
 * - 0 is a valid metric; NO DATA is a separate state.
 * - Missing values are never replaced with invented zeros.
 * - Missing root causes remain MetricData.NoData.
 * - Filter propagation across all domains.
 */
class MeetingPackService(
    private val productionRepository: ProductionRepository,
    private val rejectionRepository: RejectionRepository,
    private val stockRepository: StockRepository,
    private val planningRepository: PlanningRepository,
    private val actionTrackerRepository: ActionTrackerRepository,
    private val analyticsService: ManufacturingAnalyticsService = ManufacturingAnalyticsService(),
    private val planningAnalyticsService: PlanningAnalyticsService = PlanningAnalyticsService()
) {

    /**
     * Filters actions by the date range and department specified in [AnalyticsFilterContext].
     */
    private suspend fun filterActions(filter: AnalyticsFilterContext): List<ActionRecord> {
        val dateActions = actionTrackerRepository.getByDateRange(filter.fromDate, filter.toDate)
        return when (val dept = filter.department) {
            is DepartmentFilter.All -> dateActions
            is DepartmentFilter.Specific -> dateActions.filter { it.department == dept.department }
        }
    }

    /**
     * Computes the action tracker summary from an action list.
     */
    fun computeActionSummary(actions: List<ActionRecord>): ActionReportSummary {
        val openCount = actions.count {
            it.status == ActionStatus.OPEN || it.status == ActionStatus.ASSIGNED || it.status == ActionStatus.IN_PROGRESS
        }
        val criticalCount = actions.count {
            it.priority == ActionPriority.CRITICAL && it.status != ActionStatus.CLOSED && it.status != ActionStatus.CANCELLED
        }
        val highCount = actions.count {
            it.priority == ActionPriority.HIGH && it.status != ActionStatus.CLOSED && it.status != ActionStatus.CANCELLED
        }
        val onHoldCount = actions.count { it.status == ActionStatus.ON_HOLD }
        val completedCount = actions.count { it.status == ActionStatus.COMPLETED }
        val closedCount = actions.count { it.status == ActionStatus.CLOSED }

        return ActionReportSummary(
            totalActions = actions.size,
            openActions = openCount,
            criticalActions = criticalCount,
            highActions = highCount,
            onHoldActions = onHoldCount,
            completedActions = completedCount,
            closedActions = closedCount
        )
    }

    /**
     * Computes the management KPI summary factual values strictly from stored records.
     */
    suspend fun generateManagementSummary(filter: AnalyticsFilterContext): ManagementSummary {
        val productions = productionRepository.getByFilter(filter)
        val plans = planningRepository.getByFilter(filter)
        val rejections = rejectionRepository.getByFilter(filter)
        val stocks = stockRepository.getByFilter(filter)
        val actions = filterActions(filter)

        // Total Production
        val prodSummary: MetricData<Int> = if (productions.isEmpty()) {
            MetricData.NoData
        } else {
            MetricData.Value(productions.sumOf { it.quantity.value })
        }

        // Total Planned
        val planSummary: MetricData<Int> = if (plans.isEmpty()) {
            MetricData.NoData
        } else {
            MetricData.Value(plans.sumOf { it.plannedQuantity })
        }

        // Achievement %
        val achievementPct = analyticsService.calculateTotalAchievementPercentage(plans, productions, filter)

        // Total Rejection
        val rejSummary: MetricData<Int> = if (rejections.isEmpty()) {
            MetricData.NoData
        } else {
            MetricData.Value(rejections.sumOf { it.quantity })
        }

        // Rejection %
        val rejPct: MetricData<Double> = if (rejections.isEmpty() || productions.isEmpty()) {
            MetricData.NoData
        } else {
            val prodSum = productions.sumOf { it.quantity.value }
            val rejSum = rejections.sumOf { it.quantity }
            if (prodSum + rejSum > 0) {
                val pct = (rejSum.toDouble() / (prodSum + rejSum)) * 100.0
                MetricData.Value(BigDecimal(pct).setScale(2, RoundingMode.HALF_UP).toDouble())
            } else {
                MetricData.Value(0.0)
            }
        }

        // Rebuffing Summary (Only KAMAL rejections can be rebuffed)
        val rebuffingSummary: MetricData<Int> = if (rejections.isEmpty()) {
            MetricData.NoData
        } else {
            val rebuffedCount = rejections
                .filter { it.isRebuffed && it.source == RejectionSource.KAMAL }
                .sumOf { it.quantity }
            MetricData.Value(rebuffedCount)
        }

        // Opening & Closing Stock
        val openingStock: MetricData<Int> = if (stocks.isEmpty()) {
            MetricData.NoData
        } else {
            MetricData.Value(stocks.sumOf { it.openingQuantity })
        }

        val closingStock: MetricData<Int> = if (stocks.isEmpty()) {
            MetricData.NoData
        } else {
            MetricData.Value(stocks.sumOf { it.closingQuantity })
        }

        val actionSummary = computeActionSummary(actions)

        return ManagementSummary(
            productionSummary = prodSummary,
            planSummary = planSummary,
            achievementPercentage = achievementPct,
            rejectionSummary = rejSummary,
            rejectionPercentage = rejPct,
            rebuffingSummary = rebuffingSummary,
            openingStockSummary = openingStock,
            closingStockSummary = closingStock,
            actionSummary = actionSummary
        )
    }

    /**
     * Builds DefectReportItems from rejections, preserving NO DATA for missing root cause.
     */
    fun buildDefectReportItems(rejections: List<RejectionRecord>): List<DefectReportItem> {
        if (rejections.isEmpty()) return emptyList()

        return rejections.groupBy { it.defectName to it.department }
            .map { (key, list) ->
                DefectReportItem(
                    defectName = key.first,
                    department = key.second,
                    quantity = list.sumOf { it.quantity },
                    rootCause = MetricData.NoData // Root cause remains NO DATA unless explicitly investigated
                )
            }
            .sortedByDescending { it.quantity }
    }

    /**
     * Generates the Daily Management Report for a specific business date.
     */
    suspend fun generateDailyReport(
        date: BusinessDate,
        filter: AnalyticsFilterContext = AnalyticsFilterContext.singleDay(date)
    ): DailyManagementReport {
        // Enforce date alignment with filter
        val effectiveFilter = filter.copy(fromDate = date, toDate = date)
        val summary = generateManagementSummary(effectiveFilter)

        val productions = productionRepository.getByFilter(effectiveFilter)
        val rejections = rejectionRepository.getByFilter(effectiveFilter)
        val stocks = stockRepository.getByFilter(effectiveFilter)
        val actions = filterActions(effectiveFilter)

        val prodByDept = analyticsService.buildDepartmentWiseProduction(productions, effectiveFilter).points
        val prodByModel = analyticsService.buildModelWiseProduction(productions, effectiveFilter).points

        val rejBySource = analyticsService.buildRejectionBySource(rejections, effectiveFilter).points
        val rejByArea = analyticsService.buildRejectionByBusinessArea(rejections, effectiveFilter).points
        val rejByDefect = analyticsService.buildDefectWiseRejection(rejections, effectiveFilter).points
        val rejBySide = analyticsService.buildLhVsRh(rejections, effectiveFilter).points

        val stockComparison = analyticsService.buildOpeningVsClosing(stocks, effectiveFilter).points

        val openActions = actions.filter {
            it.status == ActionStatus.OPEN || it.status == ActionStatus.ASSIGNED || it.status == ActionStatus.IN_PROGRESS
        }
        val criticalHigh = actions.filter {
            (it.priority == ActionPriority.CRITICAL || it.priority == ActionPriority.HIGH) &&
                    it.status != ActionStatus.CLOSED && it.status != ActionStatus.CANCELLED
        }

        val defectInvestigations = buildDefectReportItems(rejections)

        return DailyManagementReport(
            selectedDate = date,
            filter = effectiveFilter,
            managementSummary = summary,
            productionByDepartment = prodByDept,
            productionByModel = prodByModel,
            rejectionBySource = rejBySource,
            rejectionByBusinessArea = rejByArea,
            rejectionByDefect = rejByDefect,
            rejectionBySide = rejBySide,
            stockOpeningClosing = stockComparison,
            openActions = openActions,
            criticalHighActions = criticalHigh,
            defectInvestigations = defectInvestigations
        )
    }

    /**
     * Generates the Monthly Management Report for a date range.
     */
    suspend fun generateMonthlyReport(
        fromDate: BusinessDate,
        toDate: BusinessDate,
        filter: AnalyticsFilterContext = AnalyticsFilterContext(fromDate = fromDate, toDate = toDate)
    ): MonthlyManagementReport {
        val effectiveFilter = filter.copy(fromDate = fromDate, toDate = toDate)
        val summary = generateManagementSummary(effectiveFilter)

        val productions = productionRepository.getByFilter(effectiveFilter)
        val rejections = rejectionRepository.getByFilter(effectiveFilter)
        val stocks = stockRepository.getByFilter(effectiveFilter)
        val plans = planningRepository.getByFilter(effectiveFilter)
        val actions = filterActions(effectiveFilter)

        val prodTrend = analyticsService.buildDailyProductionTrend(productions, effectiveFilter).points
        val rejTrend = analyticsService.buildRejectionTrend(rejections, effectiveFilter).points
        val planVsAct = analyticsService.buildPlanVsActual(plans, productions, effectiveFilter).points

        val modelProd = analyticsService.buildModelWiseProduction(productions, effectiveFilter).points
        val deptProd = analyticsService.buildDepartmentWiseProduction(productions, effectiveFilter).points

        val pareto = analyticsService.buildRejectionPareto(rejections, effectiveFilter)
        val stockTrend = analyticsService.buildStockTrend(stocks, effectiveFilter).points

        val actionStatusMap = ActionStatus.entries.associateWith { status ->
            actions.count { it.status == status }
        }

        return MonthlyManagementReport(
            fromDate = fromDate,
            toDate = toDate,
            filter = effectiveFilter,
            managementSummary = summary,
            dailyProductionTrend = prodTrend,
            dailyRejectionTrend = rejTrend,
            planVsActual = planVsAct,
            modelWiseProduction = modelProd,
            departmentWiseProduction = deptProd,
            rejectionPareto = pareto,
            stockTrend = stockTrend,
            actionStatusSummary = actionStatusMap
        )
    }

    /**
     * Generates the Management Meeting Pack.
     */
    suspend fun generateMeetingPack(
        filter: AnalyticsFilterContext,
        generatedAt: String = filter.toDate.toString()
    ): ManagementMeetingPack {
        val summary = generateManagementSummary(filter)

        val productions = productionRepository.getByFilter(filter)
        val rejections = rejectionRepository.getByFilter(filter)
        val stocks = stockRepository.getByFilter(filter)
        val plans = planningRepository.getByFilter(filter)
        val actions = filterActions(filter)

        val prodByDept = analyticsService.buildDepartmentWiseProduction(productions, filter).points
        val prodByModel = analyticsService.buildModelWiseProduction(productions, filter).points

        val rejBySource = analyticsService.buildRejectionBySource(rejections, filter).points
        val rejByArea = analyticsService.buildRejectionByBusinessArea(rejections, filter).points
        val rejBySide = analyticsService.buildLhVsRh(rejections, filter).points

        val pareto = analyticsService.buildRejectionPareto(rejections, filter)
        val topDefects = buildDefectReportItems(rejections)

        val stockByModel = analyticsService.buildModelWiseStock(stocks, filter).points
        val netChange: MetricData<Int> = if (stocks.isEmpty()) {
            MetricData.NoData
        } else {
            val open = stocks.sumOf { it.openingQuantity }
            val close = stocks.sumOf { it.closingQuantity }
            MetricData.Value(close - open)
        }

        val openActions = actions.filter {
            it.status == ActionStatus.OPEN || it.status == ActionStatus.ASSIGNED || it.status == ActionStatus.IN_PROGRESS
        }
        val criticalHigh = actions.filter {
            (it.priority == ActionPriority.CRITICAL || it.priority == ActionPriority.HIGH) &&
                    it.status != ActionStatus.CLOSED && it.status != ActionStatus.CANCELLED
        }

        // Manufacturing exceptions/attention items
        val exceptions = mutableListOf<AttentionItem>()

        if (summary.achievementPercentage is MetricData.Value && summary.achievementPercentage.value < 85.0) {
            exceptions.add(
                AttentionItem(
                    level = AttentionLevel.WARNING,
                    category = "ACHIEVEMENT",
                    message = "Plan achievement is below 85% operating threshold",
                    metricValue = "${summary.achievementPercentage.value}%"
                )
            )
        }

        if (summary.rejectionPercentage is MetricData.Value && summary.rejectionPercentage.value > 5.0) {
            exceptions.add(
                AttentionItem(
                    level = AttentionLevel.WARNING,
                    category = "QUALITY",
                    message = "Rejection rate exceeds 5.0% operational target",
                    metricValue = "${summary.rejectionPercentage.value}%"
                )
            )
        }

        if (summary.actionSummary.criticalActions > 0) {
            exceptions.add(
                AttentionItem(
                    level = AttentionLevel.CRITICAL,
                    category = "ACTION_TRACKER",
                    message = "${summary.actionSummary.criticalActions} critical action item(s) require executive intervention",
                    metricValue = "${summary.actionSummary.criticalActions}"
                )
            )
        }

        val kpis = listOf(
            ReportKpiItem(
                name = "Total Production",
                value = when (val p = summary.productionSummary) {
                    is MetricData.Value -> "${p.value} pcs"
                    is MetricData.NoData -> "NO DATA"
                }
            ),
            ReportKpiItem(
                name = "Plan Achievement",
                value = when (val a = summary.achievementPercentage) {
                    is MetricData.Value -> "${a.value}%"
                    is MetricData.NoData -> "NO DATA"
                }
            ),
            ReportKpiItem(
                name = "Rejection Rate",
                value = when (val r = summary.rejectionPercentage) {
                    is MetricData.Value -> "${r.value}%"
                    is MetricData.NoData -> "NO DATA"
                }
            ),
            ReportKpiItem(
                name = "Closing Stock",
                value = when (val s = summary.closingStockSummary) {
                    is MetricData.Value -> "${s.value} pcs"
                    is MetricData.NoData -> "NO DATA"
                }
            ),
            ReportKpiItem(
                name = "Open Actions",
                value = "${summary.actionSummary.openActions}"
            )
        )

        return ManagementMeetingPack(
            generatedAt = generatedAt,
            businessDate = filter.toDate,
            dateRange = Pair(filter.fromDate, filter.toDate),
            filter = filter,
            executiveSummary = summary,
            kpis = kpis,
            productionSection = ProductionReportSection(
                totalProduction = summary.productionSummary,
                totalPlanned = summary.planSummary,
                achievementPercentage = summary.achievementPercentage,
                departmentBreakdown = prodByDept,
                modelBreakdown = prodByModel
            ),
            rejectionSection = RejectionReportSection(
                totalRejections = summary.rejectionSummary,
                rejectionPercentage = summary.rejectionPercentage,
                totalRebuffing = summary.rebuffingSummary,
                sourceBreakdown = rejBySource,
                businessAreaBreakdown = rejByArea,
                sideBreakdown = rejBySide
            ),
            paretoSection = ParetoReportSection(
                paretoResult = pareto,
                topDefects = topDefects
            ),
            stockSection = StockReportSection(
                openingStock = summary.openingStockSummary,
                closingStock = summary.closingStockSummary,
                netChange = netChange,
                modelBreakdown = stockByModel
            ),
            actionSection = ActionReportSection(
                summary = summary.actionSummary,
                openActions = openActions,
                criticalHighActions = criticalHigh
            ),
            exceptions = exceptions
        )
    }
}
