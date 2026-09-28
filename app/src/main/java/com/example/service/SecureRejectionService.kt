package com.example.service

import com.example.analytics.AnalyticsFilterContext
import com.example.auth.AuthContext
import com.example.auth.Permission
import com.example.auth.SecurityEnforcer
import com.example.core.BusinessDate
import com.example.model.BusinessArea
import com.example.model.Department
import com.example.model.ManufacturingModel
import com.example.model.RejectionRecord
import com.example.model.RejectionSide
import com.example.model.RejectionSource
import java.util.UUID

/**
 * Authorization-Enforced Rejection Service Boundary.
 */
class SecureRejectionService(
    private val rejectionService: RejectionService,
    private val securityEnforcer: SecurityEnforcer
) {

    suspend fun recordRejection(
        authContext: AuthContext,
        date: BusinessDate,
        model: ManufacturingModel,
        department: Department,
        defectName: String,
        source: RejectionSource,
        businessArea: BusinessArea,
        side: RejectionSide,
        quantity: Int,
        isRebuffed: Boolean = false,
        notes: String? = null,
        id: String = UUID.randomUUID().toString()
    ): RejectionRecord {
        val verifiedContext = securityEnforcer.verifyUntamperedContext(authContext)
        securityEnforcer.enforceDepartmentDataEntry(verifiedContext, department, Permission.RECORD_REJECTION)

        return rejectionService.recordRejection(
            date = date,
            model = model,
            department = department,
            defectName = defectName,
            source = source,
            businessArea = businessArea,
            side = side,
            quantity = quantity,
            isRebuffed = isRebuffed,
            notes = notes,
            id = id
        )
    }

    suspend fun getRejections(
        authContext: AuthContext,
        filter: AnalyticsFilterContext
    ): List<RejectionRecord> {
        val verifiedContext = securityEnforcer.verifyUntamperedContext(authContext)
        val targetDept = (filter.department as? com.example.analytics.DepartmentFilter.Specific)?.department
        securityEnforcer.enforceDepartmentRead(verifiedContext, targetDept)

        return rejectionService.getRejections(filter)
    }
}
