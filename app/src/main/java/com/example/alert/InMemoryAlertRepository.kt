package com.example.alert

import com.example.core.BusinessDate
import com.example.model.Department

/**
 * In-Memory Implementation of [AlertRepository] for testing and deterministic evaluation.
 */
class InMemoryAlertRepository : AlertRepository {
    private val alerts = mutableMapOf<String, AlertRecord>()

    override suspend fun insert(alert: AlertRecord): AlertRecord {
        alerts[alert.id] = alert
        return alert
    }

    override suspend fun insertAll(alerts: List<AlertRecord>) {
        alerts.forEach { insert(it) }
    }

    override suspend fun getById(id: String): AlertRecord? {
        return alerts[id]
    }

    override suspend fun getAll(): List<AlertRecord> {
        return alerts.values.toList()
    }

    override suspend fun getByStatus(status: AlertStatus): List<AlertRecord> {
        return alerts.values.filter { it.status == status }
    }

    override suspend fun getByDepartment(department: Department): List<AlertRecord> {
        return alerts.values.filter { it.department == department }
    }

    override suspend fun getByDate(date: BusinessDate): List<AlertRecord> {
        return alerts.values.filter { it.businessDate == date }
    }

    override suspend fun update(alert: AlertRecord): AlertRecord {
        if (!alerts.containsKey(alert.id)) {
            throw IllegalArgumentException("Cannot update non-existent alert with ID: '${alert.id}'")
        }
        alerts[alert.id] = alert
        return alert
    }

    override suspend fun clear() {
        alerts.clear()
    }
}
