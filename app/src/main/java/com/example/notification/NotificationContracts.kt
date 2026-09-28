package com.example.notification

import com.example.alert.AlertRecord
import com.example.alert.AlertSeverity
import com.example.auth.UserRole
import com.example.model.Department

/**
 * Delivery channels for the platform-independent notification boundary.
 */
enum class NotificationChannel {
    IN_APP,
    SYSTEM_TRAY,
    PUSH,
    EMAIL
}

/**
 * Priority of notifications.
 */
enum class NotificationPriority {
    LOW,
    NORMAL,
    HIGH,
    URGENT
}

/**
 * Abstract recipient representation for notifications.
 */
sealed class NotificationRecipient {
    data class User(val uid: String, val role: UserRole? = null) : NotificationRecipient()
    data class DepartmentSupervisors(val department: Department) : NotificationRecipient()
    data class Role(val role: UserRole) : NotificationRecipient()
    object Broadcast : NotificationRecipient()
}

/**
 * Platform-independent notification payload.
 * Suitable for Web, Android, and future FCM push integration.
 */
data class NotificationPayload(
    val id: String,
    val title: String,
    val message: String,
    val channel: NotificationChannel = NotificationChannel.IN_APP,
    val priority: NotificationPriority = NotificationPriority.NORMAL,
    val alertId: String? = null,
    val data: Map<String, String> = emptyMap(),
    val timestamp: Long = System.currentTimeMillis()
)

/**
 * Result of a notification dispatch operation.
 */
data class NotificationDispatchResult(
    val success: Boolean,
    val deliveredCount: Int,
    val failedRecipients: List<NotificationRecipient> = emptyList(),
    val message: String? = null
)

/**
 * Platform-independent notification boundary interface.
 */
interface NotificationBoundary {
    suspend fun dispatch(
        payload: NotificationPayload,
        recipients: List<NotificationRecipient>
    ): NotificationDispatchResult
}

/**
 * In-memory notification boundary implementation for testing and local simulation.
 */
class InMemoryNotificationBoundary : NotificationBoundary {
    private val dispatches = mutableListOf<Pair<NotificationPayload, List<NotificationRecipient>>>()

    override suspend fun dispatch(
        payload: NotificationPayload,
        recipients: List<NotificationRecipient>
    ): NotificationDispatchResult {
        dispatches.add(payload to recipients)
        return NotificationDispatchResult(
            success = true,
            deliveredCount = recipients.size
        )
    }

    fun getDispatched(): List<Pair<NotificationPayload, List<NotificationRecipient>>> {
        return dispatches.toList()
    }

    fun getPayloads(): List<NotificationPayload> {
        return dispatches.map { it.first }
    }

    fun clear() {
        dispatches.clear()
    }
}

/**
 * Extension to convert an [AlertRecord] into a platform-independent [NotificationPayload].
 */
fun AlertRecord.toNotificationPayload(
    channel: NotificationChannel = NotificationChannel.IN_APP
): NotificationPayload {
    val priority = when (severity) {
        AlertSeverity.INFO -> NotificationPriority.LOW
        AlertSeverity.WARNING -> NotificationPriority.NORMAL
        AlertSeverity.HIGH -> NotificationPriority.HIGH
        AlertSeverity.CRITICAL -> NotificationPriority.URGENT
    }

    val metadata = mutableMapOf(
        "alertType" to type.name,
        "businessDate" to businessDate.toString(),
        "severity" to severity.name
    )
    department?.let { metadata["department"] = it.name }
    model?.let { metadata["model"] = it.name }
    reference?.let { metadata["reference"] = it }

    return NotificationPayload(
        id = "NOTIF-$id",
        title = "${severity.name}: ${type.displayName}",
        message = message,
        channel = channel,
        priority = priority,
        alertId = id,
        data = metadata
    )
}
