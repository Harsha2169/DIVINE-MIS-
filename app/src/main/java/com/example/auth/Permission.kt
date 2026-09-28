package com.example.auth

/**
 * Authoritative Granular Permissions for Divine Stamp Manufacturing MIS.
 */
enum class Permission {
    // Read permissions
    READ_ALL_DATA,
    READ_ASSIGNED_DEPARTMENT_DATA,

    // Operational permissions
    RECORD_PRODUCTION,
    RECORD_REJECTION,
    RECORD_STOCK,
    RECORD_PLAN,
    APPROVE_PLAN,
    MANAGE_ACTIONS,

    // Governance permissions
    MANAGE_USERS,
    MANAGE_SYSTEM_SETTINGS,
    VIEW_GOVERNANCE_AUDIT
}
