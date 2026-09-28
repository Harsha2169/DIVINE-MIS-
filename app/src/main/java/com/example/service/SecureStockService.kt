package com.example.service

import com.example.analytics.AnalyticsFilterContext
import com.example.auth.AuthContext
import com.example.auth.Permission
import com.example.auth.SecurityEnforcer
import com.example.core.BusinessDate
import com.example.model.ManufacturingModel
import com.example.model.StockRecord
import java.util.UUID

/**
 * Authorization-Enforced Stock Service Boundary.
 */
class SecureStockService(
    private val stockService: StockService,
    private val securityEnforcer: SecurityEnforcer
) {

    suspend fun recordManualStock(
        authContext: AuthContext,
        date: BusinessDate,
        model: ManufacturingModel,
        openingQuantity: Int,
        closingQuantity: Int,
        notes: String? = null,
        id: String = UUID.randomUUID().toString()
    ): StockRecord {
        val verifiedContext = securityEnforcer.verifyUntamperedContext(authContext)
        securityEnforcer.enforcePermission(verifiedContext, Permission.RECORD_STOCK)

        return stockService.recordManualStock(
            date = date,
            model = model,
            openingQuantity = openingQuantity,
            closingQuantity = closingQuantity,
            notes = notes,
            id = id
        )
    }

    suspend fun getStock(
        authContext: AuthContext,
        filter: AnalyticsFilterContext
    ): List<StockRecord> {
        val verifiedContext = securityEnforcer.verifyUntamperedContext(authContext)
        securityEnforcer.enforcePermission(verifiedContext, Permission.READ_ALL_DATA)

        return stockService.getStock(filter)
    }
}
