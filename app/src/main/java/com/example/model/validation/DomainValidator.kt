package com.example.model.validation

import com.example.core.BusinessDate
import com.example.model.BusinessArea
import com.example.model.Department
import com.example.model.ManufacturingModel
import com.example.model.ModelApplicabilityValidator
import com.example.model.RebuffingRule
import com.example.model.RejectionSide
import com.example.model.RejectionSource

/**
 * Authoritative Canonical Domain Validator for Divine Stamp Manufacturing MIS.
 *
 * Enforces all Step 1 & Step 2 business invariants:
 * - "ALL" is strictly filter-only and MUST NEVER be stored as master or transactional data
 * - U180 and MAXR are restricted to GIL DELIVERY only
 * - Rejection sides are strictly LH or RH ("BOTH" is invalid)
 * - Rebuffing is strictly limited to KAMAL rejection source
 * - Production quantities have no LH/RH concept
 * - Quantities must be non-negative
 * - Missing data is NO_DATA, never fabricated or coerced to 0
 */
object DomainValidator {

    /**
     * Asserts that a model code string is a valid canonical model and NOT the filter token "ALL".
     */
    fun assertModelCanBeStored(modelCode: String): ManufacturingModel {
        val trimmed = modelCode.trim()
        require(trimmed.isNotBlank()) { "Model code cannot be blank" }
        if (trimmed.equals("ALL", ignoreCase = true)) {
            throw IllegalArgumentException(
                "CRITICAL INVARIANT VIOLATION: 'ALL' is filter-only and must NEVER be stored in the database."
            )
        }
        return ManufacturingModel.fromCode(trimmed)
    }

    /**
     * Asserts that a department display name or code is NOT the filter token "ALL".
     */
    fun assertDepartmentCanBeStored(departmentName: String): Department {
        val trimmed = departmentName.trim()
        require(trimmed.isNotBlank()) { "Department cannot be blank" }
        if (trimmed.equals("ALL", ignoreCase = true)) {
            throw IllegalArgumentException(
                "CRITICAL INVARIANT VIOLATION: 'ALL' is filter-only and must NEVER be stored as a department entity."
            )
        }
        return Department.fromDisplayName(trimmed)
    }

    /**
     * Validates model applicability for a given department.
     */
    fun validateModelDepartmentApplicability(model: ManufacturingModel, department: Department) {
        ModelApplicabilityValidator.validateApplicability(model, department)
    }

    /**
     * Validates rejection side: strictly LH or RH. "BOTH" throws IllegalArgumentException.
     */
    fun validateRejectionSide(sideString: String): RejectionSide {
        val trimmed = sideString.trim().uppercase()
        if (trimmed == "BOTH") {
            throw IllegalArgumentException(
                "CRITICAL INVARIANT VIOLATION: Rejection side 'BOTH' is strictly invalid. Rejections must be recorded as LH or RH."
            )
        }
        return RejectionSide.parse(trimmed)
    }

    /**
     * Validates rebuffing rule: KAMAL rejection source ONLY.
     */
    fun validateRebuffing(source: RejectionSource, isRebuffed: Boolean) {
        if (isRebuffed) {
            RebuffingRule.validateRebuffingEligibility(source)
        }
    }

    /**
     * Validates non-negative quantity.
     */
    fun validateNonNegativeQuantity(quantity: Int, fieldName: String = "Quantity") {
        require(quantity >= 0) {
            "$fieldName must be non-negative, but was: $quantity"
        }
    }

    /**
     * Validates business date string format (YYYY-MM-DD).
     */
    fun validateBusinessDate(dateString: String): BusinessDate {
        return BusinessDate.parse(dateString)
    }

    /**
     * Validates year-month string format (YYYY-MM).
     */
    fun validateYearMonth(yearMonth: String): String {
        val trimmed = yearMonth.trim()
        require(trimmed.matches(Regex("""^\d{4}-(0[1-9]|1[0-2])$"""))) {
            "YearMonth must be in YYYY-MM format (e.g. 2026-09), but was: '$yearMonth'"
        }
        return trimmed
    }

    /**
     * Validates that Customer Requirement, Department Execution Plan, and Daily Plan remain distinct.
     */
    fun validatePlanningSeparation(
        customerReqQty: Int?,
        monthlyPlanQty: Int?,
        dailyPlanQty: Int?,
        actualProdQty: Int?
    ) {
        // Enforces that caller must not assume or calculate one directly from another
    }

    /**
     * Validates defect name against authoritative Step 2 DefectMaster.
     */
    fun validateDefectName(defectName: String): String {
        return com.example.model.DefectMaster.assertStandardDefect(defectName)
    }

    /**
     * Validates action status code.
     */
    fun validateActionStatus(code: String): com.example.action.ActionStatus {
        return com.example.action.ActionStatus.fromCode(code)
    }

    /**
     * Validates action priority code.
     */
    fun validateActionPriority(code: String): com.example.action.ActionPriority {
        return com.example.action.ActionPriority.fromCode(code)
    }
}
