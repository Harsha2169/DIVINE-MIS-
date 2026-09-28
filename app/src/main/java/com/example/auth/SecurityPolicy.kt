package com.example.auth

import com.example.model.Department

/**
 * Authoritative Centralized Role-to-Permission Mapping and Security Policy.
 */
object SecurityPolicy {

    /**
     * Base permissions granted to each canonical role.
     */
    val ROLE_PERMISSIONS: Map<UserRole, Set<Permission>> = mapOf(
        UserRole.CEO to setOf(
            Permission.READ_ALL_DATA
        ),
        UserRole.ADMIN to setOf(
            Permission.READ_ALL_DATA,
            Permission.MANAGE_USERS,
            Permission.MANAGE_SYSTEM_SETTINGS,
            Permission.VIEW_GOVERNANCE_AUDIT,
            Permission.MANAGE_ACTIONS
        ),
        UserRole.MANAGER to setOf(
            Permission.READ_ALL_DATA,
            Permission.RECORD_PRODUCTION,
            Permission.RECORD_REJECTION,
            Permission.RECORD_STOCK,
            Permission.RECORD_PLAN,
            Permission.APPROVE_PLAN,
            Permission.MANAGE_ACTIONS
        ),
        UserRole.SUPERVISOR to setOf(
            Permission.READ_ASSIGNED_DEPARTMENT_DATA,
            Permission.RECORD_PRODUCTION,
            Permission.RECORD_REJECTION,
            Permission.MANAGE_ACTIONS
        )
    )

    /**
     * Checks if a role has the base permission granted.
     */
    fun hasBasePermission(role: UserRole, permission: Permission): Boolean {
        return ROLE_PERMISSIONS[role]?.contains(permission) == true
    }
}
