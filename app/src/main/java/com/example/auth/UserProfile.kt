package com.example.auth

import com.example.model.Department

/**
 * Authoritative Canonical User Profile for Divine Stamp Manufacturing MIS.
 *
 * Rules:
 * - Passwords are NEVER stored here (managed exclusively by Firebase Authentication).
 * - UID is the authoritative Firebase Auth UID.
 * - Role is strictly governed and immutable from client-side tampering.
 * - Department assignment is strictly enforced for SUPERVISOR roles.
 */
data class UserProfile(
    val uid: String,
    val email: String,
    val displayName: String,
    val role: UserRole,
    val assignedDepartments: Set<Department> = emptySet(),
    val isActive: Boolean = true,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
) {
    init {
        require(uid.isNotBlank()) { "User UID cannot be blank" }
        require(email.isNotBlank()) { "User email cannot be blank" }
        require(displayName.isNotBlank()) { "User display name cannot be blank" }
    }

    /**
     * Checks if this user is assigned to the specified department.
     */
    fun hasDepartmentAssignment(department: Department): Boolean {
        return assignedDepartments.contains(department)
    }

    /**
     * Converts to Firestore-compatible document map.
     */
    fun toFirestoreMap(): Map<String, Any?> {
        return mapOf(
            "uid" to uid,
            "email" to email,
            "displayName" to displayName,
            "role" to role.code,
            "assignedDepartments" to assignedDepartments.map { it.displayName },
            "isActive" to isActive,
            "createdAt" to createdAt,
            "updatedAt" to updatedAt
        )
    }

    companion object {
        /**
         * Deserializes Firestore document map to UserProfile.
         */
        @Suppress("UNCHECKED_CAST")
        fun fromFirestoreMap(data: Map<String, Any?>): UserProfile {
            val uid = data["uid"] as? String ?: throw IllegalArgumentException("Missing uid in user profile map")
            val email = data["email"] as? String ?: throw IllegalArgumentException("Missing email in user profile map")
            val displayName = data["displayName"] as? String ?: ""
            val roleCode = data["role"] as? String ?: throw IllegalArgumentException("Missing role in user profile map")
            val role = UserRole.fromCode(roleCode)

            val deptList = (data["assignedDepartments"] as? List<String>) ?: emptyList()
            val assignedDepartments = deptList.mapNotNull { Department.fromDisplayNameOrNull(it) }.toSet()
            val isActive = data["isActive"] as? Boolean ?: true
            val createdAt = (data["createdAt"] as? Number)?.toLong() ?: System.currentTimeMillis()
            val updatedAt = (data["updatedAt"] as? Number)?.toLong() ?: System.currentTimeMillis()

            return UserProfile(
                uid = uid,
                email = email,
                displayName = displayName,
                role = role,
                assignedDepartments = assignedDepartments,
                isActive = isActive,
                createdAt = createdAt,
                updatedAt = updatedAt
            )
        }
    }
}
