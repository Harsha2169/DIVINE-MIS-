package com.example.analytics

import com.example.core.BusinessDate
import com.example.core.DataState
import com.example.core.MetricData
import com.example.model.Department
import com.example.model.ManufacturingFlow
import com.example.model.ManufacturingModel
import com.example.model.PlanRecord
import com.example.model.ProductionRecord
import com.example.model.RejectionRecord
import com.example.model.RejectionSide
import com.example.model.StockRecord

/**
 * Authoritative Canonical Analytics Service for Divine Stamp Manufacturing MIS.
 *
 * Implements the mathematical and business transformations for all 18 future analytics.
 * Enforces the core rules:
 * - Full uninterrupted chronological date sequences (never skips odd/even days)
 * - 0 is preserved as MetricData.Value(0)
 * - Missing authoritative records produce MetricData.NoData
 * - 0 != NO_DATA
 */
class ManufacturingAnalyticsService {

    // =========================================================================
    // 1. PRODUCTION ANALYTICS
    // =========================================================================

    /**
     * 1. Daily Production Trend — LINE
     * Generates a continuous point for EVERY date from filterContext.fromDate to toDate.
     */
    fun buildDailyProductionTrend(
        records: List<ProductionRecord>,
        filter: AnalyticsFilterContext
    ): ChartDataSet<TimeSeriesDataPoint> {
        val fullDateSequence = BusinessDate.generateSequence(filter.fromDate, filter.toDate)
        val filtered = records.filter { record ->
            record.date in filter.fromDate..filter.toDate &&
            filter.model.matches(record.model) &&
            filter.department.matches(record.department)
        }

        val recordsByDate = filtered.groupBy { it.date }

        val points = fullDateSequence.map { date ->
            val dayRecords = recordsByDate[date]
            val metric: MetricData<Int> = if (dayRecords != null) {
                MetricData.Value(dayRecords.sumOf { it.quantity.value })
            } else {
                MetricData.NoData
            }
            TimeSeriesDataPoint(date = date, metric = metric)
        }

        val overallState = if (filtered.isEmpty()) DataState.NO_DATA else DataState.VALUE

        return ChartDataSet(
            metricType = AnalyticsMetricType.DAILY_PRODUCTION_TREND,
            filterContext = filter,
            dateRange = fullDateSequence,
            points = points,
            overallDataState = overallState
        )
    }

    /**
     * 2. Plan vs Actual — BAR
     */
    fun buildPlanVsActual(
        plans: List<PlanRecord>,
        productions: List<ProductionRecord>,
        filter: AnalyticsFilterContext
    ): ChartDataSet<ComparisonDataPoint> {
        val fullDateSequence = BusinessDate.generateSequence(filter.fromDate, filter.toDate)
        val filteredPlans = plans.filter {
            it.date in filter.fromDate..filter.toDate &&
            filter.model.matches(it.model) &&
            filter.department.matches(it.department)
        }.groupBy { it.date }

        val filteredProds = productions.filter {
            it.date in filter.fromDate..filter.toDate &&
            filter.model.matches(it.model) &&
            filter.department.matches(it.department)
        }.groupBy { it.date }

        val points = fullDateSequence.map { date ->
            val planList = filteredPlans[date]
            val prodList = filteredProds[date]

            val planMetric: MetricData<Int> = if (planList != null) {
                MetricData.Value(planList.sumOf { it.plannedQuantity })
            } else {
                MetricData.NoData
            }

            val prodMetric: MetricData<Int> = if (prodList != null) {
                MetricData.Value(prodList.sumOf { it.quantity.value })
            } else {
                MetricData.NoData
            }

            val achievementMetric: MetricData<Double> = if (planMetric is MetricData.Value && prodMetric is MetricData.Value) {
                if (planMetric.value > 0) {
                    MetricData.Value((prodMetric.value.toDouble() / planMetric.value) * 100.0)
                } else {
                    MetricData.Value(0.0)
                }
            } else {
                MetricData.NoData
            }

            ComparisonDataPoint(
                label = date.toString(),
                primary = planMetric,
                secondary = prodMetric,
                achievementPercentage = achievementMetric
            )
        }

        val state = if (filteredPlans.isEmpty() && filteredProds.isEmpty()) DataState.NO_DATA else DataState.VALUE

        return ChartDataSet(
            metricType = AnalyticsMetricType.PLAN_VS_ACTUAL,
            filterContext = filter,
            dateRange = fullDateSequence,
            points = points,
            overallDataState = state
        )
    }

    /**
     * 3. Achievement % — KPI
     */
    fun calculateTotalAchievementPercentage(
        plans: List<PlanRecord>,
        productions: List<ProductionRecord>,
        filter: AnalyticsFilterContext
    ): MetricData<Double> {
        val filteredPlans = plans.filter {
            it.date in filter.fromDate..filter.toDate &&
            filter.model.matches(it.model) &&
            filter.department.matches(it.department)
        }

        val filteredProds = productions.filter {
            it.date in filter.fromDate..filter.toDate &&
            filter.model.matches(it.model) &&
            filter.department.matches(it.department)
        }

        if (filteredPlans.isEmpty() || filteredProds.isEmpty()) {
            return MetricData.NoData
        }

        val totalPlan = filteredPlans.sumOf { it.plannedQuantity }
        val totalActual = filteredProds.sumOf { it.quantity.value }

        return if (totalPlan > 0) {
            MetricData.Value((totalActual.toDouble() / totalPlan) * 100.0)
        } else {
            MetricData.Value(0.0)
        }
    }

    /**
     * 4. Model-wise Production — BAR
     */
    fun buildModelWiseProduction(
        records: List<ProductionRecord>,
        filter: AnalyticsFilterContext
    ): ChartDataSet<CategoricalDataPoint> {
        val filtered = records.filter {
            it.date in filter.fromDate..filter.toDate &&
            filter.model.matches(it.model) &&
            filter.department.matches(it.department)
        }

        val modelGroups = filtered.groupBy { it.model }
        val activeModels = ManufacturingModel.entries.filter { filter.model.matches(it) }

        val points = activeModels.map { model ->
            val modelRecords = modelGroups[model]
            val metric: MetricData<Number> = if (modelRecords != null) {
                MetricData.Value(modelRecords.sumOf { it.quantity.value })
            } else {
                MetricData.NoData
            }
            CategoricalDataPoint(category = model.displayName, metric = metric)
        }

        val state = if (filtered.isEmpty()) DataState.NO_DATA else DataState.VALUE
        return ChartDataSet(
            metricType = AnalyticsMetricType.MODEL_WISE_PRODUCTION,
            filterContext = filter,
            dateRange = BusinessDate.generateSequence(filter.fromDate, filter.toDate),
            points = points,
            overallDataState = state
        )
    }

    /**
     * 5. Department-wise Production — BAR
     */
    fun buildDepartmentWiseProduction(
        records: List<ProductionRecord>,
        filter: AnalyticsFilterContext
    ): ChartDataSet<CategoricalDataPoint> {
        val filtered = records.filter {
            it.date in filter.fromDate..filter.toDate &&
            filter.model.matches(it.model) &&
            filter.department.matches(it.department)
        }

        val deptGroups = filtered.groupBy { it.department }
        val activeDepts = Department.CANONICAL_SEQUENCE.filter { filter.department.matches(it) }

        val points = activeDepts.map { dept ->
            val deptRecords = deptGroups[dept]
            val metric: MetricData<Number> = if (deptRecords != null) {
                MetricData.Value(deptRecords.sumOf { it.quantity.value })
            } else {
                MetricData.NoData
            }
            CategoricalDataPoint(category = dept.displayName, metric = metric)
        }

        val state = if (filtered.isEmpty()) DataState.NO_DATA else DataState.VALUE
        return ChartDataSet(
            metricType = AnalyticsMetricType.DEPARTMENT_WISE_PRODUCTION,
            filterContext = filter,
            dateRange = BusinessDate.generateSequence(filter.fromDate, filter.toDate),
            points = points,
            overallDataState = state
        )
    }

    /**
     * 6. Production Flow — FLOW
     */
    fun buildProductionFlow(
        records: List<ProductionRecord>,
        filter: AnalyticsFilterContext
    ): ChartDataSet<FlowStepDataPoint> {
        val filtered = records.filter {
            it.date in filter.fromDate..filter.toDate &&
            filter.model.matches(it.model)
        }

        val deptGroups = filtered.groupBy { it.department }

        val points = ManufacturingFlow.getSequence().map { dept ->
            val deptRecords = deptGroups[dept]
            val metric: MetricData<Int> = if (deptRecords != null) {
                MetricData.Value(deptRecords.sumOf { it.quantity.value })
            } else {
                MetricData.NoData
            }
            FlowStepDataPoint(
                department = dept,
                sequenceNumber = dept.sequenceNumber,
                quantity = metric
            )
        }

        val state = if (filtered.isEmpty()) DataState.NO_DATA else DataState.VALUE
        return ChartDataSet(
            metricType = AnalyticsMetricType.PRODUCTION_FLOW,
            filterContext = filter,
            dateRange = BusinessDate.generateSequence(filter.fromDate, filter.toDate),
            points = points,
            overallDataState = state
        )
    }

    // =========================================================================
    // 2. REJECTION ANALYTICS
    // =========================================================================

    /**
     * 7. Rejection Trend — LINE
     */
    fun buildRejectionTrend(
        records: List<RejectionRecord>,
        filter: AnalyticsFilterContext
    ): ChartDataSet<TimeSeriesDataPoint> {
        val fullDateSequence = BusinessDate.generateSequence(filter.fromDate, filter.toDate)
        val filtered = filterRejectionRecords(records, filter)
        val recordsByDate = filtered.groupBy { it.date }

        val points = fullDateSequence.map { date ->
            val dayRecords = recordsByDate[date]
            val metric: MetricData<Int> = if (dayRecords != null) {
                MetricData.Value(dayRecords.sumOf { it.quantity })
            } else {
                MetricData.NoData
            }
            TimeSeriesDataPoint(date = date, metric = metric)
        }

        val overallState = if (filtered.isEmpty()) DataState.NO_DATA else DataState.VALUE

        return ChartDataSet(
            metricType = AnalyticsMetricType.REJECTION_TREND,
            filterContext = filter,
            dateRange = fullDateSequence,
            points = points,
            overallDataState = overallState
        )
    }

    /**
     * 8. Rejection by Model — BAR
     */
    fun buildRejectionByModel(
        records: List<RejectionRecord>,
        filter: AnalyticsFilterContext
    ): ChartDataSet<CategoricalDataPoint> {
        val filtered = filterRejectionRecords(records, filter)
        val modelGroups = filtered.groupBy { it.model }
        val activeModels = ManufacturingModel.entries.filter { filter.model.matches(it) }

        val points = activeModels.map { model ->
            val list = modelGroups[model]
            val metric: MetricData<Number> = if (list != null) {
                MetricData.Value(list.sumOf { it.quantity })
            } else {
                MetricData.NoData
            }
            CategoricalDataPoint(category = model.displayName, metric = metric)
        }

        return ChartDataSet(
            metricType = AnalyticsMetricType.REJECTION_BY_MODEL,
            filterContext = filter,
            dateRange = BusinessDate.generateSequence(filter.fromDate, filter.toDate),
            points = points,
            overallDataState = if (filtered.isEmpty()) DataState.NO_DATA else DataState.VALUE
        )
    }

    /**
     * 9. Rejection by Department — BAR
     */
    fun buildRejectionByDepartment(
        records: List<RejectionRecord>,
        filter: AnalyticsFilterContext
    ): ChartDataSet<CategoricalDataPoint> {
        val filtered = filterRejectionRecords(records, filter)
        val deptGroups = filtered.groupBy { it.department }
        val activeDepts = Department.CANONICAL_SEQUENCE.filter { filter.department.matches(it) }

        val points = activeDepts.map { dept ->
            val list = deptGroups[dept]
            val metric: MetricData<Number> = if (list != null) {
                MetricData.Value(list.sumOf { it.quantity })
            } else {
                MetricData.NoData
            }
            CategoricalDataPoint(category = dept.displayName, metric = metric)
        }

        return ChartDataSet(
            metricType = AnalyticsMetricType.REJECTION_BY_DEPARTMENT,
            filterContext = filter,
            dateRange = BusinessDate.generateSequence(filter.fromDate, filter.toDate),
            points = points,
            overallDataState = if (filtered.isEmpty()) DataState.NO_DATA else DataState.VALUE
        )
    }

    /**
     * 10. Rejection by Source — BAR/PIE
     */
    fun buildRejectionBySource(
        records: List<RejectionRecord>,
        filter: AnalyticsFilterContext
    ): ChartDataSet<CategoricalDataPoint> {
        val filtered = filterRejectionRecords(records, filter)
        val sourceGroups = filtered.groupBy { it.source }

        val points = com.example.model.RejectionSource.entries.map { src ->
            val list = sourceGroups[src]
            val metric: MetricData<Number> = if (list != null) {
                MetricData.Value(list.sumOf { it.quantity })
            } else {
                MetricData.NoData
            }
            CategoricalDataPoint(category = src.displayName, metric = metric)
        }

        return ChartDataSet(
            metricType = AnalyticsMetricType.REJECTION_BY_SOURCE,
            filterContext = filter,
            dateRange = BusinessDate.generateSequence(filter.fromDate, filter.toDate),
            points = points,
            overallDataState = if (filtered.isEmpty()) DataState.NO_DATA else DataState.VALUE
        )
    }

    /**
     * 11. Rejection by Business Area — BAR/PIE
     */
    fun buildRejectionByBusinessArea(
        records: List<RejectionRecord>,
        filter: AnalyticsFilterContext
    ): ChartDataSet<CategoricalDataPoint> {
        val filtered = filterRejectionRecords(records, filter)
        val areaGroups = filtered.groupBy { it.businessArea }

        val points = com.example.model.BusinessArea.entries.map { area ->
            val list = areaGroups[area]
            val metric: MetricData<Number> = if (list != null) {
                MetricData.Value(list.sumOf { it.quantity })
            } else {
                MetricData.NoData
            }
            CategoricalDataPoint(category = area.displayName, metric = metric)
        }

        return ChartDataSet(
            metricType = AnalyticsMetricType.REJECTION_BY_BUSINESS_AREA,
            filterContext = filter,
            dateRange = BusinessDate.generateSequence(filter.fromDate, filter.toDate),
            points = points,
            overallDataState = if (filtered.isEmpty()) DataState.NO_DATA else DataState.VALUE
        )
    }

    /**
     * 12. LH vs RH — BAR/PIE
     */
    fun buildLhVsRh(
        records: List<RejectionRecord>,
        filter: AnalyticsFilterContext
    ): ChartDataSet<CategoricalDataPoint> {
        val filtered = filterRejectionRecords(records, filter)
        val sideGroups = filtered.groupBy { it.side }

        val points = listOf(RejectionSide.LH, RejectionSide.RH).map { side ->
            val list = sideGroups[side]
            val metric: MetricData<Number> = if (list != null) {
                MetricData.Value(list.sumOf { it.quantity })
            } else {
                MetricData.NoData
            }
            CategoricalDataPoint(category = side.displayName, metric = metric)
        }

        return ChartDataSet(
            metricType = AnalyticsMetricType.LH_VS_RH,
            filterContext = filter,
            dateRange = BusinessDate.generateSequence(filter.fromDate, filter.toDate),
            points = points,
            overallDataState = if (filtered.isEmpty()) DataState.NO_DATA else DataState.VALUE
        )
    }

    /**
     * 13. Defect-wise Rejection — BAR
     */
    fun buildDefectWiseRejection(
        records: List<RejectionRecord>,
        filter: AnalyticsFilterContext
    ): ChartDataSet<CategoricalDataPoint> {
        val filtered = filterRejectionRecords(records, filter)
        val defectGroups = filtered.groupBy { it.defectName }

        val points = defectGroups.map { (defect, list) ->
            CategoricalDataPoint(
                category = defect,
                metric = MetricData.Value(list.sumOf { it.quantity })
            )
        }.sortedByDescending { it.metric.valueOrNull()?.toInt() ?: 0 }

        return ChartDataSet(
            metricType = AnalyticsMetricType.DEFECT_WISE_REJECTION,
            filterContext = filter,
            dateRange = BusinessDate.generateSequence(filter.fromDate, filter.toDate),
            points = points,
            overallDataState = if (filtered.isEmpty()) DataState.NO_DATA else DataState.VALUE
        )
    }

    /**
     * 14. Rejection Pareto — BAR + CUMULATIVE %
     */
    fun buildRejectionPareto(
        records: List<RejectionRecord>,
        filter: AnalyticsFilterContext
    ): ParetoAnalysisResult {
        val filtered = filterRejectionRecords(records, filter)
        return ParetoCalculator.computeFromRecords(filtered, filter)
    }

    /**
     * 15. Rebuffing Trend — LINE (Rebuffing is KAMAL rejection only)
     */
    fun buildRebuffingTrend(
        records: List<RejectionRecord>,
        filter: AnalyticsFilterContext
    ): ChartDataSet<TimeSeriesDataPoint> {
        val fullDateSequence = BusinessDate.generateSequence(filter.fromDate, filter.toDate)
        val filtered = filterRejectionRecords(records, filter).filter {
            it.isRebuffed && it.source == com.example.model.RejectionSource.KAMAL
        }
        val recordsByDate = filtered.groupBy { it.date }

        val points = fullDateSequence.map { date ->
            val dayRecords = recordsByDate[date]
            val metric: MetricData<Int> = if (dayRecords != null) {
                MetricData.Value(dayRecords.sumOf { it.quantity })
            } else {
                MetricData.NoData
            }
            TimeSeriesDataPoint(date = date, metric = metric)
        }

        return ChartDataSet(
            metricType = AnalyticsMetricType.REBUFFING_TREND,
            filterContext = filter,
            dateRange = fullDateSequence,
            points = points,
            overallDataState = if (filtered.isEmpty()) DataState.NO_DATA else DataState.VALUE
        )
    }

    // =========================================================================
    // 3. STOCK ANALYTICS
    // =========================================================================

    /**
     * 16. Opening vs Closing — BAR
     */
    fun buildOpeningVsClosing(
        records: List<StockRecord>,
        filter: AnalyticsFilterContext
    ): ChartDataSet<ComparisonDataPoint> {
        val fullDateSequence = BusinessDate.generateSequence(filter.fromDate, filter.toDate)
        val filtered = records.filter {
            it.date in filter.fromDate..filter.toDate &&
            filter.model.matches(it.model)
        }.groupBy { it.date }

        val points = fullDateSequence.map { date ->
            val list = filtered[date]
            val openingMetric: MetricData<Int> = if (list != null) {
                MetricData.Value(list.sumOf { it.openingQuantity })
            } else {
                MetricData.NoData
            }
            val closingMetric: MetricData<Int> = if (list != null) {
                MetricData.Value(list.sumOf { it.closingQuantity })
            } else {
                MetricData.NoData
            }

            ComparisonDataPoint(
                label = date.toString(),
                primary = openingMetric,
                secondary = closingMetric
            )
        }

        return ChartDataSet(
            metricType = AnalyticsMetricType.OPENING_VS_CLOSING,
            filterContext = filter,
            dateRange = fullDateSequence,
            points = points,
            overallDataState = if (filtered.isEmpty()) DataState.NO_DATA else DataState.VALUE
        )
    }

    /**
     * 17. Stock Trend — LINE (Closing stock trend)
     */
    fun buildStockTrend(
        records: List<StockRecord>,
        filter: AnalyticsFilterContext
    ): ChartDataSet<TimeSeriesDataPoint> {
        val fullDateSequence = BusinessDate.generateSequence(filter.fromDate, filter.toDate)
        val filtered = records.filter {
            it.date in filter.fromDate..filter.toDate &&
            filter.model.matches(it.model)
        }.groupBy { it.date }

        val points = fullDateSequence.map { date ->
            val list = filtered[date]
            val metric: MetricData<Int> = if (list != null) {
                MetricData.Value(list.sumOf { it.closingQuantity })
            } else {
                MetricData.NoData
            }
            TimeSeriesDataPoint(date = date, metric = metric)
        }

        return ChartDataSet(
            metricType = AnalyticsMetricType.STOCK_TREND,
            filterContext = filter,
            dateRange = fullDateSequence,
            points = points,
            overallDataState = if (filtered.isEmpty()) DataState.NO_DATA else DataState.VALUE
        )
    }

    /**
     * 18. Model-wise Stock — BAR
     */
    fun buildModelWiseStock(
        records: List<StockRecord>,
        filter: AnalyticsFilterContext
    ): ChartDataSet<CategoricalDataPoint> {
        val filtered = records.filter {
            it.date in filter.fromDate..filter.toDate &&
            filter.model.matches(it.model)
        }

        val modelGroups = filtered.groupBy { it.model }
        val activeModels = ManufacturingModel.entries.filter { filter.model.matches(it) }

        val points = activeModels.map { model ->
            val list = modelGroups[model]
            val metric: MetricData<Number> = if (list != null) {
                MetricData.Value(list.sumOf { it.closingQuantity })
            } else {
                MetricData.NoData
            }
            CategoricalDataPoint(category = model.displayName, metric = metric)
        }

        return ChartDataSet(
            metricType = AnalyticsMetricType.MODEL_WISE_STOCK,
            filterContext = filter,
            dateRange = BusinessDate.generateSequence(filter.fromDate, filter.toDate),
            points = points,
            overallDataState = if (filtered.isEmpty()) DataState.NO_DATA else DataState.VALUE
        )
    }

    // =========================================================================
    // Filter Helper
    // =========================================================================
    private fun filterRejectionRecords(
        records: List<RejectionRecord>,
        filter: AnalyticsFilterContext
    ): List<RejectionRecord> {
        return records.filter { record ->
            record.date in filter.fromDate..filter.toDate &&
            (filter.selectedDate == null || record.date == filter.selectedDate) &&
            filter.model.matches(record.model) &&
            filter.department.matches(record.department) &&
            filter.rejectionSource.matches(record.source) &&
            filter.businessArea.matches(record.businessArea) &&
            filter.defect.matches(record.defectName) &&
            filter.side.matches(record.side)
        }
    }
}
