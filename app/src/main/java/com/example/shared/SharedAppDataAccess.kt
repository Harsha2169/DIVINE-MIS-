package com.example.shared

import com.example.action.ActionPriority
import com.example.action.ActionRecord
import com.example.action.ActionStatus
import com.example.alert.AlertConfiguration
import com.example.alert.AlertGeneratorService
import com.example.alert.AlertRecord
import com.example.alert.AlertRepository
import com.example.alert.AlertSecurityEnforcer
import com.example.alert.AlertStatus
import com.example.alert.InMemoryAlertRepository
import com.example.analytics.ManufacturingAnalyticsService
import com.example.analytics.PlanningAnalyticsService
import com.example.auth.AuthContext
import com.example.auth.FirebaseAuthBoundary
import com.example.auth.InMemoryFirebaseAuthBoundary
import com.example.auth.InMemoryUserRepository
import com.example.auth.SecurityEnforcer
import com.example.auth.UserRepository
import com.example.report.MeetingPackService
import com.example.repository.ActionTrackerRepository
import com.example.repository.PlanningRepository
import com.example.repository.ProductionRepository
import com.example.repository.RejectionRepository
import com.example.repository.StockRepository
import com.example.repository.firestore.FirestoreConstants
import com.example.repository.memory.InMemoryActionTrackerRepository
import com.example.repository.memory.InMemoryPlanningRepository
import com.example.repository.memory.InMemoryProductionRepository
import com.example.repository.memory.InMemoryRejectionRepository
import com.example.repository.memory.InMemoryStockRepository

/**
 * Authoritative Shared Data Access Layer for Divine Stamp Manufacturing MIS.
 *
 * Enforces:
 * - Single authoritative repository and contract layer shared by Web and Android.
 * - Firestore collections adhere strictly to [FirestoreConstants].
 * - No duplicate platform collections (e.g. no "web_users" or "android_production").
 * - Single source of truth for analytics, alerts, reports, and security enforcement.
 */
class SharedAppDataAccess(
    val productionRepository: ProductionRepository = InMemoryProductionRepository(),
    val planningRepository: PlanningRepository = InMemoryPlanningRepository(),
    val rejectionRepository: RejectionRepository = InMemoryRejectionRepository(),
    val stockRepository: StockRepository = InMemoryStockRepository(),
    val actionTrackerRepository: ActionTrackerRepository = InMemoryActionTrackerRepository(),
    val alertRepository: AlertRepository = InMemoryAlertRepository(),
    val userRepository: UserRepository = InMemoryUserRepository(),
    val authBoundary: FirebaseAuthBoundary = InMemoryFirebaseAuthBoundary(userRepository)
) {
    val securityEnforcer: SecurityEnforcer = SecurityEnforcer(userRepository)
    val alertSecurityEnforcer: AlertSecurityEnforcer = AlertSecurityEnforcer(userRepository, securityEnforcer)

    val analyticsService: ManufacturingAnalyticsService = ManufacturingAnalyticsService()
    val planningAnalyticsService: PlanningAnalyticsService = PlanningAnalyticsService()

    val meetingPackService: MeetingPackService = MeetingPackService(
        productionRepository = productionRepository,
        rejectionRepository = rejectionRepository,
        stockRepository = stockRepository,
        planningRepository = planningRepository,
        actionTrackerRepository = actionTrackerRepository,
        analyticsService = analyticsService,
        planningAnalyticsService = planningAnalyticsService
    )

    val alertGeneratorService: AlertGeneratorService = AlertGeneratorService(
        productionRepository = productionRepository,
        planningRepository = planningRepository,
        rejectionRepository = rejectionRepository,
        stockRepository = stockRepository,
        actionTrackerRepository = actionTrackerRepository
    )

    /**
     * Verifies that the authoritative Firestore collections match the platform canonical schema.
     */
    fun getAuthoritativeCollections(): Map<String, String> {
        return mapOf(
            "production" to FirestoreConstants.COLLECTION_PRODUCTION,
            "rejections" to FirestoreConstants.COLLECTION_REJECTION,
            "stocks" to FirestoreConstants.COLLECTION_STOCK,
            "plans" to FirestoreConstants.COLLECTION_PLANS,
            "users" to FirestoreConstants.COLLECTION_USERS,
            "actions" to FirestoreConstants.COLLECTION_ACTIONS,
            "alerts" to FirestoreConstants.COLLECTION_ALERTS,
            "models" to FirestoreConstants.COLLECTION_MODELS,
            "departments" to FirestoreConstants.COLLECTION_DEPARTMENTS,
            "actionTracker" to FirestoreConstants.COLLECTION_ACTION_TRACKER,
            "customerEndPlans" to FirestoreConstants.COLLECTION_CUSTOMER_END_PLANS,
            "monthlyDepartmentPlans" to FirestoreConstants.COLLECTION_MONTHLY_DEPARTMENT_PLANS,
            "dailyPlans" to FirestoreConstants.COLLECTION_DAILY_PLANS,
            "auditLogs" to FirestoreConstants.COLLECTION_AUDIT_LOGS
        )
    }
}
