package com.example.fixture

import com.example.analytics.AnalyticsFilterContext
import com.example.analytics.ManufacturingAnalyticsService
import com.example.core.BusinessDate
import com.example.model.BusinessArea
import com.example.model.Department
import com.example.model.ManufacturingModel
import com.example.model.PlanRecord
import com.example.model.ProductionQuantity
import com.example.model.ProductionRecord
import com.example.model.RejectionRecord
import com.example.model.RejectionSide
import com.example.model.RejectionSource
import com.example.model.StockRecord
import com.example.repository.memory.InMemoryPlanningRepository
import com.example.repository.memory.InMemoryProductionRepository
import com.example.repository.memory.InMemoryRejectionRepository
import com.example.repository.memory.InMemoryStockRepository
import com.example.service.ProductionService
import com.example.service.RejectionService
import com.example.service.StockService

/**
 * Authoritative Canonical Test Fixture for Step 1.
 *
 * Rule: Uses ACTUAL production declarations. Never creates duplicate domain classes.
 */
class Step1TestFixture {
    val productionRepository = InMemoryProductionRepository()
    val rejectionRepository = InMemoryRejectionRepository()
    val stockRepository = InMemoryStockRepository()
    val planningRepository = InMemoryPlanningRepository()

    val productionService = ProductionService(productionRepository)
    val rejectionService = RejectionService(rejectionRepository)
    val stockService = StockService(stockRepository)
    val analyticsService = ManufacturingAnalyticsService()

    fun createSampleProductionRecord(
        id: String = "PRD-001",
        date: BusinessDate = BusinessDate.parse("2026-09-01"),
        model: ManufacturingModel = ManufacturingModel.U86,
        department: Department = Department.CASTING,
        quantity: Int = 100
    ): ProductionRecord {
        return ProductionRecord(
            id = id,
            date = date,
            model = model,
            department = department,
            quantity = ProductionQuantity(quantity)
        )
    }

    fun createSampleRejectionRecord(
        id: String = "REJ-001",
        date: BusinessDate = BusinessDate.parse("2026-09-01"),
        model: ManufacturingModel = ManufacturingModel.U86,
        department: Department = Department.CASTING,
        defectName: String = "Blowhole",
        source: RejectionSource = RejectionSource.KAMAL,
        businessArea: BusinessArea = BusinessArea.KAMAL,
        side: RejectionSide = RejectionSide.LH,
        quantity: Int = 5,
        isRebuffed: Boolean = false
    ): RejectionRecord {
        return RejectionRecord(
            id = id,
            date = date,
            model = model,
            department = department,
            defectName = defectName,
            source = source,
            businessArea = businessArea,
            side = side,
            quantity = quantity,
            isRebuffed = isRebuffed
        )
    }

    fun createSampleStockRecord(
        id: String = "STK-001",
        date: BusinessDate = BusinessDate.parse("2026-09-01"),
        model: ManufacturingModel = ManufacturingModel.U86,
        opening: Int = 500,
        closing: Int = 450
    ): StockRecord {
        return StockRecord(
            id = id,
            date = date,
            model = model,
            openingQuantity = opening,
            closingQuantity = closing
        )
    }

    fun createSamplePlanRecord(
        id: String = "PLN-001",
        date: BusinessDate = BusinessDate.parse("2026-09-01"),
        model: ManufacturingModel = ManufacturingModel.U86,
        department: Department = Department.CASTING,
        plannedQuantity: Int = 120
    ): PlanRecord {
        return PlanRecord(
            id = id,
            date = date,
            model = model,
            department = department,
            plannedQuantity = plannedQuantity
        )
    }

    fun defaultFilterContext(
        fromDate: String = "2026-09-01",
        toDate: String = "2026-09-30"
    ): AnalyticsFilterContext {
        return AnalyticsFilterContext(
            fromDate = BusinessDate.parse(fromDate),
            toDate = BusinessDate.parse(toDate)
        )
    }
}
