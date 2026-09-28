package com.example.auth

/**
 * Thread-safe In-Memory implementation of UserRepository for local testing & caching.
 */
class InMemoryUserRepository : UserRepository {
    private val usersByUid = mutableMapOf<String, UserProfile>()

    override suspend fun getByUid(uid: String): UserProfile? {
        return usersByUid[uid]
    }

    override suspend fun getByEmail(email: String): UserProfile? {
        val trimmed = email.trim()
        return usersByUid.values.firstOrNull { it.email.equals(trimmed, ignoreCase = true) }
    }

    override suspend fun save(user: UserProfile) {
        usersByUid[user.uid] = user
    }

    override suspend fun getAll(): List<UserProfile> {
        return usersByUid.values.toList()
    }

    override suspend fun delete(uid: String): Boolean {
        return usersByUid.remove(uid) != null
    }

    fun clear() {
        usersByUid.clear()
    }
}
