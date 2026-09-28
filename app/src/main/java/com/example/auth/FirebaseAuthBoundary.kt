package com.example.auth

/**
 * Authoritative Firestore Collection Schema for Divine Stamp Manufacturing MIS.
 * Shared synchronously between Web and Android clients.
 */
object FirestoreSchema {
    const val USERS = "users"
    const val PRODUCTION = "production"
    const val REJECTION = "rejection"
    const val STOCK = "stock"
    const val PLANNING = "planning"
    const val AUDIT_LOGS = "audit_logs"
    const val MASTER_DATA = "master_data"
}

/**
 * Authentication Boundary Interface abstracting Firebase Authentication.
 */
interface FirebaseAuthBoundary {
    suspend fun getCurrentAuthContext(): AuthContext
    suspend fun signIn(uid: String): AuthContext
    suspend fun signOut()
}

/**
 * In-Memory implementation of FirebaseAuthBoundary for deterministic unit testing.
 */
class InMemoryFirebaseAuthBoundary(
    private val userRepository: UserRepository
) : FirebaseAuthBoundary {
    private var currentContext: AuthContext = AuthContext.Unauthenticated

    override suspend fun getCurrentAuthContext(): AuthContext {
        return currentContext
    }

    override suspend fun signIn(uid: String): AuthContext {
        val user = userRepository.getByUid(uid)
            ?: throw AccessDeniedSecurityException("Cannot authenticate: User profile for '$uid' does not exist.")
        if (!user.isActive) {
            throw AccessDeniedSecurityException("Cannot authenticate: User account '$uid' is deactivated.")
        }
        currentContext = AuthContext.Authenticated(user)
        return currentContext
    }

    override suspend fun signOut() {
        currentContext = AuthContext.Unauthenticated
    }

    fun setDirectContext(context: AuthContext) {
        currentContext = context
    }
}
