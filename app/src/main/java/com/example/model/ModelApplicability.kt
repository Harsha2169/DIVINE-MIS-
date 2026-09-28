package com.example.model

/**
 * Centrally validates model-to-department applicability rules.
 *
 * Invariants:
 * - U180 is applicable to GIL DELIVERY ONLY.
 * - MAXR is applicable to GIL DELIVERY ONLY.
 * - Other models (U86, U244, N282-DISC, DRUM, N360) are applicable to all departments.
 */
object ModelApplicabilityValidator {

    fun isApplicable(model: ManufacturingModel, department: Department): Boolean {
        return when (model) {
            ManufacturingModel.U180 -> department == Department.GIL_DELIVERY
            ManufacturingModel.MAXR -> department == Department.GIL_DELIVERY
            ManufacturingModel.U86,
            ManufacturingModel.U244,
            ManufacturingModel.N282_DISC,
            ManufacturingModel.DRUM,
            ManufacturingModel.N360 -> true
        }
    }

    fun validateApplicability(model: ManufacturingModel, department: Department) {
        if (!isApplicable(model, department)) {
            throw IllegalArgumentException(
                "Model '${model.displayName}' is restricted to GIL DELIVERY only, " +
                "and cannot be used in department '${department.displayName}'."
            )
        }
    }

    /**
     * Returns the set of all valid departments for a given model.
     */
    fun applicableDepartmentsFor(model: ManufacturingModel): List<Department> {
        return Department.CANONICAL_SEQUENCE.filter { isApplicable(model, it) }
    }
}
