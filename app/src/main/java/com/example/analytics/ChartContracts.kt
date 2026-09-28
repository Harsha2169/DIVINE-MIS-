package com.example.analytics

import com.example.core.BusinessDate
import com.example.core.DataState
import com.example.core.MetricData
import com.example.model.Department

/**
 * Canonical Data Contracts for Chart Presentation.
 *
 * Rules:
 * - A chart MUST NEVER calculate business metrics directly from UI fields.
 * - Calculations belong strictly in the analytics/business layer.
 * - Every chart receives filterContext, full dateRange, canonical points, and dataState.
 */
data class TimeSeriesDataPoint(
    val date: BusinessDate,
    val metric: MetricData<Int>
)

data class CategoricalDataPoint(
    val category: String,
    val metric: MetricData<Number>
)

data class ComparisonDataPoint(
    val label: String,
    val primary: MetricData<Int>,
    val secondary: MetricData<Int>,
    val achievementPercentage: MetricData<Double> = MetricData.NoData
)

data class FlowStepDataPoint(
    val department: Department,
    val sequenceNumber: Int,
    val quantity: MetricData<Int>
)

data class ChartDataSet<T>(
    val metricType: AnalyticsMetricType,
    val filterContext: AnalyticsFilterContext,
    val dateRange: List<BusinessDate>,
    val points: List<T>,
    val overallDataState: DataState
)
