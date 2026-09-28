package com.example.repository

import com.example.model.BusinessArea
import com.example.model.Department
import com.example.model.ManufacturingModel
import com.example.model.RejectionSide
import com.example.model.RejectionSource

/**
 * Authoritative Master Data Repository Contract.
 *
 * Provides access to canonical manufacturing master data:
 * - 7 Models (U86, U180, U244, MAXR, N282-DISC, DRUM, N360)
 * - 7 Departments in exact sequence
 * - Canonical Manufacturing Flow
 * - Model-Department Applicability
 * - Rejection Sources (KAMAL, MRN, DSPL)
 * - Business Areas (KAMAL, GABRIEL, DSPL)
 * - Rejection Sides (LH, RH)
 */
interface MasterDataRepository {
    fun getModels(): List<ManufacturingModel>
    fun getModelByCode(code: String): ManufacturingModel?
    fun getDepartments(): List<Department>
    fun getDepartmentByCode(code: String): Department?
    fun getManufacturingFlow(): List<Department>
    fun getApplicableDepartments(model: ManufacturingModel): List<Department>
    fun isModelApplicable(model: ManufacturingModel, department: Department): Boolean
    fun getRejectionSources(): List<RejectionSource>
    fun getBusinessAreas(): List<BusinessArea>
    fun getRejectionSides(): List<RejectionSide>
    fun validateRebuffingEligibility(source: RejectionSource): Boolean
}
