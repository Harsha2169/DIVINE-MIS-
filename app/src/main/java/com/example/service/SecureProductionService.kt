package com.example.service

import com.example.analytics.AnalyticsFilterContext
import com.example.auth.AuthContext
import com.example.auth.Permission
import com.example.auth.SecurityEnforcer
import com.example.core.BusinessDate
import com.example.model.Department
import com.example.model.ManufacturingModel
import com.example.model.ProductionRecord
import java.util.UUID

/**
 * Authorization-Enforced Production Service Boundary.
 */
class SecureProductionService(
    private val productionService: ProductionService,
    private val securityEnforcer: SecurityEnforcer
) {

    suspend fun recordProduction(
        authContext: AuthContext,
        date: BusinessDate,
        model: ManufacturingModel,
        department: Department,
        quantity: Int,
        shift: String? = null,
        notes: String? = null,
        id: String = UUID.randomUUID().toString()
    ): ProductionRecord {
        val verifiedContext = securityEnforcer.verifyUntamperedContext(authContext)
        securityEnforcer.enforceDepartmentDataEntry(verifiedContext, department, Permission.RECORD_PRODUCTION)

        return productionService.recordProduction(
            date = date,
            model = model,
            department = department,
            quantity = quantity,
            shift = shift,
            notes = notes,
            id = id
        )
    }

    suspend fun getProduction(
        authContext: AuthContext,
        filter: AnalyticsFilterContext
    ): List<ProductionRecord> {
        val verifiedContext = securityEnforcer.verifyUntamperedContext(authContext)
        val targetDept = (filter.department as? com.example.analytics.DepartmentFilter.Specific)?.department
        securityEnforcer.enforceDepartmentRead(verifiedContext, targetDept)

        return productionService.getProduction(filter)
    }
}
