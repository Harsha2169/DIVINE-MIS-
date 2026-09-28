package com.example.model

import com.example.core.BusinessDate

/**
 * Authoritative Canonical Production Plan Record.
 *
 * Invariants:
 * - plannedQuantity >= 0
 * - model applicability to department is strictly enforced
 */
data class PlanRecord(
    val id: String,
    val date: BusinessDate,
    val model: ManufacturingModel,
    val department: Department,
    val plannedQuantity: Int,
    val createdAt: Long = System.currentTimeMillis()
) {
    init {
        require(id.isNotBlank()) { "Plan record ID cannot be blank" }
        require(plannedQuantity >= 0) { "Planned quantity must be non-negative, but was: $plannedQuantity" }
        ModelApplicabilityValidator.validateApplicability(model, department)
    }
}

/**
 * Typealias affirming PlanRecord is the authoritative Daily Department Plan.
 * Customer Requirement != Department Execution Plan != Daily Plan != Actual Production.
 */
typealias DailyDepartmentPlanRecord = PlanRecord

