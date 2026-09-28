package com.example.analytics

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.core.BusinessDate
import com.example.core.MetricData
import com.example.repository.PlanningRepository
import com.example.repository.ProductionRepository
import com.example.repository.RejectionRepository
import com.example.repository.StockRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.math.BigDecimal
import java.math.RoundingMode

/**
 * Authoritative Canonical ViewModel for the Core MIS Dashboard.
 *
 * Enforces:
 * - Calculations belong strictly in the analytics/service layer.
 * - UI only consumes pre-calculated DashboardState.
 * - 0 is a real value; NO DATA is a separate state.
 * - No data fabrication or coercion of NO DATA to 0.
 */
class DashboardViewModel(
    private val productionRepository: ProductionRepository,
    private val rejectionRepository: RejectionRepository,
    private val stockRepository: StockRepository,
    private val planningRepository: PlanningRepository,
    private val analyticsService: ManufacturingAnalyticsService = ManufacturingAnalyticsService(),
    private val planningAnalyticsService: PlanningAnalyticsService = PlanningAnalyticsService(),
    initialFilter: AnalyticsFilterContext = AnalyticsFilterContext.singleDay(BusinessDate.today())
) : ViewModel() {

    private val _uiState = MutableStateFlow(DashboardState.loading(initialFilter))
    val uiState: StateFlow<DashboardState> = _uiState.asStateFlow()

    init {
        loadDashboard(initialFilter)
    }

    fun updateFilter(newFilter: AnalyticsFilterContext) {
        loadDashboard(newFilter)
    }

    fun refresh() {
        loadDashboard(_uiState.value.activeFilters)
    }

    private fun loadDashboard(filter: AnalyticsFilterContext) {
        viewModelScope.launch {
            _uiState.value = DashboardState.loading(filter)
            try {
                _uiState.value = buildDashboardState(filter)
            } catch (e: Exception) {
                _uiState.value = DashboardState.error(e.message ?: "Failed to compute dashboard analytics", filter)
            }
        }
    }

    /**
     * Authoritative calculation engine that transforms raw repository records
     * into the canonical DashboardState.
     */
    suspend fun buildDashboardState(filter: AnalyticsFilterContext): DashboardState {
        // Enforce applicability if both specific
        if (!filter.isApplicable()) {
            return DashboardState.error(
                "Model '${(filter.model as ModelFilter.Specific).model.displayName}' is not applicable to department '${(filter.department as DepartmentFilter.Specific).department.displayName}'.",
                filter
            )
        }

        val productions = productionRepository.getByFilter(filter)
        val rejections = rejectionRepository.getByFilter(filter)
        val stocks = stockRepository.getByFilter(filter)
        val plans = planningRepository.getByFilter(filter)

        val hasAnyRecords = productions.isNotEmpty() ||
                            rejections.isNotEmpty() ||
                            stocks.isNotEmpty() ||
                            plans.isNotEmpty()

        if (!hasAnyRecords) {
            return DashboardState.noData(filter)
        }

        // Calculate KPIs strictly distinguishing 0 from NO DATA
        val totalProdMetric: MetricData<Int> = if (productions.isEmpty()) {
            MetricData.NoData
        } else {
            MetricData.Value(productions.sumOf { it.quantity.value })
        }

        val totalPlanMetric: MetricData<Int> = if (plans.isEmpty()) {
            MetricData.NoData
        } else {
            MetricData.Value(plans.sumOf { it.plannedQuantity })
        }

        val achievementMetric: MetricData<Double> = analyticsService.calculateTotalAchievementPercentage(
            plans = plans,
            productions = productions,
            filter = filter
        )

        val totalRejMetric: MetricData<Int> = if (rejections.isEmpty()) {
            MetricData.NoData
        } else {
            MetricData.Value(rejections.sumOf { it.quantity })
        }

        val rejPercentMetric: MetricData<Double> = if (rejections.isEmpty() || productions.isEmpty()) {
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

        val closingStockMetric: MetricData<Int> = if (stocks.isEmpty()) {
            MetricData.NoData
        } else {
            MetricData.Value(stocks.sumOf { it.closingQuantity })
        }

        val kpis = DashboardKpis(
            totalProduction = totalProdMetric,
            totalPlanned = totalPlanMetric,
            achievementPercentage = achievementMetric,
            totalRejections = totalRejMetric,
            rejectionPercentage = rejPercentMetric,
            totalClosingStock = closingStockMetric
        )

        // Build all chart datasets using authoritative services
        val charts = DashboardChartDataSets(
            dailyProductionTrend = analyticsService.buildDailyProductionTrend(productions, filter),
            planVsActual = analyticsService.buildPlanVsActual(plans, productions, filter),
            modelWiseProduction = analyticsService.buildModelWiseProduction(productions, filter),
            departmentWiseProduction = analyticsService.buildDepartmentWiseProduction(productions, filter),
            productionFlow = analyticsService.buildProductionFlow(productions, filter),

            rejectionTrend = analyticsService.buildRejectionTrend(rejections, filter),
            rejectionByModel = analyticsService.buildRejectionByModel(rejections, filter),
            rejectionByDepartment = analyticsService.buildRejectionByDepartment(rejections, filter),
            rejectionBySource = analyticsService.buildRejectionBySource(rejections, filter),
            rejectionByBusinessArea = analyticsService.buildRejectionByBusinessArea(rejections, filter),
            lhVsRh = analyticsService.buildLhVsRh(rejections, filter),
            defectWiseRejection = analyticsService.buildDefectWiseRejection(rejections, filter),
            rejectionPareto = analyticsService.buildRejectionPareto(rejections, filter),
            rebuffingTrend = analyticsService.buildRebuffingTrend(rejections, filter),

            openingVsClosingStock = analyticsService.buildOpeningVsClosing(stocks, filter),
            stockTrend = analyticsService.buildStockTrend(stocks, filter),
            modelWiseStock = analyticsService.buildModelWiseStock(stocks, filter),

            dailyPlanTrend = planningAnalyticsService.buildDailyPlanTrend(plans, filter),
            modelWisePlan = planningAnalyticsService.buildModelWisePlan(plans, filter),
            departmentWisePlan = planningAnalyticsService.buildDepartmentWisePlan(plans, filter)
        )

        return DashboardState(
            isLoading = false,
            errorMessage = null,
            isNoData = false,
            activeFilters = filter,
            kpis = kpis,
            charts = charts
        )
    }
}
