package com.example.auth

/**
 * Authoritative User Profile Repository Contract.
 *
 * Provides UID-based lookup, profile persistence, and supervisor assignment governance.
 * Compatible with Firestore collection `users`.
 */
interface UserRepository {
    suspend fun getByUid(uid: String): UserProfile?
    suspend fun getByEmail(email: String): UserProfile?
    suspend fun save(user: UserProfile)
    suspend fun getAll(): List<UserProfile>
    suspend fun delete(uid: String): Boolean
}
