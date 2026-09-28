package com.example.alert

import com.example.core.BusinessDate
import com.example.model.Department

/**
 * Authoritative Repository Boundary for Alert Records.
 */
interface AlertRepository {
    suspend fun insert(alert: AlertRecord): AlertRecord
    suspend fun insertAll(alerts: List<AlertRecord>)
    suspend fun getById(id: String): AlertRecord?
    suspend fun getAll(): List<AlertRecord>
    suspend fun getByStatus(status: AlertStatus): List<AlertRecord>
    suspend fun getByDepartment(department: Department): List<AlertRecord>
    suspend fun getByDate(date: BusinessDate): List<AlertRecord>
    suspend fun update(alert: AlertRecord): AlertRecord
    suspend fun clear()
}
