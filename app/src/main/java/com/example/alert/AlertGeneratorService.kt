package com.example.alert

import com.example.action.ActionPriority
import com.example.action.ActionStatus
import com.example.core.BusinessDate
import com.example.model.Department
import com.example.model.ManufacturingModel
import com.example.repository.ActionTrackerRepository
import com.example.repository.PlanningRepository
import com.example.repository.ProductionRepository
import com.example.repository.RejectionRepository
import com.example.repository.StockRepository

/**
 * Authoritative Deterministic Alert Generation Engine for Divine Stamp Manufacturing MIS.
 *
 * Enforces:
 * - Alerts must be deterministic.
 * - 0 is a valid metric and does not trigger missing data alerts.
 * - NO DATA only triggers alerts if the configured rule explicitly enables it.
 * - Thresholds are supplied via [AlertConfiguration], never hardcoded assumptions.
 */
class AlertGeneratorService(
    private val productionRepository: ProductionRepository,
    private val planningRepository: PlanningRepository,
    private val rejectionRepository: RejectionRepository,
    private val stockRepository: StockRepository,
    private val actionTrackerRepository: ActionTrackerRepository
) {

    /**
     * Generates all authoritative alerts for a given business date using [config].
     */
    suspend fun generateAlerts(
        businessDate: BusinessDate,
        config: AlertConfiguration = AlertConfiguration(),
        timestamp: String = businessDate.toString()
    ): List<AlertRecord> {
        val alerts = mutableListOf<AlertRecord>()

        val productions = productionRepository.getByDateRange(businessDate, businessDate)
        val plans = planningRepository.getByDateRange(businessDate, businessDate)
        val rejections = rejectionRepository.getByDateRange(businessDate, businessDate)
        val stocks = stockRepository.getByDateRange(businessDate, businessDate)

        // 1. PRODUCTION BELOW PLAN & MISSING PRODUCTION/PLAN DATA
        // Map combinations of (department, model) from plans
        val planGroups = plans.groupBy { it.department to it.model }
        val prodGroups = productions.groupBy { it.department to it.model }

        // Evaluate all planned combinations
        for ((key, planList) in planGroups) {
            val (dept, model) = key
            val totalPlanned = planList.sumOf { it.plannedQuantity }
            val prodList = prodGroups[key]

            if (prodList == null || prodList.isEmpty()) {
                // Production records are completely absent (NO DATA)
                if (config.alertOnMissingProduction) {
                    alerts.add(
                        AlertRecord(
                            id = "ALT-MISSING-PROD-${businessDate}-${dept.name}-${model.name}",
                            type = AlertType.MISSING_REQUIRED_OPERATIONAL_DATA,
                            severity = AlertSeverity.WARNING,
                            businessDate = businessDate,
                            department = dept,
                            model = model,
                            reference = "PLAN-${dept.name}-${model.name}",
                            message = "Missing required production data for ${dept.displayName} (${model.displayName}) on date $businessDate",
                            createdAt = timestamp
                        )
                    )
                }
            } else {
                // Production records exist (even if quantity is 0, it is valid data!)
                val totalProduced = prodList.sumOf { it.quantity.value }
                if (totalPlanned > 0 && config.minAchievementPercentageThreshold != null) {
                    val achievement = (totalProduced.toDouble() / totalPlanned.toDouble()) * 100.0
                    if (achievement < config.minAchievementPercentageThreshold) {
                        val severity = if (achievement < (config.minAchievementPercentageThreshold / 2.0)) {
                            AlertSeverity.CRITICAL
                        } else {
                            AlertSeverity.WARNING
                        }
                        val formattedAchievement = "%.1f".format(achievement)
                        alerts.add(
                            AlertRecord(
                                id = "ALT-PROD-BELOW-PLAN-${businessDate}-${dept.name}-${model.name}",
                                type = AlertType.PRODUCTION_BELOW_PLAN,
                                severity = severity,
                                businessDate = businessDate,
                                department = dept,
                                model = model,
                                reference = "ACHIEVEMENT-$formattedAchievement%",
                                message = "Production below plan for ${dept.displayName} (${model.displayName}): Achieved $formattedAchievement% (Actual: $totalProduced, Plan: $totalPlanned), below configured threshold of ${config.minAchievementPercentageThreshold}%",
                                createdAt = timestamp
                            )
                        )
                    }
                }
            }
        }

        // Missing plan data check (if production occurred with no plan)
        if (config.alertOnMissingPlan) {
            for ((key, _) in prodGroups) {
                if (!planGroups.containsKey(key)) {
                    val (dept, model) = key
                    alerts.add(
                        AlertRecord(
                            id = "ALT-MISSING-PLAN-${businessDate}-${dept.name}-${model.name}",
                            type = AlertType.MISSING_REQUIRED_OPERATIONAL_DATA,
                            severity = AlertSeverity.INFO,
                            businessDate = businessDate,
                            department = dept,
                            model = model,
                            reference = "PROD-${dept.name}-${model.name}",
                            message = "Missing required plan data for ${dept.displayName} (${model.displayName}) on date $businessDate",
                            createdAt = timestamp
                        )
                    )
                }
            }
        }

        // 2. REJECTION THRESHOLD EXCEEDED
        if (config.maxRejectionRatePercentageThreshold != null) {
            val rejByDept = rejections.groupBy { it.department }
            val prodByDept = productions.groupBy { it.department }

            for ((dept, rejList) in rejByDept) {
                val prodList = prodByDept[dept] ?: emptyList()
                val rejQty = rejList.sumOf { it.quantity }
                val prodQty = prodList.sumOf { it.quantity.value }
                val totalQty = prodQty + rejQty

                if (totalQty > 0) {
                    val rejRate = (rejQty.toDouble() / totalQty.toDouble()) * 100.0
                    if (rejRate > config.maxRejectionRatePercentageThreshold) {
                        val severity = if (rejRate >= config.maxRejectionRatePercentageThreshold * 2.0) {
                            AlertSeverity.CRITICAL
                        } else {
                            AlertSeverity.WARNING
                        }
                        val formattedRate = "%.2f".format(rejRate)
                        alerts.add(
                            AlertRecord(
                                id = "ALT-REJ-THRESH-${businessDate}-${dept.name}",
                                type = AlertType.REJECTION_THRESHOLD_EXCEEDED,
                                severity = severity,
                                businessDate = businessDate,
                                department = dept,
                                reference = "RATE-$formattedRate%",
                                message = "Rejection threshold exceeded for ${dept.displayName}: $formattedRate% (Rejections: $rejQty, Total: $totalQty), exceeding configured limit of ${config.maxRejectionRatePercentageThreshold}%",
                                createdAt = timestamp
                            )
                        )
                    }
                }
            }
        }

        // 3. CRITICAL / HIGH REJECTION ISSUE
        for (rej in rejections) {
            val isCriticalName = config.criticalDefectNames.contains(rej.defectName)
            val isHighVolume = config.highDefectQuantityThreshold != null && rej.quantity >= config.highDefectQuantityThreshold

            if (isCriticalName) {
                alerts.add(
                    AlertRecord(
                        id = "ALT-REJ-CRIT-${businessDate}-${rej.id}",
                        type = AlertType.CRITICAL_HIGH_REJECTION_ISSUE,
                        severity = AlertSeverity.CRITICAL,
                        businessDate = businessDate,
                        department = rej.department,
                        model = rej.model,
                        reference = rej.id,
                        message = "Critical rejection defect '${rej.defectName}' recorded in ${rej.department.displayName} (Quantity: ${rej.quantity})",
                        createdAt = timestamp
                    )
                )
            } else if (isHighVolume) {
                alerts.add(
                    AlertRecord(
                        id = "ALT-REJ-HIGH-${businessDate}-${rej.id}",
                        type = AlertType.CRITICAL_HIGH_REJECTION_ISSUE,
                        severity = AlertSeverity.HIGH,
                        businessDate = businessDate,
                        department = rej.department,
                        model = rej.model,
                        reference = rej.id,
                        message = "High rejection volume for defect '${rej.defectName}' in ${rej.department.displayName}: ${rej.quantity} items (configured threshold: ${config.highDefectQuantityThreshold})",
                        createdAt = timestamp
                    )
                )
            }
        }

        // 4. STOCK CONDITION REQUIRING ATTENTION & MISSING STOCK DATA
        val stockModels = stocks.map { it.model }.toSet()
        if (config.minClosingStockThreshold != null) {
            for (stock in stocks) {
                if (stock.closingQuantity < config.minClosingStockThreshold) {
                    val severity = if (stock.closingQuantity <= 0) AlertSeverity.CRITICAL else AlertSeverity.WARNING
                    alerts.add(
                        AlertRecord(
                            id = "ALT-STK-ATTN-${businessDate}-${stock.model.name}",
                            type = AlertType.STOCK_CONDITION_ATTENTION,
                            severity = severity,
                            businessDate = businessDate,
                            model = stock.model,
                            reference = "CLOSING-${stock.closingQuantity}",
                            message = "Stock condition alert for model ${stock.model.displayName}: Closing stock is ${stock.closingQuantity}, below configured threshold of ${config.minClosingStockThreshold}",
                            createdAt = timestamp
                        )
                    )
                }
            }
        }

        if (config.alertOnMissingStock) {
            val activeModels = (plans.map { it.model } + productions.map { it.model }).distinct()
            for (model in activeModels) {
                if (!stockModels.contains(model)) {
                    alerts.add(
                        AlertRecord(
                            id = "ALT-MISSING-STOCK-${businessDate}-${model.name}",
                            type = AlertType.MISSING_REQUIRED_OPERATIONAL_DATA,
                            severity = AlertSeverity.WARNING,
                            businessDate = businessDate,
                            model = model,
                            reference = "STOCK-${model.name}",
                            message = "Missing required stock data for model ${model.displayName} on date $businessDate",
                            createdAt = timestamp
                        )
                    )
                }
            }
        }

        // 5. OVERDUE ACTION & 6. CRITICAL ACTION
        val allActions = actionTrackerRepository.getAll()
        val activeActions = allActions.filter {
            it.status == ActionStatus.OPEN ||
                    it.status == ActionStatus.ASSIGNED ||
                    it.status == ActionStatus.IN_PROGRESS ||
                    it.status == ActionStatus.ON_HOLD
        }

        for (action in activeActions) {
            // Overdue Action
            if (config.alertOnOverdueActions && action.dueDate != null && action.dueDate < businessDate) {
                val severity = if (action.priority == ActionPriority.CRITICAL) AlertSeverity.CRITICAL else AlertSeverity.HIGH
                alerts.add(
                    AlertRecord(
                        id = "ALT-ACTION-OVERDUE-${action.id}",
                        type = AlertType.OVERDUE_ACTION,
                        severity = severity,
                        businessDate = businessDate,
                        department = action.department,
                        reference = action.id,
                        message = "Action '${action.title}' (ID: ${action.id}) in ${action.department.displayName} is overdue since ${action.dueDate}",
                        createdAt = timestamp
                    )
                )
            }

            // Critical Action
            if (config.alertOnCriticalActions && action.priority == ActionPriority.CRITICAL) {
                alerts.add(
                    AlertRecord(
                        id = "ALT-ACTION-CRITICAL-${action.id}",
                        type = AlertType.CRITICAL_ACTION,
                        severity = AlertSeverity.CRITICAL,
                        businessDate = businessDate,
                        department = action.department,
                        reference = action.id,
                        message = "Critical action requires immediate attention: '${action.title}' (ID: ${action.id}) in ${action.department.displayName}",
                        createdAt = timestamp
                    )
                )
            }
        }

        return alerts
    }
}
