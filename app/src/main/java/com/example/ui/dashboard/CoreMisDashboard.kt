package com.example.ui.dashboard

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.Assessment
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Factory
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Inventory
import androidx.compose.material.icons.filled.ReportProblem
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.analytics.DashboardKpis
import com.example.analytics.DashboardState
import com.example.analytics.DashboardViewModel
import com.example.analytics.ParetoItem
import com.example.core.MetricData

@Composable
fun CoreMisDashboardScreen(
    viewModel: DashboardViewModel,
    modifier: Modifier = Modifier
) {
    val state by viewModel.uiState.collectAsState()
    CoreMisDashboard(state = state, modifier = modifier)
}

@Composable
fun CoreMisDashboard(
    state: DashboardState,
    modifier: Modifier = Modifier
) {
    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(16.dp)
            .testTag("dashboard_screen"),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            ExecutiveHeader(state = state)
        }

        item {
            FilterSummaryCard(state = state)
        }

        when {
            state.isLoading -> {
                item {
                    LoadingView()
                }
            }
            state.errorMessage != null -> {
                item {
                    ErrorView(message = state.errorMessage)
                }
            }
            state.isNoData -> {
                item {
                    NoDataView()
                }
            }
            state.hasData -> {
                item {
                    KpiSummarySection(kpis = state.kpis)
                }

                item {
                    ProductionOverviewSection(state = state)
                }

                item {
                    RejectionParetoSection(state = state)
                }

                item {
                    StockOverviewSection(state = state)
                }
            }
        }
    }
}

@Composable
private fun ExecutiveHeader(state: DashboardState) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("executive_header_card")
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = Color.White,
                    shadowElevation = 2.dp,
                    modifier = Modifier
                        .height(48.dp)
                        .width(96.dp)
                ) {
                    Image(
                        painter = painterResource(id = R.drawable.dspl_logo),
                        contentDescription = "Official DSPL Logo",
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(4.dp)
                            .testTag("dashboard_brand_logo"),
                        contentScale = ContentScale.Fit
                    )
                }
                Column {
                    Text(
                        text = "DIVINE STAMP PVT LTD",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.testTag("dashboard_company_title")
                    )
                    Text(
                        text = "ADDING EXCELLENCE • Executive Control Tower",
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FilterSummaryCard(state: DashboardState) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("filter_summary_card")
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.FilterList,
                    contentDescription = "Active Filters",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "ACTIVE FILTERS",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                FilterChipTag(label = "Date: ${state.activeFilters.fromDate} to ${state.activeFilters.toDate}")
                FilterChipTag(label = "Model: ${state.activeFilters.model}")
                FilterChipTag(label = "Dept: ${state.activeFilters.department}")
                FilterChipTag(label = "Source: ${state.activeFilters.rejectionSource}")
            }
        }
    }
}

@Composable
private fun FilterChipTag(label: String) {
    Surface(
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(6.dp)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
        )
    }
}

@Composable
private fun KpiSummarySection(kpis: DashboardKpis) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("kpi_section"),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text(
            text = "Executive Key Performance Indicators",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            KpiMetricCard(
                title = "Total Production",
                metric = kpis.totalProduction,
                icon = Icons.AutoMirrored.Filled.TrendingUp,
                modifier = Modifier
                    .weight(1f)
                    .testTag("kpi_total_production")
            )
            KpiMetricCard(
                title = "Planned Quantity",
                metric = kpis.totalPlanned,
                icon = Icons.Default.Assessment,
                modifier = Modifier
                    .weight(1f)
                    .testTag("kpi_total_planned")
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            KpiMetricCard(
                title = "Achievement %",
                metric = kpis.achievementPercentage,
                icon = Icons.Default.CheckCircle,
                suffix = "%",
                modifier = Modifier
                    .weight(1f)
                    .testTag("kpi_achievement")
            )
            KpiMetricCard(
                title = "Total Rejections",
                metric = kpis.totalRejections,
                icon = Icons.Default.ReportProblem,
                modifier = Modifier
                    .weight(1f)
                    .testTag("kpi_total_rejections")
            )
            KpiMetricCard(
                title = "Closing Stock",
                metric = kpis.totalClosingStock,
                icon = Icons.Default.Inventory,
                modifier = Modifier
                    .weight(1f)
                    .testTag("kpi_closing_stock")
            )
        }
    }
}

@Composable
private fun KpiMetricCard(
    title: String,
    metric: MetricData<Number>,
    icon: ImageVector,
    suffix: String = "",
    modifier: Modifier = Modifier
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        shape = RoundedCornerShape(12.dp),
        modifier = modifier
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(16.dp)
                )
            }

            Spacer(modifier = Modifier.height(6.dp))

            when (metric) {
                is MetricData.Value -> {
                    val formatted = if (metric.value is Double) {
                        String.format("%.1f", metric.value)
                    } else {
                        metric.value.toString()
                    }
                    Text(
                        text = "$formatted$suffix",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                is MetricData.NoData -> {
                    Surface(
                        color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f),
                        shape = RoundedCornerShape(4.dp)
                    ) {
                        Text(
                            text = "NO DATA",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ProductionOverviewSection(state: DashboardState) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("production_section")
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "Production Flow & Department Actuals",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(10.dp))

            state.productionFlow?.points?.forEach { step ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "${step.sequenceNumber}. ${step.department.displayName}",
                        style = MaterialTheme.typography.bodySmall
                    )
                    Text(
                        text = when (val q = step.quantity) {
                            is MetricData.Value -> "${q.value}"
                            is MetricData.NoData -> "NO DATA"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }
    }
}

@Composable
private fun RejectionParetoSection(state: DashboardState) {
    val pareto = state.rejectionPareto ?: return
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("pareto_section")
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Rejection Pareto Breakdown",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Total: ${pareto.totalRejectionQuantity}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.error,
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            if (pareto.items.isEmpty()) {
                Text(
                    text = "No rejections recorded for this period.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                pareto.items.take(5).forEach { item ->
                    ParetoRow(item = item)
                }
            }
        }
    }
}

@Composable
private fun ParetoRow(item: ParetoItem) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = item.defectName,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.weight(1.5f)
        )
        Text(
            text = "${item.quantity} pcs",
            style = MaterialTheme.typography.bodySmall,
            textAlign = TextAlign.End,
            modifier = Modifier.weight(1f)
        )
        Text(
            text = "${item.percentage}%",
            style = MaterialTheme.typography.bodySmall,
            textAlign = TextAlign.End,
            modifier = Modifier.weight(1f)
        )
        Text(
            text = "${item.cumulativePercentage}%",
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.End,
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun StockOverviewSection(state: DashboardState) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("stock_section")
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "Model-wise Stock Inventory",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(10.dp))

            state.modelWiseStock?.points?.forEach { point ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = point.category,
                        style = MaterialTheme.typography.bodySmall
                    )
                    Text(
                        text = when (val m = point.metric) {
                            is MetricData.Value -> "${m.value} pcs"
                            is MetricData.NoData -> "NO DATA"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }
    }
}

@Composable
private fun LoadingView() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(200.dp)
            .testTag("loading_indicator"),
        contentAlignment = Alignment.Center
    ) {
        CircularProgressIndicator()
    }
}

@Composable
private fun ErrorView(message: String) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("error_card")
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.ErrorOutline,
                contentDescription = "Error",
                tint = MaterialTheme.colorScheme.onErrorContainer
            )
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onErrorContainer
            )
        }
    }
}

@Composable
private fun NoDataView() {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("no_data_card")
    ) {
        Row(
            modifier = Modifier.padding(20.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.Info,
                contentDescription = "No Data",
                tint = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column {
                Text(
                    text = "NO DATA AVAILABLE",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "No records matched the selected date range and filter criteria.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                )
            }
        }
    }
}
