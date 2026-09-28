package com.example

import com.example.analytics.AnalyticsFilterContext
import com.example.analytics.AnalyticsMetricType
import com.example.analytics.ChartType
import com.example.analytics.DepartmentFilter
import com.example.analytics.ModelFilter
import com.example.analytics.ParetoCalculator
import com.example.analytics.RejectionSourceFilter
import com.example.core.BusinessDate
import com.example.core.DataState
import com.example.core.MetricData
import com.example.fixture.Step1TestFixture
import com.example.model.BusinessArea
import com.example.model.Department
import com.example.model.ManufacturingFlow
import com.example.model.ManufacturingModel
import com.example.model.ModelApplicabilityValidator
import com.example.model.ProductionQuantity
import com.example.model.ProductionRecord
import com.example.model.RebuffingRule
import com.example.model.RejectionQuantity
import com.example.model.RejectionRecord
import com.example.model.RejectionSide
import com.example.model.RejectionSource
import com.example.model.StockQuantity
import com.example.model.StockRecord
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
 * Step 1 Verification Test Suite: Foundation + Analytics-Ready Architecture.
 *
 * Verifies all 20 required architecture contracts using actual production declarations.
 */
class Step1FoundationTest {

    private lateinit var fixture: Step1TestFixture

    @Before
    fun setUp() {
        fixture = Step1TestFixture()
    }

    // 1. Exact model master
    @Test
    fun testExactModelMaster() {
        val expectedCodes = listOf("U86", "U180", "U244", "MAXR", "N282-DISC", "DRUM", "N360")
        val actualCodes = ManufacturingModel.entries.map { it.code }

        assertEquals(7, ManufacturingModel.entries.size)
        assertEquals(expectedCodes, actualCodes)

        assertEquals("U86", ManufacturingModel.U86.displayName)
        assertEquals("U180", ManufacturingModel.U180.displayName)
        assertEquals("U244", ManufacturingModel.U244.displayName)
        assertEquals("MAXR", ManufacturingModel.MAXR.displayName)
        assertEquals("N282-DISC", ManufacturingModel.N282_DISC.displayName)
        assertEquals("DRUM", ManufacturingModel.DRUM.displayName)
        assertEquals("N360", ManufacturingModel.N360.displayName)
    }

    // 2. ALL filter-only rule
    @Test
    fun testAllFilterOnlyRule() {
        assertNull(ManufacturingModel.fromCodeOrNull("ALL"))

        try {
            ManufacturingModel.fromCode("ALL")
            fail("ManufacturingModel.fromCode('ALL') must throw IllegalArgumentException")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message?.contains("filter-only", ignoreCase = true) == true)
        }

        val allFilter: ModelFilter = ModelFilter.All
        assertTrue(allFilter is ModelFilter.All)
        assertTrue(allFilter.matches(ManufacturingModel.U86))
        assertTrue(allFilter.matches(ManufacturingModel.MAXR))
    }

    // 3. Exact department master
    @Test
    fun testExactDepartmentMaster() {
        val expectedDisplayNames = listOf(
            "CASTING",
            "POST CASTING",
            "M/C",
            "BUFFING",
            "FINAL",
            "KAMAL OK",
            "GIL DELIVERY"
        )
        val actualDisplayNames = Department.CANONICAL_SEQUENCE.map { it.displayName }

        assertEquals(7, Department.entries.size)
        assertEquals(expectedDisplayNames, actualDisplayNames)
    }

    // 4. Department sequence
    @Test
    fun testDepartmentSequence() {
        val sequence = Department.CANONICAL_SEQUENCE
        assertEquals(Department.CASTING, sequence[0])
        assertEquals(1, sequence[0].sequenceNumber)

        assertEquals(Department.POST_CASTING, sequence[1])
        assertEquals(2, sequence[1].sequenceNumber)

        assertEquals(Department.MACHINING, sequence[2])
        assertEquals(3, sequence[2].sequenceNumber)

        assertEquals(Department.BUFFING, sequence[3])
        assertEquals(4, sequence[3].sequenceNumber)

        assertEquals(Department.FINAL, sequence[4])
        assertEquals(5, sequence[4].sequenceNumber)

        assertEquals(Department.KAMAL_OK, sequence[5])
        assertEquals(6, sequence[5].sequenceNumber)

        assertEquals(Department.GIL_DELIVERY, sequence[6])
        assertEquals(7, sequence[6].sequenceNumber)
    }

    // 5. U180 GIL-only rule
    @Test
    fun testU180GilOnlyRule() {
        assertTrue(ModelApplicabilityValidator.isApplicable(ManufacturingModel.U180, Department.GIL_DELIVERY))

        val nonGilDepartments = listOf(
            Department.CASTING,
            Department.POST_CASTING,
            Department.MACHINING,
            Department.BUFFING,
            Department.FINAL,
            Department.KAMAL_OK
        )

        for (dept in nonGilDepartments) {
            assertFalse(
                "U180 must not be applicable to ${dept.displayName}",
                ModelApplicabilityValidator.isApplicable(ManufacturingModel.U180, dept)
            )

            try {
                ModelApplicabilityValidator.validateApplicability(ManufacturingModel.U180, dept)
                fail("Expected IllegalArgumentException for U180 in ${dept.displayName}")
            } catch (e: IllegalArgumentException) {
                // Expected
            }
        }
    }

    // 6. MAXR GIL-only rule
    @Test
    fun testMaxrGilOnlyRule() {
        assertTrue(ModelApplicabilityValidator.isApplicable(ManufacturingModel.MAXR, Department.GIL_DELIVERY))

        val nonGilDepartments = listOf(
            Department.CASTING,
            Department.POST_CASTING,
            Department.MACHINING,
            Department.BUFFING,
            Department.FINAL,
            Department.KAMAL_OK
        )

        for (dept in nonGilDepartments) {
            assertFalse(
                "MAXR must not be applicable to ${dept.displayName}",
                ModelApplicabilityValidator.isApplicable(ManufacturingModel.MAXR, dept)
            )

            try {
                ModelApplicabilityValidator.validateApplicability(ManufacturingModel.MAXR, dept)
                fail("Expected IllegalArgumentException for MAXR in ${dept.displayName}")
            } catch (e: IllegalArgumentException) {
                // Expected
            }
        }
    }

    // 7. Rejection sources
    @Test
    fun testRejectionSources() {
        val expected = listOf("KAMAL", "MRN", "DSPL")
        val actual = RejectionSource.entries.map { it.code }
        assertEquals(3, RejectionSource.entries.size)
        assertEquals(expected, actual)
    }

    // 8. Rejection business areas
    @Test
    fun testRejectionBusinessAreas() {
        val expected = listOf("KAMAL", "GABRIEL", "DSPL")
        val actual = BusinessArea.entries.map { it.code }
        assertEquals(3, BusinessArea.entries.size)
        assertEquals(expected, actual)
    }

    // 9. LH/RH only
    @Test
    fun testLhRhOnly() {
        assertEquals(2, RejectionSide.entries.size)
        assertEquals(RejectionSide.LH, RejectionSide.parse("LH"))
        assertEquals(RejectionSide.RH, RejectionSide.parse("RH"))
    }

    // 10. BOTH rejected
    @Test
    fun testBothSideRejected() {
        assertNull(RejectionSide.parseOrNull("BOTH"))

        try {
            RejectionSide.parse("BOTH")
            fail("RejectionSide.parse('BOTH') must throw IllegalArgumentException")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message?.contains("invalid", ignoreCase = true) == true)
        }
    }

    // 11. Production has no LH/RH
    @Test
    fun testProductionHasNoLhRh() {
        val prodQty = ProductionQuantity(50)
        assertEquals(50, prodQty.value)

        val prodRecord = ProductionRecord(
            id = "PRD-TEST",
            date = BusinessDate.parse("2026-09-01"),
            model = ManufacturingModel.U86,
            department = Department.CASTING,
            quantity = prodQty
        )
        // Verify class declaration has no 'side' property
        val properties = ProductionRecord::class.java.declaredFields.map { it.name }
        assertFalse("ProductionRecord must not have a 'side' property", properties.contains("side"))
    }

    // 12. KAMAL-only rebuffing
    @Test
    fun testKamalOnlyRebuffing() {
        assertTrue(RebuffingRule.canBeRebuffed(RejectionSource.KAMAL))
        assertFalse(RebuffingRule.canBeRebuffed(RejectionSource.MRN))
        assertFalse(RebuffingRule.canBeRebuffed(RejectionSource.DSPL))

        // Valid KAMAL rebuffing
        val validRebuffRecord = RejectionRecord(
            id = "REJ-KAMAL",
            date = BusinessDate.parse("2026-09-01"),
            model = ManufacturingModel.U86,
            department = Department.BUFFING,
            defectName = "Dent",
            source = RejectionSource.KAMAL,
            businessArea = BusinessArea.KAMAL,
            side = RejectionSide.LH,
            quantity = 2,
            isRebuffed = true
        )
        assertTrue(validRebuffRecord.isRebuffed)

        // Invalid MRN rebuffing
        try {
            RejectionRecord(
                id = "REJ-MRN",
                date = BusinessDate.parse("2026-09-01"),
                model = ManufacturingModel.U86,
                department = Department.BUFFING,
                defectName = "Dent",
                source = RejectionSource.MRN,
                businessArea = BusinessArea.GABRIEL,
                side = RejectionSide.LH,
                quantity = 2,
                isRebuffed = true
            )
            fail("MRN rejection must not be allowed as rebuffing")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message?.contains("KAMAL", ignoreCase = true) == true)
        }
    }

    // 13. 0 vs NO_DATA
    @Test
    fun testZeroVsNoData() {
        val zeroMetric: MetricData<Int> = MetricData.Value(0)
        val noDataMetric: MetricData<Int> = MetricData.NoData

        assertEquals(DataState.VALUE, zeroMetric.state)
        assertEquals(DataState.NO_DATA, noDataMetric.state)

        assertNotEquals(zeroMetric, noDataMetric)
        assertEquals("0", zeroMetric.formatForDisplay())
        assertEquals("NO DATA", noDataMetric.formatForDisplay())

        assertEquals(0, zeroMetric.valueOrNull())
        assertNull(noDataMetric.valueOrNull())
    }

    // 14. Date format
    @Test
    fun testDateFormat() {
        val validDate = BusinessDate.parse("2026-09-15")
        assertEquals(2026, validDate.year)
        assertEquals(9, validDate.month)
        assertEquals(15, validDate.day)
        assertEquals("2026-09-15", validDate.toString())

        // Invalid formats must fail
        val invalidDates = listOf("15/09/2026", "2026/09/15", "2026-02-31", "15-09-2026", "random-date")
        for (invalid in invalidDates) {
            assertNull("Expected null for parseOrNull('$invalid')", BusinessDate.parseOrNull(invalid))
            try {
                BusinessDate.parse(invalid)
                fail("Expected IllegalArgumentException for '$invalid'")
            } catch (e: IllegalArgumentException) {
                // Expected
            }
        }
    }

    // 15. Quantity validation
    @Test
    fun testQuantityValidation() {
        try {
            ProductionQuantity(-5)
            fail("Expected exception for negative production quantity")
        } catch (e: IllegalArgumentException) {
            // Expected
        }

        try {
            RejectionQuantity(-1, RejectionSide.LH)
            fail("Expected exception for negative rejection quantity")
        } catch (e: IllegalArgumentException) {
            // Expected
        }

        try {
            StockQuantity(-10, 50)
            fail("Expected exception for negative opening stock")
        } catch (e: IllegalArgumentException) {
            // Expected
        }

        try {
            StockQuantity(50, -5)
            fail("Expected exception for negative closing stock")
        } catch (e: IllegalArgumentException) {
            // Expected
        }
    }

    // 16. Canonical flow
    @Test
    fun testCanonicalFlow() {
        val sequence = ManufacturingFlow.getSequence()
        assertEquals(7, sequence.size)
        assertEquals(Department.CASTING, sequence[0])
        assertEquals(Department.POST_CASTING, sequence[1])
        assertEquals(Department.MACHINING, sequence[2])
        assertEquals(Department.BUFFING, sequence[3])
        assertEquals(Department.FINAL, sequence[4])
        assertEquals(Department.KAMAL_OK, sequence[5])
        assertEquals(Department.GIL_DELIVERY, sequence[6])

        assertEquals(Department.POST_CASTING, ManufacturingFlow.getNextStep(Department.CASTING))
        assertEquals(Department.CASTING, ManufacturingFlow.getPreviousStep(Department.POST_CASTING))
        assertNull(ManufacturingFlow.getNextStep(Department.GIL_DELIVERY))
        assertNull(ManufacturingFlow.getPreviousStep(Department.CASTING))
    }

    // 17. Analytics filter contract
    @Test
    fun testAnalyticsFilterContract() {
        val from = BusinessDate.parse("2026-09-01")
        val to = BusinessDate.parse("2026-09-30")

        val filter = AnalyticsFilterContext(
            fromDate = from,
            toDate = to,
            model = ModelFilter.Specific(ManufacturingModel.U86),
            department = DepartmentFilter.Specific(Department.CASTING),
            rejectionSource = RejectionSourceFilter.Specific(RejectionSource.KAMAL)
        )

        assertEquals(from, filter.fromDate)
        assertEquals(to, filter.toDate)
        assertTrue(filter.model is ModelFilter.Specific)
        assertTrue(filter.model.matches(ManufacturingModel.U86))
        assertFalse(filter.model.matches(ManufacturingModel.U180))

        // Invalid range must throw
        try {
            AnalyticsFilterContext(fromDate = to, toDate = from)
            fail("Filter fromDate after toDate must throw exception")
        } catch (e: IllegalArgumentException) {
            // Expected
        }
    }

    // 18. Chart date-range contract (01-Sep through 30-Sep)
    @Test
    fun testChartDateRangeContract() {
        val from = BusinessDate.parse("2026-09-01")
        val to = BusinessDate.parse("2026-09-30")
        val fullSequence = BusinessDate.generateSequence(from, to)

        // Must generate all 30 consecutive dates
        assertEquals(30, fullSequence.size)
        assertEquals("2026-09-01", fullSequence.first().toString())
        assertEquals("2026-09-30", fullSequence.last().toString())

        // Ensure no skipping (e.g. not only odd numbers)
        for (day in 1..30) {
            val expectedDayStr = "2026-09-%02d".format(day)
            assertEquals(expectedDayStr, fullSequence[day - 1].toString())
        }

        // Test with daily trend pipeline:
        // Day 1 has 100, Day 2 has recorded 0, Day 3 onwards has no data
        val records = listOf(
            fixture.createSampleProductionRecord(
                id = "P1",
                date = BusinessDate.parse("2026-09-01"),
                quantity = 100
            ),
            fixture.createSampleProductionRecord(
                id = "P2",
                date = BusinessDate.parse("2026-09-02"),
                quantity = 0
            )
        )

        val filter = AnalyticsFilterContext(fromDate = from, toDate = to)
        val trend = fixture.analyticsService.buildDailyProductionTrend(records, filter)

        assertEquals(30, trend.points.size)

        // Day 1: Value(100)
        assertEquals(MetricData.Value(100), trend.points[0].metric)
        assertEquals(DataState.VALUE, trend.points[0].metric.state)

        // Day 2: Value(0) - NOT NO_DATA
        assertEquals(MetricData.Value(0), trend.points[1].metric)
        assertEquals(DataState.VALUE, trend.points[1].metric.state)

        // Day 3: NoData - NOT 0
        assertEquals(MetricData.NoData, trend.points[2].metric)
        assertEquals(DataState.NO_DATA, trend.points[2].metric.state)
    }

    // 19. Pareto calculation contract
    @Test
    fun testParetoCalculationContract() {
        val filter = fixture.defaultFilterContext()
        val records = listOf(
            fixture.createSampleRejectionRecord(defectName = "Blowhole", quantity = 50),
            fixture.createSampleRejectionRecord(defectName = "Crack", quantity = 30),
            fixture.createSampleRejectionRecord(defectName = "Dent", quantity = 20)
        )

        val result = ParetoCalculator.computeFromRecords(records, filter)

        assertEquals(100, result.totalRejectionQuantity)
        assertEquals(3, result.items.size)
        assertEquals(DataState.VALUE, result.dataState)

        // 1. Sorted descending
        assertEquals("Blowhole", result.items[0].defectName)
        assertEquals(50, result.items[0].quantity)
        assertEquals(50.0, result.items[0].percentage, 0.01)
        assertEquals(50.0, result.items[0].cumulativePercentage, 0.01)

        assertEquals("Crack", result.items[1].defectName)
        assertEquals(30, result.items[1].quantity)
        assertEquals(30.0, result.items[1].percentage, 0.01)
        assertEquals(80.0, result.items[1].cumulativePercentage, 0.01)

        assertEquals("Dent", result.items[2].defectName)
        assertEquals(20, result.items[2].quantity)
        assertEquals(20.0, result.items[2].percentage, 0.01)
        assertEquals(100.0, result.items[2].cumulativePercentage, 0.01)
    }

    // 20. Repository contract consistency
    @Test
    fun testRepositoryContractConsistency() = runBlocking {
        val prodRepo = fixture.productionRepository
        val rejRepo = fixture.rejectionRepository
        val stkRepo = fixture.stockRepository

        val pRecord = fixture.createSampleProductionRecord()
        val rRecord = fixture.createSampleRejectionRecord()
        val sRecord = fixture.createSampleStockRecord()

        prodRepo.insert(pRecord)
        rejRepo.insert(rRecord)
        stkRepo.insert(sRecord)

        val filter = fixture.defaultFilterContext()

        val fetchedProds = prodRepo.getByFilter(filter)
        val fetchedRejs = rejRepo.getByFilter(filter)
        val fetchedStks = stkRepo.getByFilter(filter)

        assertEquals(1, fetchedProds.size)
        assertEquals(pRecord, fetchedProds[0])

        assertEquals(1, fetchedRejs.size)
        assertEquals(rRecord, fetchedRejs[0])

        assertEquals(1, fetchedStks.size)
        assertEquals(sRecord, fetchedStks[0])
    }
}
