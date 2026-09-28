package com.example.analytics

import com.example.core.BusinessDate
import com.example.core.DataState
import com.example.core.MetricData
import com.example.model.Department
import com.example.model.ManufacturingModel
import com.example.model.MonthlyDepartmentPlanRecord
import com.example.model.PlanRecord
import com.example.model.ProductionRecord

/**
 * Authoritative Planning Analytics Service for Divine Stamp Manufacturing MIS.
 *
 * Implements clean data transformations for:
 * - Plan vs Actual
 * - Achievement %
 * - Daily Plan Trend
 * - Monthly Plan vs Actual
 * - Model-wise Plan
 * - Department-wise Plan
 *
 * Core Rules:
 * - Full uninterrupted chronological date sequences.
 * - 0 is preserved as MetricData.Value(0).
 * - Missing plan/production data produces MetricData.NoData (never coerced to 0).
 * - Customer requirement != Monthly plan != Daily plan != Actual production.
 */
class PlanningAnalyticsService {

    /**
     * 1. Daily Plan Trend — LINE
     * Continuous points across the entire requested date sequence.
     */
    fun buildDailyPlanTrend(
        plans: List<PlanRecord>,
        filter: AnalyticsFilterContext
    ): ChartDataSet<TimeSeriesDataPoint> {
        val fullDateSequence = BusinessDate.generateSequence(filter.fromDate, filter.toDate)
        val filtered = plans.filter { plan ->
            plan.date in filter.fromDate..filter.toDate &&
            filter.model.matches(plan.model) &&
            filter.department.matches(plan.department)
        }

        val plansByDate = filtered.groupBy { it.date }

        val points = fullDateSequence.map { date ->
            val dayPlans = plansByDate[date]
            val metric: MetricData<Int> = if (dayPlans != null) {
                MetricData.Value(dayPlans.sumOf { it.plannedQuantity })
            } else {
                MetricData.NoData
            }
            TimeSeriesDataPoint(date = date, metric = metric)
        }

        val overallState = if (filtered.isEmpty()) DataState.NO_DATA else DataState.VALUE

        return ChartDataSet(
            metricType = AnalyticsMetricType.DAILY_PLAN_TREND,
            filterContext = filter,
            dateRange = fullDateSequence,
            points = points,
            overallDataState = overallState
        )
    }

    /**
     * 2. Plan vs Actual — BAR (Daily)
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
    fun calculateAchievementPercentage(
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
     * 4. Monthly Plan vs Actual — BAR
     * Groups by Model or Department for the given month.
     */
    fun buildMonthlyPlanVsActual(
        monthlyPlans: List<MonthlyDepartmentPlanRecord>,
        productions: List<ProductionRecord>,
        yearMonth: String,
        filter: AnalyticsFilterContext
    ): ChartDataSet<ComparisonDataPoint> {
        val activeModels = ManufacturingModel.entries.filter { filter.model.matches(it) }

        val filteredMonthlyPlans = monthlyPlans.filter {
            it.yearMonth == yearMonth &&
            filter.model.matches(it.model) &&
            filter.department.matches(it.department)
        }.groupBy { it.model }

        // Filter productions by month substring "YYYY-MM"
        val filteredProds = productions.filter {
            it.date.toString().startsWith(yearMonth) &&
            filter.model.matches(it.model) &&
            filter.department.matches(it.department)
        }.groupBy { it.model }

        val points = activeModels.map { model ->
            val planList = filteredMonthlyPlans[model]
            val prodList = filteredProds[model]

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
                label = model.displayName,
                primary = planMetric,
                secondary = prodMetric,
                achievementPercentage = achievementMetric
            )
        }

        val state = if (filteredMonthlyPlans.isEmpty() && filteredProds.isEmpty()) DataState.NO_DATA else DataState.VALUE

        return ChartDataSet(
            metricType = AnalyticsMetricType.MONTHLY_PLAN_VS_ACTUAL,
            filterContext = filter,
            dateRange = emptyList(),
            points = points,
            overallDataState = state
        )
    }

    /**
     * 5. Model-wise Plan — BAR
     */
    fun buildModelWisePlan(
        plans: List<PlanRecord>,
        filter: AnalyticsFilterContext
    ): ChartDataSet<CategoricalDataPoint> {
        val filtered = plans.filter {
            it.date in filter.fromDate..filter.toDate &&
            filter.model.matches(it.model) &&
            filter.department.matches(it.department)
        }

        val modelGroups = filtered.groupBy { it.model }
        val activeModels = ManufacturingModel.entries.filter { filter.model.matches(it) }

        val points = activeModels.map { model ->
            val modelPlans = modelGroups[model]
            val metric: MetricData<Number> = if (modelPlans != null) {
                MetricData.Value(modelPlans.sumOf { it.plannedQuantity })
            } else {
                MetricData.NoData
            }
            CategoricalDataPoint(category = model.displayName, metric = metric)
        }

        val state = if (filtered.isEmpty()) DataState.NO_DATA else DataState.VALUE
        return ChartDataSet(
            metricType = AnalyticsMetricType.MODEL_WISE_PLAN,
            filterContext = filter,
            dateRange = BusinessDate.generateSequence(filter.fromDate, filter.toDate),
            points = points,
            overallDataState = state
        )
    }

    /**
     * 6. Department-wise Plan — BAR
     */
    fun buildDepartmentWisePlan(
        plans: List<PlanRecord>,
        filter: AnalyticsFilterContext
    ): ChartDataSet<CategoricalDataPoint> {
        val filtered = plans.filter {
            it.date in filter.fromDate..filter.toDate &&
            filter.model.matches(it.model) &&
            filter.department.matches(it.department)
        }

        val deptGroups = filtered.groupBy { it.department }
        val activeDepts = Department.CANONICAL_SEQUENCE.filter { filter.department.matches(it) }

        val points = activeDepts.map { dept ->
            val deptPlans = deptGroups[dept]
            val metric: MetricData<Number> = if (deptPlans != null) {
                MetricData.Value(deptPlans.sumOf { it.plannedQuantity })
            } else {
                MetricData.NoData
            }
            CategoricalDataPoint(category = dept.displayName, metric = metric)
        }

        val state = if (filtered.isEmpty()) DataState.NO_DATA else DataState.VALUE
        return ChartDataSet(
            metricType = AnalyticsMetricType.DEPARTMENT_WISE_PLAN,
            filterContext = filter,
            dateRange = BusinessDate.generateSequence(filter.fromDate, filter.toDate),
            points = points,
            overallDataState = state
        )
    }
}
