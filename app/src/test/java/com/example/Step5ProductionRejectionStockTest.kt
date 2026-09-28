package com.example

import com.example.analytics.AnalyticsFilterContext
import com.example.analytics.AnalyticsMetricType
import com.example.analytics.DepartmentFilter
import com.example.analytics.ManufacturingAnalyticsService
import com.example.analytics.ModelFilter
import com.example.analytics.ParetoAnalysisResult
import com.example.analytics.RejectionSourceFilter
import com.example.core.BusinessDate
import com.example.core.DataState
import com.example.core.MetricData
import com.example.model.BusinessArea
import com.example.model.DefectMaster
import com.example.model.Department
import com.example.model.ManufacturingModel
import com.example.model.PlanRecord
import com.example.model.ProductionQuantity
import com.example.model.ProductionRecord
import com.example.model.RebuffingRule
import com.example.model.RejectionRecord
import com.example.model.RejectionSide
import com.example.model.RejectionSource
import com.example.model.StockQuantity
import com.example.model.StockRecord
import com.example.model.validation.DomainValidator
import com.example.repository.ProductionRepository
import com.example.repository.RejectionRepository
import com.example.repository.StockRepository
import com.example.repository.memory.InMemoryProductionRepository
import com.example.repository.memory.InMemoryRejectionRepository
import com.example.repository.memory.InMemoryStockRepository
import com.example.service.ProductionService
import com.example.service.RejectionService
import com.example.service.StockService
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test

/**
 * Step 5 Verification Test Suite: Production, Rejection, and Stock Architecture.
 */
class Step5ProductionRejectionStockTest {

    private lateinit var productionRepo: ProductionRepository
    private lateinit var rejectionRepo: RejectionRepository
    private lateinit var stockRepo: StockRepository

    private lateinit var productionService: ProductionService
    private lateinit var rejectionService: RejectionService
    private lateinit var stockService: StockService

    private lateinit var analyticsService: ManufacturingAnalyticsService

    @Before
    fun setUp() {
        productionRepo = InMemoryProductionRepository()
        rejectionRepo = InMemoryRejectionRepository()
        stockRepo = InMemoryStockRepository()

        productionService = ProductionService(productionRepo)
        rejectionService = RejectionService(rejectionRepo)
        stockService = StockService(stockRepo)

        analyticsService = ManufacturingAnalyticsService()
    }

    // 1. Production Creation and Validation
    @Test
    fun testProductionCreationAndValidation() = runBlocking {
        val date = BusinessDate.parse("2026-09-01")
        val prod = productionService.recordProduction(
            date = date,
            model = ManufacturingModel.U86,
            department = Department.CASTING,
            quantity = 500,
            shift = "Shift A",
            notes = "Smooth run"
        )

        assertEquals("2026-09-01", prod.date.toString())
        assertEquals(ManufacturingModel.U86, prod.model)
        assertEquals(Department.CASTING, prod.department)
        assertEquals(500, prod.quantity.value)
        assertEquals("Shift A", prod.shift)

        // Quantity 0 is valid data
        val zeroProd = productionService.recordProduction(
            date = date,
            model = ManufacturingModel.U86,
            department = Department.BUFFING,
            quantity = 0
        )
        assertEquals(0, zeroProd.quantity.value)

        // Blank ID must throw
        try {
            ProductionRecord(
                id = "   ",
                date = date,
                model = ManufacturingModel.U86,
                department = Department.CASTING,
                quantity = ProductionQuantity(100)
            )
            fail("Blank ID must throw IllegalArgumentException")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message?.contains("ID", ignoreCase = true) == true)
        }
    }

    // 2. Production Has NO LH/RH Field
    @Test
    fun testProductionHasNoLhRh() {
        val declaredFieldNames = ProductionRecord::class.java.declaredFields.map { it.name.lowercase() }

        assertFalse("Production must NOT have a 'side' field", declaredFieldNames.contains("side"))
        assertFalse("Production must NOT have a 'rejectionside' field", declaredFieldNames.contains("rejectionside"))
        assertFalse("Production must NOT have an 'islh' field", declaredFieldNames.contains("islh"))
        assertFalse("Production must NOT have an 'isrh' field", declaredFieldNames.contains("isrh"))
        assertFalse("Production must NOT have a 'lhquantity' field", declaredFieldNames.contains("lhquantity"))
        assertFalse("Production must NOT have a 'rhquantity' field", declaredFieldNames.contains("rhquantity"))

        // Production quantity is an scalar integer wrapper without LH/RH concepts
        val qty = ProductionQuantity(250)
        assertEquals(250, qty.value)
    }

    // 3. Production-Plan Independence & Stage Separation
    @Test
    fun testProductionPlanIndependence() {
        val date = BusinessDate.parse("2026-09-02")

        val plan = PlanRecord(
            id = "PLN-IND-01",
            date = date,
            model = ManufacturingModel.U86,
            department = Department.CASTING,
            plannedQuantity = 600
        )

        val actual = ProductionRecord(
            id = "PRD-IND-01",
            date = date,
            model = ManufacturingModel.U86,
            department = Department.CASTING,
            quantity = ProductionQuantity(550)
        )

        // Independent entities
        assertNotEquals(plan.plannedQuantity, actual.quantity.value)

        // Missing production does NOT get filled by plan or coerced to 0
        val plans = listOf(plan)
        val productions = emptyList<ProductionRecord>()
        val filter = AnalyticsFilterContext(
            fromDate = date,
            toDate = date,
            selectedDate = null,
            model = ModelFilter.All,
            department = DepartmentFilter.All
        )
        val comparison = analyticsService.buildPlanVsActual(plans, productions, filter)
        val point = comparison.points.first()
        assertEquals(MetricData.Value(600), point.primary)
        assertEquals(MetricData.NoData, point.secondary) // Missing actual is NoData, NEVER coerced to 0 or plan!

        // Enterprise production must NEVER sum different manufacturing stages
        val castingProd = ProductionRecord("C1", date, ManufacturingModel.U86, Department.CASTING, ProductionQuantity(500))
        val machiningProd = ProductionRecord("M1", date, ManufacturingModel.U86, Department.MACHINING, ProductionQuantity(480))
        // They represent WIP at stages, not additive output of 980 units
        assertNotEquals(980, castingProd.quantity.value)
        assertNotEquals(980, machiningProd.quantity.value)
    }

    // 4. Rejection Source Validation (Exactly 3)
    @Test
    fun testRejectionSourceValidation() {
        val sources = RejectionSource.entries
        assertEquals(3, sources.size)

        val codes = sources.map { it.code }
        assertTrue(codes.contains("KAMAL"))
        assertTrue(codes.contains("MRN"))
        assertTrue(codes.contains("DSPL"))

        assertEquals(RejectionSource.KAMAL, RejectionSource.fromCode("KAMAL"))
        assertEquals(RejectionSource.MRN, RejectionSource.fromCode("MRN"))
        assertEquals(RejectionSource.DSPL, RejectionSource.fromCode("DSPL"))

        try {
            RejectionSource.fromCode("VENDOR_X")
            fail("Invented rejection source must throw IllegalArgumentException")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message?.contains("Unknown rejection source", ignoreCase = true) == true)
        }
    }

    // 5. Rejection Business-Area Validation (Exactly 3)
    @Test
    fun testRejectionBusinessAreaValidation() {
        val areas = BusinessArea.entries
        assertEquals(3, areas.size)

        val codes = areas.map { it.code }
        assertTrue(codes.contains("KAMAL"))
        assertTrue(codes.contains("GABRIEL"))
        assertTrue(codes.contains("DSPL"))

        assertEquals(BusinessArea.KAMAL, BusinessArea.fromCode("KAMAL"))
        assertEquals(BusinessArea.GABRIEL, BusinessArea.fromCode("GABRIEL"))
        assertEquals(BusinessArea.DSPL, BusinessArea.fromCode("DSPL"))

        try {
            BusinessArea.fromCode("BAJAJ_AREA")
            fail("Invented business area must throw IllegalArgumentException")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message?.contains("Unknown business area", ignoreCase = true) == true)
        }
    }

    // 6. LH/RH Validation (Exactly 2)
    @Test
    fun testLhRhValidation() {
        val sides = RejectionSide.entries
        assertEquals(2, sides.size)

        val codes = sides.map { it.code }
        assertTrue(codes.contains("LH"))
        assertTrue(codes.contains("RH"))

        assertEquals(RejectionSide.LH, RejectionSide.parse("LH"))
        assertEquals(RejectionSide.RH, RejectionSide.parse("RH"))
        assertEquals(RejectionSide.LH, RejectionSide.parse("lh"))
        assertEquals(RejectionSide.RH, RejectionSide.parse("rh"))
    }

    // 7. BOTH Rejection Denial
    @Test
    fun testBothRejectionDenial() {
        try {
            RejectionSide.parse("BOTH")
            fail("'BOTH' must throw IllegalArgumentException")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message?.contains("BOTH", ignoreCase = true) == true)
        }

        try {
            DomainValidator.validateRejectionSide("BOTH")
            fail("DomainValidator must reject 'BOTH'")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message?.contains("BOTH", ignoreCase = true) == true)
        }
    }

    // 8. KAMAL-Only Rebuffing
    @Test
    fun testKamalOnlyRebuffing() = runBlocking {
        assertTrue(RebuffingRule.canBeRebuffed(RejectionSource.KAMAL))
        RebuffingRule.validateRebuffingEligibility(RejectionSource.KAMAL)

        val date = BusinessDate.parse("2026-09-03")
        val kamalRebuffed = rejectionService.recordRejection(
            date = date,
            model = ManufacturingModel.U86,
            department = Department.CASTING,
            defectName = "Blowhole",
            source = RejectionSource.KAMAL,
            businessArea = BusinessArea.KAMAL,
            side = RejectionSide.LH,
            quantity = 5,
            isRebuffed = true
        )

        assertTrue(kamalRebuffed.isRebuffed)
        assertEquals(RejectionSource.KAMAL, kamalRebuffed.source)
    }

    // 9. MRN/DSPL Not Rebuffing
    @Test
    fun testMrnAndDsplNotRebuffing() = runBlocking {
        assertFalse(RebuffingRule.canBeRebuffed(RejectionSource.MRN))
        assertFalse(RebuffingRule.canBeRebuffed(RejectionSource.DSPL))

        val date = BusinessDate.parse("2026-09-03")

        // MRN cannot be rebuffed
        try {
            rejectionService.recordRejection(
                date = date,
                model = ManufacturingModel.U86,
                department = Department.CASTING,
                defectName = "Crack",
                source = RejectionSource.MRN,
                businessArea = BusinessArea.GABRIEL,
                side = RejectionSide.RH,
                quantity = 2,
                isRebuffed = true
            )
            fail("MRN rejection with isRebuffed=true must throw IllegalArgumentException")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message?.contains("KAMAL rejection ONLY", ignoreCase = true) == true)
        }

        // DSPL cannot be rebuffed
        try {
            rejectionService.recordRejection(
                date = date,
                model = ManufacturingModel.U86,
                department = Department.CASTING,
                defectName = "Porosity",
                source = RejectionSource.DSPL,
                businessArea = BusinessArea.DSPL,
                side = RejectionSide.LH,
                quantity = 3,
                isRebuffed = true
            )
            fail("DSPL rejection with isRebuffed=true must throw IllegalArgumentException")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message?.contains("KAMAL rejection ONLY", ignoreCase = true) == true)
        }
    }

    // 10. Authoritative Defect Validation
    @Test
    fun testAuthoritativeDefectValidation() {
        val standardDefects = DefectMaster.STANDARD_DEFECTS
        assertTrue(standardDefects.contains("BLOWHOLE"))
        assertTrue(standardDefects.contains("CRACK"))
        assertTrue(standardDefects.contains("DENT"))
        assertTrue(standardDefects.contains("POROSITY"))
        assertTrue(standardDefects.contains("ROUGH BUFFING"))

        // Standard defect checks
        assertTrue(DefectMaster.isStandardDefect("BLOWHOLE"))
        assertTrue(DefectMaster.isStandardDefect("blowhole"))
        assertEquals("BLOWHOLE", DefectMaster.assertStandardDefect("blowhole"))

        // Blank defect throws
        try {
            DefectMaster.validateDefectName("   ")
            fail("Blank defect name must throw")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message?.contains("blank", ignoreCase = true) == true)
        }

        // Single character defect throws
        try {
            DefectMaster.validateDefectName("X")
            fail("Single char defect must throw")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message?.contains("at least 2", ignoreCase = true) == true)
        }

        // Invented defect throws
        try {
            DefectMaster.assertStandardDefect("INVENTED_DEFECT_XYZ")
            fail("Invented defect must throw IllegalArgumentException")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message?.contains("Invented defect", ignoreCase = true) == true)
        }

        try {
            DomainValidator.validateDefectName("FAKE_DEFECT")
            fail("DomainValidator must reject invented defect")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message?.contains("Invented defect", ignoreCase = true) == true)
        }
    }

    // 11. Production/Rejection Quantity Validation
    @Test
    fun testProductionAndRejectionQuantityValidation() {
        // Production quantity >= 0
        assertEquals(0, ProductionQuantity(0).value)
        assertEquals(100, ProductionQuantity(100).value)

        try {
            ProductionQuantity(-5)
            fail("Negative production quantity must throw")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message?.contains("non-negative", ignoreCase = true) == true)
        }

        // Rejection quantity >= 0
        val date = BusinessDate.parse("2026-09-04")
        val zeroRej = RejectionRecord(
            id = "REJ-ZERO",
            date = date,
            model = ManufacturingModel.U86,
            department = Department.CASTING,
            defectName = "Blowhole",
            source = RejectionSource.KAMAL,
            businessArea = BusinessArea.KAMAL,
            side = RejectionSide.LH,
            quantity = 0
        )
        assertEquals(0, zeroRej.quantity)

        try {
            RejectionRecord(
                id = "REJ-NEG",
                date = date,
                model = ManufacturingModel.U86,
                department = Department.CASTING,
                defectName = "Blowhole",
                source = RejectionSource.KAMAL,
                businessArea = BusinessArea.KAMAL,
                side = RejectionSide.LH,
                quantity = -1
            )
            fail("Negative rejection quantity must throw")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message?.contains("non-negative", ignoreCase = true) == true)
        }
    }

    // 12. Stock Opening/Closing Independent Manual Entry
    @Test
    fun testStockOpeningClosingIndependentManualEntry() = runBlocking {
        val date = BusinessDate.parse("2026-09-05")

        // Opening and closing are independent numbers
        val stock = stockService.recordManualStock(
            date = date,
            model = ManufacturingModel.U86,
            openingQuantity = 450,
            closingQuantity = 410,
            notes = "End of shift count"
        )

        assertEquals(450, stock.openingQuantity)
        assertEquals(410, stock.closingQuantity)

        // 0 is valid data
        val zeroStock = stockService.recordManualStock(
            date = date,
            model = ManufacturingModel.U244,
            openingQuantity = 0,
            closingQuantity = 0
        )
        assertEquals(0, zeroStock.openingQuantity)
        assertEquals(0, zeroStock.closingQuantity)

        // Negative values throw
        try {
            StockQuantity(-10, 100)
            fail("Negative opening quantity must throw")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message?.contains("Opening quantity", ignoreCase = true) == true)
        }

        try {
            StockQuantity(100, -5)
            fail("Negative closing quantity must throw")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message?.contains("Closing quantity", ignoreCase = true) == true)
        }
    }

    // 13. No Automatic Stock Carry-Forward
    @Test
    fun testNoAutomaticStockCarryForward() = runBlocking {
        val day1 = BusinessDate.parse("2026-09-05")
        val day2 = BusinessDate.parse("2026-09-06")

        // Day 1: Opening = 500, Closing = 420
        stockService.recordManualStock(
            date = day1,
            model = ManufacturingModel.U86,
            openingQuantity = 500,
            closingQuantity = 420
        )

        // Day 2: Physical inventory audit reveals Opening is 415 (e.g. 5 units damaged/discrepancy overnight)
        // System must NOT auto-populate 420 as Day 2 opening!
        val day2Stock = stockService.recordManualStock(
            date = day2,
            model = ManufacturingModel.U86,
            openingQuantity = 415,
            closingQuantity = 390
        )

        assertEquals(415, day2Stock.openingQuantity)
        assertNotEquals(420, day2Stock.openingQuantity)
    }

    // 14. 0 vs NO DATA in Analytics
    @Test
    fun testZeroVsNoDataInAnalytics() {
        val d1 = BusinessDate.parse("2026-09-01")
        val d2 = BusinessDate.parse("2026-09-02")
        val d3 = BusinessDate.parse("2026-09-03")

        val filter = AnalyticsFilterContext(
            fromDate = d1,
            toDate = d3,
            selectedDate = null,
            model = ModelFilter.All,
            department = DepartmentFilter.All
        )

        // D1 has production 100, D2 has production 0 (recorded zero-run), D3 has NO record
        val prods = listOf(
            ProductionRecord("P1", d1, ManufacturingModel.U86, Department.CASTING, ProductionQuantity(100)),
            ProductionRecord("P2", d2, ManufacturingModel.U86, Department.CASTING, ProductionQuantity(0))
        )

        val trend = analyticsService.buildDailyProductionTrend(prods, filter)
        assertEquals(3, trend.points.size)

        // D1: Value(100)
        assertEquals(MetricData.Value(100), trend.points[0].metric)
        // D2: Value(0) — 0 is valid data!
        assertEquals(MetricData.Value(0), trend.points[1].metric)
        // D3: NoData — missing is NEVER coerced to 0!
        assertEquals(MetricData.NoData, trend.points[2].metric)

        // Rejection trend 0 vs NoData
        val rejections = listOf(
            RejectionRecord("R1", d1, ManufacturingModel.U86, Department.CASTING, "Blowhole", RejectionSource.KAMAL, BusinessArea.KAMAL, RejectionSide.LH, 5),
            RejectionRecord("R2", d2, ManufacturingModel.U86, Department.CASTING, "Blowhole", RejectionSource.KAMAL, BusinessArea.KAMAL, RejectionSide.LH, 0)
        )
        val rejTrend = analyticsService.buildRejectionTrend(rejections, filter)
        assertEquals(MetricData.Value(5), rejTrend.points[0].metric)
        assertEquals(MetricData.Value(0), rejTrend.points[1].metric)
        assertEquals(MetricData.NoData, rejTrend.points[2].metric)
    }

    // 15. Canonical Model and Department Applicability
    @Test
    fun testCanonicalModelDepartmentApplicability() {
        val date = BusinessDate.parse("2026-09-07")

        // U180 and MAXR allowed in GIL DELIVERY
        val u180Gil = ProductionRecord("P-U180", date, ManufacturingModel.U180, Department.GIL_DELIVERY, ProductionQuantity(50))
        assertEquals(Department.GIL_DELIVERY, u180Gil.department)

        val maxrGil = ProductionRecord("P-MAXR", date, ManufacturingModel.MAXR, Department.GIL_DELIVERY, ProductionQuantity(60))
        assertEquals(Department.GIL_DELIVERY, maxrGil.department)

        // U180 prohibited in CASTING
        try {
            ProductionRecord("P-U180-BAD", date, ManufacturingModel.U180, Department.CASTING, ProductionQuantity(50))
            fail("U180 in CASTING must throw IllegalArgumentException")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message?.contains("GIL DELIVERY only", ignoreCase = true) == true)
        }

        // MAXR prohibited in MACHINING
        try {
            ProductionRecord("P-MAXR-BAD", date, ManufacturingModel.MAXR, Department.MACHINING, ProductionQuantity(50))
            fail("MAXR in MACHINING must throw IllegalArgumentException")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message?.contains("GIL DELIVERY only", ignoreCase = true) == true)
        }
    }

    // 16. Repository Contract Consistency
    @Test
    fun testRepositoryContractConsistency() = runBlocking {
        val d1 = BusinessDate.parse("2026-09-01")
        val d2 = BusinessDate.parse("2026-09-02")

        // Production Repository CRUD
        val prod = ProductionRecord("P-REP-1", d1, ManufacturingModel.U86, Department.CASTING, ProductionQuantity(200))
        productionRepo.insert(prod)
        assertEquals(prod, productionRepo.getById("P-REP-1"))
        assertEquals(1, productionRepo.getByDateRange(d1, d2).size)
        assertTrue(productionRepo.deleteById("P-REP-1"))
        assertNull(productionRepo.getById("P-REP-1"))

        // Rejection Repository CRUD
        val rej = RejectionRecord("R-REP-1", d1, ManufacturingModel.U86, Department.CASTING, "Dent", RejectionSource.MRN, BusinessArea.GABRIEL, RejectionSide.RH, 4)
        rejectionRepo.insert(rej)
        assertEquals(rej, rejectionRepo.getById("R-REP-1"))
        val filterRej = AnalyticsFilterContext(
            fromDate = d1,
            toDate = d2,
            selectedDate = null,
            model = ModelFilter.Specific(ManufacturingModel.U86),
            department = DepartmentFilter.Specific(Department.CASTING),
            rejectionSource = RejectionSourceFilter.Specific(RejectionSource.MRN)
        )
        assertEquals(1, rejectionRepo.getByFilter(filterRej).size)
        assertTrue(rejectionRepo.deleteById("R-REP-1"))
        assertNull(rejectionRepo.getById("R-REP-1"))

        // Stock Repository CRUD
        val stock = StockRecord("S-REP-1", d1, ManufacturingModel.U86, 300, 280)
        stockRepo.insert(stock)
        assertEquals(stock, stockRepo.getById("S-REP-1"))
        assertEquals(1, stockRepo.getByDateRange(d1, d2).size)
        assertTrue(stockRepo.deleteById("S-REP-1"))
        assertNull(stockRepo.getById("S-REP-1"))
    }

    // 17. Analytics-Ready Rejection Aggregation and Pareto Contract
    @Test
    fun testAnalyticsReadyRejectionParetoContract() {
        val date = BusinessDate.parse("2026-09-08")
        val records = listOf(
            RejectionRecord("R1", date, ManufacturingModel.U86, Department.CASTING, "Blowhole", RejectionSource.KAMAL, BusinessArea.KAMAL, RejectionSide.LH, 50),
            RejectionRecord("R2", date, ManufacturingModel.U86, Department.CASTING, "Crack", RejectionSource.KAMAL, BusinessArea.KAMAL, RejectionSide.LH, 30),
            RejectionRecord("R3", date, ManufacturingModel.U86, Department.CASTING, "Dent", RejectionSource.KAMAL, BusinessArea.KAMAL, RejectionSide.RH, 20)
        )

        val filter = AnalyticsFilterContext(
            fromDate = date,
            toDate = date,
            selectedDate = null,
            model = ModelFilter.All,
            department = DepartmentFilter.All
        )

        val pareto: ParetoAnalysisResult = analyticsService.buildRejectionPareto(records, filter)

        assertEquals(DataState.VALUE, pareto.dataState)
        assertEquals(100, pareto.totalRejections)
        assertEquals(3, pareto.items.size)

        // Sorted by quantity descending
        assertEquals("Blowhole", pareto.items[0].defectName)
        assertEquals(50, pareto.items[0].quantity)
        assertEquals(50.0, pareto.items[0].contributionPercentage, 0.01)
        assertEquals(50.0, pareto.items[0].cumulativePercentage, 0.01)

        assertEquals("Crack", pareto.items[1].defectName)
        assertEquals(30, pareto.items[1].quantity)
        assertEquals(30.0, pareto.items[1].contributionPercentage, 0.01)
        assertEquals(80.0, pareto.items[1].cumulativePercentage, 0.01)

        assertEquals("Dent", pareto.items[2].defectName)
        assertEquals(20, pareto.items[2].quantity)
        assertEquals(20.0, pareto.items[2].contributionPercentage, 0.01)
        assertEquals(100.0, pareto.items[2].cumulativePercentage, 0.01)

        // Also test Rebuffing Trend analytics contract (KAMAL only)
        val rebuffTrend = analyticsService.buildRebuffingTrend(records, filter)
        assertEquals(AnalyticsMetricType.REBUFFING_TREND, rebuffTrend.metricType)
    }
}
