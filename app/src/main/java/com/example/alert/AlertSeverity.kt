package com.example.alert

/**
 * Authoritative Canonical Alert Severity for Divine Stamp Manufacturing MIS.
 */
enum class AlertSeverity(val level: Int) {
    INFO(1),
    WARNING(2),
    HIGH(3),
    CRITICAL(4);

    val isUrgent: Boolean get() = this == HIGH || this == CRITICAL
}
