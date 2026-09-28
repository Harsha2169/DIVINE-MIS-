package com.example

import com.example.analytics.AnalyticsFilterContext
import com.example.analytics.DepartmentFilter
import com.example.analytics.ModelFilter
import com.example.analytics.PlanningAnalyticsService
import com.example.core.BusinessDate
import com.example.core.MetricData
import com.example.model.CustomerRequirementRecord
import com.example.model.DailyDepartmentPlanRecord
import com.example.model.Department
import com.example.model.ManufacturingModel
import com.example.model.MonthlyDepartmentPlanRecord
import com.example.model.PlanRecord
import com.example.model.ProductionQuantity
import com.example.model.ProductionRecord
import com.example.model.validation.DomainValidator
import com.example.repository.PlanningRepository
import com.example.repository.memory.InMemoryPlanningRepository
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
 * Step 4 Verification Test Suite: Planning Domain and Customer Requirement Architecture.
 */
class Step4PlanningTest {

    private lateinit var planningRepo: PlanningRepository
    private lateinit var planningAnalytics: PlanningAnalyticsService

    @Before
    fun setUp() {
        planningRepo = InMemoryPlanningRepository()
        planningAnalytics = PlanningAnalyticsService()
    }

    // 1. Customer Requirement Creation & Validation
    @Test
    fun testCustomerRequirementCreationAndValidation() {
        val validDate = BusinessDate.parse("2026-09-01")
        val req = CustomerRequirementRecord(
            id = "CR-001",
            customerName = "GABRIEL INDIA",
            date = validDate,
            model = ManufacturingModel.U86,
            requiredQuantity = 500,
            notes = "Urgent dispatch"
        )
        assertEquals("CR-001", req.id)
        assertEquals("GABRIEL INDIA", req.customerName)
        assertEquals(validDate, req.date)
        assertEquals(ManufacturingModel.U86, req.model)
        assertEquals(500, req.requiredQuantity)

        // Quantity 0 is valid data
        val zeroReq = CustomerRequirementRecord(
            id = "CR-ZERO",
            customerName = "HERO MOTOCORP",
            date = validDate,
            model = ManufacturingModel.U244,
            requiredQuantity = 0
        )
        assertEquals(0, zeroReq.requiredQuantity)

        // Blank customer name must throw
        try {
            CustomerRequirementRecord(
                id = "CR-BAD",
                customerName = "   ",
                date = validDate,
                model = ManufacturingModel.U86,
                requiredQuantity = 100
            )
            fail("Blank customer name must throw IllegalArgumentException")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message?.contains("customer name", ignoreCase = true) == true)
        }

        // Blank ID must throw
        try {
            CustomerRequirementRecord(
                id = "",
                customerName = "BAJAJ",
                date = validDate,
                model = ManufacturingModel.U86,
                requiredQuantity = 100
            )
            fail("Blank ID must throw IllegalArgumentException")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message?.contains("ID", ignoreCase = true) == true)
        }

        // Negative quantity must throw
        try {
            CustomerRequirementRecord(
                id = "CR-NEG",
                customerName = "TVS",
                date = validDate,
                model = ManufacturingModel.U86,
                requiredQuantity = -10
            )
            fail("Negative quantity must throw IllegalArgumentException")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message?.contains("non-negative", ignoreCase = true) == true)
        }
    }

    // 2. Monthly Department Plan Validation
    @Test
    fun testMonthlyDepartmentPlanValidation() {
        val monthlyPlan = MonthlyDepartmentPlanRecord(
            id = "MP-2026-09-CASTING-U86",
            yearMonth = "2026-09",
            model = ManufacturingModel.U86,
            department = Department.CASTING,
            plannedQuantity = 12000,
            notes = "Target based on kiln capacity"
        )
        assertEquals("MP-2026-09-CASTING-U86", monthlyPlan.id)
        assertEquals("2026-09", monthlyPlan.yearMonth)
        assertEquals(Department.CASTING, monthlyPlan.department)
        assertEquals(12000, monthlyPlan.plannedQuantity)

        // Quantity 0 is valid
        val zeroPlan = MonthlyDepartmentPlanRecord(
            id = "MP-ZERO",
            yearMonth = "2026-09",
            model = ManufacturingModel.U86,
            department = Department.BUFFING,
            plannedQuantity = 0
        )
        assertEquals(0, zeroPlan.plannedQuantity)

        // Invalid YearMonth format must throw
        val invalidMonths = listOf("2026/09", "2026-9", "2026-13", "2026-00", "SEPTEMBER", "   ")
        for (badMonth in invalidMonths) {
            try {
                MonthlyDepartmentPlanRecord(
                    id = "MP-BAD",
                    yearMonth = badMonth,
                    model = ManufacturingModel.U86,
                    department = Department.CASTING,
                    plannedQuantity = 1000
                )
                fail("Invalid yearMonth '$badMonth' must throw IllegalArgumentException")
            } catch (e: IllegalArgumentException) {
                // Expected
            }
        }

        // Negative quantity must throw
        try {
            MonthlyDepartmentPlanRecord(
                id = "MP-NEG",
                yearMonth = "2026-09",
                model = ManufacturingModel.U86,
                department = Department.CASTING,
                plannedQuantity = -100
            )
            fail("Negative plannedQuantity must throw IllegalArgumentException")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message?.contains("non-negative", ignoreCase = true) == true)
        }
    }

    // 3. Daily Plan Validation
    @Test
    fun testDailyPlanValidation() {
        val validDate = BusinessDate.parse("2026-09-02")
        val dailyPlan: DailyDepartmentPlanRecord = PlanRecord(
            id = "DP-001",
            date = validDate,
            model = ManufacturingModel.N282_DISC,
            department = Department.MACHINING,
            plannedQuantity = 450
        )
        assertEquals("DP-001", dailyPlan.id)
        assertEquals(450, dailyPlan.plannedQuantity)

        // Quantity 0 is valid
        val zeroDaily = PlanRecord(
            id = "DP-ZERO",
            date = validDate,
            model = ManufacturingModel.N282_DISC,
            department = Department.MACHINING,
            plannedQuantity = 0
        )
        assertEquals(0, zeroDaily.plannedQuantity)

        // Blank ID must throw
        try {
            PlanRecord(
                id = "",
                date = validDate,
                model = ManufacturingModel.N282_DISC,
                department = Department.MACHINING,
                plannedQuantity = 100
            )
            fail("Blank ID must throw")
        } catch (e: IllegalArgumentException) {
            // Expected
        }

        // Negative quantity must throw
        try {
            PlanRecord(
                id = "DP-NEG",
                date = validDate,
                model = ManufacturingModel.N282_DISC,
                department = Department.MACHINING,
                plannedQuantity = -1
            )
            fail("Negative daily plannedQuantity must throw")
        } catch (e: IllegalArgumentException) {
            // Expected
        }
    }

    // 4. Customer Requirement != Department Execution Plan
    @Test
    fun testCustomerRequirementSeparationFromDepartmentPlan() {
        val date = BusinessDate.parse("2026-09-03")

        val customerReq = CustomerRequirementRecord(
            id = "CR-SEP-01",
            customerName = "GABRIEL",
            date = date,
            model = ManufacturingModel.U86,
            requiredQuantity = 1000
        )

        val castingPlan = PlanRecord(
            id = "PLN-SEP-01",
            date = date,
            model = ManufacturingModel.U86,
            department = Department.CASTING,
            plannedQuantity = 1100 // Buffer for scrap/yield
        )

        val mcPlan = PlanRecord(
            id = "PLN-SEP-02",
            date = date,
            model = ManufacturingModel.U86,
            department = Department.MACHINING,
            plannedQuantity = 1050
        )

        // The quantities are structurally independent and cannot be automatically derived
        assertNotEquals(customerReq.requiredQuantity, castingPlan.plannedQuantity)
        assertNotEquals(castingPlan.plannedQuantity, mcPlan.plannedQuantity)

        // Verify that DomainValidator asserts planning separation
        DomainValidator.validatePlanningSeparation(
            customerReqQty = customerReq.requiredQuantity,
            monthlyPlanQty = 30000,
            dailyPlanQty = castingPlan.plannedQuantity,
            actualProdQty = 980
        )
    }

    // 5. Department Plan != Actual Production
    @Test
    fun testDepartmentPlanSeparationFromActualProduction() {
        val date = BusinessDate.parse("2026-09-04")

        val plan = PlanRecord(
            id = "PLN-INDEP",
            date = date,
            model = ManufacturingModel.U244,
            department = Department.BUFFING,
            plannedQuantity = 600
        )

        val prod = ProductionRecord(
            id = "PRD-INDEP",
            date = date,
            model = ManufacturingModel.U244,
            department = Department.BUFFING,
            quantity = ProductionQuantity(520)
        )

        // The plan and production are strictly separate entities
        assertFalse(plan::class.java == prod::class.java)
        assertNotEquals(plan.plannedQuantity, prod.quantity.value)
    }

    // 6. Canonical Model and Department Validation
    @Test
    fun testCanonicalModelAndDepartmentValidation() {
        // Models must be one of the exact 7
        val models = ManufacturingModel.entries
        assertEquals(7, models.size)
        assertEquals(listOf("U86", "U180", "U244", "MAXR", "N282-DISC", "DRUM", "N360"), models.map { it.code })

        // Departments must be one of the exact 7
        val depts = Department.CANONICAL_SEQUENCE
        assertEquals(7, depts.size)
        assertEquals(
            listOf("CASTING", "POST CASTING", "M/C", "BUFFING", "FINAL", "KAMAL OK", "GIL DELIVERY"),
            depts.map { it.displayName }
        )

        // Invalid model throws
        try {
            DomainValidator.assertModelCanBeStored("U999")
            fail("Unknown model must throw")
        } catch (e: IllegalArgumentException) {
            // Expected
        }

        // Invalid department throws
        try {
            DomainValidator.assertDepartmentCanBeStored("PAINT SHOP")
            fail("Unknown department must throw")
        } catch (e: IllegalArgumentException) {
            // Expected
        }
    }

    // 7. U180 and MAXR GIL-only Applicability in Planning
    @Test
    fun testU180AndMaxrApplicabilityInPlanning() {
        val date = BusinessDate.parse("2026-09-05")

        // U180 allowed in GIL DELIVERY
        val u180GilPlan = PlanRecord(
            id = "P-U180-GIL",
            date = date,
            model = ManufacturingModel.U180,
            department = Department.GIL_DELIVERY,
            plannedQuantity = 200
        )
        assertEquals(Department.GIL_DELIVERY, u180GilPlan.department)

        // U180 prohibited in CASTING (throws)
        try {
            PlanRecord(
                id = "P-U180-BAD",
                date = date,
                model = ManufacturingModel.U180,
                department = Department.CASTING,
                plannedQuantity = 200
            )
            fail("U180 in CASTING must throw IllegalArgumentException")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message?.contains("GIL DELIVERY only", ignoreCase = true) == true)
        }

        // MAXR allowed in GIL DELIVERY for monthly plan
        val maxrMonthly = MonthlyDepartmentPlanRecord(
            id = "MP-MAXR-GIL",
            yearMonth = "2026-09",
            model = ManufacturingModel.MAXR,
            department = Department.GIL_DELIVERY,
            plannedQuantity = 5000
        )
        assertEquals(Department.GIL_DELIVERY, maxrMonthly.department)

        // MAXR prohibited in MACHINING for monthly plan (throws)
        try {
            MonthlyDepartmentPlanRecord(
                id = "MP-MAXR-BAD",
                yearMonth = "2026-09",
                model = ManufacturingModel.MAXR,
                department = Department.MACHINING,
                plannedQuantity = 5000
            )
            fail("MAXR in MACHINING must throw IllegalArgumentException")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message?.contains("GIL DELIVERY only", ignoreCase = true) == true)
        }
    }

    // 8. ALL Cannot Be Persisted in Planning
    @Test
    fun testAllPersistenceRejection() {
        // Customer requirement model cannot be ALL
        try {
            DomainValidator.assertModelCanBeStored("ALL")
            fail("ALL model must throw")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message?.contains("filter-only", ignoreCase = true) == true)
        }

        // Monthly plan department cannot be ALL
        try {
            DomainValidator.assertDepartmentCanBeStored("ALL")
            fail("ALL department must throw")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message?.contains("filter-only", ignoreCase = true) == true)
        }
    }

    // 9. Zero vs NO DATA In Planning Analytics
    @Test
    fun testZeroVsNoDataInPlanningAnalytics() {
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

        // Day 1 has plan = 100, Day 2 has plan = 0 (planned downtime), Day 3 has NO record
        val plans = listOf(
            PlanRecord("P1", d1, ManufacturingModel.U86, Department.CASTING, 100),
            PlanRecord("P2", d2, ManufacturingModel.U86, Department.CASTING, 0)
        )

        val trend = planningAnalytics.buildDailyPlanTrend(plans, filter)
        assertEquals(3, trend.points.size)

        // Day 1: Value(100)
        assertEquals(MetricData.Value(100), trend.points[0].metric)

        // Day 2: Value(0) — 0 is valid data!
        assertEquals(MetricData.Value(0), trend.points[1].metric)

        // Day 3: NoData — missing record is NOT converted to 0
        assertEquals(MetricData.NoData, trend.points[2].metric)
    }

    // 10. Date and YearMonth Validation
    @Test
    fun testDateAndYearMonthValidation() {
        // Valid business date
        val d = DomainValidator.validateBusinessDate("2026-09-06")
        assertEquals(2026, d.year)
        assertEquals(9, d.month)
        assertEquals(6, d.day)

        // Invalid business dates throw
        val invalidDates = listOf("2026-02-30", "06-09-2026", "2026/09/06", "bad-date")
        for (badDate in invalidDates) {
            try {
                DomainValidator.validateBusinessDate(badDate)
                fail("Invalid date '$badDate' must throw")
            } catch (e: Exception) {
                // Expected
            }
        }

        // YearMonth validation
        assertEquals("2026-09", DomainValidator.validateYearMonth("2026-09"))
        try {
            DomainValidator.validateYearMonth("2026-15")
            fail("Invalid month must throw")
        } catch (e: IllegalArgumentException) {
            // Expected
        }
    }

    // 11. Quantity Validation
    @Test
    fun testQuantityValidation() {
        // Non-negative
        DomainValidator.validateNonNegativeQuantity(0, "TestPlan")
        DomainValidator.validateNonNegativeQuantity(1500, "TestPlan")

        // Negative throws
        try {
            DomainValidator.validateNonNegativeQuantity(-5, "TestPlan")
            fail("Negative quantity must throw")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message?.contains("non-negative", ignoreCase = true) == true)
        }
    }

    // 12. Repository Contract Consistency
    @Test
    fun testRepositoryContractConsistency() = runBlocking {
        val date1 = BusinessDate.parse("2026-09-01")
        val date2 = BusinessDate.parse("2026-09-02")

        // 1. Daily Plan
        val daily = PlanRecord("DP-REP-1", date1, ManufacturingModel.U86, Department.CASTING, 300)
        planningRepo.insert(daily)
        assertEquals(daily, planningRepo.getById("DP-REP-1"))

        val filter = AnalyticsFilterContext(
            fromDate = date1,
            toDate = date2,
            selectedDate = null,
            model = ModelFilter.Specific(ManufacturingModel.U86),
            department = DepartmentFilter.Specific(Department.CASTING)
        )
        val dailyResults = planningRepo.getByFilter(filter)
        assertEquals(1, dailyResults.size)
        assertEquals(300, dailyResults[0].plannedQuantity)

        // 2. Customer Requirement
        val custReq = CustomerRequirementRecord(
            id = "CR-REP-1",
            customerName = "HERO",
            date = date1,
            model = ManufacturingModel.U86,
            requiredQuantity = 2000
        )
        planningRepo.insertCustomerRequirement(custReq)
        assertEquals(custReq, planningRepo.getCustomerRequirementById("CR-REP-1"))

        val custResults = planningRepo.getCustomerRequirementsByFilter(filter)
        assertEquals(1, custResults.size)
        assertEquals("HERO", custResults[0].customerName)

        // 3. Monthly Department Plan
        val monthly = MonthlyDepartmentPlanRecord(
            id = "MP-REP-1",
            yearMonth = "2026-09",
            model = ManufacturingModel.U86,
            department = Department.CASTING,
            plannedQuantity = 9000
        )
        planningRepo.insertMonthlyPlan(monthly)
        assertEquals(monthly, planningRepo.getMonthlyPlanById("MP-REP-1"))

        val monthlyList = planningRepo.getMonthlyPlansByYearMonth("2026-09")
        assertEquals(1, monthlyList.size)
        assertEquals(9000, monthlyList[0].plannedQuantity)

        // Deletions
        assertTrue(planningRepo.deleteById("DP-REP-1"))
        assertNull(planningRepo.getById("DP-REP-1"))

        assertTrue(planningRepo.deleteCustomerRequirementById("CR-REP-1"))
        assertNull(planningRepo.getCustomerRequirementById("CR-REP-1"))

        assertTrue(planningRepo.deleteMonthlyPlanById("MP-REP-1"))
        assertNull(planningRepo.getMonthlyPlanById("MP-REP-1"))
    }
}
