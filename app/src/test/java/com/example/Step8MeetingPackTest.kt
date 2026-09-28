package com.example

import com.example.action.ActionPriority
import com.example.action.ActionRecord
import com.example.action.ActionStatus
import com.example.analytics.AnalyticsFilterContext
import com.example.analytics.DepartmentFilter
import com.example.analytics.ManufacturingAnalyticsService
import com.example.analytics.ModelFilter
import com.example.analytics.PlanningAnalyticsService
import com.example.analytics.RejectionSourceFilter
import com.example.core.BusinessDate
import com.example.core.MetricData
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
import com.example.report.AttentionLevel
import com.example.report.CsvSanitizer
import com.example.report.ExportConverters
import com.example.report.MeetingPackService
import com.example.repository.memory.InMemoryActionTrackerRepository
import com.example.repository.memory.InMemoryPlanningRepository
import com.example.repository.memory.InMemoryProductionRepository
import com.example.repository.memory.InMemoryRejectionRepository
import com.example.repository.memory.InMemoryStockRepository
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Step 8 Authoritative Reports & Management Meeting Pack Test Suite.
 *
 * Verifies:
 * - daily report
 * - monthly report
 * - management summary
 * - Plan vs Actual
 * - Achievement %
 * - rejection summary
 * - Pareto
 * - stock summary
 * - action summary
 * - filter propagation
 * - 0 vs NO DATA
 * - CSV formula-injection sanitization
 * - deterministic report output
 * - missing root cause remains NO DATA
 * - contract consistency
 */
class Step8MeetingPackTest {

    private lateinit var productionRepository: InMemoryProductionRepository
    private lateinit var rejectionRepository: InMemoryRejectionRepository
    private lateinit var stockRepository: InMemoryStockRepository
    private lateinit var planningRepository: InMemoryPlanningRepository
    private lateinit var actionTrackerRepository: InMemoryActionTrackerRepository

    private lateinit var meetingPackService: MeetingPackService

    private val testDate = BusinessDate.parse("2026-09-15")
    private val startDate = BusinessDate.parse("2026-09-01")
    private val endDate = BusinessDate.parse("2026-09-07")

    @Before
    fun setUp() {
        productionRepository = InMemoryProductionRepository()
        rejectionRepository = InMemoryRejectionRepository()
        stockRepository = InMemoryStockRepository()
        planningRepository = InMemoryPlanningRepository()
        actionTrackerRepository = InMemoryActionTrackerRepository()

        meetingPackService = MeetingPackService(
            productionRepository = productionRepository,
            rejectionRepository = rejectionRepository,
            stockRepository = stockRepository,
            planningRepository = planningRepository,
            actionTrackerRepository = actionTrackerRepository,
            analyticsService = ManufacturingAnalyticsService(),
            planningAnalyticsService = PlanningAnalyticsService()
        )
    }

    // -------------------------------------------------------------------------
    // 1. Daily Report
    // -------------------------------------------------------------------------
    @Test
    fun test01_dailyReport() = runBlocking {
        productionRepository.insert(ProductionRecord("PRD-1", testDate, ManufacturingModel.U86, Department.CASTING, ProductionQuantity(350)))
        planningRepository.insert(PlanRecord("PLN-1", testDate, ManufacturingModel.U86, Department.CASTING, 400))
        rejectionRepository.insert(RejectionRecord("REJ-1", testDate, ManufacturingModel.U86, Department.CASTING, "BLOWHOLE", RejectionSource.KAMAL, BusinessArea.KAMAL, RejectionSide.LH, 15))
        stockRepository.insert(StockRecord("STK-1", testDate, ManufacturingModel.U86, openingQuantity = 1000, closingQuantity = 980))

        actionTrackerRepository.insert(
            ActionRecord(
                id = "ACT-1",
                title = "Inspect blowhole porosity",
                description = "Investigate mold temperature",
                department = Department.CASTING,
                status = ActionStatus.OPEN,
                priority = ActionPriority.HIGH,
                businessDate = testDate,
                createdBy = "USR-1"
            )
        )

        val report = meetingPackService.generateDailyReport(testDate)

        assertEquals(testDate, report.selectedDate)
        assertEquals(MetricData.Value(350), report.managementSummary.productionSummary)
        assertEquals(MetricData.Value(400), report.managementSummary.planSummary)
        assertEquals(MetricData.Value(15), report.managementSummary.rejectionSummary)
        assertEquals(MetricData.Value(1000), report.managementSummary.openingStockSummary)
        assertEquals(MetricData.Value(980), report.managementSummary.closingStockSummary)
        assertEquals(1, report.openActions.size)
        assertEquals("ACT-1", report.openActions.first().id)
        assertEquals(1, report.criticalHighActions.size)
    }

    // -------------------------------------------------------------------------
    // 2. Monthly Report
    // -------------------------------------------------------------------------
    @Test
    fun test02_monthlyReport() = runBlocking {
        val d1 = BusinessDate.parse("2026-09-01")
        val d2 = BusinessDate.parse("2026-09-02")
        val d3 = BusinessDate.parse("2026-09-03")

        productionRepository.insert(ProductionRecord("P1", d1, ManufacturingModel.U86, Department.CASTING, ProductionQuantity(200)))
        productionRepository.insert(ProductionRecord("P2", d2, ManufacturingModel.U86, Department.CASTING, ProductionQuantity(250)))

        rejectionRepository.insert(RejectionRecord("R1", d1, ManufacturingModel.U86, Department.CASTING, "BLOWHOLE", RejectionSource.KAMAL, BusinessArea.KAMAL, RejectionSide.LH, 10))
        rejectionRepository.insert(RejectionRecord("R2", d3, ManufacturingModel.U86, Department.CASTING, "CRACK", RejectionSource.KAMAL, BusinessArea.KAMAL, RejectionSide.RH, 5))

        val report = meetingPackService.generateMonthlyReport(d1, d3)

        assertEquals(d1, report.fromDate)
        assertEquals(d3, report.toDate)
        assertEquals(3, report.dailyProductionTrend.size) // Covers d1, d2, d3
        assertEquals(MetricData.Value(200), report.dailyProductionTrend[0].metric)
        assertEquals(MetricData.Value(250), report.dailyProductionTrend[1].metric)
        assertEquals(MetricData.NoData, report.dailyProductionTrend[2].metric)

        assertEquals(3, report.dailyRejectionTrend.size)
        assertEquals(2, report.rejectionPareto.items.size)
        assertEquals("BLOWHOLE", report.rejectionPareto.items[0].defectName)
    }

    // -------------------------------------------------------------------------
    // 3. Management Summary
    // -------------------------------------------------------------------------
    @Test
    fun test03_managementSummary() = runBlocking {
        val filter = AnalyticsFilterContext(fromDate = startDate, toDate = endDate)

        productionRepository.insert(ProductionRecord("P1", startDate, ManufacturingModel.U86, Department.CASTING, ProductionQuantity(500)))
        planningRepository.insert(PlanRecord("PL1", startDate, ManufacturingModel.U86, Department.CASTING, 600))
        rejectionRepository.insert(RejectionRecord("R1", startDate, ManufacturingModel.U86, Department.CASTING, "BLOWHOLE", RejectionSource.KAMAL, BusinessArea.KAMAL, RejectionSide.LH, 20, isRebuffed = true))
        stockRepository.insert(StockRecord("S1", startDate, ManufacturingModel.U86, openingQuantity = 800, closingQuantity = 750))

        val summary = meetingPackService.generateManagementSummary(filter)

        assertEquals(MetricData.Value(500), summary.productionSummary)
        assertEquals(MetricData.Value(600), summary.planSummary)
        assertTrue(summary.achievementPercentage is MetricData.Value)
        assertEquals(83.33, (summary.achievementPercentage as MetricData.Value).value, 0.01)
        assertEquals(MetricData.Value(20), summary.rejectionSummary)
        assertEquals(MetricData.Value(3.85), summary.rejectionPercentage) // 20 / (500 + 20) = 3.846% -> 3.85%
        assertEquals(MetricData.Value(20), summary.rebuffingSummary)
        assertEquals(MetricData.Value(800), summary.openingStockSummary)
        assertEquals(MetricData.Value(750), summary.closingStockSummary)
    }

    // -------------------------------------------------------------------------
    // 4. Plan vs Actual
    // -------------------------------------------------------------------------
    @Test
    fun test04_planVsActual() = runBlocking {
        val filter = AnalyticsFilterContext.singleDay(testDate)
        planningRepository.insert(PlanRecord("PL1", testDate, ManufacturingModel.U86, Department.CASTING, 1000))
        productionRepository.insert(ProductionRecord("P1", testDate, ManufacturingModel.U86, Department.CASTING, ProductionQuantity(950)))

        val summary = meetingPackService.generateManagementSummary(filter)
        assertEquals(MetricData.Value(1000), summary.planSummary)
        assertEquals(MetricData.Value(950), summary.productionSummary)
        assertEquals(MetricData.Value(950), summary.actualSummary)
        assertEquals(MetricData.Value(95.0), summary.achievementPercentage)
    }

    // -------------------------------------------------------------------------
    // 5. Achievement %
    // -------------------------------------------------------------------------
    @Test
    fun test05_achievementPercentage() = runBlocking {
        val filter = AnalyticsFilterContext.singleDay(testDate)

        // Case 1: 100%
        planningRepository.insert(PlanRecord("PL1", testDate, ManufacturingModel.U86, Department.CASTING, 500))
        productionRepository.insert(ProductionRecord("P1", testDate, ManufacturingModel.U86, Department.CASTING, ProductionQuantity(500)))
        var summary = meetingPackService.generateManagementSummary(filter)
        assertEquals(MetricData.Value(100.0), summary.achievementPercentage)

        // Case 2: 0 planned -> 0.0
        planningRepository.clear()
        productionRepository.clear()
        planningRepository.insert(PlanRecord("PL2", testDate, ManufacturingModel.U86, Department.CASTING, 0))
        productionRepository.insert(ProductionRecord("P2", testDate, ManufacturingModel.U86, Department.CASTING, ProductionQuantity(100)))
        summary = meetingPackService.generateManagementSummary(filter)
        assertEquals(MetricData.Value(0.0), summary.achievementPercentage)

        // Case 3: No records -> NoData
        planningRepository.clear()
        productionRepository.clear()
        summary = meetingPackService.generateManagementSummary(filter)
        assertEquals(MetricData.NoData, summary.achievementPercentage)
    }

    // -------------------------------------------------------------------------
    // 6. Rejection Summary & Rebuffing
    // -------------------------------------------------------------------------
    @Test
    fun test06_rejectionSummary() = runBlocking {
        val filter = AnalyticsFilterContext.singleDay(testDate)

        // KAMAL rejection with rebuffing
        rejectionRepository.insert(
            RejectionRecord("R1", testDate, ManufacturingModel.U86, Department.CASTING, "BLOWHOLE", RejectionSource.KAMAL, BusinessArea.KAMAL, RejectionSide.LH, 30, isRebuffed = true)
        )
        // MRN rejection (not eligible for rebuffing)
        rejectionRepository.insert(
            RejectionRecord("R2", testDate, ManufacturingModel.U86, Department.CASTING, "CRACK", RejectionSource.MRN, BusinessArea.DSPL, RejectionSide.RH, 20, isRebuffed = false)
        )
        productionRepository.insert(ProductionRecord("P1", testDate, ManufacturingModel.U86, Department.CASTING, ProductionQuantity(450)))

        val summary = meetingPackService.generateManagementSummary(filter)

        assertEquals(MetricData.Value(50), summary.rejectionSummary) // 30 + 20
        assertEquals(MetricData.Value(30), summary.rebuffingSummary) // Only KAMAL rebuffed
        assertEquals(MetricData.Value(10.0), summary.rejectionPercentage) // 50 / (450 + 50) = 10.0%
    }

    // -------------------------------------------------------------------------
    // 7. Pareto
    // -------------------------------------------------------------------------
    @Test
    fun test07_pareto() = runBlocking {
        val filter = AnalyticsFilterContext(fromDate = startDate, toDate = endDate)

        rejectionRepository.insert(RejectionRecord("R1", startDate, ManufacturingModel.U86, Department.CASTING, "BLOWHOLE", RejectionSource.KAMAL, BusinessArea.KAMAL, RejectionSide.LH, 60))
        rejectionRepository.insert(RejectionRecord("R2", startDate, ManufacturingModel.U86, Department.CASTING, "CRACK", RejectionSource.KAMAL, BusinessArea.KAMAL, RejectionSide.LH, 30))
        rejectionRepository.insert(RejectionRecord("R3", startDate, ManufacturingModel.U86, Department.CASTING, "DENT", RejectionSource.KAMAL, BusinessArea.KAMAL, RejectionSide.LH, 10))

        val pack = meetingPackService.generateMeetingPack(filter)
        val pareto = pack.paretoSection.paretoResult

        assertEquals(3, pareto.items.size)
        assertEquals("BLOWHOLE", pareto.items[0].defectName)
        assertEquals(60, pareto.items[0].quantity)
        assertEquals(60.0, pareto.items[0].percentage, 0.01)
        assertEquals(60.0, pareto.items[0].cumulativePercentage, 0.01)

        assertEquals("CRACK", pareto.items[1].defectName)
        assertEquals(30, pareto.items[1].quantity)
        assertEquals(30.0, pareto.items[1].percentage, 0.01)
        assertEquals(90.0, pareto.items[1].cumulativePercentage, 0.01)

        assertEquals("DENT", pareto.items[2].defectName)
        assertEquals(10, pareto.items[2].quantity)
        assertEquals(10.0, pareto.items[2].percentage, 0.01)
        assertEquals(100.0, pareto.items[2].cumulativePercentage, 0.01)
    }

    // -------------------------------------------------------------------------
    // 8. Stock Summary
    // -------------------------------------------------------------------------
    @Test
    fun test08_stockSummary() = runBlocking {
        val filter = AnalyticsFilterContext.singleDay(testDate)

        stockRepository.insert(StockRecord("S1", testDate, ManufacturingModel.U86, openingQuantity = 1200, closingQuantity = 1150))
        stockRepository.insert(StockRecord("S2", testDate, ManufacturingModel.U244, openingQuantity = 800, closingQuantity = 850))

        val summary = meetingPackService.generateManagementSummary(filter)

        assertEquals(MetricData.Value(2000), summary.openingStockSummary)
        assertEquals(MetricData.Value(2000), summary.closingStockSummary)

        val pack = meetingPackService.generateMeetingPack(filter)
        assertEquals(MetricData.Value(0), pack.stockSection.netChange)
    }

    // -------------------------------------------------------------------------
    // 9. Action Summary
    // -------------------------------------------------------------------------
    @Test
    fun test09_actionSummary() = runBlocking {
        val filter = AnalyticsFilterContext.singleDay(testDate)

        actionTrackerRepository.insert(ActionRecord("A1", "Action 1", "Desc", Department.CASTING, ActionStatus.OPEN, ActionPriority.CRITICAL, testDate, createdBy = "USR1"))
        actionTrackerRepository.insert(ActionRecord("A2", "Action 2", "Desc", Department.CASTING, ActionStatus.ASSIGNED, ActionPriority.HIGH, testDate, createdBy = "USR1", assigneeId = "USR2", assigneeName = "Bob"))
        actionTrackerRepository.insert(ActionRecord("A3", "Action 3", "Desc", Department.CASTING, ActionStatus.ON_HOLD, ActionPriority.MEDIUM, testDate, createdBy = "USR1", holdReason = "Awaiting parts"))
        actionTrackerRepository.insert(ActionRecord("A4", "Action 4", "Desc", Department.CASTING, ActionStatus.COMPLETED, ActionPriority.LOW, testDate, createdBy = "USR1", assigneeId = "USR2", assigneeName = "Bob"))
        actionTrackerRepository.insert(ActionRecord("A5", "Action 5", "Desc", Department.CASTING, ActionStatus.CLOSED, ActionPriority.LOW, testDate, createdBy = "USR1"))

        val summary = meetingPackService.generateManagementSummary(filter)
        val act = summary.actionSummary

        assertEquals(5, act.totalActions)
        assertEquals(2, act.openActions) // OPEN + ASSIGNED
        assertEquals(1, act.criticalActions)
        assertEquals(1, act.highActions)
        assertEquals(1, act.onHoldActions)
        assertEquals(1, act.completedActions)
        assertEquals(1, act.closedActions)
    }

    // -------------------------------------------------------------------------
    // 10. Filter Propagation
    // -------------------------------------------------------------------------
    @Test
    fun test10_filterPropagation() = runBlocking {
        // Records for two models: U86 and U244
        productionRepository.insert(ProductionRecord("P1", startDate, ManufacturingModel.U86, Department.CASTING, ProductionQuantity(100)))
        productionRepository.insert(ProductionRecord("P2", startDate, ManufacturingModel.U244, Department.CASTING, ProductionQuantity(250)))

        // Specific filter for U86
        val u86Filter = AnalyticsFilterContext(
            fromDate = startDate,
            toDate = endDate,
            model = ModelFilter.Specific(ManufacturingModel.U86)
        )

        val summary = meetingPackService.generateManagementSummary(u86Filter)
        assertEquals(MetricData.Value(100), summary.productionSummary)

        // Underlying repository preserved
        assertEquals(2, productionRepository.getByDateRange(startDate, endDate).size)
    }

    // -------------------------------------------------------------------------
    // 11. 0 vs NO DATA
    // -------------------------------------------------------------------------
    @Test
    fun test11_zeroVsNoData() = runBlocking {
        val filter = AnalyticsFilterContext.singleDay(testDate)

        // 1. Day with 0 quantity explicitly recorded
        productionRepository.insert(ProductionRecord("P0", testDate, ManufacturingModel.U86, Department.CASTING, ProductionQuantity(0)))
        val summaryZero = meetingPackService.generateManagementSummary(filter)
        assertEquals(MetricData.Value(0), summaryZero.productionSummary)
        assertTrue(summaryZero.productionSummary.isPresent)

        // 2. Day with no records at all
        productionRepository.clear()
        val summaryNoData = meetingPackService.generateManagementSummary(filter)
        assertEquals(MetricData.NoData, summaryNoData.productionSummary)
        assertFalse(summaryNoData.productionSummary.isPresent)
    }

    // -------------------------------------------------------------------------
    // 12. CSV Formula-Injection Sanitization
    // -------------------------------------------------------------------------
    @Test
    fun test12_csvFormulaInjectionSanitization() {
        val maliciousInputs = listOf(
            "=SUM(A1:A10)",
            "+12345",
            "-cmd|'/C calc'!A0",
            "@dangerousFunction()",
            "\tTAB_ATTACK",
            "\rCR_ATTACK"
        )

        for (input in maliciousInputs) {
            val sanitized = CsvSanitizer.sanitizeValue(input)
            assertTrue("Input '$input' must start with single-quote: '$sanitized'", sanitized.startsWith("'"))

            val escaped = CsvSanitizer.escapeCsvField(input)
            assertTrue("Escaped field must be safe from formula execution: '$escaped'", escaped.contains("'"))
        }

        // Safe input remains unescaped by single-quote
        val safeInput = "Normal manufacturing defect description"
        assertEquals(safeInput, CsvSanitizer.sanitizeValue(safeInput))

        // Row formatting safety
        val row = listOf("Safe", "=malicious()", "100")
        val formatted = CsvSanitizer.formatRow(row)
        assertTrue(formatted.contains("\"'=malicious()\""))
    }

    // -------------------------------------------------------------------------
    // 13. Deterministic Report Output
    // -------------------------------------------------------------------------
    @Test
    fun test13_deterministicReportOutput() = runBlocking {
        val filter = AnalyticsFilterContext.singleDay(testDate)
        productionRepository.insert(ProductionRecord("P1", testDate, ManufacturingModel.U86, Department.CASTING, ProductionQuantity(400)))
        planningRepository.insert(PlanRecord("PL1", testDate, ManufacturingModel.U86, Department.CASTING, 500))

        val pack1 = meetingPackService.generateMeetingPack(filter, generatedAt = "2026-09-15T10:00:00Z")
        val pack2 = meetingPackService.generateMeetingPack(filter, generatedAt = "2026-09-15T10:00:00Z")

        val doc1 = ExportConverters.fromMeetingPack(pack1)
        val doc2 = ExportConverters.fromMeetingPack(pack2)

        assertEquals("CSV exports must be identical for identical inputs", doc1.toCsv(), doc2.toCsv())
        assertEquals(pack1.kpis, pack2.kpis)
    }

    // -------------------------------------------------------------------------
    // 14. Missing Root Cause Remains NO DATA
    // -------------------------------------------------------------------------
    @Test
    fun test14_missingRootCauseRemainsNoData() = runBlocking {
        val rejections = listOf(
            RejectionRecord("R1", testDate, ManufacturingModel.U86, Department.CASTING, "BLOWHOLE", RejectionSource.KAMAL, BusinessArea.KAMAL, RejectionSide.LH, 10)
        )
        val items = meetingPackService.buildDefectReportItems(rejections)

        assertEquals(1, items.size)
        assertEquals("BLOWHOLE", items.first().defectName)
        assertEquals("Missing root cause must strictly be NoData", MetricData.NoData, items.first().rootCause)
        assertFalse(items.first().rootCause.isPresent)

        // Verify in daily report export
        rejectionRepository.insertAll(rejections)
        val dailyReport = meetingPackService.generateDailyReport(testDate)
        val exportDoc = ExportConverters.fromDailyReport(dailyReport)
        val csv = exportDoc.toCsv()

        assertTrue("CSV must render 'NO DATA' for missing root cause: $csv", csv.contains("NO DATA"))
        assertFalse("Must never output 'null' or invented cause", csv.contains("null"))
    }

    // -------------------------------------------------------------------------
    // 15. Contract Consistency
    // -------------------------------------------------------------------------
    @Test
    fun test15_contractConsistency() = runBlocking {
        val filter = AnalyticsFilterContext.singleDay(testDate)

        productionRepository.insert(ProductionRecord("P1", testDate, ManufacturingModel.U86, Department.CASTING, ProductionQuantity(300)))
        rejectionRepository.insert(RejectionRecord("R1", testDate, ManufacturingModel.U86, Department.CASTING, "BLOWHOLE", RejectionSource.KAMAL, BusinessArea.KAMAL, RejectionSide.LH, 20))
        actionTrackerRepository.insert(ActionRecord("A1", "Critical leak check", "Check furnace", Department.CASTING, ActionStatus.OPEN, ActionPriority.CRITICAL, testDate, createdBy = "ENG1"))

        val pack = meetingPackService.generateMeetingPack(filter)

        // Verify Attention Items are triggered purely by factual thresholds
        val critAttention = pack.exceptions.find { it.category == "ACTION_TRACKER" }
        assertNotNull(critAttention)
        assertEquals(AttentionLevel.CRITICAL, critAttention?.level)
        assertTrue(critAttention?.message?.contains("critical action item") == true)

        // Verify Export Document conversion
        val exportDoc = ExportConverters.fromMeetingPack(pack)
        assertEquals(pack.title, exportDoc.title)
        assertEquals(2, exportDoc.tables.size)
        assertEquals("KEY_PERFORMANCE_INDICATORS", exportDoc.tables[0].name)
        assertEquals("ATTENTION_AND_EXCEPTIONS", exportDoc.tables[1].name)

        val csv = exportDoc.toCsv()
        assertNotNull(csv)
        assertTrue(csv.contains("KEY_PERFORMANCE_INDICATORS"))
        assertTrue(csv.contains("ATTENTION_AND_EXCEPTIONS"))
    }
}
