package com.example.alert

/**
 * Authoritative Canonical Alert Status Lifecycle for Divine Stamp Manufacturing MIS.
 *
 * Single authoritative lifecycle:
 * OPEN -> ACKNOWLEDGED -> RESOLVED / DISMISSED
 * or directly OPEN -> RESOLVED / DISMISSED
 */
enum class AlertStatus {
    OPEN,
    ACKNOWLEDGED,
    RESOLVED,
    DISMISSED;

    val isTerminal: Boolean get() = this == RESOLVED || this == DISMISSED
    val isActive: Boolean get() = this == OPEN || this == ACKNOWLEDGED
}
