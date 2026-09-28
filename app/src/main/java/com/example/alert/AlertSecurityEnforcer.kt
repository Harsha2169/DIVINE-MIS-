package com.example.alert

import com.example.auth.AccessDeniedSecurityException
import com.example.auth.AuthContext
import com.example.auth.SecurityEnforcer
import com.example.auth.UserRepository
import com.example.auth.UserRole

/**
 * Authoritative Security Enforcer for Alert & Notification Domain.
 *
 * Reuses Step 3 RBAC:
 * - CEO can receive/read alerts across all departments, but cannot modify operational data or alerts.
 * - ADMIN has full governance access.
 * - MANAGER has operational management across all departments.
 * - SUPERVISOR is strictly limited to assigned department scope.
 * - Client-side role escalation is detected and rejected.
 */
class AlertSecurityEnforcer(
    private val userRepository: UserRepository? = null,
    private val securityEnforcer: SecurityEnforcer = SecurityEnforcer(userRepository)
) {

    /**
     * Determines whether the user in [context] is authorized to read [alert].
     */
    fun canReadAlert(context: AuthContext, alert: AlertRecord): Boolean {
        if (context !is AuthContext.Authenticated) return false
        val user = context.user
        if (!user.isActive) return false

        return when (user.role) {
            UserRole.CEO, UserRole.ADMIN, UserRole.MANAGER -> true
            UserRole.SUPERVISOR -> {
                // Supervisor can only view alerts within their assigned department(s)
                alert.department != null && user.hasDepartmentAssignment(alert.department)
            }
        }
    }

    /**
     * Enforces alert read authorization.
     */
    fun enforceReadAlert(context: AuthContext, alert: AlertRecord) {
        if (!canReadAlert(context, alert)) {
            val user = (context as? AuthContext.Authenticated)?.user
            if (user == null) {
                throw AccessDeniedSecurityException("Unauthenticated access to alert is denied.")
            }
            if (!user.isActive) {
                throw AccessDeniedSecurityException("User '${user.displayName}' is deactivated.")
            }
            throw AccessDeniedSecurityException(
                "Supervisor '${user.displayName}' is not authorized to access alert for department '${alert.department?.displayName ?: "GLOBAL"}'."
            )
        }
    }

    /**
     * Filters a list of alerts to only those readable by the authenticated user.
     */
    fun filterReadableAlerts(context: AuthContext, alerts: List<AlertRecord>): List<AlertRecord> {
        return alerts.filter { canReadAlert(context, it) }
    }

    /**
     * Determines whether the user in [context] is authorized to modify (acknowledge/resolve/dismiss) [alert].
     */
    fun canModifyAlert(context: AuthContext, alert: AlertRecord): Boolean {
        if (context !is AuthContext.Authenticated) return false
        val user = context.user
        if (!user.isActive) return false

        return when (user.role) {
            UserRole.CEO -> false // CEO is read-only oversight
            UserRole.ADMIN, UserRole.MANAGER -> true
            UserRole.SUPERVISOR -> {
                // Supervisor can only modify alerts for assigned department
                alert.department != null && user.hasDepartmentAssignment(alert.department)
            }
        }
    }

    /**
     * Enforces alert modification authorization.
     */
    fun enforceModifyAlert(context: AuthContext, alert: AlertRecord) {
        if (!canModifyAlert(context, alert)) {
            val user = (context as? AuthContext.Authenticated)?.user
            if (user == null) {
                throw AccessDeniedSecurityException("Unauthenticated access to alert modification is denied.")
            }
            if (!user.isActive) {
                throw AccessDeniedSecurityException("User '${user.displayName}' is deactivated.")
            }
            if (user.role == UserRole.CEO) {
                throw AccessDeniedSecurityException("Role 'CEO' has executive read-only privileges and cannot modify alerts.")
            }
            throw AccessDeniedSecurityException(
                "Supervisor '${user.displayName}' is not authorized to modify alert for department '${alert.department?.displayName ?: "GLOBAL"}'."
            )
        }
    }

    /**
     * Verifies that the client context has not tampered with its role claims.
     */
    suspend fun verifyUntamperedContext(context: AuthContext): AuthContext.Authenticated {
        return securityEnforcer.verifyUntamperedContext(context)
    }
}
