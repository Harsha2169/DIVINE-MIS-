package com.example.service

import com.example.analytics.AnalyticsFilterContext
import com.example.core.BusinessDate
import com.example.model.BusinessArea
import com.example.model.Department
import com.example.model.ManufacturingModel
import com.example.model.ModelApplicabilityValidator
import com.example.model.RebuffingRule
import com.example.model.RejectionRecord
import com.example.model.RejectionSide
import com.example.model.RejectionSource
import com.example.repository.RejectionRepository
import java.util.UUID

class RejectionService(
    private val rejectionRepository: RejectionRepository
) {
    suspend fun recordRejection(
        date: BusinessDate,
        model: ManufacturingModel,
        department: Department,
        defectName: String,
        source: RejectionSource,
        businessArea: BusinessArea,
        side: RejectionSide,
        quantity: Int,
        isRebuffed: Boolean = false,
        notes: String? = null,
        id: String = UUID.randomUUID().toString()
    ): RejectionRecord {
        ModelApplicabilityValidator.validateApplicability(model, department)
        if (isRebuffed) {
            RebuffingRule.validateRebuffingEligibility(source)
        }
        val record = RejectionRecord(
            id = id,
            date = date,
            model = model,
            department = department,
            defectName = defectName,
            source = source,
            businessArea = businessArea,
            side = side,
            quantity = quantity,
            isRebuffed = isRebuffed,
            notes = notes
        )
        rejectionRepository.insert(record)
        return record
    }

    suspend fun getRejections(filter: AnalyticsFilterContext): List<RejectionRecord> {
        return rejectionRepository.getByFilter(filter)
    }

    suspend fun getRejectionsByDateRange(from: BusinessDate, to: BusinessDate): List<RejectionRecord> {
        return rejectionRepository.getByDateRange(from, to)
    }
}
