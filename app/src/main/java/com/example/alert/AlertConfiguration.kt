package com.example.alert

/**
 * Authoritative Configurable Thresholds for Alert Generation.
 *
 * Invariant:
 * Thresholds must be explicitly supplied through configuration,
 * never hard-coded as unexplained assumptions.
 */
data class AlertConfiguration(
    val minAchievementPercentageThreshold: Double? = 85.0,
    val maxRejectionRatePercentageThreshold: Double? = 5.0,
    val highDefectQuantityThreshold: Int? = 50,
    val criticalDefectNames: Set<String> = emptySet(),
    val minClosingStockThreshold: Int? = null,
    val alertOnMissingProduction: Boolean = false,
    val alertOnMissingPlan: Boolean = false,
    val alertOnMissingStock: Boolean = false,
    val alertOnOverdueActions: Boolean = true,
    val alertOnCriticalActions: Boolean = true
)
