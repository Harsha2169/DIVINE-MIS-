package com.example.analytics

import com.example.core.DataState
import com.example.model.RejectionRecord
import java.math.BigDecimal
import java.math.RoundingMode

/**
 * Authoritative Canonical Pareto Analysis Data Structures and Calculation.
 *
 * Invariants:
 * - Items sorted descending by quantity
 * - Individual percentage contribution = (quantity / total) * 100.0
 * - Cumulative percentage accumulation reaching 100.0%
 * - NO DATA handling when input dataset is empty
 */
data class ParetoItem(
    val defectName: String,
    val quantity: Int,
    val percentage: Double,
    val cumulativePercentage: Double
) {
    val category: String get() = defectName
    val contributionPercentage: Double get() = percentage

    init {
        require(quantity >= 0) { "Pareto item quantity must be non-negative: $quantity" }
        require(percentage in 0.0..100.01) { "Pareto percentage must be between 0 and 100: $percentage" }
        require(cumulativePercentage in 0.0..100.01) { "Pareto cumulative percentage must be between 0 and 100: $cumulativePercentage" }
    }
}

data class ParetoAnalysisResult(
    val filterContext: AnalyticsFilterContext,
    val totalRejectionQuantity: Int,
    val items: List<ParetoItem>,
    val dataState: DataState
) {
    val totalRejections: Int get() = totalRejectionQuantity
}

object ParetoCalculator {

    fun computeFromRecords(
        records: List<RejectionRecord>,
        filterContext: AnalyticsFilterContext
    ): ParetoAnalysisResult {
        if (records.isEmpty()) {
            return ParetoAnalysisResult(
                filterContext = filterContext,
                totalRejectionQuantity = 0,
                items = emptyList(),
                dataState = DataState.NO_DATA
            )
        }

        val defectQuantities: Map<String, Int> = records
            .groupBy { it.defectName }
            .mapValues { entry -> entry.value.sumOf { it.quantity } }

        return computeFromDefectMap(defectQuantities, filterContext)
    }

    fun computeFromDefectMap(
        defectMap: Map<String, Int>,
        filterContext: AnalyticsFilterContext
    ): ParetoAnalysisResult {
        val totalQuantity = defectMap.values.sum()

        if (defectMap.isEmpty() || totalQuantity == 0) {
            val emptyItems = defectMap.entries
                .sortedByDescending { it.value }
                .map { (defect, qty) ->
                    ParetoItem(
                        defectName = defect,
                        quantity = qty,
                        percentage = 0.0,
                        cumulativePercentage = 0.0
                    )
                }

            return ParetoAnalysisResult(
                filterContext = filterContext,
                totalRejectionQuantity = totalQuantity,
                items = emptyItems,
                dataState = if (defectMap.isEmpty()) DataState.NO_DATA else DataState.VALUE
            )
        }

        val sortedDefects = defectMap.entries.sortedByDescending { it.value }
        var runningCumulative = 0.0
        val items = mutableListOf<ParetoItem>()

        for (i in sortedDefects.indices) {
            val (defect, qty) = sortedDefects[i]
            val pct = roundToTwoDecimals((qty.toDouble() / totalQuantity) * 100.0)
            runningCumulative += pct
            if (i == sortedDefects.size - 1) {
                // Ensure the final cumulative percentage locks to 100.0%
                runningCumulative = 100.0
            } else {
                runningCumulative = roundToTwoDecimals(runningCumulative)
            }
            items.add(
                ParetoItem(
                    defectName = defect,
                    quantity = qty,
                    percentage = pct,
                    cumulativePercentage = runningCumulative
                )
            )
        }

        return ParetoAnalysisResult(
            filterContext = filterContext,
            totalRejectionQuantity = totalQuantity,
            items = items,
            dataState = DataState.VALUE
        )
    }

    private fun roundToTwoDecimals(value: Double): Double {
        return BigDecimal(value).setScale(2, RoundingMode.HALF_UP).toDouble()
    }
}
