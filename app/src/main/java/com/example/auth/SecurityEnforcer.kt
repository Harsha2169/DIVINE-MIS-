package com.example.auth

import com.example.model.Department

sealed class AuthorizationResult {
    object Allowed : AuthorizationResult()
    data class Denied(val reason: String) : AuthorizationResult() {
        val isDenied: Boolean = true
    }

    val isAllowed: Boolean get() = this is Allowed
}

class AccessDeniedSecurityException(
    val reason: String
) : SecurityException("Access Denied: $reason")

/**
 * Authoritative Security Enforcer for Divine Stamp Manufacturing MIS.
 *
 * Implements deterministic RBAC contracts:
 * - Unauthenticated = DENIED
 * - Inactive user = DENIED
 * - Missing profile/role = DENIED
 * - CEO cannot create/edit/delete operational data (read-only)
 * - SUPERVISOR restricted to assigned department(s) only
 * - Missing supervisor assignment = DENIED
 * - ADMIN has full governance access
 * - MANAGER has operational management access
 * - Client role escalation attempts are detected and strictly rejected
 */
class SecurityEnforcer(
    private val userRepository: UserRepository? = null
) {

    /**
     * Evaluates permission for a given AuthContext.
     */
    fun evaluatePermission(context: AuthContext, permission: Permission): AuthorizationResult {
        if (context !is AuthContext.Authenticated) {
            return AuthorizationResult.Denied("Unauthenticated request. A valid authenticated session is required.")
        }

        val user = context.user
        if (!user.isActive) {
            return AuthorizationResult.Denied("User account '${user.uid}' is deactivated.")
        }

        // CEO write prohibition
        if (user.role == UserRole.CEO && permission != Permission.READ_ALL_DATA) {
            return AuthorizationResult.Denied("Role 'CEO' has executive read-only privileges and cannot perform write operations.")
        }

        val hasPermission = SecurityPolicy.hasBasePermission(user.role, permission)
        return if (hasPermission) {
            AuthorizationResult.Allowed
        } else {
            AuthorizationResult.Denied("Role '${user.role.code}' lacks required permission '$permission'.")
        }
    }

    /**
     * Enforces permission, throwing AccessDeniedSecurityException if unauthorized.
     */
    fun enforcePermission(context: AuthContext, permission: Permission) {
        when (val result = evaluatePermission(context, permission)) {
            is AuthorizationResult.Allowed -> Unit
            is AuthorizationResult.Denied -> throw AccessDeniedSecurityException(result.reason)
        }
    }

    /**
     * Evaluates department-scoped operational entry (Production or Rejection).
     */
    fun evaluateDepartmentDataEntry(
        context: AuthContext,
        department: Department,
        permission: Permission
    ): AuthorizationResult {
        val baseCheck = evaluatePermission(context, permission)
        if (baseCheck is AuthorizationResult.Denied) {
            return baseCheck
        }

        val user = (context as AuthContext.Authenticated).user

        // Role-specific scoping
        return when (user.role) {
            UserRole.SUPERVISOR -> {
                if (user.assignedDepartments.isEmpty()) {
                    AuthorizationResult.Denied(
                        "Supervisor '${user.displayName}' has no assigned departments. Missing department assignment."
                    )
                } else if (!user.hasDepartmentAssignment(department)) {
                    AuthorizationResult.Denied(
                        "Supervisor '${user.displayName}' is not assigned to department '${department.displayName}'. Assigned departments: ${user.assignedDepartments.map { it.displayName }}."
                    )
                } else {
                    AuthorizationResult.Allowed
                }
            }
            UserRole.MANAGER -> AuthorizationResult.Allowed
            UserRole.ADMIN -> AuthorizationResult.Allowed
            UserRole.CEO -> AuthorizationResult.Denied("CEO cannot perform operational data entry.")
        }
    }

    /**
     * Enforces department-scoped operational entry, throwing on failure.
     */
    fun enforceDepartmentDataEntry(
        context: AuthContext,
        department: Department,
        permission: Permission
    ) {
        when (val result = evaluateDepartmentDataEntry(context, department, permission)) {
            is AuthorizationResult.Allowed -> Unit
            is AuthorizationResult.Denied -> throw AccessDeniedSecurityException(result.reason)
        }
    }

    /**
     * Evaluates action management permissions and department scoping.
     */
    fun evaluateActionManagement(
        context: AuthContext,
        department: Department
    ): AuthorizationResult {
        if (context !is AuthContext.Authenticated) {
            return AuthorizationResult.Denied("Unauthenticated request. A valid authenticated session is required.")
        }

        val user = context.user
        if (!user.isActive) {
            return AuthorizationResult.Denied("User account '${user.uid}' is deactivated.")
        }

        if (user.role == UserRole.CEO) {
            return AuthorizationResult.Denied("Role 'CEO' has executive read-only privileges and cannot perform action write operations.")
        }

        return when (user.role) {
            UserRole.ADMIN, UserRole.MANAGER -> AuthorizationResult.Allowed
            UserRole.SUPERVISOR -> {
                if (user.assignedDepartments.isEmpty()) {
                    AuthorizationResult.Denied("Supervisor '${user.displayName}' has no assigned departments.")
                } else if (!user.hasDepartmentAssignment(department)) {
                    AuthorizationResult.Denied(
                        "Supervisor '${user.displayName}' is not authorized for department '${department.displayName}'. Assigned: ${user.assignedDepartments.map { it.displayName }}."
                    )
                } else {
                    AuthorizationResult.Allowed
                }
            }
            UserRole.CEO -> AuthorizationResult.Denied("Role 'CEO' cannot manage actions.")
        }
    }

    fun enforceActionManagement(context: AuthContext, department: Department) {
        when (val result = evaluateActionManagement(context, department)) {
            is AuthorizationResult.Allowed -> Unit
            is AuthorizationResult.Denied -> throw AccessDeniedSecurityException(result.reason)
        }
    }

    /**
     * Evaluates department read access.
     */
    fun evaluateDepartmentRead(context: AuthContext, department: Department? = null): AuthorizationResult {
        if (context !is AuthContext.Authenticated) {
            return AuthorizationResult.Denied("Unauthenticated request. A valid authenticated session is required.")
        }

        val user = context.user
        if (!user.isActive) {
            return AuthorizationResult.Denied("User account '${user.uid}' is deactivated.")
        }

        return when (user.role) {
            UserRole.CEO, UserRole.ADMIN, UserRole.MANAGER -> AuthorizationResult.Allowed
            UserRole.SUPERVISOR -> {
                if (department == null) {
                    // Global read without department filter is restricted for supervisor
                    AuthorizationResult.Denied("Supervisor cannot read across all departments without a department scope.")
                } else if (user.assignedDepartments.isEmpty()) {
                    AuthorizationResult.Denied("Supervisor '${user.displayName}' has no assigned departments.")
                } else if (!user.hasDepartmentAssignment(department)) {
                    AuthorizationResult.Denied(
                        "Supervisor '${user.displayName}' cannot read unassigned department '${department.displayName}'."
                    )
                } else {
                    AuthorizationResult.Allowed
                }
            }
        }
    }

    fun enforceDepartmentRead(context: AuthContext, department: Department? = null) {
        when (val result = evaluateDepartmentRead(context, department)) {
            is AuthorizationResult.Allowed -> Unit
            is AuthorizationResult.Denied -> throw AccessDeniedSecurityException(result.reason)
        }
    }

    /**
     * Verifies that the client context has not tampered with or escalated roles.
     * Checks against authoritative repository record.
     */
    suspend fun verifyUntamperedContext(clientContext: AuthContext): AuthContext.Authenticated {
        if (clientContext !is AuthContext.Authenticated) {
            throw AccessDeniedSecurityException("Unauthenticated request.")
        }

        if (userRepository == null) {
            return clientContext
        }

        val authoritativeProfile = userRepository.getByUid(clientContext.uid)
            ?: throw AccessDeniedSecurityException("User profile for '${clientContext.uid}' does not exist in authoritative storage.")

        if (!authoritativeProfile.isActive) {
            throw AccessDeniedSecurityException("User account '${clientContext.uid}' is deactivated.")
        }

        if (authoritativeProfile.role != clientContext.role) {
            throw AccessDeniedSecurityException(
                "Client role escalation detected! Claimed '${clientContext.role.code}' but authoritative role is '${authoritativeProfile.role.code}'."
            )
        }

        return AuthContext.Authenticated(authoritativeProfile)
    }
}
