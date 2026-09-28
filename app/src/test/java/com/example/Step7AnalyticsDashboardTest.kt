package com.example

import com.example.analytics.AnalyticsFilterContext
import com.example.analytics.BusinessAreaFilter
import com.example.analytics.DashboardKpis
import com.example.analytics.DashboardState
import com.example.analytics.DashboardViewModel
import com.example.analytics.DefectFilter
import com.example.analytics.DepartmentFilter
import com.example.analytics.ManufacturingAnalyticsService
import com.example.analytics.ModelFilter
import com.example.analytics.ParetoCalculator
import com.example.analytics.PlanningAnalyticsService
import com.example.analytics.RejectionSourceFilter
import com.example.analytics.SideFilter
import com.example.core.BusinessDate
import com.example.core.DataState
import com.example.core.MetricData
import com.example.model.BusinessArea
import com.example.model.Department
import com.example.model.ManufacturingModel
import com.example.model.ModelApplicabilityValidator
import com.example.model.MonthlyDepartmentPlanRecord
import com.example.model.PlanRecord
import com.example.model.ProductionQuantity
import com.example.model.ProductionRecord
import com.example.model.RebuffingRule
import com.example.model.RejectionRecord
import com.example.model.RejectionSide
import com.example.model.RejectionSource
import com.example.model.StockRecord
import com.example.repository.memory.InMemoryPlanningRepository
import com.example.repository.memory.InMemoryProductionRepository
import com.example.repository.memory.InMemoryRejectionRepository
import com.example.repository.memory.InMemoryStockRepository
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test

/**
 * Step 7 Authoritative Analytics & Core MIS Dashboard Test Suite.
 *
 * Verifies all 23 core requirements:
 * 1. daily production trend includes every date in range
 * 2. no odd-date-only behavior
 * 3. Plan vs Actual
 * 4. Achievement %
 * 5. model-wise production
 * 6. department-wise production
 * 7. rejection trend
 * 8. rejection source aggregation
 * 9. rejection business-area aggregation
 * 10. LH/RH aggregation
 * 11. defect aggregation
 * 12. Pareto sorting
 * 13. Pareto contribution %
 * 14. Pareto cumulative %
 * 15. rebuffing only from KAMAL
 * 16. stock opening vs closing
 * 17. stock trend
 * 18. planning analytics
 * 19. common filter behavior
 * 20. 0 vs NO DATA
 * 21. U180/MAXR applicability
 * 22. dashboard state contract
 * 23. repository/analytics contract consistency
 */
class Step7AnalyticsDashboardTest {

    private lateinit var productionRepository: InMemoryProductionRepository
    private lateinit var rejectionRepository: InMemoryRejectionRepository
    private lateinit var stockRepository: InMemoryStockRepository
    private lateinit var planningRepository: InMemoryPlanningRepository

    private lateinit var analyticsService: ManufacturingAnalyticsService
    private lateinit var planningAnalyticsService: PlanningAnalyticsService
    private lateinit var dashboardViewModel: DashboardViewModel

    private val startDate = BusinessDate.parse("2026-09-01")
    private val endDate = BusinessDate.parse("2026-09-07") // 7 days

    @Before
    fun setUp() {
        productionRepository = InMemoryProductionRepository()
        rejectionRepository = InMemoryRejectionRepository()
        stockRepository = InMemoryStockRepository()
        planningRepository = InMemoryPlanningRepository()

        analyticsService = ManufacturingAnalyticsService()
        planningAnalyticsService = PlanningAnalyticsService()

        dashboardViewModel = DashboardViewModel(
            productionRepository = productionRepository,
            rejectionRepository = rejectionRepository,
            stockRepository = stockRepository,
            planningRepository = planningRepository,
            analyticsService = analyticsService,
            planningAnalyticsService = planningAnalyticsService,
            initialFilter = AnalyticsFilterContext(fromDate = startDate, toDate = endDate)
        )
    }

    // -------------------------------------------------------------------------
    // 1. Daily production trend includes every date in range
    // -------------------------------------------------------------------------
    @Test
    fun test01_dailyProductionTrendIncludesEveryDateInRange() = runBlocking {
        val filter = AnalyticsFilterContext(fromDate = startDate, toDate = endDate)
        productionRepository.insert(
            ProductionRecord("PRD-1", startDate, ManufacturingModel.U86, Department.CASTING, ProductionQuantity(150))
        )
        productionRepository.insert(
            ProductionRecord("PRD-2", BusinessDate.parse("2026-09-04"), ManufacturingModel.U86, Department.CASTING, ProductionQuantity(200))
        )

        val records = productionRepository.getByFilter(filter)
        val trend = analyticsService.buildDailyProductionTrend(records, filter)

        assertEquals("Should have exactly 7 points for a 7-day range", 7, trend.points.size)
        assertEquals("Start date must be first", startDate, trend.points.first().date)
        assertEquals("End date must be last", endDate, trend.points.last().date)

        val dates = trend.points.map { it.date.toString() }
        val expectedDates = listOf(
            "2026-09-01", "2026-09-02", "2026-09-03", "2026-09-04", "2026-09-05", "2026-09-06", "2026-09-07"
        )
        assertEquals(expectedDates, dates)
    }

    // -------------------------------------------------------------------------
    // 2. No odd-date-only behavior
    // -------------------------------------------------------------------------
    @Test
    fun test02_noOddDateOnlyBehavior() {
        val filter = AnalyticsFilterContext(
            fromDate = BusinessDate.parse("2026-09-01"),
            toDate = BusinessDate.parse("2026-09-06")
        )
        val trend = analyticsService.buildDailyProductionTrend(emptyList(), filter)

        assertEquals(6, trend.points.size)
        // Check both odd and even days are present
        assertEquals("2026-09-01", trend.points[0].date.toString())
        assertEquals("2026-09-02", trend.points[1].date.toString()) // Even day present!
        assertEquals("2026-09-03", trend.points[2].date.toString())
        assertEquals("2026-09-04", trend.points[3].date.toString()) // Even day present!
        assertEquals("2026-09-05", trend.points[4].date.toString())
        assertEquals("2026-09-06", trend.points[5].date.toString()) // Even day present!
    }

    // -------------------------------------------------------------------------
    // 3. Plan vs Actual
    // -------------------------------------------------------------------------
    @Test
    fun test03_planVsActual() = runBlocking {
        val d1 = BusinessDate.parse("2026-09-01")
        val d2 = BusinessDate.parse("2026-09-02")
        val filter = AnalyticsFilterContext(fromDate = d1, toDate = d2)

        planningRepository.insert(PlanRecord("PLN-1", d1, ManufacturingModel.U86, Department.CASTING, 500))
        productionRepository.insert(ProductionRecord("PRD-1", d1, ManufacturingModel.U86, Department.CASTING, ProductionQuantity(450)))

        val plans = planningRepository.getByFilter(filter)
        val prods = productionRepository.getByFilter(filter)

        val chart = analyticsService.buildPlanVsActual(plans, prods, filter)
        assertEquals(2, chart.points.size)

        // Day 1: Plan=500, Actual=450
        val p1 = chart.points[0]
        assertEquals(MetricData.Value(500), p1.primary)
        assertEquals(MetricData.Value(450), p1.secondary)
        assertEquals(MetricData.Value(90.0), p1.achievementPercentage)

        // Day 2: No records recorded -> NoData for both
        val p2 = chart.points[1]
        assertEquals(MetricData.NoData, p2.primary)
        assertEquals(MetricData.NoData, p2.secondary)
        assertEquals(MetricData.NoData, p2.achievementPercentage)
    }

    // -------------------------------------------------------------------------
    // 4. Achievement %
    // -------------------------------------------------------------------------
    @Test
    fun test04_achievementPercentage() {
        val d1 = BusinessDate.parse("2026-09-01")
        val filter = AnalyticsFilterContext.singleDay(d1)

        val plans = listOf(PlanRecord("PLN-1", d1, ManufacturingModel.U86, Department.CASTING, 200))
        val prods = listOf(ProductionRecord("PRD-1", d1, ManufacturingModel.U86, Department.CASTING, ProductionQuantity(150)))

        val achievement = analyticsService.calculateTotalAchievementPercentage(plans, prods, filter)
        assertEquals(MetricData.Value(75.0), achievement)

        // 0 planned returns 0.0
        val zeroPlan = listOf(PlanRecord("PLN-0", d1, ManufacturingModel.U86, Department.CASTING, 0))
        val zeroPlanAchievement = analyticsService.calculateTotalAchievementPercentage(zeroPlan, prods, filter)
        assertEquals(MetricData.Value(0.0), zeroPlanAchievement)

        // No records returns NoData
        val noDataAchievement = analyticsService.calculateTotalAchievementPercentage(emptyList(), prods, filter)
        assertEquals(MetricData.NoData, noDataAchievement)
    }

    // -------------------------------------------------------------------------
    // 5. Model-wise production
    // -------------------------------------------------------------------------
    @Test
    fun test05_modelWiseProduction() {
        val filter = AnalyticsFilterContext(fromDate = startDate, toDate = endDate)
        val prods = listOf(
            ProductionRecord("P1", startDate, ManufacturingModel.U86, Department.CASTING, ProductionQuantity(100)),
            ProductionRecord("P2", startDate, ManufacturingModel.U86, Department.CASTING, ProductionQuantity(50)),
            ProductionRecord("P3", startDate, ManufacturingModel.U244, Department.CASTING, ProductionQuantity(220))
        )

        val chart = analyticsService.buildModelWiseProduction(prods, filter)
        val map = chart.points.associate { it.category to it.metric }

        assertEquals(MetricData.Value(150), map["U86"])
        assertEquals(MetricData.Value(220), map["U244"])
        assertEquals(MetricData.NoData, map["DRUM"]) // Unrecorded model produces NoData
    }

    // -------------------------------------------------------------------------
    // 6. Department-wise production
    // -------------------------------------------------------------------------
    @Test
    fun test06_departmentWiseProduction() {
        val filter = AnalyticsFilterContext(fromDate = startDate, toDate = endDate)
        val prods = listOf(
            ProductionRecord("P1", startDate, ManufacturingModel.U86, Department.CASTING, ProductionQuantity(300)),
            ProductionRecord("P2", startDate, ManufacturingModel.U86, Department.BUFFING, ProductionQuantity(250))
        )

        val chart = analyticsService.buildDepartmentWiseProduction(prods, filter)
        val map = chart.points.associate { it.category to it.metric }

        assertEquals(MetricData.Value(300), map[Department.CASTING.displayName])
        assertEquals(MetricData.Value(250), map[Department.BUFFING.displayName])
        assertEquals(MetricData.NoData, map[Department.FINAL.displayName])
    }

    // -------------------------------------------------------------------------
    // 7. Rejection trend
    // -------------------------------------------------------------------------
    @Test
    fun test07_rejectionTrend() {
        val filter = AnalyticsFilterContext(
            fromDate = BusinessDate.parse("2026-09-01"),
            toDate = BusinessDate.parse("2026-09-03")
        )
        val rejections = listOf(
            RejectionRecord("R1", BusinessDate.parse("2026-09-01"), ManufacturingModel.U86, Department.CASTING, "Blowhole", RejectionSource.KAMAL, BusinessArea.KAMAL, RejectionSide.LH, 12),
            RejectionRecord("R2", BusinessDate.parse("2026-09-03"), ManufacturingModel.U86, Department.CASTING, "Crack", RejectionSource.KAMAL, BusinessArea.KAMAL, RejectionSide.RH, 8)
        )

        val trend = analyticsService.buildRejectionTrend(rejections, filter)
        assertEquals(3, trend.points.size)
        assertEquals(MetricData.Value(12), trend.points[0].metric)
        assertEquals(MetricData.NoData, trend.points[1].metric) // 09-02 has NoData
        assertEquals(MetricData.Value(8), trend.points[2].metric)
    }

    // -------------------------------------------------------------------------
    // 8. Rejection source aggregation
    // -------------------------------------------------------------------------
    @Test
    fun test08_rejectionSourceAggregation() {
        val filter = AnalyticsFilterContext(fromDate = startDate, toDate = endDate)
        val rejections = listOf(
            RejectionRecord("R1", startDate, ManufacturingModel.U86, Department.CASTING, "Blowhole", RejectionSource.KAMAL, BusinessArea.KAMAL, RejectionSide.LH, 30),
            RejectionRecord("R2", startDate, ManufacturingModel.U86, Department.CASTING, "Crack", RejectionSource.MRN, BusinessArea.DSPL, RejectionSide.RH, 15)
        )

        val chart = analyticsService.buildRejectionBySource(rejections, filter)
        val map = chart.points.associate { it.category to it.metric }

        assertEquals(MetricData.Value(30), map[RejectionSource.KAMAL.displayName])
        assertEquals(MetricData.Value(15), map[RejectionSource.MRN.displayName])
        assertEquals(MetricData.NoData, map[RejectionSource.DSPL.displayName])
    }

    // -------------------------------------------------------------------------
    // 9. Rejection business-area aggregation
    // -------------------------------------------------------------------------
    @Test
    fun test09_rejectionBusinessAreaAggregation() {
        val filter = AnalyticsFilterContext(fromDate = startDate, toDate = endDate)
        val rejections = listOf(
            RejectionRecord("R1", startDate, ManufacturingModel.U86, Department.CASTING, "Blowhole", RejectionSource.KAMAL, BusinessArea.KAMAL, RejectionSide.LH, 40),
            RejectionRecord("R2", startDate, ManufacturingModel.U86, Department.CASTING, "Crack", RejectionSource.DSPL, BusinessArea.DSPL, RejectionSide.RH, 25)
        )

        val chart = analyticsService.buildRejectionByBusinessArea(rejections, filter)
        val map = chart.points.associate { it.category to it.metric }

        assertEquals(MetricData.Value(40), map[BusinessArea.KAMAL.displayName])
        assertEquals(MetricData.Value(25), map[BusinessArea.DSPL.displayName])
    }

    // -------------------------------------------------------------------------
    // 10. LH/RH aggregation
    // -------------------------------------------------------------------------
    @Test
    fun test10_lhVsRhAggregation() {
        val filter = AnalyticsFilterContext(fromDate = startDate, toDate = endDate)
        val rejections = listOf(
            RejectionRecord("R1", startDate, ManufacturingModel.U86, Department.CASTING, "Blowhole", RejectionSource.KAMAL, BusinessArea.KAMAL, RejectionSide.LH, 60),
            RejectionRecord("R2", startDate, ManufacturingModel.U86, Department.CASTING, "Blowhole", RejectionSource.KAMAL, BusinessArea.KAMAL, RejectionSide.LH, 20),
            RejectionRecord("R3", startDate, ManufacturingModel.U86, Department.CASTING, "Blowhole", RejectionSource.KAMAL, BusinessArea.KAMAL, RejectionSide.RH, 35)
        )

        val chart = analyticsService.buildLhVsRh(rejections, filter)
        val map = chart.points.associate { it.category to it.metric }

        assertEquals(MetricData.Value(80), map[RejectionSide.LH.displayName])
        assertEquals(MetricData.Value(35), map[RejectionSide.RH.displayName])
    }

    // -------------------------------------------------------------------------
    // 11. Defect aggregation
    // -------------------------------------------------------------------------
    @Test
    fun test11_defectAggregation() {
        val filter = AnalyticsFilterContext(fromDate = startDate, toDate = endDate)
        val rejections = listOf(
            RejectionRecord("R1", startDate, ManufacturingModel.U86, Department.CASTING, "Blowhole", RejectionSource.KAMAL, BusinessArea.KAMAL, RejectionSide.LH, 50),
            RejectionRecord("R2", startDate, ManufacturingModel.U86, Department.CASTING, "Blowhole", RejectionSource.KAMAL, BusinessArea.KAMAL, RejectionSide.RH, 30),
            RejectionRecord("R3", startDate, ManufacturingModel.U86, Department.CASTING, "Crack", RejectionSource.KAMAL, BusinessArea.KAMAL, RejectionSide.LH, 20)
        )

        val chart = analyticsService.buildDefectWiseRejection(rejections, filter)
        val map = chart.points.associate { it.category to it.metric }

        assertEquals(MetricData.Value(80), map["Blowhole"])
        assertEquals(MetricData.Value(20), map["Crack"])
    }

    // -------------------------------------------------------------------------
    // 12. Pareto sorting
    // -------------------------------------------------------------------------
    @Test
    fun test12_paretoSorting() {
        val filter = AnalyticsFilterContext(fromDate = startDate, toDate = endDate)
        val defectMap = mapOf(
            "Dent" to 10,
            "Blowhole" to 100,
            "Crack" to 50,
            "Porosity" to 40
        )

        val result = ParetoCalculator.computeFromDefectMap(defectMap, filter)

        assertEquals("Blowhole", result.items[0].defectName)
        assertEquals("Crack", result.items[1].defectName)
        assertEquals("Porosity", result.items[2].defectName)
        assertEquals("Dent", result.items[3].defectName)

        assertTrue(result.items[0].quantity >= result.items[1].quantity)
        assertTrue(result.items[1].quantity >= result.items[2].quantity)
        assertTrue(result.items[2].quantity >= result.items[3].quantity)
    }

    // -------------------------------------------------------------------------
    // 13. Pareto contribution %
    // -------------------------------------------------------------------------
    @Test
    fun test13_paretoContributionPercentage() {
        val filter = AnalyticsFilterContext(fromDate = startDate, toDate = endDate)
        val defectMap = mapOf(
            "Blowhole" to 50,
            "Crack" to 30,
            "Dent" to 20
        )
        // Total = 100

        val result = ParetoCalculator.computeFromDefectMap(defectMap, filter)

        assertEquals(50.0, result.items[0].contributionPercentage, 0.01)
        assertEquals(30.0, result.items[1].contributionPercentage, 0.01)
        assertEquals(20.0, result.items[2].contributionPercentage, 0.01)
    }

    // -------------------------------------------------------------------------
    // 14. Pareto cumulative %
    // -------------------------------------------------------------------------
    @Test
    fun test14_paretoCumulativePercentage() {
        val filter = AnalyticsFilterContext(fromDate = startDate, toDate = endDate)
        val defectMap = mapOf(
            "Blowhole" to 50,
            "Crack" to 30,
            "Dent" to 20
        )

        val result = ParetoCalculator.computeFromDefectMap(defectMap, filter)

        assertEquals(50.0, result.items[0].cumulativePercentage, 0.01)
        assertEquals(80.0, result.items[1].cumulativePercentage, 0.01)
        assertEquals(100.0, result.items[2].cumulativePercentage, 0.01) // Locks strictly to 100.0%
    }

    // -------------------------------------------------------------------------
    // 15. Rebuffing only from KAMAL
    // -------------------------------------------------------------------------
    @Test
    fun test15_rebuffingOnlyFromKamal() {
        val filter = AnalyticsFilterContext(fromDate = startDate, toDate = endDate)

        // Domain invariant: Only KAMAL is eligible for rebuffing
        assertTrue(RebuffingRule.canBeRebuffed(RejectionSource.KAMAL))
        assertFalse(RebuffingRule.canBeRebuffed(RejectionSource.MRN))

        val rejections = listOf(
            RejectionRecord("R1", startDate, ManufacturingModel.U86, Department.CASTING, "Blowhole", RejectionSource.KAMAL, BusinessArea.KAMAL, RejectionSide.LH, 15, isRebuffed = true),
            RejectionRecord("R2", startDate, ManufacturingModel.U86, Department.CASTING, "Blowhole", RejectionSource.KAMAL, BusinessArea.KAMAL, RejectionSide.LH, 10, isRebuffed = false)
        )

        val rebuffingTrend = analyticsService.buildRebuffingTrend(rejections, filter)
        assertEquals(MetricData.Value(15), rebuffingTrend.points.first().metric)
    }

    // -------------------------------------------------------------------------
    // 16. Stock opening vs closing
    // -------------------------------------------------------------------------
    @Test
    fun test16_stockOpeningVsClosing() {
        val filter = AnalyticsFilterContext(
            fromDate = BusinessDate.parse("2026-09-01"),
            toDate = BusinessDate.parse("2026-09-02")
        )
        val stocks = listOf(
            StockRecord("S1", BusinessDate.parse("2026-09-01"), ManufacturingModel.U86, openingQuantity = 1000, closingQuantity = 950)
        )

        val chart = analyticsService.buildOpeningVsClosing(stocks, filter)
        assertEquals(2, chart.points.size)

        val day1 = chart.points[0]
        assertEquals(MetricData.Value(1000), day1.primary)
        assertEquals(MetricData.Value(950), day1.secondary)

        val day2 = chart.points[1]
        assertEquals(MetricData.NoData, day2.primary)
        assertEquals(MetricData.NoData, day2.secondary)
    }

    // -------------------------------------------------------------------------
    // 17. Stock trend
    // -------------------------------------------------------------------------
    @Test
    fun test17_stockTrend() {
        val filter = AnalyticsFilterContext(
            fromDate = BusinessDate.parse("2026-09-01"),
            toDate = BusinessDate.parse("2026-09-02")
        )
        val stocks = listOf(
            StockRecord("S1", BusinessDate.parse("2026-09-01"), ManufacturingModel.U86, openingQuantity = 500, closingQuantity = 480)
        )

        val trend = analyticsService.buildStockTrend(stocks, filter)
        assertEquals(2, trend.points.size)
        assertEquals(MetricData.Value(480), trend.points[0].metric)
        assertEquals(MetricData.NoData, trend.points[1].metric)
    }

    // -------------------------------------------------------------------------
    // 18. Planning analytics
    // -------------------------------------------------------------------------
    @Test
    fun test18_planningAnalytics() {
        val filter = AnalyticsFilterContext(fromDate = startDate, toDate = endDate)
        val plans = listOf(
            PlanRecord("P1", startDate, ManufacturingModel.U86, Department.CASTING, 400),
            PlanRecord("P2", startDate, ManufacturingModel.U244, Department.BUFFING, 300)
        )

        val dailyPlanTrend = planningAnalyticsService.buildDailyPlanTrend(plans, filter)
        assertEquals(7, dailyPlanTrend.points.size)
        assertEquals(MetricData.Value(700), dailyPlanTrend.points.first().metric)

        val modelWisePlan = planningAnalyticsService.buildModelWisePlan(plans, filter)
        val modelMap = modelWisePlan.points.associate { it.category to it.metric }
        assertEquals(MetricData.Value(400), modelMap["U86"])
        assertEquals(MetricData.Value(300), modelMap["U244"])

        val monthlyPlan = listOf(
            MonthlyDepartmentPlanRecord("MP1", "2026-09", ManufacturingModel.U86, Department.CASTING, 10000)
        )
        val monthlyPlanChart = planningAnalyticsService.buildMonthlyPlanVsActual(
            monthlyPlans = monthlyPlan,
            productions = listOf(ProductionRecord("PRD-1", startDate, ManufacturingModel.U86, Department.CASTING, ProductionQuantity(2500))),
            yearMonth = "2026-09",
            filter = filter
        )
        val u86Point = monthlyPlanChart.points.first { it.label == "U86" }
        assertEquals(MetricData.Value(10000), u86Point.primary)
        assertEquals(MetricData.Value(2500), u86Point.secondary)
        assertEquals(MetricData.Value(25.0), u86Point.achievementPercentage)
    }

    // -------------------------------------------------------------------------
    // 19. Common filter behavior
    // -------------------------------------------------------------------------
    @Test
    fun test19_commonFilterBehavior() = runBlocking {
        // Setup multiple records
        productionRepository.insertAll(listOf(
            ProductionRecord("P1", startDate, ManufacturingModel.U86, Department.CASTING, ProductionQuantity(100)),
            ProductionRecord("P2", startDate, ManufacturingModel.U244, Department.CASTING, ProductionQuantity(200)),
            ProductionRecord("P3", startDate, ManufacturingModel.U86, Department.BUFFING, ProductionQuantity(300))
        ))
        rejectionRepository.insertAll(listOf(
            RejectionRecord("R1", startDate, ManufacturingModel.U86, Department.CASTING, "Blowhole", RejectionSource.KAMAL, BusinessArea.KAMAL, RejectionSide.LH, 10),
            RejectionRecord("R2", startDate, ManufacturingModel.U86, Department.CASTING, "Blowhole", RejectionSource.MRN, BusinessArea.DSPL, RejectionSide.RH, 20)
        ))

        // Filter by Model: U86 only
        val modelFilter = AnalyticsFilterContext(
            fromDate = startDate,
            toDate = endDate,
            model = ModelFilter.Specific(ManufacturingModel.U86)
        )
        val prodsFiltered = productionRepository.getByFilter(modelFilter)
        assertEquals(2, prodsFiltered.size)
        assertTrue(prodsFiltered.all { it.model == ManufacturingModel.U86 })

        // Filter by Rejection Source: KAMAL only
        val sourceFilter = AnalyticsFilterContext(
            fromDate = startDate,
            toDate = endDate,
            rejectionSource = RejectionSourceFilter.Specific(RejectionSource.KAMAL)
        )
        val rejsFiltered = rejectionRepository.getByFilter(sourceFilter)
        assertEquals(1, rejsFiltered.size)
        assertEquals(RejectionSource.KAMAL, rejsFiltered.first().source)

        // Verify underlying repository data remains unmutated
        val allProds = productionRepository.getByDateRange(startDate, endDate)
        assertEquals(3, allProds.size)
    }

    // -------------------------------------------------------------------------
    // 20. 0 vs NO DATA
    // -------------------------------------------------------------------------
    @Test
    fun test20_zeroVsNoData() {
        val d1 = BusinessDate.parse("2026-09-01")
        val d2 = BusinessDate.parse("2026-09-02")
        val filter = AnalyticsFilterContext(fromDate = d1, toDate = d2)

        // Day 1 has a record with quantity 0 (real zero-production run)
        // Day 2 has NO record at all
        val records = listOf(
            ProductionRecord("P1", d1, ManufacturingModel.U86, Department.CASTING, ProductionQuantity(0))
        )

        val trend = analyticsService.buildDailyProductionTrend(records, filter)

        // Day 1 is VALUE(0)
        assertEquals(MetricData.Value(0), trend.points[0].metric)
        assertTrue("Day 1 must have data state VALUE", trend.points[0].metric.isPresent)

        // Day 2 is NO_DATA (never converted to 0)
        assertEquals(MetricData.NoData, trend.points[1].metric)
        assertFalse("Day 2 must not have data", trend.points[1].metric.isPresent)
    }

    // -------------------------------------------------------------------------
    // 21. U180/MAXR applicability
    // -------------------------------------------------------------------------
    @Test
    fun test21_u180MaxrApplicability() {
        // U180 is applicable to GIL DELIVERY ONLY
        assertTrue(ModelApplicabilityValidator.isApplicable(ManufacturingModel.U180, Department.GIL_DELIVERY))
        assertFalse(ModelApplicabilityValidator.isApplicable(ManufacturingModel.U180, Department.CASTING))
        assertFalse(ModelApplicabilityValidator.isApplicable(ManufacturingModel.U180, Department.BUFFING))

        // MAXR is applicable to GIL DELIVERY ONLY
        assertTrue(ModelApplicabilityValidator.isApplicable(ManufacturingModel.MAXR, Department.GIL_DELIVERY))
        assertFalse(ModelApplicabilityValidator.isApplicable(ManufacturingModel.MAXR, Department.MACHINING))

        // Filter applicability check
        val validFilter = AnalyticsFilterContext(
            fromDate = startDate,
            toDate = endDate,
            model = ModelFilter.Specific(ManufacturingModel.U180),
            department = DepartmentFilter.Specific(Department.GIL_DELIVERY)
        )
        assertTrue(validFilter.isApplicable())

        val invalidFilter = AnalyticsFilterContext(
            fromDate = startDate,
            toDate = endDate,
            model = ModelFilter.Specific(ManufacturingModel.U180),
            department = DepartmentFilter.Specific(Department.CASTING)
        )
        assertFalse(invalidFilter.isApplicable())
        try {
            invalidFilter.validateApplicability()
            fail("Should throw IllegalArgumentException for U180 in CASTING")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message?.contains("GIL DELIVERY") == true)
        }
    }

    // -------------------------------------------------------------------------
    // 22. Dashboard state contract
    // -------------------------------------------------------------------------
    @Test
    fun test22_dashboardStateContract() {
        val filter = AnalyticsFilterContext(fromDate = startDate, toDate = endDate)

        // Loading State
        val loadingState = DashboardState.loading(filter)
        assertTrue(loadingState.isLoading)
        assertFalse(loadingState.hasData)
        assertNull(loadingState.errorMessage)

        // Error State
        val errorState = DashboardState.error("Test error message", filter)
        assertFalse(errorState.isLoading)
        assertFalse(errorState.hasData)
        assertEquals("Test error message", errorState.errorMessage)

        // No Data State
        val noDataState = DashboardState.noData(filter)
        assertFalse(noDataState.isLoading)
        assertTrue(noDataState.isNoData)
        assertFalse(noDataState.hasData)

        // Check KPI contract distinguishes 0 and NoData
        val kpis = DashboardKpis(
            totalProduction = MetricData.Value(0),
            totalPlanned = MetricData.NoData,
            achievementPercentage = MetricData.NoData
        )
        assertEquals(MetricData.Value(0), kpis.totalProduction)
        assertEquals(MetricData.NoData, kpis.totalPlanned)
    }

    // -------------------------------------------------------------------------
    // 23. Repository/analytics contract consistency
    // -------------------------------------------------------------------------
    @Test
    fun test23_repositoryAnalyticsContractConsistency() = runBlocking {
        val d1 = BusinessDate.parse("2026-09-01")
        val d2 = BusinessDate.parse("2026-09-02")
        val filter = AnalyticsFilterContext(fromDate = d1, toDate = d2)

        // Seed repositories with known deterministic data
        productionRepository.insert(ProductionRecord("P1", d1, ManufacturingModel.U86, Department.CASTING, ProductionQuantity(100)))
        planningRepository.insert(PlanRecord("PL1", d1, ManufacturingModel.U86, Department.CASTING, 120))
        rejectionRepository.insert(RejectionRecord("R1", d1, ManufacturingModel.U86, Department.CASTING, "Blowhole", RejectionSource.KAMAL, BusinessArea.KAMAL, RejectionSide.LH, 5))
        stockRepository.insert(StockRecord("S1", d1, ManufacturingModel.U86, openingQuantity = 500, closingQuantity = 490))

        // Build dashboard state via ViewModel
        val state = dashboardViewModel.buildDashboardState(filter)

        assertFalse(state.isLoading)
        assertNull(state.errorMessage)
        assertFalse(state.isNoData)
        assertTrue(state.hasData)

        // Verify KPIs computed consistently from repository contents
        assertEquals(MetricData.Value(100), state.kpis.totalProduction)
        assertEquals(MetricData.Value(120), state.kpis.totalPlanned)
        assertEquals(83.33, (state.kpis.achievementPercentage as MetricData.Value).value, 0.01)
        assertEquals(MetricData.Value(5), state.kpis.totalRejections)
        assertEquals(MetricData.Value(490), state.kpis.totalClosingStock)

        // Verify Chart Datasets are non-null and populated
        assertNotNull(state.dailyProductionTrend)
        assertEquals(2, state.dailyProductionTrend?.points?.size)
        assertEquals(MetricData.Value(100), state.dailyProductionTrend?.points?.get(0)?.metric)

        assertNotNull(state.rejectionPareto)
        assertEquals(5, state.rejectionPareto?.totalRejectionQuantity)
        assertEquals(1, state.rejectionPareto?.items?.size)
        assertEquals("Blowhole", state.rejectionPareto?.items?.get(0)?.defectName)
        assertEquals(100.0, state.rejectionPareto?.items?.get(0)?.cumulativePercentage ?: 0.0, 0.01)

        assertNotNull(state.stockTrend)
        assertEquals(MetricData.Value(490), state.stockTrend?.points?.get(0)?.metric)
    }
}
