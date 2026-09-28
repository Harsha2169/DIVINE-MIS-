package com.example.service

import com.example.analytics.AnalyticsFilterContext
import com.example.core.BusinessDate
import com.example.model.Department
import com.example.model.ManufacturingModel
import com.example.model.ModelApplicabilityValidator
import com.example.model.ProductionQuantity
import com.example.model.ProductionRecord
import com.example.repository.ProductionRepository
import java.util.UUID

class ProductionService(
    private val productionRepository: ProductionRepository
) {
    suspend fun recordProduction(
        date: BusinessDate,
        model: ManufacturingModel,
        department: Department,
        quantity: Int,
        shift: String? = null,
        notes: String? = null,
        id: String = UUID.randomUUID().toString()
    ): ProductionRecord {
        ModelApplicabilityValidator.validateApplicability(model, department)
        val prodQty = ProductionQuantity(quantity)
        val record = ProductionRecord(
            id = id,
            date = date,
            model = model,
            department = department,
            quantity = prodQty,
            shift = shift,
            notes = notes
        )
        productionRepository.insert(record)
        return record
    }

    suspend fun getProduction(filter: AnalyticsFilterContext): List<ProductionRecord> {
        return productionRepository.getByFilter(filter)
    }

    suspend fun getProductionByDateRange(from: BusinessDate, to: BusinessDate): List<ProductionRecord> {
        return productionRepository.getByDateRange(from, to)
    }
}
