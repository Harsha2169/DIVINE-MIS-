package com.example.repository.memory

import com.example.model.BusinessArea
import com.example.model.Department
import com.example.model.ManufacturingFlow
import com.example.model.ManufacturingModel
import com.example.model.ModelApplicabilityValidator
import com.example.model.RebuffingRule
import com.example.model.RejectionSide
import com.example.model.RejectionSource
import com.example.repository.MasterDataRepository

/**
 * In-Memory Master Data Repository Implementation.
 *
 * Backed by canonical enums and validators from Step 1.
 */
class InMemoryMasterDataRepository : MasterDataRepository {

    override fun getModels(): List<ManufacturingModel> {
        return ManufacturingModel.entries
    }

    override fun getModelByCode(code: String): ManufacturingModel? {
        return ManufacturingModel.fromCodeOrNull(code)
    }

    override fun getDepartments(): List<Department> {
        return Department.CANONICAL_SEQUENCE
    }

    override fun getDepartmentByCode(code: String): Department? {
        return Department.fromDisplayNameOrNull(code)
    }

    override fun getManufacturingFlow(): List<Department> {
        return ManufacturingFlow.getSequence()
    }

    override fun getApplicableDepartments(model: ManufacturingModel): List<Department> {
        return ModelApplicabilityValidator.applicableDepartmentsFor(model)
    }

    override fun isModelApplicable(model: ManufacturingModel, department: Department): Boolean {
        return ModelApplicabilityValidator.isApplicable(model, department)
    }

    override fun getRejectionSources(): List<RejectionSource> {
        return RejectionSource.entries
    }

    override fun getBusinessAreas(): List<BusinessArea> {
        return BusinessArea.entries
    }

    override fun getRejectionSides(): List<RejectionSide> {
        return RejectionSide.entries
    }

    override fun validateRebuffingEligibility(source: RejectionSource): Boolean {
        return RebuffingRule.canBeRebuffed(source)
    }
}
