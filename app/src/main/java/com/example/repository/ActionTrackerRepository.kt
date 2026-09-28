package com.example.repository

import com.example.action.ActionPriority
import com.example.action.ActionRecord
import com.example.action.ActionStatus
import com.example.core.BusinessDate
import com.example.model.Department

/**
 * Authoritative Repository Interface for Action Tracker.
 *
 * All implementations (Firestore, In-Memory, Room) MUST strictly follow this contract.
 */
interface ActionTrackerRepository {
    suspend fun insert(action: ActionRecord)
    suspend fun update(action: ActionRecord)
    suspend fun getById(id: String): ActionRecord?
    suspend fun getAll(): List<ActionRecord>
    suspend fun getByDepartment(department: Department): List<ActionRecord>
    suspend fun getByStatus(status: ActionStatus): List<ActionRecord>
    suspend fun getByPriority(priority: ActionPriority): List<ActionRecord>
    suspend fun getByAssignee(assigneeId: String): List<ActionRecord>
    suspend fun getByDateRange(from: BusinessDate, to: BusinessDate): List<ActionRecord>
    suspend fun deleteById(id: String): Boolean
}
