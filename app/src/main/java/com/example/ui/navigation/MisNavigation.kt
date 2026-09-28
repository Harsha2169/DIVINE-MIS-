package com.example.ui.navigation

import com.example.auth.AuthContext
import com.example.auth.UserRole

/**
 * Authoritative Canonical Screens for Divine Stamp Manufacturing MIS.
 */
enum class MisScreen(val route: String, val title: String, val iconName: String) {
    LOGIN("login", "Login", "lock"),
    DASHBOARD("dashboard", "Core MIS Dashboard", "dashboard"),
    PRODUCTION("production", "Production", "factory"),
    REJECTION("rejection", "Rejection", "report_problem"),
    STOCK("stock", "Stock & Inventory", "inventory"),
    PLANNING("planning", "Planning", "calendar_today"),
    ACTION_TRACKER("action_tracker", "Action Tracker", "checklist"),
    ALERTS("alerts", "Alerts & Notifications", "notifications"),
    REPORTS("reports", "Meeting Pack & Reports", "assessment")
}

/**
 * Navigation Policy enforcing role-based screen availability and permissions.
 */
object MisNavigationPolicy {

    /**
     * Returns list of available screens for an authenticated user session.
     */
    fun getAvailableScreens(context: AuthContext): List<MisScreen> {
        if (context !is AuthContext.Authenticated) {
            return listOf(MisScreen.LOGIN)
        }

        // All authenticated roles can navigate to the operational and reporting screens
        return listOf(
            MisScreen.DASHBOARD,
            MisScreen.PRODUCTION,
            MisScreen.REJECTION,
            MisScreen.STOCK,
            MisScreen.PLANNING,
            MisScreen.ACTION_TRACKER,
            MisScreen.ALERTS,
            MisScreen.REPORTS
        )
    }

    /**
     * Determines if the user is authorized to perform data-entry / write operations on a given screen.
     */
    fun canPerformWrite(context: AuthContext): Boolean {
        if (context !is AuthContext.Authenticated) return false
        val user = context.user
        if (!user.isActive) return false

        // CEO is strictly read-only
        if (user.role == UserRole.CEO) return false

        return true
    }

    /**
     * Determines whether the user can manage governance / system settings.
     */
    fun canManageGovernance(context: AuthContext): Boolean {
        if (context !is AuthContext.Authenticated) return false
        return context.user.isActive && context.user.role == UserRole.ADMIN
    }
}
