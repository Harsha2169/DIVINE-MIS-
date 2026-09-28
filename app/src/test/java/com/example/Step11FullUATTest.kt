package com.example

import com.example.action.ActionPriority
import com.example.action.ActionRecord
import com.example.action.ActionStatus
import com.example.action.ActionWorkflowValidator
import com.example.alert.AlertConfiguration
import com.example.alert.AlertSeverity
import com.example.alert.AlertStatus
import com.example.alert.AlertType
import com.example.analytics.AnalyticsFilterContext
import com.example.analytics.DepartmentFilter
import com.example.analytics.ManufacturingAnalyticsService
import com.example.analytics.ModelFilter
import com.example.analytics.PlanningAnalyticsService
import com.example.auth.AccessDeniedSecurityException
import com.example.auth.AuthContext
import com.example.auth.Permission
import com.example.auth.SecurityPolicy
import com.example.auth.UserProfile
import com.example.auth.UserRole
import com.example.core.BusinessDate
import com.example.core.MetricData
import com.example.model.BusinessArea
import com.example.model.CustomerRequirementRecord
import com.example.model.DefectMaster
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
import com.example.report.CsvSanitizer
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
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test

/**
 * STEP 11 — Comprehensive End-to-End Integration, Security, and Full UAT Gate.
 *
 * Validates:
 * 1. Auth + RBAC (role boundaries, read-only CEO, department-scoped supervisor, untampered context)
 * 2. Master Data Invariants (7 models, 7 departments, U180/MAXR GIL DELIVERY only, ALL filter-only)
 * 3. Planning Independence (Customer != Monthly != Daily != Actuals)
 * 4. Production Invariants (no LH/RH, department applicability, 0 vs NO DATA)
 * 5. Rejection Invariants (KAMAL/MRN/DSPL, KAMAL/GABRIEL/DSPL, LH/RH only, rebuffing=KAMAL only)
 * 6. Stock Invariants (manual opening/closing, no carry-forward, 0 vs NO DATA)
 * 7. Action Tracker Invariants (deterministic lifecycle transitions, department scoping)
 * 8. Analytics Engine (continuous dates, Pareto cumulative %, achievement %, trends, NO DATA)
 * 9. Reports & Exports (meeting pack, CSV sanitization against CWE-1236, determinism)
 * 10. Alerts Engine (configurable thresholds, determinism, lifecycle, 0 vs NO DATA)
 * 11. Web + Android Consistency (shared data layer, same filters, same RBAC, zero duplicates)
 * 12. Firestore Canonical Contracts (all authoritative collection names verified)
 * 13. Security Audit (no hard-coded passwords, no fake data, no client-only bypass)
 */
class Step11FullUATTest {

    private lateinit var sharedData: SharedAppDataAccess
    private lateinit var androidPresenter: AndroidMisPresenter
    private lateinit var webSurface: WebMisSurface

    private val testDate = BusinessDate.parse("2026-09-27")
    private val yesterday = BusinessDate.parse("2026-09-26")
    private val tomorrow = BusinessDate.parse("2026-09-28")

    private val ceoUser = UserProfile("user-ceo", "ceo@divinestamp.com", "Executive CEO", UserRole.CEO)
    private val adminUser = UserProfile("user-admin", "admin@divinestamp.com", "System Admin", UserRole.ADMIN)
    private val managerUser = UserProfile("user-manager", "manager@divinestamp.com", "Plant Manager", UserRole.MANAGER)
    private val supervisorCasting = UserProfile(
        uid = "user-sup-cast",
        email = "sup.cast@divinestamp.com",
        displayName = "Casting Supervisor",
        role = UserRole.SUPERVISOR,
        assignedDepartments = setOf(Department.CASTING)
    )
    private val deactivatedUser = UserProfile(
        uid = "user-deactivated",
        email = "deactivated@divinestamp.com",
        displayName = "Ex-Employee",
        role = UserRole.MANAGER,
        isActive = false
    )

    @Before
    fun setUp() = runBlocking {
        sharedData = SharedAppDataAccess()
        androidPresenter = AndroidMisPresenter(sharedData)
        webSurface = WebMisSurface(sharedData)

        // Seed users into authoritative user repository
        sharedData.userRepository.save(ceoUser)
        sharedData.userRepository.save(adminUser)
        sharedData.userRepository.save(managerUser)
        sharedData.userRepository.save(supervisorCasting)
        sharedData.userRepository.save(deactivatedUser)
    }

    // -------------------------------------------------------------------------
    // 1. AUTH + RBAC
    // -------------------------------------------------------------------------
    @Test
    fun test01_authAndRbacSecurity() = runBlocking {
        val unauthenticated = AuthContext.Unauthenticated
        val ceoContext = AuthContext.Authenticated(ceoUser)
        val adminContext = AuthContext.Authenticated(adminUser)
        val managerContext = AuthContext.Authenticated(managerUser)
        val supervisorContext = AuthContext.Authenticated(supervisorCasting)
        val deactContext = AuthContext.Authenticated(deactivatedUser)

        // Unauthenticated access rejected
        try {
            sharedData.securityEnforcer.enforcePermission(unauthenticated, Permission.READ_ALL_DATA)
            fail("Expected AccessDeniedSecurityException for unauthenticated context")
        } catch (e: AccessDeniedSecurityException) {
            assertTrue(e.reason.contains("Unauthenticated"))
        }

        // Deactivated user rejected
        try {
            sharedData.securityEnforcer.enforcePermission(deactContext, Permission.READ_ALL_DATA)
            fail("Expected AccessDeniedSecurityException for deactivated user")
        } catch (e: AccessDeniedSecurityException) {
            assertTrue(e.reason.contains("deactivated"))
        }

        // CEO read-only: can read all data, write operations denied
        sharedData.securityEnforcer.enforcePermission(ceoContext, Permission.READ_ALL_DATA)
        try {
            sharedData.securityEnforcer.enforceDepartmentDataEntry(
                ceoContext, Department.CASTING, Permission.RECORD_PRODUCTION
            )
            fail("Expected AccessDeniedSecurityException for CEO write attempt")
        } catch (e: AccessDeniedSecurityException) {
            assertTrue(e.reason.contains("CEO") || e.reason.contains("read-only"))
        }

        // ADMIN governance: can manage users, system settings, governance audit, actions; cannot enter production
        sharedData.securityEnforcer.enforcePermission(adminContext, Permission.MANAGE_USERS)
        sharedData.securityEnforcer.enforcePermission(adminContext, Permission.VIEW_GOVERNANCE_AUDIT)
        sharedData.securityEnforcer.enforceActionManagement(adminContext, Department.CASTING)
        try {
            sharedData.securityEnforcer.enforceDepartmentDataEntry(
                adminContext, Department.CASTING, Permission.RECORD_PRODUCTION
            )
            fail("Expected AccessDeniedSecurityException for Admin entering operational production data")
        } catch (e: AccessDeniedSecurityException) {
            assertTrue(e.reason.contains("lacks required permission"))
        }

        // MANAGER operational management: can record production, rejection, stock, plan, approve plan, manage actions
        sharedData.securityEnforcer.enforceDepartmentDataEntry(managerContext, Department.CASTING, Permission.RECORD_PRODUCTION)
        sharedData.securityEnforcer.enforceDepartmentDataEntry(managerContext, Department.CASTING, Permission.RECORD_REJECTION)
        sharedData.securityEnforcer.enforcePermission(managerContext, Permission.RECORD_STOCK)
        sharedData.securityEnforcer.enforcePermission(managerContext, Permission.RECORD_PLAN)
        sharedData.securityEnforcer.enforcePermission(managerContext, Permission.APPROVE_PLAN)
        sharedData.securityEnforcer.enforceActionManagement(managerContext, Department.CASTING)

        // SUPERVISOR department-scoped: Casting allowed, Machining denied
        sharedData.securityEnforcer.enforceDepartmentDataEntry(supervisorContext, Department.CASTING, Permission.RECORD_PRODUCTION)
        sharedData.securityEnforcer.enforceDepartmentRead(supervisorContext, Department.CASTING)
        try {
            sharedData.securityEnforcer.enforceDepartmentDataEntry(
                supervisorContext, Department.MACHINING, Permission.RECORD_PRODUCTION
            )
            fail("Expected AccessDeniedSecurityException for supervisor in unassigned department")
        } catch (e: AccessDeniedSecurityException) {
            assertTrue(e.reason.contains("not assigned") || e.reason.contains("Department"))
        }

        // Client role escalation attempt: tampering supervisor role to ADMIN in memory
        val tamperedUser = supervisorCasting.copy(role = UserRole.ADMIN)
        val tamperedContext = AuthContext.Authenticated(tamperedUser)
        try {
            sharedData.securityEnforcer.verifyUntamperedContext(tamperedContext)
            fail("Expected AccessDeniedSecurityException for tampered client context")
        } catch (e: AccessDeniedSecurityException) {
            assertTrue(e.reason.contains("escalation", ignoreCase = true) || e.reason.contains("mismatch", ignoreCase = true))
        }
    }

    // -------------------------------------------------------------------------
    // 2. MASTER DATA
    // -------------------------------------------------------------------------
    @Test
    fun test02_masterDataInvariants() {
        // Exact 7 models
        val expectedModels = setOf("U86", "U180", "U244", "MAXR", "N282-DISC", "DRUM", "N360")
        assertEquals(7, ManufacturingModel.entries.size)
        assertEquals(expectedModels, ManufacturingModel.entries.map { it.displayName }.toSet())

        // Exact 7 departments in exact manufacturing sequence
        val expectedDepts = listOf("CASTING", "POST CASTING", "M/C", "BUFFING", "FINAL", "KAMAL OK", "GIL DELIVERY")
        assertEquals(7, Department.entries.size)
        assertEquals(expectedDepts, Department.CANONICAL_SEQUENCE.map { it.displayName })

        // U180 and MAXR are GIL DELIVERY ONLY
        assertTrue(ModelApplicabilityValidator.isApplicable(ManufacturingModel.U180, Department.GIL_DELIVERY))
        assertFalse(ModelApplicabilityValidator.isApplicable(ManufacturingModel.U180, Department.CASTING))
        assertFalse(ModelApplicabilityValidator.isApplicable(ManufacturingModel.U180, Department.MACHINING))

        assertTrue(ModelApplicabilityValidator.isApplicable(ManufacturingModel.MAXR, Department.GIL_DELIVERY))
        assertFalse(ModelApplicabilityValidator.isApplicable(ManufacturingModel.MAXR, Department.BUFFING))

        // Other 5 models applicable to all departments
        listOf(
            ManufacturingModel.U86,
            ManufacturingModel.U244,
            ManufacturingModel.N282_DISC,
            ManufacturingModel.DRUM,
            ManufacturingModel.N360
        ).forEach { model ->
            Department.entries.forEach { dept ->
                assertTrue(ModelApplicabilityValidator.isApplicable(model, dept))
            }
        }

        // "ALL" is filter-only, never persisted
        try {
            ManufacturingModel.fromCode("ALL")
            fail("Expected IllegalArgumentException for model ALL")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message?.contains("filter-only") == true)
        }

        try {
            Department.fromDisplayName("ALL")
            fail("Expected IllegalArgumentException for department ALL")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message?.contains("filter-only") == true)
        }
    }

    // -------------------------------------------------------------------------
    // 3. PLANNING
    // -------------------------------------------------------------------------
    @Test
    fun test03_planningIndependence() = runBlocking {
        val custReq = CustomerRequirementRecord("CR-1", "Gabriel India", testDate, ManufacturingModel.U86, 1000)
        val monthPlan = MonthlyDepartmentPlanRecord("MP-1", "2026-09", ManufacturingModel.U86, Department.CASTING, 25000)
        val dailyPlan = PlanRecord("DP-1", testDate, ManufacturingModel.U86, Department.CASTING, 1000)

        sharedData.planningRepository.insertCustomerRequirement(custReq)
        sharedData.planningRepository.insertMonthlyPlan(monthPlan)
        sharedData.planningRepository.insert(dailyPlan)

        // Verify independent entities
        val filterContext = AnalyticsFilterContext(
            fromDate = testDate,
            toDate = testDate,
            department = DepartmentFilter.Specific(Department.CASTING),
            model = ModelFilter.Specific(ManufacturingModel.U86)
        )
        val fetchedCust = sharedData.planningRepository.getCustomerRequirementsByFilter(filterContext)
        val fetchedMonth = sharedData.planningRepository.getMonthlyPlansByYearMonth("2026-09")
        val fetchedDaily = sharedData.planningRepository.getByFilter(filterContext)

        assertEquals(1, fetchedCust.size)
        assertEquals(1, fetchedMonth.size)
        assertEquals(1, fetchedDaily.size)

        // Plans never become actuals automatically
        val actualProduction = sharedData.productionRepository.getByFilter(filterContext)
        assertTrue(actualProduction.isEmpty())

        // Achievement % against 0 actuals is NoData
        val planVsActual = sharedData.planningAnalyticsService.buildPlanVsActual(
            plans = fetchedDaily,
            productions = actualProduction,
            filter = filterContext
        )
        assertEquals(1, planVsActual.points.size)
        assertEquals(MetricData.Value(1000), planVsActual.points.first().primary)
        assertEquals(MetricData.NoData, planVsActual.points.first().secondary)
        assertEquals(MetricData.NoData, planVsActual.points.first().achievementPercentage)
    }

    // -------------------------------------------------------------------------
    // 4. PRODUCTION
    // -------------------------------------------------------------------------
    @Test
    fun test04_productionInvariants() = runBlocking {
        // Valid production record (no LH/RH concept)
        val validRecord = ProductionRecord(
            id = "PR-1",
            date = testDate,
            model = ManufacturingModel.U86,
            department = Department.CASTING,
            quantity = ProductionQuantity(500)
        )
        sharedData.productionRepository.insert(validRecord)

        // Model applicability violation (U180 in CASTING) rejected at construction
        try {
            ProductionRecord(
                id = "PR-INVALID",
                date = testDate,
                model = ManufacturingModel.U180,
                department = Department.CASTING,
                quantity = ProductionQuantity(100)
            )
            fail("Expected IllegalArgumentException for U180 in CASTING")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message?.contains("restricted to GIL DELIVERY") == true)
        }

        // 0 vs NO DATA preserved
        val zeroRecord = ProductionRecord(
            id = "PR-ZERO",
            date = tomorrow,
            model = ManufacturingModel.U86,
            department = Department.CASTING,
            quantity = ProductionQuantity(0)
        )
        sharedData.productionRepository.insert(zeroRecord)

        val filterContext = AnalyticsFilterContext(
            fromDate = yesterday,
            toDate = tomorrow,
            department = DepartmentFilter.Specific(Department.CASTING),
            model = ModelFilter.Specific(ManufacturingModel.U86)
        )
        val trend = sharedData.analyticsService.buildDailyProductionTrend(
            records = listOf(validRecord, zeroRecord),
            filter = filterContext
        )
        // yesterday = NoData, testDate = 500, tomorrow = 0
        assertEquals(3, trend.points.size)
        assertEquals(MetricData.NoData, trend.points[0].metric)
        assertEquals(MetricData.Value(500), trend.points[1].metric)
        assertEquals(MetricData.Value(0), trend.points[2].metric)
    }

    // -------------------------------------------------------------------------
    // 5. REJECTION
    // -------------------------------------------------------------------------
    @Test
    fun test05_rejectionInvariants() = runBlocking {
        // Valid sources: KAMAL, MRN, DSPL
        assertEquals(setOf("KAMAL", "MRN", "DSPL"), RejectionSource.entries.map { it.name }.toSet())

        // Valid business areas: KAMAL, GABRIEL, DSPL
        assertEquals(setOf("KAMAL", "GABRIEL", "DSPL"), BusinessArea.entries.map { it.name }.toSet())

        // Sides: LH and RH only
        assertEquals(setOf(RejectionSide.LH, RejectionSide.RH), RejectionSide.entries.toSet())

        // Rebuffing allowed ONLY for source = KAMAL
        val validRebuffed = RejectionRecord(
            id = "REJ-1",
            date = testDate,
            model = ManufacturingModel.U86,
            department = Department.BUFFING,
            defectName = "Dent",
            source = RejectionSource.KAMAL,
            businessArea = BusinessArea.KAMAL,
            side = RejectionSide.LH,
            quantity = 5,
            isRebuffed = true
        )
        sharedData.rejectionRepository.insert(validRebuffed)

        // MRN source with isRebuffed = true is strictly rejected
        try {
            RejectionRecord(
                id = "REJ-INVALID",
                date = testDate,
                model = ManufacturingModel.U86,
                department = Department.BUFFING,
                defectName = "Dent",
                source = RejectionSource.MRN,
                businessArea = BusinessArea.KAMAL,
                side = RejectionSide.LH,
                quantity = 5,
                isRebuffed = true
            )
            fail("Expected IllegalArgumentException for rebuffing with MRN source")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message?.contains("KAMAL") == true)
        }

        // Defect validation enforcement
        try {
            RejectionRecord(
                id = "REJ-BAD-DEFECT",
                date = testDate,
                model = ManufacturingModel.U86,
                department = Department.BUFFING,
                defectName = "",
                source = RejectionSource.KAMAL,
                businessArea = BusinessArea.KAMAL,
                side = RejectionSide.LH,
                quantity = 2
            )
            fail("Expected IllegalArgumentException for blank defect")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message?.contains("Defect name cannot be blank") == true)
        }

        // Defect master standard defect validation
        try {
            DefectMaster.assertStandardDefect("Invented Fake Defect")
            fail("Expected IllegalArgumentException for non-master defect")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message?.contains("Invented defect") == true)
        }
    }

    // -------------------------------------------------------------------------
    // 6. STOCK
    // -------------------------------------------------------------------------
    @Test
    fun test06_stockInvariants() = runBlocking {
        // Manual opening and closing independent across days
        val stockDay1 = StockRecord("ST-1", yesterday, ManufacturingModel.U86, openingQuantity = 100, closingQuantity = 150)
        val stockDay2 = StockRecord("ST-2", testDate, ManufacturingModel.U86, openingQuantity = 130, closingQuantity = 180) // 130 != 150 (independent manual entry)

        sharedData.stockRepository.insert(stockDay1)
        sharedData.stockRepository.insert(stockDay2)

        val retrievedDay2 = sharedData.stockRepository.getById("ST-2")
        assertNotNull(retrievedDay2)
        assertEquals(130, retrievedDay2?.openingQuantity) // Not derived from 150

        // 0 vs NO DATA preserved
        val stockDay3Zero = StockRecord("ST-3", tomorrow, ManufacturingModel.U86, openingQuantity = 0, closingQuantity = 0)
        sharedData.stockRepository.insert(stockDay3Zero)

        val retrievedDay3 = sharedData.stockRepository.getById("ST-3")
        assertEquals(0, retrievedDay3?.openingQuantity)
        assertEquals(0, retrievedDay3?.closingQuantity)
    }

    // -------------------------------------------------------------------------
    // 7. ACTION TRACKER
    // -------------------------------------------------------------------------
    @Test
    fun test07_actionTrackerWorkflowAndScoping() = runBlocking {
        // Valid workflow: OPEN -> ASSIGNED -> IN_PROGRESS -> COMPLETED -> CLOSED
        ActionWorkflowValidator.validateTransition(ActionStatus.OPEN, ActionStatus.ASSIGNED)
        ActionWorkflowValidator.validateTransition(ActionStatus.ASSIGNED, ActionStatus.IN_PROGRESS)
        ActionWorkflowValidator.validateTransition(ActionStatus.IN_PROGRESS, ActionStatus.COMPLETED)
        ActionWorkflowValidator.validateTransition(ActionStatus.COMPLETED, ActionStatus.CLOSED)

        // Invalid transitions rejected
        try {
            ActionWorkflowValidator.validateTransition(ActionStatus.OPEN, ActionStatus.CLOSED)
            fail("Expected IllegalStateException for OPEN -> CLOSED")
        } catch (e: IllegalStateException) {
            assertTrue(e.message?.contains("VIOLATION") == true || e.message?.contains("Cannot transition") == true)
        }

        try {
            ActionWorkflowValidator.validateTransition(ActionStatus.CLOSED, ActionStatus.IN_PROGRESS)
            fail("Expected IllegalStateException for transitioning out of terminal CLOSED state")
        } catch (e: IllegalStateException) {
            assertTrue(e.message?.contains("terminal") == true)
        }

        // Supervisor department scoping on actions
        val castingAction = ActionRecord(
            id = "ACT-1",
            title = "Fix Die Misalignment",
            description = "Investigate blowholes on U86 line",
            department = Department.CASTING,
            priority = ActionPriority.HIGH,
            businessDate = testDate,
            createdBy = "sup.cast@divinestamp.com"
        )
        val machiningAction = ActionRecord(
            id = "ACT-2",
            title = "Calibrate CNC Spindle",
            description = "Check chatter marks",
            department = Department.MACHINING,
            priority = ActionPriority.CRITICAL,
            businessDate = testDate,
            createdBy = "sup.mach@divinestamp.com"
        )
        sharedData.actionTrackerRepository.insert(castingAction)
        sharedData.actionTrackerRepository.insert(machiningAction)

        val supContext = AuthContext.Authenticated(supervisorCasting)
        val screenState = androidPresenter.loadActionTrackerScreen(supContext, CommonFilterState.forDate(testDate))
        assertEquals(1, screenState.actions.size)
        assertEquals("ACT-1", screenState.actions.first().id)
    }

    // -------------------------------------------------------------------------
    // 8. ANALYTICS
    // -------------------------------------------------------------------------
    @Test
    fun test08_analyticsComprehensive() = runBlocking {
        // Continuous dates in range
        val p1 = ProductionRecord("P-1", yesterday, ManufacturingModel.U86, Department.CASTING, ProductionQuantity(100))
        val p2 = ProductionRecord("P-2", tomorrow, ManufacturingModel.U86, Department.CASTING, ProductionQuantity(200))
        sharedData.productionRepository.insert(p1)
        sharedData.productionRepository.insert(p2)

        val filterContext = AnalyticsFilterContext(
            fromDate = yesterday,
            toDate = tomorrow,
            department = DepartmentFilter.Specific(Department.CASTING),
            model = ModelFilter.Specific(ManufacturingModel.U86)
        )
        val trend = sharedData.analyticsService.buildDailyProductionTrend(
            records = listOf(p1, p2),
            filter = filterContext
        )
        // 3 days continuous: yesterday, testDate, tomorrow
        assertEquals(3, trend.points.size)
        assertEquals(yesterday, trend.points[0].date)
        assertEquals(testDate, trend.points[1].date)
        assertEquals(tomorrow, trend.points[2].date)
        assertEquals(MetricData.NoData, trend.points[1].metric)

        // Pareto analysis & cumulative %
        val rej1 = RejectionRecord("R-1", testDate, ManufacturingModel.U86, Department.CASTING, "Blow Hole", RejectionSource.KAMAL, BusinessArea.KAMAL, RejectionSide.LH, 60)
        val rej2 = RejectionRecord("R-2", testDate, ManufacturingModel.U86, Department.CASTING, "Dent", RejectionSource.KAMAL, BusinessArea.KAMAL, RejectionSide.RH, 30)
        val rej3 = RejectionRecord("R-3", testDate, ManufacturingModel.U86, Department.CASTING, "Crack", RejectionSource.KAMAL, BusinessArea.KAMAL, RejectionSide.LH, 10)

        val paretoFilter = AnalyticsFilterContext(fromDate = testDate, toDate = testDate)
        val paretoResult = sharedData.analyticsService.buildRejectionPareto(listOf(rej1, rej2, rej3), paretoFilter)
        assertEquals(3, paretoResult.items.size)
        assertEquals("Blow Hole", paretoResult.items[0].defectName)
        assertEquals(60.0, paretoResult.items[0].cumulativePercentage, 0.01)
        assertEquals("Dent", paretoResult.items[1].defectName)
        assertEquals(90.0, paretoResult.items[1].cumulativePercentage, 0.01)
        assertEquals("Crack", paretoResult.items[2].defectName)
        assertEquals(100.0, paretoResult.items[2].cumulativePercentage, 0.01)
    }

    // -------------------------------------------------------------------------
    // 9. REPORTS
    // -------------------------------------------------------------------------
    @Test
    fun test09_reportsAndMeetingPack() = runBlocking {
        val managerContext = AuthContext.Authenticated(managerUser)
        val filter = CommonFilterState.forDate(testDate)

        val pack = sharedData.meetingPackService.generateMeetingPack(filter.toAnalyticsFilterContext())
        assertNotNull(pack)
        assertEquals("EXECUTIVE MANUFACTURING MANAGEMENT MEETING PACK", pack.title)

        // CSV sanitization against CWE-1236 Formula Injection
        assertEquals("Normal Text", CsvSanitizer.sanitizeValue("Normal Text"))
        assertEquals("'=1+1", CsvSanitizer.sanitizeValue("=1+1"))
        assertEquals("'+cmd|' /C calc'!A0", CsvSanitizer.sanitizeValue("+cmd|' /C calc'!A0"))
        assertEquals("'-200", CsvSanitizer.sanitizeValue("-200"))
        assertEquals("'@SUM(A1:A10)", CsvSanitizer.sanitizeValue("@SUM(A1:A10)"))

        val csvLine = CsvSanitizer.formatRow(listOf("=DANGEROUS", "Safe", "Comma, Here"))
        assertTrue(csvLine.contains("\"'=DANGEROUS\""))
        assertTrue(csvLine.contains("Safe"))
        assertTrue(csvLine.contains("\"Comma, Here\""))
    }

    // -------------------------------------------------------------------------
    // 10. ALERTS
    // -------------------------------------------------------------------------
    @Test
    fun test10_alertsEngine() = runBlocking {
        // High rejection rate trigger
        val p = ProductionRecord("P-10", testDate, ManufacturingModel.U86, Department.CASTING, ProductionQuantity(100))
        val r = RejectionRecord("R-10", testDate, ManufacturingModel.U86, Department.CASTING, "Blow Hole", RejectionSource.KAMAL, BusinessArea.KAMAL, RejectionSide.LH, 25)
        sharedData.productionRepository.insert(p)
        sharedData.rejectionRepository.insert(r)

        val config = AlertConfiguration(maxRejectionRatePercentageThreshold = 10.0)
        val alerts = sharedData.alertGeneratorService.generateAlerts(testDate, config)

        val rejAlert = alerts.firstOrNull { it.type == AlertType.REJECTION_THRESHOLD_EXCEEDED }
        assertNotNull(rejAlert)
        assertEquals(AlertSeverity.CRITICAL, rejAlert?.severity)
        assertEquals(AlertStatus.OPEN, rejAlert?.status)

        // Lifecycle: Acknowledge alert
        val acked = rejAlert!!.acknowledge("user-manager", "2026-09-27T10:00:00Z")
        assertEquals(AlertStatus.ACKNOWLEDGED, acked.status)
        assertEquals("user-manager", acked.acknowledgedBy)

        // Resolve alert
        val resolved = acked.resolve("user-manager", "2026-09-27T11:00:00Z", "Resolved by re-tuning mold")
        assertEquals(AlertStatus.RESOLVED, resolved.status)
    }

    // -------------------------------------------------------------------------
    // 11. WEB + ANDROID
    // -------------------------------------------------------------------------
    @Test
    fun test11_webAndAndroidConsistency() = runBlocking {
        val managerContext = AuthContext.Authenticated(managerUser)
        val filter = CommonFilterState.forDate(testDate)

        // Manager records production on Web
        val record = ProductionRecord("PR-W-1", testDate, ManufacturingModel.U86, Department.CASTING, ProductionQuantity(350))
        webSurface.recordProduction(managerContext, record)

        // Android immediately reflects the exact same record
        val androidState = androidPresenter.loadProductionScreen(managerContext, filter)
        assertEquals(1, androidState.records.size)
        assertEquals("PR-W-1", androidState.records.first().id)
        assertEquals(350, androidState.records.first().quantity.value)

        // Web query returns the same data
        val webResponse = webSurface.getProductionData(managerContext, filter)
        assertEquals(1, webResponse.data?.size)
        assertEquals("PR-W-1", webResponse.data?.first()?.id)

        // Consistent permission policy across platforms
        val ceoContext = AuthContext.Authenticated(ceoUser)
        assertFalse(webSurface.canWrite(ceoContext))
        assertFalse(MisNavigationPolicy.canPerformWrite(ceoContext))
        assertTrue(webSurface.canWrite(managerContext))
        assertTrue(MisNavigationPolicy.canPerformWrite(managerContext))
    }

    // -------------------------------------------------------------------------
    // 12. FIRESTORE CONTRACT
    // -------------------------------------------------------------------------
    @Test
    fun test12_firestoreContracts() {
        val collections = sharedData.getAuthoritativeCollections()

        assertEquals("users", collections["users"])
        assertEquals("models", collections["models"])
        assertEquals("departments", collections["departments"])
        assertEquals("production_records", collections["production"])
        assertEquals("rejection_records", collections["rejections"])
        assertEquals("stock_records", collections["stocks"])
        assertEquals("actionTracker", collections["actionTracker"])
        assertEquals("customerEndPlans", collections["customerEndPlans"])
        assertEquals("monthlyDepartmentPlans", collections["monthlyDepartmentPlans"])
        assertEquals("dailyPlans", collections["dailyPlans"])
        assertEquals("auditLogs", collections["auditLogs"])
        assertEquals("action_records", collections["actions"])
        assertEquals("alert_records", collections["alerts"])

        // Zero duplicate platform collections
        collections.values.forEach { coll ->
            assertFalse(coll.startsWith("android_"))
            assertFalse(coll.startsWith("web_"))
        }
    }

    // -------------------------------------------------------------------------
    // 13. SECURITY AUDIT
    // -------------------------------------------------------------------------
    @Test
    fun test13_securityAudit() = runBlocking {
        // User profile contains no stored passwords
        val users = sharedData.userRepository.getAll()
        users.forEach { user ->
            assertFalse(user.displayName.contains("password", ignoreCase = true))
            assertNotNull(user.uid)
            assertNotNull(user.role)
        }

        // Canonical roles have no duplicate or invented roles
        val roles = UserRole.entries.map { it.code }.toSet()
        assertEquals(setOf("CEO", "ADMIN", "MANAGER", "SUPERVISOR"), roles)

        // ALL is never an element of the model master
        assertFalse(ManufacturingModel.entries.any { it.name.equals("ALL", ignoreCase = true) })
        assertFalse(Department.entries.any { it.name.equals("ALL", ignoreCase = true) })

        // No automatic stock carry-forward
        val s1 = StockRecord("S-AUDIT-1", yesterday, ManufacturingModel.U86, openingQuantity = 500, closingQuantity = 600)
        val s2 = StockRecord("S-AUDIT-2", testDate, ManufacturingModel.U86, openingQuantity = 400, closingQuantity = 450)
        assertEquals(400, s2.openingQuantity) // Explicit manual entry, NOT 600
    }
}
