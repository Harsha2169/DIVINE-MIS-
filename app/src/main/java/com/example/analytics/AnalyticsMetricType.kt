package com.example.analytics

/**
 * Authoritative enumerations of Chart Types and Metric Types supported
 * by Divine Stamp Manufacturing MIS.
 */
enum class ChartType {
    LINE,
    BAR,
    PIE,
    FLOW,
    KPI,
    PARETO
}

enum class AnalyticsMetricType(
    val chartType: ChartType,
    val category: MetricCategory,
    val title: String
) {
    // Production Analytics
    DAILY_PRODUCTION_TREND(ChartType.LINE, MetricCategory.PRODUCTION, "Daily Production Trend"),
    PLAN_VS_ACTUAL(ChartType.BAR, MetricCategory.PRODUCTION, "Plan vs Actual"),
    ACHIEVEMENT_PERCENTAGE(ChartType.KPI, MetricCategory.PRODUCTION, "Achievement %"),
    MODEL_WISE_PRODUCTION(ChartType.BAR, MetricCategory.PRODUCTION, "Model-wise Production"),
    DEPARTMENT_WISE_PRODUCTION(ChartType.BAR, MetricCategory.PRODUCTION, "Department-wise Production"),
    PRODUCTION_FLOW(ChartType.FLOW, MetricCategory.PRODUCTION, "Production Flow"),

    // Rejection Analytics
    REJECTION_TREND(ChartType.LINE, MetricCategory.REJECTION, "Rejection Trend"),
    REJECTION_BY_MODEL(ChartType.BAR, MetricCategory.REJECTION, "Rejection by Model"),
    REJECTION_BY_DEPARTMENT(ChartType.BAR, MetricCategory.REJECTION, "Rejection by Department"),
    REJECTION_BY_SOURCE(ChartType.PIE, MetricCategory.REJECTION, "Rejection by Source"),
    REJECTION_BY_BUSINESS_AREA(ChartType.PIE, MetricCategory.REJECTION, "Rejection by Business Area"),
    LH_VS_RH(ChartType.PIE, MetricCategory.REJECTION, "LH vs RH"),
    DEFECT_WISE_REJECTION(ChartType.BAR, MetricCategory.REJECTION, "Defect-wise Rejection"),
    REJECTION_PARETO(ChartType.PARETO, MetricCategory.REJECTION, "Rejection Pareto"),
    REBUFFING_TREND(ChartType.LINE, MetricCategory.REJECTION, "Rebuffing Trend"),

    // Stock Analytics
    OPENING_VS_CLOSING(ChartType.BAR, MetricCategory.STOCK, "Opening vs Closing"),
    STOCK_TREND(ChartType.LINE, MetricCategory.STOCK, "Stock Trend"),
    MODEL_WISE_STOCK(ChartType.BAR, MetricCategory.STOCK, "Model-wise Stock"),

    // Planning Analytics
    DAILY_PLAN_TREND(ChartType.LINE, MetricCategory.PLANNING, "Daily Plan Trend"),
    MONTHLY_PLAN_VS_ACTUAL(ChartType.BAR, MetricCategory.PLANNING, "Monthly Plan vs Actual"),
    MODEL_WISE_PLAN(ChartType.BAR, MetricCategory.PLANNING, "Model-wise Plan"),
    DEPARTMENT_WISE_PLAN(ChartType.BAR, MetricCategory.PLANNING, "Department-wise Plan")
}

enum class MetricCategory {
    PRODUCTION,
    REJECTION,
    STOCK,
    PLANNING
}
