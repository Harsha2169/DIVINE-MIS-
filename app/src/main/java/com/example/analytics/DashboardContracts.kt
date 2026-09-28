package com.example.analytics

import com.example.core.DataState
import com.example.core.MetricData

/**
 * Authoritative Canonical KPI summary for the Core MIS Dashboard.
 *
 * Distinguishes 0 from NO_DATA using [MetricData].
 */
data class DashboardKpis(
    val totalProduction: MetricData<Int> = MetricData.NoData,
    val totalPlanned: MetricData<Int> = MetricData.NoData,
    val achievementPercentage: MetricData<Double> = MetricData.NoData,
    val totalRejections: MetricData<Int> = MetricData.NoData,
    val rejectionPercentage: MetricData<Double> = MetricData.NoData,
    val totalClosingStock: MetricData<Int> = MetricData.NoData
)

/**
 * Container holding all authoritative chart datasets for the Core MIS Dashboard.
 */
data class DashboardChartDataSets(
    // Production Analytics
    val dailyProductionTrend: ChartDataSet<TimeSeriesDataPoint>,
    val planVsActual: ChartDataSet<ComparisonDataPoint>,
    val modelWiseProduction: ChartDataSet<CategoricalDataPoint>,
    val departmentWiseProduction: ChartDataSet<CategoricalDataPoint>,
    val productionFlow: ChartDataSet<FlowStepDataPoint>,

    // Rejection Analytics
    val rejectionTrend: ChartDataSet<TimeSeriesDataPoint>,
    val rejectionByModel: ChartDataSet<CategoricalDataPoint>,
    val rejectionByDepartment: ChartDataSet<CategoricalDataPoint>,
    val rejectionBySource: ChartDataSet<CategoricalDataPoint>,
    val rejectionByBusinessArea: ChartDataSet<CategoricalDataPoint>,
    val lhVsRh: ChartDataSet<CategoricalDataPoint>,
    val defectWiseRejection: ChartDataSet<CategoricalDataPoint>,
    val rejectionPareto: ParetoAnalysisResult,
    val rebuffingTrend: ChartDataSet<TimeSeriesDataPoint>,

    // Stock Analytics
    val openingVsClosingStock: ChartDataSet<ComparisonDataPoint>,
    val stockTrend: ChartDataSet<TimeSeriesDataPoint>,
    val modelWiseStock: ChartDataSet<CategoricalDataPoint>,

    // Planning Analytics
    val dailyPlanTrend: ChartDataSet<TimeSeriesDataPoint>,
    val modelWisePlan: ChartDataSet<CategoricalDataPoint>,
    val departmentWisePlan: ChartDataSet<CategoricalDataPoint>
)

/**
 * Authoritative Canonical Core MIS Dashboard State.
 *
 * Implements:
 * - KPI values/states
 * - Chart datasets
 * - Active filters
 * - Loading state
 * - NO DATA state
 * - Error state
 */
data class DashboardState(
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val isNoData: Boolean = false,
    val activeFilters: AnalyticsFilterContext,
    val kpis: DashboardKpis = DashboardKpis(),
    val charts: DashboardChartDataSets? = null
) {
    val filter: AnalyticsFilterContext get() = activeFilters
    val hasData: Boolean get() = !isLoading && errorMessage == null && !isNoData && charts != null

    // Convenient direct accessors to charts
    val dailyProductionTrend: ChartDataSet<TimeSeriesDataPoint>? get() = charts?.dailyProductionTrend
    val planVsActual: ChartDataSet<ComparisonDataPoint>? get() = charts?.planVsActual
    val modelWiseProduction: ChartDataSet<CategoricalDataPoint>? get() = charts?.modelWiseProduction
    val departmentWiseProduction: ChartDataSet<CategoricalDataPoint>? get() = charts?.departmentWiseProduction
    val productionFlow: ChartDataSet<FlowStepDataPoint>? get() = charts?.productionFlow
    val rejectionTrend: ChartDataSet<TimeSeriesDataPoint>? get() = charts?.rejectionTrend
    val rejectionByModel: ChartDataSet<CategoricalDataPoint>? get() = charts?.rejectionByModel
    val rejectionByDepartment: ChartDataSet<CategoricalDataPoint>? get() = charts?.rejectionByDepartment
    val rejectionBySource: ChartDataSet<CategoricalDataPoint>? get() = charts?.rejectionBySource
    val rejectionByBusinessArea: ChartDataSet<CategoricalDataPoint>? get() = charts?.rejectionByBusinessArea
    val lhVsRh: ChartDataSet<CategoricalDataPoint>? get() = charts?.lhVsRh
    val defectWiseRejection: ChartDataSet<CategoricalDataPoint>? get() = charts?.defectWiseRejection
    val rejectionPareto: ParetoAnalysisResult? get() = charts?.rejectionPareto
    val rebuffingTrend: ChartDataSet<TimeSeriesDataPoint>? get() = charts?.rebuffingTrend
    val openingVsClosingStock: ChartDataSet<ComparisonDataPoint>? get() = charts?.openingVsClosingStock
    val stockTrend: ChartDataSet<TimeSeriesDataPoint>? get() = charts?.stockTrend
    val modelWiseStock: ChartDataSet<CategoricalDataPoint>? get() = charts?.modelWiseStock
    val dailyPlanTrend: ChartDataSet<TimeSeriesDataPoint>? get() = charts?.dailyPlanTrend
    val modelWisePlan: ChartDataSet<CategoricalDataPoint>? get() = charts?.modelWisePlan
    val departmentWisePlan: ChartDataSet<CategoricalDataPoint>? get() = charts?.departmentWisePlan

    companion object {
        fun loading(filter: AnalyticsFilterContext): DashboardState =
            DashboardState(isLoading = true, activeFilters = filter)

        fun error(message: String, filter: AnalyticsFilterContext): DashboardState =
            DashboardState(isLoading = false, errorMessage = message, activeFilters = filter)

        fun noData(filter: AnalyticsFilterContext): DashboardState =
            DashboardState(isLoading = false, isNoData = true, activeFilters = filter)
    }
}
