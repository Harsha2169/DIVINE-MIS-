package com.example.auth

import com.example.model.Department

/**
 * Service governing user management and role assignment.
 * Governed strictly by the ADMIN role.
 */
class UserGovernanceService(
    private val userRepository: UserRepository,
    private val securityEnforcer: SecurityEnforcer
) {

    suspend fun createUser(
        actorContext: AuthContext,
        uid: String,
        email: String,
        displayName: String,
        role: UserRole,
        assignedDepartments: Set<Department> = emptySet()
    ): UserProfile {
        val verifiedActor = securityEnforcer.verifyUntamperedContext(actorContext)
        securityEnforcer.enforcePermission(verifiedActor, Permission.MANAGE_USERS)

        val profile = UserProfile(
            uid = uid.trim(),
            email = email.trim(),
            displayName = displayName.trim(),
            role = role,
            assignedDepartments = if (role == UserRole.SUPERVISOR) assignedDepartments else emptySet(),
            isActive = true
        )
        userRepository.save(profile)
        return profile
    }

    suspend fun updateUserRole(
        actorContext: AuthContext,
        targetUid: String,
        newRole: UserRole
    ): UserProfile {
        val verifiedActor = securityEnforcer.verifyUntamperedContext(actorContext)
        securityEnforcer.enforcePermission(verifiedActor, Permission.MANAGE_USERS)

        val existing = userRepository.getByUid(targetUid)
            ?: throw AccessDeniedSecurityException("User profile with UID '$targetUid' does not exist.")

        val updated = existing.copy(
            role = newRole,
            assignedDepartments = if (newRole == UserRole.SUPERVISOR) existing.assignedDepartments else emptySet(),
            updatedAt = System.currentTimeMillis()
        )
        userRepository.save(updated)
        return updated
    }

    suspend fun updateSupervisorDepartments(
        actorContext: AuthContext,
        targetUid: String,
        departments: Set<Department>
    ): UserProfile {
        val verifiedActor = securityEnforcer.verifyUntamperedContext(actorContext)
        securityEnforcer.enforcePermission(verifiedActor, Permission.MANAGE_USERS)

        val existing = userRepository.getByUid(targetUid)
            ?: throw AccessDeniedSecurityException("User profile with UID '$targetUid' does not exist.")

        require(existing.role == UserRole.SUPERVISOR) {
            "Department assignments can only be configured for SUPERVISOR roles, but user '$targetUid' is '${existing.role.code}'."
        }

        val updated = existing.copy(
            assignedDepartments = departments,
            updatedAt = System.currentTimeMillis()
        )
        userRepository.save(updated)
        return updated
    }

    suspend fun setUserActiveStatus(
        actorContext: AuthContext,
        targetUid: String,
        isActive: Boolean
    ): UserProfile {
        val verifiedActor = securityEnforcer.verifyUntamperedContext(actorContext)
        securityEnforcer.enforcePermission(verifiedActor, Permission.MANAGE_USERS)

        val existing = userRepository.getByUid(targetUid)
            ?: throw AccessDeniedSecurityException("User profile with UID '$targetUid' does not exist.")

        val updated = existing.copy(
            isActive = isActive,
            updatedAt = System.currentTimeMillis()
        )
        userRepository.save(updated)
        return updated
    }
}
