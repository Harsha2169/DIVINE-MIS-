package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Assessment
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Checklist
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Factory
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Inventory
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.ReportProblem
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.example.action.ActionRecord
import com.example.alert.AlertRecord
import com.example.model.PlanRecord
import com.example.model.ProductionRecord
import com.example.model.RejectionRecord
import com.example.model.StockRecord
import com.example.report.ManagementMeetingPack
import com.example.ui.state.ActionTrackerScreenState
import com.example.ui.state.AlertsScreenState
import com.example.ui.state.PlanningScreenState
import com.example.ui.state.ProductionScreenState
import com.example.ui.state.RejectionScreenState
import com.example.ui.state.ReportsScreenState
import com.example.ui.state.StockScreenState

@Composable
fun LoginScreen(
    onLoginClick: (String, String) -> Unit,
    modifier: Modifier = Modifier,
    isLoading: Boolean = false,
    errorMessage: String? = null
) {
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(24.dp)
            .testTag("login_screen"),
        contentAlignment = Alignment.Center
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .testTag("login_card"),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Lock,
                    contentDescription = "Login Security Icon",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(48.dp)
                )
                Text(
                    text = "Divine Stamp MIS Login",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                OutlinedTextField(
                    value = email,
                    onValueChange = { email = it },
                    label = { Text("Email") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("email_input"),
                    singleLine = true
                )
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = { Text("Password") },
                    visualTransformation = PasswordVisualTransformation(),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("password_input"),
                    singleLine = true
                )
                if (errorMessage != null) {
                    Text(
                        text = errorMessage,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.testTag("login_error_text")
                    )
                }
                Button(
                    onClick = { onLoginClick(email, password) },
                    enabled = !isLoading && email.isNotBlank(),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .testTag("login_submit_button")
                ) {
                    if (isLoading) {
                        CircularProgressIndicator(
                            color = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.size(24.dp)
                        )
                    } else {
                        Text("Sign In")
                    }
                }
            }
        }
    }
}

@Composable
fun ProductionScreen(
    state: ProductionScreenState,
    modifier: Modifier = Modifier,
    onAddRecordClick: () -> Unit = {}
) {
    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(16.dp)
            .testTag("production_screen"),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            ScreenHeader(title = "Production Records", subtitle = "Date: ${state.filter.fromDate} to ${state.filter.toDate}")
        }
        if (state.isLoading) {
            item { LoadingStateView() }
        } else if (state.isNoData) {
            item { NoDataStateView(message = "No production data found for the selected filter.") }
        } else {
            items(state.records) { record ->
                ProductionCard(record = record)
            }
        }
        if (state.canAddRecord) {
            item {
                Button(
                    onClick = onAddRecordClick,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .testTag("add_production_button")
                ) {
                    Text("Add Production Record")
                }
            }
        }
    }
}

@Composable
fun RejectionScreen(
    state: RejectionScreenState,
    modifier: Modifier = Modifier,
    onAddRecordClick: () -> Unit = {}
) {
    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(16.dp)
            .testTag("rejection_screen"),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            ScreenHeader(title = "Rejection Records", subtitle = "Total Quantity: ${state.totalRejections}")
        }
        if (state.isLoading) {
            item { LoadingStateView() }
        } else if (state.isNoData) {
            item { NoDataStateView(message = "No rejection data found for the selected filter.") }
        } else {
            items(state.records) { record ->
                RejectionCard(record = record)
            }
        }
        if (state.canAddRecord) {
            item {
                Button(
                    onClick = onAddRecordClick,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .testTag("add_rejection_button")
                ) {
                    Text("Record Rejection")
                }
            }
        }
    }
}

@Composable
fun StockScreen(
    state: StockScreenState,
    modifier: Modifier = Modifier
) {
    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(16.dp)
            .testTag("stock_screen"),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            ScreenHeader(title = "Stock & Inventory", subtitle = "Date: ${state.filter.fromDate} to ${state.filter.toDate}")
        }
        if (state.isLoading) {
            item { LoadingStateView() }
        } else if (state.isNoData) {
            item { NoDataStateView(message = "No stock inventory records found.") }
        } else {
            items(state.records) { record ->
                StockCard(record = record)
            }
        }
    }
}

@Composable
fun PlanningScreen(
    state: PlanningScreenState,
    modifier: Modifier = Modifier
) {
    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(16.dp)
            .testTag("planning_screen"),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            ScreenHeader(title = "Production Planning", subtitle = "Daily Plans: ${state.dailyPlans.size}")
        }
        if (state.isLoading) {
            item { LoadingStateView() }
        } else if (state.isNoData) {
            item { NoDataStateView(message = "No operational plan data recorded.") }
        } else {
            items(state.dailyPlans) { plan ->
                PlanCard(plan = plan)
            }
        }
    }
}

@Composable
fun ActionTrackerScreen(
    state: ActionTrackerScreenState,
    modifier: Modifier = Modifier
) {
    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(16.dp)
            .testTag("action_tracker_screen"),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            ScreenHeader(title = "Action Tracker", subtitle = "Total Open/Assigned: ${state.actions.size}")
        }
        if (state.isLoading) {
            item { LoadingStateView() }
        } else if (state.isNoData) {
            item { NoDataStateView(message = "No action items matching filter criteria.") }
        } else {
            items(state.actions) { action ->
                ActionCard(action = action)
            }
        }
    }
}

@Composable
fun AlertsScreen(
    state: AlertsScreenState,
    modifier: Modifier = Modifier,
    onAcknowledgeClick: (String) -> Unit = {}
) {
    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(16.dp)
            .testTag("alerts_screen"),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            ScreenHeader(title = "Alerts & Notifications", subtitle = "Active Alerts: ${state.alerts.size}")
        }
        if (state.isLoading) {
            item { LoadingStateView() }
        } else if (state.isNoData) {
            item { NoDataStateView(message = "No active alerts. System running within normal parameters.") }
        } else {
            items(state.alerts) { alert ->
                AlertCard(alert = alert, canModify = state.canModifyAlert, onAcknowledge = onAcknowledgeClick)
            }
        }
    }
}

@Composable
fun ReportsScreen(
    state: ReportsScreenState,
    modifier: Modifier = Modifier,
    onExportClick: () -> Unit = {}
) {
    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(16.dp)
            .testTag("reports_screen"),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            ScreenHeader(title = "Meeting Pack & Reports", subtitle = "Executive summary and analysis")
        }
        if (state.isLoading) {
            item { LoadingStateView() }
        } else if (state.isNoData || state.meetingPack == null) {
            item { NoDataStateView(message = "No meeting pack data available for current date range.") }
        } else {
            item {
                MeetingPackSummaryCard(pack = state.meetingPack)
            }
            if (state.canExport) {
                item {
                    Button(
                        onClick = onExportClick,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .testTag("export_meeting_pack_button")
                    ) {
                        Text("Export Meeting Pack (CSV / Excel)")
                    }
                }
            }
        }
    }
}

// -----------------------------------------------------------------------------
// Component Cards
// -----------------------------------------------------------------------------

@Composable
fun ScreenHeader(title: String, subtitle: String) {
    Card(
        modifier = Modifier.fillMaxWidth().testTag("screen_header_card"),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(text = title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Text(text = subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onPrimaryContainer)
        }
    }
}

@Composable
fun ProductionCard(record: ProductionRecord) {
    Card(
        modifier = Modifier.fillMaxWidth().testTag("production_item_${record.id}"),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(text = "${record.department.displayName} — Model ${record.model.displayName}", fontWeight = FontWeight.Bold)
            Text(text = "Produced: ${record.quantity.value} units | Date: ${record.date}", style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
fun RejectionCard(record: RejectionRecord) {
    Card(
        modifier = Modifier.fillMaxWidth().testTag("rejection_item_${record.id}"),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(text = "${record.defectName} (${record.quantity} items)", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onErrorContainer)
            Text(text = "${record.department.displayName} | Source: ${record.source.displayName} | Side: ${record.side.name}", style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
fun StockCard(record: StockRecord) {
    Card(
        modifier = Modifier.fillMaxWidth().testTag("stock_item_${record.id}"),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(text = "Model: ${record.model.displayName}", fontWeight = FontWeight.Bold)
            Text(text = "Opening: ${record.openingQuantity} | Closing: ${record.closingQuantity} | Date: ${record.date}")
        }
    }
}

@Composable
fun PlanCard(plan: PlanRecord) {
    Card(
        modifier = Modifier.fillMaxWidth().testTag("plan_item_${plan.id}"),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(text = "${plan.department.displayName} — Model ${plan.model.displayName}", fontWeight = FontWeight.Bold)
            Text(text = "Planned Quantity: ${plan.plannedQuantity} | Date: ${plan.date}")
        }
    }
}

@Composable
fun ActionCard(action: ActionRecord) {
    Card(
        modifier = Modifier.fillMaxWidth().testTag("action_item_${action.id}"),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(text = action.title, fontWeight = FontWeight.Bold)
                Text(text = action.priority.name, color = MaterialTheme.colorScheme.primary)
            }
            Text(text = action.description, style = MaterialTheme.typography.bodyMedium)
            Text(text = "Status: ${action.status.name} | Dept: ${action.department.displayName}", style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
fun AlertCard(alert: AlertRecord, canModify: Boolean, onAcknowledge: (String) -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().testTag("alert_item_${alert.id}"),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(text = alert.type.displayName, fontWeight = FontWeight.Bold)
                Text(text = alert.severity.name, color = MaterialTheme.colorScheme.error)
            }
            Text(text = alert.message, style = MaterialTheme.typography.bodyMedium)
            Text(text = "Status: ${alert.status.name} | Date: ${alert.businessDate}", style = MaterialTheme.typography.bodySmall)
            if (canModify && alert.status == com.example.alert.AlertStatus.OPEN) {
                Button(
                    onClick = { onAcknowledge(alert.id) },
                    modifier = Modifier.testTag("ack_button_${alert.id}")
                ) {
                    Text("Acknowledge Alert")
                }
            }
        }
    }
}

@Composable
fun MeetingPackSummaryCard(pack: ManagementMeetingPack) {
    Card(
        modifier = Modifier.fillMaxWidth().testTag("meeting_pack_summary_card"),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(text = pack.title, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
            Text(text = "Overall Health: ${pack.kpis.size} KPIs tracked")
            Text(text = "Total Production: ${pack.productionSection.totalProduction}")
            Text(text = "Total Rejections: ${pack.rejectionSection.totalRejections}")
            Text(text = "Open Actions: ${pack.actionSection.openActions.size}")
        }
    }
}

@Composable
fun LoadingStateView() {
    Box(
        modifier = Modifier.fillMaxWidth().padding(32.dp).testTag("loading_view"),
        contentAlignment = Alignment.Center
    ) {
        CircularProgressIndicator()
    }
}

@Composable
fun NoDataStateView(message: String) {
    Card(
        modifier = Modifier.fillMaxWidth().testTag("no_data_view"),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Row(
            modifier = Modifier.padding(20.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Icon(Icons.Default.Info, contentDescription = "No Data", tint = MaterialTheme.colorScheme.outline)
            Text(text = message, style = MaterialTheme.typography.bodyMedium)
        }
    }
}
