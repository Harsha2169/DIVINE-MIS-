package com.example

import com.example.action.ActionPriority
import com.example.action.ActionRecord
import com.example.action.ActionStatus
import com.example.alert.AlertConfiguration
import com.example.alert.AlertSeverity
import com.example.alert.AlertStatus
import com.example.alert.AlertType
import com.example.auth.AccessDeniedSecurityException
import com.example.auth.AuthContext
import com.example.auth.UserProfile
import com.example.auth.UserRole
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
import com.example.repository.firestore.FirestoreConstants
import com.example.shared.CommonFilterState
import com.example.shared.SharedAppDataAccess
import com.example.ui.navigation.MisNavigationPolicy
import com.example.ui.navigation.MisScreen
import com.example.ui.state.AndroidMisPresenter
import com.example.web.WebMisSurface
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test

/**
 * Step 10 Web & Android Application Integration Test Suite.
 *
 * Verifies:
 * - authentication flow contract
 * - role-based navigation/access
 * - shared repository contract
 * - Web/Android shared data contract
 * - dashboard filter propagation
 * - production/rejection/stock access
 * - planning access
 * - action tracker access
 * - alerts access
 * - report access
 * - supervisor department restriction
 * - CEO read-only behavior
 * - no duplicate platform collections
 * - NO DATA handling
 */
class Step10ApplicationIntegrationTest {

    private lateinit var sharedData: SharedAppDataAccess
    private lateinit var androidPresenter: AndroidMisPresenter
    private lateinit var webSurface: WebMisSurface

    private val testDate = BusinessDate.parse("2026-09-15")
    private val fromDate = BusinessDate.parse("2026-09-01")
    private val toDate = BusinessDate.parse("2026-09-15")

    private val adminUser = UserProfile(
        uid = "ADM-1",
        email = "admin@divinestamp.com",
        displayName = "System Administrator",
        role = UserRole.ADMIN
    )

    private val ceoUser = UserProfile(
        uid = "CEO-1",
        email = "ceo@divinestamp.com",
        displayName = "Chief Executive Officer",
        role = UserRole.CEO
    )

    private val managerUser = UserProfile(
        uid = "MGR-1",
        email = "manager@divinestamp.com",
        displayName = "Operations Manager",
        role = UserRole.MANAGER
    )

    private val supervisorCasting = UserProfile(
        uid = "SUP-1",
        email = "sup.casting@divinestamp.com",
        displayName = "Casting Supervisor",
        role = UserRole.SUPERVISOR,
        assignedDepartments = setOf(Department.CASTING)
    )

    private val deactivatedUser = UserProfile(
        uid = "INACT-1",
        email = "inactive@divinestamp.com",
        displayName = "Former Employee",
        role = UserRole.MANAGER,
        isActive = false
    )

    @Before
    fun setUp() = runBlocking {
        sharedData = SharedAppDataAccess()
        androidPresenter = AndroidMisPresenter(sharedData)
        webSurface = WebMisSurface(sharedData)

        sharedData.userRepository.save(adminUser)
        sharedData.userRepository.save(ceoUser)
        sharedData.userRepository.save(managerUser)
        sharedData.userRepository.save(supervisorCasting)
        sharedData.userRepository.save(deactivatedUser)
    }

    // -------------------------------------------------------------------------
    // 1. Authentication Flow Contract
    // -------------------------------------------------------------------------
    @Test
    fun test01_authenticationFlowContract() = runBlocking {
        // Successful authentication
        val authResult = webSurface.authenticate("admin@divinestamp.com", UserRole.ADMIN)
        assertTrue(authResult is AuthContext.Authenticated)
        assertEquals("ADM-1", (authResult as AuthContext.Authenticated).uid)
        assertEquals(UserRole.ADMIN, authResult.role)

        // Deactivated user denied
        try {
            webSurface.authenticate("inactive@divinestamp.com", UserRole.MANAGER)
            fail("Expected AccessDeniedSecurityException for deactivated user")
        } catch (e: AccessDeniedSecurityException) {
            assertTrue(e.message?.contains("deactivated") == true)
        }

        // Non-existent user denied
        try {
            webSurface.authenticate("unknown@divinestamp.com", UserRole.MANAGER)
            fail("Expected AccessDeniedSecurityException for non-existent user")
        } catch (e: AccessDeniedSecurityException) {
            assertTrue(e.message?.contains("not found") == true)
        }
    }

    // -------------------------------------------------------------------------
    // 2. Role-Based Navigation & Access
    // -------------------------------------------------------------------------
    @Test
    fun test02_roleBasedNavigationAccess() {
        val unauth = AuthContext.Unauthenticated
        assertEquals(listOf(MisScreen.LOGIN), MisNavigationPolicy.getAvailableScreens(unauth))

        val ceoContext = AuthContext.Authenticated(ceoUser)
        val adminContext = AuthContext.Authenticated(adminUser)
        val supervisorContext = AuthContext.Authenticated(supervisorCasting)

        val operationalScreens = listOf(
            MisScreen.DASHBOARD,
            MisScreen.PRODUCTION,
            MisScreen.REJECTION,
            MisScreen.STOCK,
            MisScreen.PLANNING,
            MisScreen.ACTION_TRACKER,
            MisScreen.ALERTS,
            MisScreen.REPORTS
        )

        // All authenticated users can navigate to operational screens
        assertEquals(operationalScreens, MisNavigationPolicy.getAvailableScreens(ceoContext))
        assertEquals(operationalScreens, MisNavigationPolicy.getAvailableScreens(adminContext))
        assertEquals(operationalScreens, MisNavigationPolicy.getAvailableScreens(supervisorContext))

        // Web surface permitted screens match Android navigation policy
        assertEquals(operationalScreens, webSurface.getPermittedScreens(ceoContext))

        // Permission to perform write operations
        assertFalse(MisNavigationPolicy.canPerformWrite(ceoContext))
        assertTrue(MisNavigationPolicy.canPerformWrite(adminContext))
        assertTrue(MisNavigationPolicy.canPerformWrite(supervisorContext))

        // Governance permission: ADMIN only
        assertTrue(MisNavigationPolicy.canManageGovernance(adminContext))
        assertFalse(MisNavigationPolicy.canManageGovernance(ceoContext))
        assertFalse(MisNavigationPolicy.canManageGovernance(supervisorContext))
    }

    // -------------------------------------------------------------------------
    // 3. Shared Repository Contract
    // -------------------------------------------------------------------------
    @Test
    fun test03_sharedRepositoryContract() = runBlocking {
        val filter = CommonFilterState.forDate(testDate)
        val managerContext = AuthContext.Authenticated(managerUser)
        val adminContext = AuthContext.Authenticated(adminUser)

        // Web records production using Manager operational role
        val record = ProductionRecord(
            id = "PR-SHARED-1",
            date = testDate,
            model = ManufacturingModel.U86,
            department = Department.CASTING,
            quantity = ProductionQuantity(250)
        )
        webSurface.recordProduction(managerContext, record)

        // Android presenter immediately reflects the exact same record without stale data
        val androidState = androidPresenter.loadProductionScreen(adminContext, filter)
        assertEquals(1, androidState.records.size)
        assertEquals("PR-SHARED-1", androidState.records.first().id)
        assertEquals(250, androidState.records.first().quantity.value)

        // Web queries also reflect it
        val webResponse = webSurface.getProductionData(adminContext, filter)
        assertEquals(1, webResponse.data?.size)
        assertEquals("PR-SHARED-1", webResponse.data?.first()?.id)
    }

    // -------------------------------------------------------------------------
    // 4. Web / Android Shared Data Contract
    // -------------------------------------------------------------------------
    @Test
    fun test04_webAndroidSharedDataContract() = runBlocking {
        val action = ActionRecord(
            id = "ACT-SHARED-10",
            title = "Repair melting furnace",
            description = "Crucible thermocouple fault",
            department = Department.CASTING,
            status = ActionStatus.OPEN,
            priority = ActionPriority.CRITICAL,
            businessDate = testDate,
            createdBy = "ADM-1"
        )

        // Create on Web
        webSurface.createAction(AuthContext.Authenticated(adminUser), action)

        // Read on Android
        val filter = CommonFilterState.forRange(fromDate, toDate)
        val androidActions = androidPresenter.loadActionTrackerScreen(AuthContext.Authenticated(adminUser), filter)
        val webActions = webSurface.getActionTrackerData(AuthContext.Authenticated(adminUser), filter)

        assertEquals(1, androidActions.actions.size)
        assertEquals(1, webActions.data?.size)
        assertEquals(androidActions.actions.first(), webActions.data?.first())
    }

    // -------------------------------------------------------------------------
    // 5. Dashboard Filter Propagation (9 Common Filters)
    // -------------------------------------------------------------------------
    @Test
    fun test05_dashboardFilterPropagation() {
        val filter = CommonFilterState(
            fromDate = fromDate,
            toDate = toDate,
            selectedDate = testDate,
            model = ManufacturingModel.U86,
            department = Department.CASTING,
            rejectionSource = RejectionSource.KAMAL,
            businessArea = BusinessArea.KAMAL,
            defect = "BLOWHOLE",
            side = RejectionSide.LH
        )

        val context = filter.toAnalyticsFilterContext()
        assertEquals(fromDate, context.fromDate)
        assertEquals(toDate, context.toDate)
        assertEquals(testDate, context.selectedDate)
        assertTrue(context.model.matches(ManufacturingModel.U86))
        assertFalse(context.model.matches(ManufacturingModel.U244))
        assertTrue(context.department.matches(Department.CASTING))
        assertFalse(context.department.matches(Department.MACHINING))
        assertTrue(context.rejectionSource.matches(RejectionSource.KAMAL))
        assertTrue(context.businessArea.matches(BusinessArea.KAMAL))
        assertTrue(context.defect.matches("blowhole"))
        assertTrue(context.side.matches(RejectionSide.LH))
    }

    // -------------------------------------------------------------------------
    // 6. Production, Rejection, Stock Access
    // -------------------------------------------------------------------------
    @Test
    fun test06_productionRejectionStockAccess() = runBlocking {
        val adminContext = AuthContext.Authenticated(adminUser)

        // Insert Casting and Machining records
        sharedData.productionRepository.insert(ProductionRecord("P1", testDate, ManufacturingModel.U86, Department.CASTING, ProductionQuantity(100)))
        sharedData.productionRepository.insert(ProductionRecord("P2", testDate, ManufacturingModel.U86, Department.MACHINING, ProductionQuantity(80)))

        sharedData.rejectionRepository.insert(
            RejectionRecord("R1", testDate, ManufacturingModel.U86, Department.CASTING, "BLOWHOLE", RejectionSource.KAMAL, BusinessArea.KAMAL, RejectionSide.LH, 5)
        )
        sharedData.rejectionRepository.insert(
            RejectionRecord("R2", testDate, ManufacturingModel.U86, Department.MACHINING, "CRACK", RejectionSource.MRN, BusinessArea.DSPL, RejectionSide.RH, 8)
        )

        sharedData.stockRepository.insert(StockRecord("S1", testDate, ManufacturingModel.U86, 100, 195))

        // Filter by CASTING only
        val castingFilter = CommonFilterState.forDate(testDate).copy(department = Department.CASTING)

        val prodState = androidPresenter.loadProductionScreen(adminContext, castingFilter)
        assertEquals(1, prodState.records.size)
        assertEquals("P1", prodState.records.first().id)

        val rejState = androidPresenter.loadRejectionScreen(adminContext, castingFilter)
        assertEquals(1, rejState.records.size)
        assertEquals("R1", rejState.records.first().id)
        assertEquals(5, rejState.totalRejections)

        val stockState = androidPresenter.loadStockScreen(adminContext, castingFilter)
        assertEquals(1, stockState.records.size)
        assertEquals("S1", stockState.records.first().id)
    }

    // -------------------------------------------------------------------------
    // 7. Planning Access
    // -------------------------------------------------------------------------
    @Test
    fun test07_planningAccess() = runBlocking {
        val adminContext = AuthContext.Authenticated(adminUser)
        sharedData.planningRepository.insert(PlanRecord("PL1", testDate, ManufacturingModel.U86, Department.CASTING, 500))

        val filter = CommonFilterState.forDate(testDate)
        val androidPlan = androidPresenter.loadPlanningScreen(adminContext, filter)
        val webPlan = webSurface.getPlanningData(adminContext, filter)

        assertEquals(1, androidPlan.dailyPlans.size)
        assertEquals(500, androidPlan.dailyPlans.first().plannedQuantity)
        assertEquals(1, webPlan.data?.dailyPlans?.size)
        assertEquals(500, webPlan.data?.dailyPlans?.first()?.plannedQuantity)
    }

    // -------------------------------------------------------------------------
    // 8. Action Tracker Access
    // -------------------------------------------------------------------------
    @Test
    fun test08_actionTrackerAccess() = runBlocking {
        val adminContext = AuthContext.Authenticated(adminUser)
        sharedData.actionTrackerRepository.insert(
            ActionRecord(
                id = "ACT-1",
                title = "Inspect leak",
                description = "Pipe fitting issue",
                department = Department.CASTING,
                businessDate = testDate,
                createdBy = "ADM-1"
            )
        )

        val filter = CommonFilterState.forRange(fromDate, toDate)
        val actions = androidPresenter.loadActionTrackerScreen(adminContext, filter)

        assertEquals(1, actions.actions.size)
        assertEquals("ACT-1", actions.actions.first().id)
    }

    // -------------------------------------------------------------------------
    // 9. Alerts Access & Workflow
    // -------------------------------------------------------------------------
    @Test
    fun test09_alertsAccess() = runBlocking {
        val managerContext = AuthContext.Authenticated(managerUser)
        // Plan = 100, Produced = 30 -> Below 85% plan -> Triggers PRODUCTION_BELOW_PLAN
        sharedData.planningRepository.insert(PlanRecord("PL1", testDate, ManufacturingModel.U86, Department.CASTING, 100))
        sharedData.productionRepository.insert(ProductionRecord("PR1", testDate, ManufacturingModel.U86, Department.CASTING, ProductionQuantity(30)))

        val filter = CommonFilterState.forDate(testDate)
        val alertState = androidPresenter.loadAlertsScreen(managerContext, filter, AlertConfiguration(minAchievementPercentageThreshold = 85.0))

        assertEquals(1, alertState.alerts.size)
        val alert = alertState.alerts.first()
        assertEquals(AlertType.PRODUCTION_BELOW_PLAN, alert.type)
        assertEquals(AlertStatus.OPEN, alert.status)

        // Web acknowledges alert
        val ackAlert = webSurface.acknowledgeAlert(managerContext, alert.id, "2026-09-15T10:00:00Z")
        assertEquals(AlertStatus.ACKNOWLEDGED, ackAlert.status)
        assertEquals("MGR-1", ackAlert.acknowledgedBy)
    }

    // -------------------------------------------------------------------------
    // 10. Report Access (Meeting Pack)
    // -------------------------------------------------------------------------
    @Test
    fun test10_reportAccess() = runBlocking {
        val ceoContext = AuthContext.Authenticated(ceoUser)
        sharedData.productionRepository.insert(ProductionRecord("PR1", testDate, ManufacturingModel.U86, Department.CASTING, ProductionQuantity(100)))
        sharedData.rejectionRepository.insert(RejectionRecord("RJ1", testDate, ManufacturingModel.U86, Department.CASTING, "PINHOLE", RejectionSource.KAMAL, BusinessArea.KAMAL, RejectionSide.LH, 5))

        val filter = CommonFilterState.forDate(testDate)
        val androidReport = androidPresenter.loadReportsScreen(ceoContext, filter)
        val webReport = webSurface.getMeetingPack(ceoContext, filter)

        assertNotNull(androidReport.meetingPack)
        assertNotNull(webReport.data)
        assertEquals(MetricData.Value(100), androidReport.meetingPack?.executiveSummary?.productionSummary)
        assertEquals(MetricData.Value(100), webReport.data?.executiveSummary?.productionSummary)
        assertEquals(MetricData.Value(5), androidReport.meetingPack?.executiveSummary?.rejectionSummary)
        assertEquals(MetricData.Value(5), webReport.data?.executiveSummary?.rejectionSummary)
    }

    // -------------------------------------------------------------------------
    // 11. Supervisor Department Restriction
    // -------------------------------------------------------------------------
    @Test
    fun test11_supervisorDepartmentRestriction() = runBlocking {
        val supContext = AuthContext.Authenticated(supervisorCasting)

        // Supervisor can read casting
        val castingFilter = CommonFilterState.forDate(testDate).copy(department = Department.CASTING)
        val castingProd = androidPresenter.loadProductionScreen(supContext, castingFilter)
        assertFalse(castingProd.isLoading)

        // Supervisor cannot read machining
        val machiningFilter = CommonFilterState.forDate(testDate).copy(department = Department.MACHINING)
        try {
            androidPresenter.loadProductionScreen(supContext, machiningFilter)
            fail("Expected AccessDeniedSecurityException for supervisor accessing unassigned department")
        } catch (e: AccessDeniedSecurityException) {
            assertTrue(e.reason.contains("unassigned department") || e.reason.contains("cannot read"))
        }

        // Automatic filter scoping
        val unscopedFilter = CommonFilterState.forDate(testDate)
        val scopedFilter = unscopedFilter.withSupervisorScope(supervisorCasting)
        assertEquals(Department.CASTING, scopedFilter.department)

        // Web: Supervisor recording production in unassigned department is rejected
        val machiningRecord = ProductionRecord("P-M1", testDate, ManufacturingModel.U86, Department.MACHINING, ProductionQuantity(50))
        try {
            webSurface.recordProduction(supContext, machiningRecord)
            fail("Expected AccessDeniedSecurityException when supervisor attempts entry outside department")
        } catch (e: AccessDeniedSecurityException) {
            assertTrue(e.reason.contains("not assigned"))
        }
    }

    // -------------------------------------------------------------------------
    // 12. CEO Read-Only Behavior
    // -------------------------------------------------------------------------
    @Test
    fun test12_ceoReadOnlyBehavior() = runBlocking {
        val ceoContext = AuthContext.Authenticated(ceoUser)

        // CEO can read all screens
        val filter = CommonFilterState.forDate(testDate)
        val prod = androidPresenter.loadProductionScreen(ceoContext, filter)
        assertFalse(prod.canAddRecord)

        val actions = androidPresenter.loadActionTrackerScreen(ceoContext, filter)
        assertFalse(actions.canCreateAction)
        assertFalse(actions.canModifyAction)

        val alerts = androidPresenter.loadAlertsScreen(ceoContext, filter)
        assertFalse(alerts.canModifyAlert)

        // CEO write attempts on Web surface throw AccessDeniedSecurityException
        try {
            webSurface.recordProduction(
                ceoContext,
                ProductionRecord("P-CEO", testDate, ManufacturingModel.U86, Department.CASTING, ProductionQuantity(10))
            )
            fail("CEO write operation must fail with AccessDeniedSecurityException")
        } catch (e: AccessDeniedSecurityException) {
            assertTrue(e.reason.contains("CEO") || e.reason.contains("read-only"))
        }

        try {
            webSurface.createAction(
                ceoContext,
                ActionRecord("A-CEO", "Test Title", "Desc", Department.CASTING, businessDate = testDate, createdBy = "CEO-1")
            )
            fail("CEO action creation must fail with AccessDeniedSecurityException")
        } catch (e: AccessDeniedSecurityException) {
            assertTrue(e.reason.contains("CEO"))
        }
    }

    // -------------------------------------------------------------------------
    // 13. No Duplicate Platform Collections
    // -------------------------------------------------------------------------
    @Test
    fun test13_noDuplicatePlatformCollections() {
        val collections = sharedData.getAuthoritativeCollections()

        assertEquals("production_records", collections["production"])
        assertEquals("rejection_records", collections["rejections"])
        assertEquals("stock_records", collections["stocks"])
        assertEquals("plan_records", collections["plans"])
        assertEquals("users", collections["users"])
        assertEquals("action_records", collections["actions"])
        assertEquals("alert_records", collections["alerts"])

        // Ensure no platform prefixes
        for ((_, collectionName) in collections) {
            assertFalse(collectionName.startsWith("web_"))
            assertFalse(collectionName.startsWith("android_"))
            assertFalse(collectionName.startsWith("mobile_"))
        }
    }

    // -------------------------------------------------------------------------
    // 14. NO DATA Handling
    // -------------------------------------------------------------------------
    @Test
    fun test14_noDataHandling() = runBlocking {
        val adminContext = AuthContext.Authenticated(adminUser)
        val emptyDate = BusinessDate.parse("2026-01-01")
        val filter = CommonFilterState.forDate(emptyDate)

        // Verify Android screens return explicit isNoData = true without inventing fake numbers
        val prodState = androidPresenter.loadProductionScreen(adminContext, filter)
        assertTrue(prodState.isNoData)
        assertEquals(0, prodState.records.size)

        val rejState = androidPresenter.loadRejectionScreen(adminContext, filter)
        assertTrue(rejState.isNoData)
        assertEquals(0, rejState.records.size)
        assertEquals(0, rejState.totalRejections)

        val stockState = androidPresenter.loadStockScreen(adminContext, filter)
        assertTrue(stockState.isNoData)
        assertEquals(0, stockState.records.size)

        val planState = androidPresenter.loadPlanningScreen(adminContext, filter)
        assertTrue(planState.isNoData)

        // Web responses return isNoData = true
        val webProd = webSurface.getProductionData(adminContext, filter)
        assertTrue(webProd.isNoData)
        assertEquals(0, webProd.data?.size)

        val webStock = webSurface.getStockData(adminContext, filter)
        assertTrue(webStock.isNoData)
        assertEquals(0, webStock.data?.size)
    }
}
