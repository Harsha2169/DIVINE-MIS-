package com.example.repository.memory

import com.example.action.ActionPriority
import com.example.action.ActionRecord
import com.example.action.ActionStatus
import com.example.core.BusinessDate
import com.example.model.Department
import com.example.repository.ActionTrackerRepository
import java.util.concurrent.ConcurrentHashMap

/**
 * Thread-safe In-Memory implementation of ActionTrackerRepository.
 */
class InMemoryActionTrackerRepository : ActionTrackerRepository {
    private val records = ConcurrentHashMap<String, ActionRecord>()

    override suspend fun insert(action: ActionRecord) {
        records[action.id] = action
    }

    override suspend fun update(action: ActionRecord) {
        records[action.id] = action
    }

    override suspend fun getById(id: String): ActionRecord? = records[id]

    override suspend fun getAll(): List<ActionRecord> {
        return records.values.sortedByDescending { it.createdAt }
    }

    override suspend fun getByDepartment(department: Department): List<ActionRecord> {
        return records.values
            .filter { it.department == department }
            .sortedByDescending { it.createdAt }
    }

    override suspend fun getByStatus(status: ActionStatus): List<ActionRecord> {
        return records.values
            .filter { it.status == status }
            .sortedByDescending { it.createdAt }
    }

    override suspend fun getByPriority(priority: ActionPriority): List<ActionRecord> {
        return records.values
            .filter { it.priority == priority }
            .sortedByDescending { it.createdAt }
    }

    override suspend fun getByAssignee(assigneeId: String): List<ActionRecord> {
        return records.values
            .filter { it.assigneeId == assigneeId }
            .sortedByDescending { it.createdAt }
    }

    override suspend fun getByDateRange(from: BusinessDate, to: BusinessDate): List<ActionRecord> {
        return records.values
            .filter { it.businessDate in from..to }
            .sortedBy { it.businessDate }
    }

    override suspend fun deleteById(id: String): Boolean = records.remove(id) != null

    fun clear() {
        records.clear()
    }
}
