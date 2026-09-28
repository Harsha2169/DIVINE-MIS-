package com.example

import com.example.action.ActionPriority
import com.example.action.ActionRecord
import com.example.action.ActionStatus
import com.example.alert.AlertConfiguration
import com.example.alert.AlertGeneratorService
import com.example.alert.AlertRecord
import com.example.alert.AlertSecurityEnforcer
import com.example.alert.AlertSeverity
import com.example.alert.AlertStatus
import com.example.alert.AlertType
import com.example.alert.AlertWorkflowValidator
import com.example.alert.InMemoryAlertRepository
import com.example.alert.InvalidAlertTransitionException
import com.example.auth.AccessDeniedSecurityException
import com.example.auth.AuthContext
import com.example.auth.InMemoryUserRepository
import com.example.auth.UserProfile
import com.example.auth.UserRole
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
import com.example.notification.InMemoryNotificationBoundary
import com.example.notification.NotificationChannel
import com.example.notification.NotificationPriority
import com.example.notification.NotificationRecipient
import com.example.notification.toNotificationPayload
import com.example.repository.memory.InMemoryActionTrackerRepository
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
 * Step 9 Authoritative Alerts & Notifications Test Suite.
 *
 * Verifies:
 * - exact alert types
 * - alert creation
 * - severity
 * - lifecycle transitions
 * - invalid transitions
 * - configurable thresholds
 * - production-below-plan alert
 * - rejection-threshold alert
 * - overdue-action alert
 * - missing-data alert behavior
 * - 0 vs NO DATA
 * - model/department applicability
 * - supervisor scope
 * - RBAC
 * - notification contract
 * - deterministic alert generation
 * - repository/contract consistency
 */
class Step9AlertsNotificationTest {

    private lateinit var productionRepository: InMemoryProductionRepository
    private lateinit var planningRepository: InMemoryPlanningRepository
    private lateinit var rejectionRepository: InMemoryRejectionRepository
    private lateinit var stockRepository: InMemoryStockRepository
    private lateinit var actionTrackerRepository: InMemoryActionTrackerRepository
    private lateinit var alertRepository: InMemoryAlertRepository
    private lateinit var userRepository: InMemoryUserRepository

    private lateinit var alertGeneratorService: AlertGeneratorService
    private lateinit var alertSecurityEnforcer: AlertSecurityEnforcer
    private lateinit var notificationBoundary: InMemoryNotificationBoundary

    private val testDate = BusinessDate.parse("2026-09-15")

    @Before
    fun setUp() {
        productionRepository = InMemoryProductionRepository()
        planningRepository = InMemoryPlanningRepository()
        rejectionRepository = InMemoryRejectionRepository()
        stockRepository = InMemoryStockRepository()
        actionTrackerRepository = InMemoryActionTrackerRepository()
        alertRepository = InMemoryAlertRepository()
        userRepository = InMemoryUserRepository()

        alertGeneratorService = AlertGeneratorService(
            productionRepository = productionRepository,
            planningRepository = planningRepository,
            rejectionRepository = rejectionRepository,
            stockRepository = stockRepository,
            actionTrackerRepository = actionTrackerRepository
        )

        alertSecurityEnforcer = AlertSecurityEnforcer(userRepository)
        notificationBoundary = InMemoryNotificationBoundary()
    }

    // -------------------------------------------------------------------------
    // 1. Exact Alert Types
    // -------------------------------------------------------------------------
    @Test
    fun test01_exactAlertTypes() {
        val expectedCodes = listOf(
            "PRODUCTION_BELOW_PLAN",
            "REJECTION_THRESHOLD_EXCEEDED",
            "CRITICAL_HIGH_REJECTION_ISSUE",
            "STOCK_CONDITION_ATTENTION",
            "OVERDUE_ACTION",
            "CRITICAL_ACTION",
            "MISSING_REQUIRED_OPERATIONAL_DATA"
        )

        assertEquals(7, AlertType.entries.size)
        for (code in expectedCodes) {
            val type = AlertType.fromCode(code)
            assertEquals(code, type.code)
            assertNotNull(type.displayName)
            assertEquals(type, AlertType.fromCodeOrNull(code))
        }

        assertNull(AlertType.fromCodeOrNull(null))
        assertNull(AlertType.fromCodeOrNull(""))
    }

    // -------------------------------------------------------------------------
    // 2. Alert Creation & Invariants
    // -------------------------------------------------------------------------
    @Test
    fun test02_alertCreation() {
        val alert = AlertRecord(
            id = "ALT-1",
            type = AlertType.PRODUCTION_BELOW_PLAN,
            severity = AlertSeverity.WARNING,
            businessDate = testDate,
            department = Department.CASTING,
            model = ManufacturingModel.U86,
            reference = "ACH-75%",
            message = "Production below plan",
            createdAt = "2026-09-15T08:00:00Z"
        )

        assertEquals("ALT-1", alert.id)
        assertEquals(AlertType.PRODUCTION_BELOW_PLAN, alert.type)
        assertEquals(AlertSeverity.WARNING, alert.severity)
        assertEquals(testDate, alert.businessDate)
        assertEquals(Department.CASTING, alert.department)
        assertEquals(ManufacturingModel.U86, alert.model)
        assertEquals(AlertStatus.OPEN, alert.status)
        assertTrue(alert.status.isActive)
        assertFalse(alert.status.isTerminal)

        // Blank ID validation
        try {
            alert.copy(id = "")
            fail("Expected exception for blank alert ID")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message?.contains("ID") == true)
        }

        // Blank message validation
        try {
            alert.copy(message = "")
            fail("Expected exception for blank alert message")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message?.contains("message") == true)
        }
    }

    // -------------------------------------------------------------------------
    // 3. Severity
    // -------------------------------------------------------------------------
    @Test
    fun test03_severity() {
        assertEquals(4, AlertSeverity.entries.size)
        assertTrue(AlertSeverity.CRITICAL.isUrgent)
        assertTrue(AlertSeverity.HIGH.isUrgent)
        assertFalse(AlertSeverity.WARNING.isUrgent)
        assertFalse(AlertSeverity.INFO.isUrgent)
        assertTrue(AlertSeverity.CRITICAL.level > AlertSeverity.HIGH.level)
        assertTrue(AlertSeverity.HIGH.level > AlertSeverity.WARNING.level)
        assertTrue(AlertSeverity.WARNING.level > AlertSeverity.INFO.level)
    }

    // -------------------------------------------------------------------------
    // 4. Lifecycle Transitions
    // -------------------------------------------------------------------------
    @Test
    fun test04_lifecycleTransitions() {
        val alert = AlertRecord(
            id = "ALT-1",
            type = AlertType.CRITICAL_ACTION,
            severity = AlertSeverity.CRITICAL,
            businessDate = testDate,
            message = "Critical action required",
            createdAt = "2026-09-15T08:00:00Z"
        )

        // Path A: OPEN -> ACKNOWLEDGED -> RESOLVED
        val ackAlert = alert.acknowledge("USR-1", "2026-09-15T09:00:00Z")
        assertEquals(AlertStatus.ACKNOWLEDGED, ackAlert.status)
        assertEquals("USR-1", ackAlert.acknowledgedBy)
        assertEquals("2026-09-15T09:00:00Z", ackAlert.acknowledgedAt)

        val resolvedAlert = ackAlert.resolve("USR-1", "2026-09-15T10:00:00Z", "Fixed issue")
        assertEquals(AlertStatus.RESOLVED, resolvedAlert.status)
        assertEquals("USR-1", resolvedAlert.resolvedBy)
        assertEquals("Fixed issue", resolvedAlert.resolutionNote)
        assertTrue(resolvedAlert.status.isTerminal)

        // Path B: OPEN -> DISMISSED
        val dismissedAlert = alert.dismiss("USR-2", "2026-09-15T09:30:00Z", "False alarm")
        assertEquals(AlertStatus.DISMISSED, dismissedAlert.status)
        assertEquals("USR-2", dismissedAlert.dismissedBy)
        assertEquals("False alarm", dismissedAlert.dismissalReason)
        assertTrue(dismissedAlert.status.isTerminal)

        // Path C: OPEN -> RESOLVED
        val directResolved = alert.resolve("USR-1", "2026-09-15T09:00:00Z", "Direct resolution")
        assertEquals(AlertStatus.RESOLVED, directResolved.status)
    }

    // -------------------------------------------------------------------------
    // 5. Invalid Transitions
    // -------------------------------------------------------------------------
    @Test
    fun test05_invalidTransitions() {
        val alert = AlertRecord(
            id = "ALT-1",
            type = AlertType.CRITICAL_ACTION,
            severity = AlertSeverity.CRITICAL,
            businessDate = testDate,
            message = "Critical action required",
            createdAt = "2026-09-15T08:00:00Z"
        )

        val resolvedAlert = alert.resolve("USR-1", "2026-09-15T10:00:00Z")

        // Terminal state cannot transition to anything
        try {
            resolvedAlert.acknowledge("USR-1", "2026-09-15T11:00:00Z")
            fail("Expected InvalidAlertTransitionException from RESOLVED")
        } catch (e: InvalidAlertTransitionException) {
            assertEquals(AlertStatus.RESOLVED, e.from)
            assertEquals(AlertStatus.ACKNOWLEDGED, e.to)
        }

        val dismissedAlert = alert.dismiss("USR-1", "2026-09-15T10:00:00Z")
        try {
            dismissedAlert.resolve("USR-1", "2026-09-15T11:00:00Z")
            fail("Expected InvalidAlertTransitionException from DISMISSED")
        } catch (e: InvalidAlertTransitionException) {
            assertEquals(AlertStatus.DISMISSED, e.from)
            assertEquals(AlertStatus.RESOLVED, e.to)
        }

        // ACKNOWLEDGED cannot transition back to OPEN
        val ackAlert = alert.acknowledge("USR-1", "2026-09-15T09:00:00Z")
        assertFalse(AlertWorkflowValidator.isValidTransition(AlertStatus.ACKNOWLEDGED, AlertStatus.OPEN))

        // Same-state transitions are invalid
        assertFalse(AlertWorkflowValidator.isValidTransition(AlertStatus.OPEN, AlertStatus.OPEN))
    }

    // -------------------------------------------------------------------------
    // 6. Configurable Thresholds
    // -------------------------------------------------------------------------
    @Test
    fun test06_configurableThresholds() = runBlocking {
        // Plan: 100, Actual: 85 (Achievement: 85%)
        planningRepository.insert(PlanRecord("PL1", testDate, ManufacturingModel.U86, Department.CASTING, 100))
        productionRepository.insert(ProductionRecord("PR1", testDate, ManufacturingModel.U86, Department.CASTING, ProductionQuantity(85)))

        // Config 1: min threshold = 85.0 -> 85% is NOT below 85.0 -> No alert
        val configDefault = AlertConfiguration(minAchievementPercentageThreshold = 85.0)
        val alerts1 = alertGeneratorService.generateAlerts(testDate, configDefault)
        assertEquals(0, alerts1.count { it.type == AlertType.PRODUCTION_BELOW_PLAN })

        // Config 2: min threshold = 90.0 -> 85% is below 90.0 -> Alert generated!
        val configStrict = AlertConfiguration(minAchievementPercentageThreshold = 90.0)
        val alerts2 = alertGeneratorService.generateAlerts(testDate, configStrict)
        val prodAlert = alerts2.find { it.type == AlertType.PRODUCTION_BELOW_PLAN }
        assertNotNull(prodAlert)
        assertTrue(prodAlert?.message?.contains("below configured threshold of 90.0%") == true)
    }

    // -------------------------------------------------------------------------
    // 7. Production Below Plan Alert
    // -------------------------------------------------------------------------
    @Test
    fun test07_productionBelowPlanAlert() = runBlocking {
        planningRepository.insert(PlanRecord("PL1", testDate, ManufacturingModel.U86, Department.CASTING, 500))
        productionRepository.insert(ProductionRecord("PR1", testDate, ManufacturingModel.U86, Department.CASTING, ProductionQuantity(200))) // 40% achievement

        val alerts = alertGeneratorService.generateAlerts(testDate, AlertConfiguration(minAchievementPercentageThreshold = 85.0))

        val prodAlert = alerts.find { it.type == AlertType.PRODUCTION_BELOW_PLAN }
        assertNotNull(prodAlert)
        assertEquals(Department.CASTING, prodAlert?.department)
        assertEquals(ManufacturingModel.U86, prodAlert?.model)
        assertEquals(AlertSeverity.CRITICAL, prodAlert?.severity) // 40% is < 85/2 = 42.5% -> CRITICAL
        assertTrue(prodAlert?.message?.contains("40.0%") == true)
    }

    // -------------------------------------------------------------------------
    // 8. Rejection Threshold Alert & Critical Defects
    // -------------------------------------------------------------------------
    @Test
    fun test08_rejectionThresholdAlert() = runBlocking {
        // Production: 100, Rejection: 15 (Rejection Rate: 15 / 115 = 13.04%)
        productionRepository.insert(ProductionRecord("PR1", testDate, ManufacturingModel.U86, Department.MACHINING, ProductionQuantity(100)))
        rejectionRepository.insert(
            RejectionRecord("RJ1", testDate, ManufacturingModel.U86, Department.MACHINING, "CRACK", RejectionSource.KAMAL, BusinessArea.KAMAL, RejectionSide.LH, 15)
        )

        val config = AlertConfiguration(
            maxRejectionRatePercentageThreshold = 5.0,
            criticalDefectNames = setOf("CRACK")
        )

        val alerts = alertGeneratorService.generateAlerts(testDate, config)

        // 1. Rejection threshold alert
        val rateAlert = alerts.find { it.type == AlertType.REJECTION_THRESHOLD_EXCEEDED }
        assertNotNull(rateAlert)
        assertEquals(Department.MACHINING, rateAlert?.department)
        assertTrue(rateAlert?.message?.contains("13.04%") == true)

        // 2. Critical defect alert
        val critAlert = alerts.find { it.type == AlertType.CRITICAL_HIGH_REJECTION_ISSUE }
        assertNotNull(critAlert)
        assertEquals(AlertSeverity.CRITICAL, critAlert?.severity)
        assertTrue(critAlert?.message?.contains("CRACK") == true)
    }

    // -------------------------------------------------------------------------
    // 9. Overdue Action Alert
    // -------------------------------------------------------------------------
    @Test
    fun test09_overdueActionAlert() = runBlocking {
        val overdueDate = BusinessDate.parse("2026-09-10")
        val futureDate = BusinessDate.parse("2026-09-20")

        // Overdue active action
        actionTrackerRepository.insert(
            ActionRecord(
                id = "ACT-OVERDUE",
                title = "Replace casting mold",
                description = "Mold wear exceeded",
                department = Department.CASTING,
                status = ActionStatus.OPEN,
                priority = ActionPriority.HIGH,
                businessDate = BusinessDate.parse("2026-09-01"),
                dueDate = overdueDate,
                createdBy = "USR-1"
            )
        )

        // Non-overdue action
        actionTrackerRepository.insert(
            ActionRecord(
                id = "ACT-FUTURE",
                title = "Calibrate machine",
                description = "Routine calibration",
                department = Department.MACHINING,
                status = ActionStatus.OPEN,
                priority = ActionPriority.LOW,
                businessDate = BusinessDate.parse("2026-09-01"),
                dueDate = futureDate,
                createdBy = "USR-1"
            )
        )

        // Overdue but COMPLETED action (must not trigger overdue alert)
        actionTrackerRepository.insert(
            ActionRecord(
                id = "ACT-COMPLETED",
                title = "Fix oil leak",
                description = "Hydraulic pressure loss",
                department = Department.CASTING,
                status = ActionStatus.COMPLETED,
                priority = ActionPriority.HIGH,
                businessDate = BusinessDate.parse("2026-09-01"),
                dueDate = overdueDate,
                createdBy = "USR-1",
                assigneeId = "USR-2",
                assigneeName = "Bob",
                completedAt = 1000L
            )
        )

        val alerts = alertGeneratorService.generateAlerts(testDate, AlertConfiguration(alertOnOverdueActions = true))
        val overdueAlerts = alerts.filter { it.type == AlertType.OVERDUE_ACTION }

        assertEquals(1, overdueAlerts.size)
        assertEquals("ACT-OVERDUE", overdueAlerts.first().reference)
        assertEquals(Department.CASTING, overdueAlerts.first().department)
    }

    // -------------------------------------------------------------------------
    // 10. Missing Data Alert Behavior
    // -------------------------------------------------------------------------
    @Test
    fun test10_missingDataAlertBehavior() = runBlocking {
        // Plan exists for CASTING U86, but NO production record exists
        planningRepository.insert(PlanRecord("PL1", testDate, ManufacturingModel.U86, Department.CASTING, 300))

        // Rule A: alertOnMissingProduction = false -> NO missing data alert
        val alertsNoRule = alertGeneratorService.generateAlerts(
            testDate,
            AlertConfiguration(alertOnMissingProduction = false)
        )
        assertEquals(0, alertsNoRule.count { it.type == AlertType.MISSING_REQUIRED_OPERATIONAL_DATA })

        // Rule B: alertOnMissingProduction = true -> Missing data alert generated
        val alertsWithRule = alertGeneratorService.generateAlerts(
            testDate,
            AlertConfiguration(alertOnMissingProduction = true)
        )
        val missingAlert = alertsWithRule.find { it.type == AlertType.MISSING_REQUIRED_OPERATIONAL_DATA }
        assertNotNull(missingAlert)
        assertEquals(Department.CASTING, missingAlert?.department)
        assertEquals(ManufacturingModel.U86, missingAlert?.model)
    }

    // -------------------------------------------------------------------------
    // 11. 0 vs NO DATA
    // -------------------------------------------------------------------------
    @Test
    fun test11_zeroVsNoData() = runBlocking {
        planningRepository.insert(PlanRecord("PL1", testDate, ManufacturingModel.U86, Department.CASTING, 300))

        // Scenario 1: Actual quantity 0 is explicitly recorded
        productionRepository.insert(ProductionRecord("PR0", testDate, ManufacturingModel.U86, Department.CASTING, ProductionQuantity(0)))

        val alertsZero = alertGeneratorService.generateAlerts(
            testDate,
            AlertConfiguration(
                minAchievementPercentageThreshold = 85.0,
                alertOnMissingProduction = true
            )
        )

        // 0 IS data: it must NOT trigger MISSING_REQUIRED_OPERATIONAL_DATA
        assertEquals(0, alertsZero.count { it.type == AlertType.MISSING_REQUIRED_OPERATIONAL_DATA })
        // But 0 is below plan: it MUST trigger PRODUCTION_BELOW_PLAN (achievement = 0.0%)
        val prodAlert = alertsZero.find { it.type == AlertType.PRODUCTION_BELOW_PLAN }
        assertNotNull(prodAlert)
        assertTrue(prodAlert?.message?.contains("0.0%") == true)

        // Scenario 2: Production records completely absent
        productionRepository.clear()
        val alertsNoData = alertGeneratorService.generateAlerts(
            testDate,
            AlertConfiguration(
                minAchievementPercentageThreshold = 85.0,
                alertOnMissingProduction = true
            )
        )

        // Missing data: MUST trigger MISSING_REQUIRED_OPERATIONAL_DATA
        assertEquals(1, alertsNoData.count { it.type == AlertType.MISSING_REQUIRED_OPERATIONAL_DATA })
        // And must NOT trigger PRODUCTION_BELOW_PLAN (because there is no production data to evaluate)
        assertEquals(0, alertsNoData.count { it.type == AlertType.PRODUCTION_BELOW_PLAN })
    }

    // -------------------------------------------------------------------------
    // 12. Model / Department Applicability
    // -------------------------------------------------------------------------
    @Test
    fun test12_modelDepartmentApplicability() = runBlocking {
        stockRepository.insert(StockRecord("STK1", testDate, ManufacturingModel.U244, openingQuantity = 10, closingQuantity = 5))

        val config = AlertConfiguration(minClosingStockThreshold = 20)
        val alerts = alertGeneratorService.generateAlerts(testDate, config)

        val stockAlert = alerts.find { it.type == AlertType.STOCK_CONDITION_ATTENTION }
        assertNotNull(stockAlert)
        assertEquals(ManufacturingModel.U244, stockAlert?.model)
        assertNull(stockAlert?.department) // Stock alerts are model-specific, not department-specific
        assertTrue(stockAlert?.message?.contains("U244") == true)
    }

    // -------------------------------------------------------------------------
    // 13. Supervisor Scope
    // -------------------------------------------------------------------------
    @Test
    fun test13_supervisorScope() {
        val supervisorCasting = UserProfile(
            uid = "SUP-1",
            email = "sup.casting@example.com",
            displayName = "Casting Supervisor",
            role = UserRole.SUPERVISOR,
            assignedDepartments = setOf(Department.CASTING)
        )
        val contextCasting = AuthContext.Authenticated(supervisorCasting)

        val castingAlert = AlertRecord(
            id = "ALT-CASTING",
            type = AlertType.PRODUCTION_BELOW_PLAN,
            severity = AlertSeverity.WARNING,
            businessDate = testDate,
            department = Department.CASTING,
            message = "Casting issue",
            createdAt = "2026-09-15T08:00:00Z"
        )

        val machiningAlert = AlertRecord(
            id = "ALT-MACHINING",
            type = AlertType.PRODUCTION_BELOW_PLAN,
            severity = AlertSeverity.WARNING,
            businessDate = testDate,
            department = Department.MACHINING,
            message = "Machining issue",
            createdAt = "2026-09-15T08:00:00Z"
        )

        val globalAlert = AlertRecord(
            id = "ALT-GLOBAL",
            type = AlertType.STOCK_CONDITION_ATTENTION,
            severity = AlertSeverity.WARNING,
            businessDate = testDate,
            message = "Global stock issue",
            createdAt = "2026-09-15T08:00:00Z"
        )

        // Can read and modify CASTING alert
        assertTrue(alertSecurityEnforcer.canReadAlert(contextCasting, castingAlert))
        assertTrue(alertSecurityEnforcer.canModifyAlert(contextCasting, castingAlert))
        alertSecurityEnforcer.enforceReadAlert(contextCasting, castingAlert)
        alertSecurityEnforcer.enforceModifyAlert(contextCasting, castingAlert)

        // Cannot read or modify MACHINING alert
        assertFalse(alertSecurityEnforcer.canReadAlert(contextCasting, machiningAlert))
        assertFalse(alertSecurityEnforcer.canModifyAlert(contextCasting, machiningAlert))
        try {
            alertSecurityEnforcer.enforceReadAlert(contextCasting, machiningAlert)
            fail("Expected AccessDeniedSecurityException for reading unassigned department")
        } catch (e: AccessDeniedSecurityException) {
            assertTrue(e.reason.contains("not authorized"))
        }

        // Cannot modify global alert
        assertFalse(alertSecurityEnforcer.canModifyAlert(contextCasting, globalAlert))

        // Filter readable alerts
        val readable = alertSecurityEnforcer.filterReadableAlerts(
            contextCasting,
            listOf(castingAlert, machiningAlert, globalAlert)
        )
        assertEquals(1, readable.size)
        assertEquals("ALT-CASTING", readable.first().id)
    }

    // -------------------------------------------------------------------------
    // 14. RBAC (CEO read-only, Admin, Manager, Deactivated, Client Escalation)
    // -------------------------------------------------------------------------
    @Test
    fun test14_rbac() = runBlocking {
        val ceoProfile = UserProfile(uid = "CEO-1", email = "ceo@example.com", displayName = "Chief Executive", role = UserRole.CEO)
        val adminProfile = UserProfile(uid = "ADM-1", email = "admin@example.com", displayName = "System Admin", role = UserRole.ADMIN)
        val mgrProfile = UserProfile(uid = "MGR-1", email = "mgr@example.com", displayName = "Operations Manager", role = UserRole.MANAGER)
        val inactiveProfile = UserProfile(uid = "MGR-2", email = "inactive@example.com", displayName = "Deactivated Manager", role = UserRole.MANAGER, isActive = false)

        userRepository.save(ceoProfile)
        userRepository.save(adminProfile)
        userRepository.save(mgrProfile)
        userRepository.save(inactiveProfile)

        val alert = AlertRecord(
            id = "ALT-1",
            type = AlertType.CRITICAL_ACTION,
            severity = AlertSeverity.CRITICAL,
            businessDate = testDate,
            department = Department.CASTING,
            message = "Critical alert",
            createdAt = "2026-09-15T08:00:00Z"
        )

        // CEO: Can read, but CANNOT modify
        val ceoContext = AuthContext.Authenticated(ceoProfile)
        assertTrue(alertSecurityEnforcer.canReadAlert(ceoContext, alert))
        assertFalse(alertSecurityEnforcer.canModifyAlert(ceoContext, alert))
        try {
            alertSecurityEnforcer.enforceModifyAlert(ceoContext, alert)
            fail("CEO must not be allowed to modify operational alerts")
        } catch (e: AccessDeniedSecurityException) {
            assertTrue(e.reason.contains("CEO"))
        }

        // ADMIN: Can read and modify
        val adminContext = AuthContext.Authenticated(adminProfile)
        assertTrue(alertSecurityEnforcer.canReadAlert(adminContext, alert))
        assertTrue(alertSecurityEnforcer.canModifyAlert(adminContext, alert))

        // MANAGER: Can read and modify
        val mgrContext = AuthContext.Authenticated(mgrProfile)
        assertTrue(alertSecurityEnforcer.canReadAlert(mgrContext, alert))
        assertTrue(alertSecurityEnforcer.canModifyAlert(mgrContext, alert))

        // Inactive: Denied both read and modify
        val inactiveContext = AuthContext.Authenticated(inactiveProfile)
        assertFalse(alertSecurityEnforcer.canReadAlert(inactiveContext, alert))
        assertFalse(alertSecurityEnforcer.canModifyAlert(inactiveContext, alert))

        // Client role escalation attempt: User claims ADMIN but authoritative storage says SUPERVISOR
        val tamperedSupervisor = UserProfile(uid = "SUP-99", email = "sneaky@example.com", displayName = "Sneaky User", role = UserRole.SUPERVISOR)
        userRepository.save(tamperedSupervisor)

        val escalatedClaim = AuthContext.Authenticated(
            UserProfile(uid = "SUP-99", email = "sneaky@example.com", displayName = "Sneaky User", role = UserRole.ADMIN)
        )
        try {
            alertSecurityEnforcer.verifyUntamperedContext(escalatedClaim)
            fail("Expected AccessDeniedSecurityException on client role escalation")
        } catch (e: AccessDeniedSecurityException) {
            assertTrue(e.reason.contains("Client role escalation detected"))
        }
    }

    // -------------------------------------------------------------------------
    // 15. Notification Contract
    // -------------------------------------------------------------------------
    @Test
    fun test15_notificationContract() = runBlocking {
        val alert = AlertRecord(
            id = "ALT-NOTIF-1",
            type = AlertType.CRITICAL_HIGH_REJECTION_ISSUE,
            severity = AlertSeverity.CRITICAL,
            businessDate = testDate,
            department = Department.CASTING,
            model = ManufacturingModel.U86,
            reference = "REJ-101",
            message = "Critical blowhole defect spike",
            createdAt = "2026-09-15T08:30:00Z"
        )

        val payload = alert.toNotificationPayload(channel = NotificationChannel.PUSH)

        assertEquals("NOTIF-ALT-NOTIF-1", payload.id)
        assertEquals("CRITICAL: Critical/High Rejection Issue", payload.title)
        assertEquals("Critical blowhole defect spike", payload.message)
        assertEquals(NotificationChannel.PUSH, payload.channel)
        assertEquals(NotificationPriority.URGENT, payload.priority)
        assertEquals("ALT-NOTIF-1", payload.alertId)
        assertEquals("CRITICAL_HIGH_REJECTION_ISSUE", payload.data["alertType"])
        assertEquals("CASTING", payload.data["department"])
        assertEquals("U86", payload.data["model"])

        val recipients = listOf(
            NotificationRecipient.Role(UserRole.MANAGER),
            NotificationRecipient.DepartmentSupervisors(Department.CASTING)
        )

        val result = notificationBoundary.dispatch(payload, recipients)

        assertTrue(result.success)
        assertEquals(2, result.deliveredCount)
        assertEquals(1, notificationBoundary.getDispatched().size)
        assertEquals(payload, notificationBoundary.getPayloads().first())
    }

    // -------------------------------------------------------------------------
    // 16. Deterministic Alert Generation
    // -------------------------------------------------------------------------
    @Test
    fun test16_deterministicAlertGeneration() = runBlocking {
        planningRepository.insert(PlanRecord("PL1", testDate, ManufacturingModel.U86, Department.CASTING, 400))
        productionRepository.insert(ProductionRecord("PR1", testDate, ManufacturingModel.U86, Department.CASTING, ProductionQuantity(300)))
        rejectionRepository.insert(
            RejectionRecord("RJ1", testDate, ManufacturingModel.U86, Department.CASTING, "BLOWHOLE", RejectionSource.KAMAL, BusinessArea.KAMAL, RejectionSide.LH, 30)
        )

        val config = AlertConfiguration(
            minAchievementPercentageThreshold = 85.0,
            maxRejectionRatePercentageThreshold = 5.0
        )

        val run1 = alertGeneratorService.generateAlerts(testDate, config, timestamp = "2026-09-15T12:00:00Z")
        val run2 = alertGeneratorService.generateAlerts(testDate, config, timestamp = "2026-09-15T12:00:00Z")

        assertEquals(run1.size, run2.size)
        assertEquals(run1.map { it.id }, run2.map { it.id })
        assertEquals(run1.map { it.type }, run2.map { it.type })
        assertEquals(run1.map { it.message }, run2.map { it.message })
        assertEquals(run1.map { it.severity }, run2.map { it.severity })
    }

    // -------------------------------------------------------------------------
    // 17. Repository / Contract Consistency
    // -------------------------------------------------------------------------
    @Test
    fun test17_repositoryContractConsistency() = runBlocking {
        val alert1 = AlertRecord(
            id = "ALT-1",
            type = AlertType.PRODUCTION_BELOW_PLAN,
            severity = AlertSeverity.WARNING,
            businessDate = testDate,
            department = Department.CASTING,
            message = "Production below plan",
            createdAt = "2026-09-15T08:00:00Z"
        )
        val alert2 = AlertRecord(
            id = "ALT-2",
            type = AlertType.CRITICAL_ACTION,
            severity = AlertSeverity.CRITICAL,
            businessDate = testDate,
            department = Department.MACHINING,
            message = "Critical action required",
            createdAt = "2026-09-15T08:30:00Z"
        )

        alertRepository.insertAll(listOf(alert1, alert2))

        assertEquals(2, alertRepository.getAll().size)
        assertEquals(alert1, alertRepository.getById("ALT-1"))
        assertEquals(1, alertRepository.getByDepartment(Department.CASTING).size)
        assertEquals(2, alertRepository.getByDate(testDate).size)
        assertEquals(2, alertRepository.getByStatus(AlertStatus.OPEN).size)

        // Acknowledge alert1 and update
        val ackAlert = alert1.acknowledge("MGR-1", "2026-09-15T09:00:00Z")
        alertRepository.update(ackAlert)

        val updated = alertRepository.getById("ALT-1")
        assertEquals(AlertStatus.ACKNOWLEDGED, updated?.status)
        assertEquals("MGR-1", updated?.acknowledgedBy)
        assertEquals(1, alertRepository.getByStatus(AlertStatus.ACKNOWLEDGED).size)
        assertEquals(1, alertRepository.getByStatus(AlertStatus.OPEN).size)

        // Clear
        alertRepository.clear()
        assertEquals(0, alertRepository.getAll().size)
    }
}
