package com.example.report

import com.example.core.MetricData

/**
 * An export-ready table data structure suitable for CSV, Excel, or PDF conversion.
 */
data class ExportTable(
    val name: String,
    val headers: List<String>,
    val rows: List<List<String>>
) {
    /**
     * Converts table content to sanitized CSV format.
     */
    fun toCsv(): String {
        val sb = StringBuilder()
        sb.append(CsvSanitizer.formatRow(headers)).append("\n")
        for (row in rows) {
            sb.append(CsvSanitizer.formatRow(row)).append("\n")
        }
        return sb.toString()
    }
}

/**
 * Standard Export Document contract.
 */
data class ExportDocument(
    val title: String,
    val generatedAt: String,
    val metadata: Map<String, String> = emptyMap(),
    val tables: List<ExportTable> = emptyList()
) {
    /**
     * Serializes the document into a full sanitized multi-table CSV string.
     */
    fun toCsv(): String {
        val sb = StringBuilder()
        sb.append(CsvSanitizer.formatRow(listOf("TITLE", title))).append("\n")
        sb.append(CsvSanitizer.formatRow(listOf("GENERATED_AT", generatedAt))).append("\n")
        for ((k, v) in metadata) {
            sb.append(CsvSanitizer.formatRow(listOf(k, v))).append("\n")
        }
        sb.append("\n")

        for (table in tables) {
            sb.append(CsvSanitizer.formatRow(listOf("TABLE", table.name))).append("\n")
            sb.append(table.toCsv()).append("\n")
        }
        return sb.toString()
    }
}

/**
 * Converters to transform reports and meeting packs into export documents.
 */
object ExportConverters {

    private fun <T> formatMetric(m: MetricData<T>, suffix: String = ""): String {
        return when (m) {
            is MetricData.Value -> "${m.value}$suffix"
            is MetricData.NoData -> "NO DATA"
        }
    }

    fun fromDailyReport(report: DailyManagementReport): ExportDocument {
        val meta = linkedMapOf(
            "REPORT_TYPE" to "DAILY_MANAGEMENT_REPORT",
            "BUSINESS_DATE" to report.selectedDate.toString(),
            "FILTER_MODEL" to report.filter.model.toString(),
            "FILTER_DEPARTMENT" to report.filter.department.toString()
        )

        val summaryTable = ExportTable(
            name = "EXECUTIVE_SUMMARY",
            headers = listOf("Metric", "Value"),
            rows = listOf(
                listOf("Total Production", formatMetric(report.managementSummary.productionSummary)),
                listOf("Planned Quantity", formatMetric(report.managementSummary.planSummary)),
                listOf("Achievement %", formatMetric(report.managementSummary.achievementPercentage, "%")),
                listOf("Total Rejection", formatMetric(report.managementSummary.rejectionSummary)),
                listOf("Rejection %", formatMetric(report.managementSummary.rejectionPercentage, "%")),
                listOf("Rebuffing Quantity", formatMetric(report.managementSummary.rebuffingSummary)),
                listOf("Opening Stock", formatMetric(report.managementSummary.openingStockSummary)),
                listOf("Closing Stock", formatMetric(report.managementSummary.closingStockSummary)),
                listOf("Open Actions", report.managementSummary.actionSummary.openActions.toString()),
                listOf("Critical Actions", report.managementSummary.actionSummary.criticalActions.toString())
            )
        )

        val deptProdTable = ExportTable(
            name = "PRODUCTION_BY_DEPARTMENT",
            headers = listOf("Department", "Quantity"),
            rows = report.productionByDepartment.map { listOf(it.category, formatMetric(it.metric)) }
        )

        val defectTable = ExportTable(
            name = "DEFECT_INVESTIGATIONS",
            headers = listOf("Defect", "Department", "Quantity", "Root Cause"),
            rows = report.defectInvestigations.map {
                listOf(it.defectName, it.department.displayName, it.quantity.toString(), formatMetric(it.rootCause))
            }
        )

        val actionsTable = ExportTable(
            name = "OPEN_ACTIONS",
            headers = listOf("ID", "Title", "Department", "Priority", "Status", "Assignee"),
            rows = report.openActions.map {
                listOf(it.id, it.title, it.department.displayName, it.priority.name, it.status.name, it.assigneeName ?: "UNASSIGNED")
            }
        )

        return ExportDocument(
            title = "Daily Management Report - ${report.selectedDate}",
            generatedAt = report.selectedDate.toString(),
            metadata = meta,
            tables = listOf(summaryTable, deptProdTable, defectTable, actionsTable)
        )
    }

    fun fromMonthlyReport(report: MonthlyManagementReport): ExportDocument {
        val meta = linkedMapOf(
            "REPORT_TYPE" to "MONTHLY_MANAGEMENT_REPORT",
            "DATE_RANGE" to "${report.fromDate} to ${report.toDate}",
            "FILTER_MODEL" to report.filter.model.toString(),
            "FILTER_DEPARTMENT" to report.filter.department.toString()
        )

        val summaryTable = ExportTable(
            name = "MONTHLY_EXECUTIVE_SUMMARY",
            headers = listOf("Metric", "Value"),
            rows = listOf(
                listOf("Total Production", formatMetric(report.managementSummary.productionSummary)),
                listOf("Total Planned", formatMetric(report.managementSummary.planSummary)),
                listOf("Achievement %", formatMetric(report.managementSummary.achievementPercentage, "%")),
                listOf("Total Rejections", formatMetric(report.managementSummary.rejectionSummary)),
                listOf("Rejection %", formatMetric(report.managementSummary.rejectionPercentage, "%")),
                listOf("Closing Stock", formatMetric(report.managementSummary.closingStockSummary))
            )
        )

        val paretoTable = ExportTable(
            name = "REJECTION_PARETO",
            headers = listOf("Defect", "Quantity", "Contribution %", "Cumulative %"),
            rows = report.rejectionPareto.items.map {
                listOf(it.defectName, it.quantity.toString(), "${it.percentage}%", "${it.cumulativePercentage}%")
            }
        )

        return ExportDocument(
            title = "Monthly Management Report (${report.fromDate} to ${report.toDate})",
            generatedAt = "${report.fromDate} to ${report.toDate}",
            metadata = meta,
            tables = listOf(summaryTable, paretoTable)
        )
    }

    fun fromMeetingPack(pack: ManagementMeetingPack): ExportDocument {
        val meta = linkedMapOf(
            "REPORT_TYPE" to "MANAGEMENT_MEETING_PACK",
            "DATE_RANGE" to "${pack.fromDate} to ${pack.toDate}",
            "BUSINESS_DATE" to pack.businessDate.toString(),
            "GENERATED_AT" to pack.generatedAt
        )

        val kpiTable = ExportTable(
            name = "KEY_PERFORMANCE_INDICATORS",
            headers = listOf("KPI", "Value", "Status"),
            rows = pack.kpis.map { listOf(it.name, it.value, it.status) }
        )

        val exceptionsTable = ExportTable(
            name = "ATTENTION_AND_EXCEPTIONS",
            headers = listOf("Level", "Category", "Message", "Metric"),
            rows = pack.exceptions.map {
                listOf(it.level.name, it.category, it.message, it.metricValue ?: "N/A")
            }
        )

        return ExportDocument(
            title = pack.title,
            generatedAt = pack.generatedAt,
            metadata = meta,
            tables = listOf(kpiTable, exceptionsTable)
        )
    }
}
