package com.example.report

import com.example.action.ActionPriority
import com.example.action.ActionRecord
import com.example.action.ActionStatus
import com.example.analytics.AnalyticsFilterContext
import com.example.analytics.CategoricalDataPoint
import com.example.analytics.ComparisonDataPoint
import com.example.analytics.ParetoAnalysisResult
import com.example.analytics.TimeSeriesDataPoint
import com.example.core.BusinessDate
import com.example.core.MetricData
import com.example.model.Department

/**
 * Summary of actions for executive management reports.
 */
data class ActionReportSummary(
    val totalActions: Int = 0,
    val openActions: Int = 0,
    val criticalActions: Int = 0,
    val highActions: Int = 0,
    val onHoldActions: Int = 0,
    val completedActions: Int = 0,
    val closedActions: Int = 0
)

/**
 * Factual management KPI summary.
 * Strictly distinguishes 0 from NO DATA using [MetricData].
 */
data class ManagementSummary(
    val productionSummary: MetricData<Int> = MetricData.NoData,
    val planSummary: MetricData<Int> = MetricData.NoData,
    val achievementPercentage: MetricData<Double> = MetricData.NoData,
    val rejectionSummary: MetricData<Int> = MetricData.NoData,
    val rejectionPercentage: MetricData<Double> = MetricData.NoData,
    val rebuffingSummary: MetricData<Int> = MetricData.NoData,
    val openingStockSummary: MetricData<Int> = MetricData.NoData,
    val closingStockSummary: MetricData<Int> = MetricData.NoData,
    val actionSummary: ActionReportSummary = ActionReportSummary()
) {
    val actualSummary: MetricData<Int> get() = productionSummary
}

/**
 * Itemized defect investigation record.
 * Missing root cause remains strictly [MetricData.NoData].
 */
data class DefectReportItem(
    val defectName: String,
    val quantity: Int,
    val department: Department,
    val rootCause: MetricData<String> = MetricData.NoData
)

/**
 * Attention level for manufacturing exceptions.
 */
enum class AttentionLevel {
    INFO,
    WARNING,
    CRITICAL
}

/**
 * Factual exception or attention item derived strictly from manufacturing data.
 * No subjective or political ranking.
 */
data class AttentionItem(
    val level: AttentionLevel,
    val category: String,
    val message: String,
    val metricValue: String? = null
)

/**
 * Standard KPI representation in report packs.
 */
data class ReportKpiItem(
    val name: String,
    val value: String,
    val status: String = "NORMAL"
)

/**
 * Daily Management Report Contract.
 */
data class DailyManagementReport(
    val selectedDate: BusinessDate,
    val filter: AnalyticsFilterContext,
    val managementSummary: ManagementSummary,
    val productionByDepartment: List<CategoricalDataPoint>,
    val productionByModel: List<CategoricalDataPoint>,
    val rejectionBySource: List<CategoricalDataPoint>,
    val rejectionByBusinessArea: List<CategoricalDataPoint>,
    val rejectionByDefect: List<CategoricalDataPoint>,
    val rejectionBySide: List<CategoricalDataPoint>,
    val stockOpeningClosing: List<ComparisonDataPoint>,
    val openActions: List<ActionRecord>,
    val criticalHighActions: List<ActionRecord>,
    val defectInvestigations: List<DefectReportItem> = emptyList()
) {
    val planVsActual: MetricData<Double> get() = managementSummary.achievementPercentage
    val achievementPercentage: MetricData<Double> get() = managementSummary.achievementPercentage
}

/**
 * Monthly Management Report Contract.
 */
data class MonthlyManagementReport(
    val fromDate: BusinessDate,
    val toDate: BusinessDate,
    val filter: AnalyticsFilterContext,
    val managementSummary: ManagementSummary,
    val dailyProductionTrend: List<TimeSeriesDataPoint>,
    val dailyRejectionTrend: List<TimeSeriesDataPoint>,
    val planVsActual: List<ComparisonDataPoint>,
    val modelWiseProduction: List<CategoricalDataPoint>,
    val departmentWiseProduction: List<CategoricalDataPoint>,
    val rejectionPareto: ParetoAnalysisResult,
    val stockTrend: List<TimeSeriesDataPoint>,
    val actionStatusSummary: Map<ActionStatus, Int>
) {
    val achievementPercentage: MetricData<Double> get() = managementSummary.achievementPercentage
}

/**
 * Section definitions for Management Meeting Pack.
 */
data class ProductionReportSection(
    val totalProduction: MetricData<Int>,
    val totalPlanned: MetricData<Int>,
    val achievementPercentage: MetricData<Double>,
    val departmentBreakdown: List<CategoricalDataPoint>,
    val modelBreakdown: List<CategoricalDataPoint>
)

data class RejectionReportSection(
    val totalRejections: MetricData<Int>,
    val rejectionPercentage: MetricData<Double>,
    val totalRebuffing: MetricData<Int>,
    val sourceBreakdown: List<CategoricalDataPoint>,
    val businessAreaBreakdown: List<CategoricalDataPoint>,
    val sideBreakdown: List<CategoricalDataPoint>
)

data class ParetoReportSection(
    val paretoResult: ParetoAnalysisResult,
    val topDefects: List<DefectReportItem>
)

data class StockReportSection(
    val openingStock: MetricData<Int>,
    val closingStock: MetricData<Int>,
    val netChange: MetricData<Int>,
    val modelBreakdown: List<CategoricalDataPoint>
)

data class ActionReportSection(
    val summary: ActionReportSummary,
    val openActions: List<ActionRecord>,
    val criticalHighActions: List<ActionRecord>
)

/**
 * Comprehensive Management Meeting Pack.
 */
data class ManagementMeetingPack(
    val title: String = "EXECUTIVE MANUFACTURING MANAGEMENT MEETING PACK",
    val generatedAt: String,
    val businessDate: BusinessDate,
    val dateRange: Pair<BusinessDate, BusinessDate>,
    val filter: AnalyticsFilterContext,
    val executiveSummary: ManagementSummary,
    val kpis: List<ReportKpiItem>,
    val productionSection: ProductionReportSection,
    val rejectionSection: RejectionReportSection,
    val paretoSection: ParetoReportSection,
    val stockSection: StockReportSection,
    val actionSection: ActionReportSection,
    val exceptions: List<AttentionItem>
) {
    val fromDate: BusinessDate get() = dateRange.first
    val toDate: BusinessDate get() = dateRange.second
}
