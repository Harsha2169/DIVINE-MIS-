package com.example.auth

/**
 * Authentication Context representing the current security principal.
 */
sealed class AuthContext {
    object Unauthenticated : AuthContext()

    data class Authenticated(
        val user: UserProfile
    ) : AuthContext() {
        val uid: String get() = user.uid
        val role: UserRole get() = user.role
        val email: String get() = user.email
    }

    val isAuthenticated: Boolean get() = this is Authenticated
    val currentUser: UserProfile? get() = (this as? Authenticated)?.user
}
