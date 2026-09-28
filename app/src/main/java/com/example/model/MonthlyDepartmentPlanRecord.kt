package com.example.model

/**
 * Authoritative Canonical Monthly Department Plan Record.
 *
 * Invariants:
 * - Department execution plan != Customer requirement != Daily plan.
 * - Must specify month context in YYYY-MM format.
 * - Model and Department must be canonical.
 * - U180 and MAXR applicability strictly enforced (GIL DELIVERY only!).
 * - plannedQuantity >= 0 (0 is valid data; missing data is NO DATA).
 */
data class MonthlyDepartmentPlanRecord(
    val id: String,
    val yearMonth: String, // Format "YYYY-MM"
    val model: ManufacturingModel,
    val department: Department,
    val plannedQuantity: Int,
    val notes: String? = null,
    val createdAt: Long = System.currentTimeMillis()
) {
    init {
        require(id.isNotBlank()) { "Monthly plan ID cannot be blank" }
        require(yearMonth.matches(Regex("""^\d{4}-(0[1-9]|1[0-2])$"""))) {
            "YearMonth must be in YYYY-MM format (e.g. 2026-09), but was: '$yearMonth'"
        }
        require(plannedQuantity >= 0) { "Planned quantity must be non-negative, but was: $plannedQuantity" }
        ModelApplicabilityValidator.validateApplicability(model, department)
    }
}
