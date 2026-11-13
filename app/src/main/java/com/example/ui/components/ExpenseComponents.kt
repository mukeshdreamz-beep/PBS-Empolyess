package com.example.ui.components

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Paid
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Summarize
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.ExpenseEntity
import com.example.domain.AttendanceCalculator
import com.example.domain.AttendanceExportHelper
import com.example.ui.theme.JetBrainsMono
import com.example.ui.theme.LocalAttendanceColors

enum class ExpenseDateFilterMode(val labelRes: Int) {
    ALL(R.string.filter_all_dates),
    TODAY(R.string.filter_today),
    THIS_MONTH(R.string.filter_this_month),
    CUSTOM_RANGE(R.string.filter_date_range)
}

enum class ExpenseStatusFilter(val label: String) {
    ALL("All"),
    PENDING("Pending"),
    APPROVED("Approved"),
    CLAIMED("Claimed"),
    UNCLAIMED("Unclaimed"),
    REJECTED("Rejected")
}

fun filterExpenses(
    expenses: List<ExpenseEntity>,
    dateMode: ExpenseDateFilterMode,
    startDate: String,
    endDate: String,
    statusFilter: ExpenseStatusFilter,
    searchQuery: String = ""
): List<ExpenseEntity> {
    val today = AttendanceCalculator.todayDateString()
    val currentMonth = AttendanceCalculator.currentYearMonth()
    val startMillis = AttendanceCalculator.parseDateMillis(startDate)
    val endMillis = AttendanceCalculator.parseDateMillis(endDate)
    val minMillis = if (startMillis != null && endMillis != null) minOf(startMillis, endMillis) else null
    val maxMillis = if (startMillis != null && endMillis != null) maxOf(startMillis, endMillis) else null
    val q = searchQuery.trim().lowercase()

    return expenses.filter { exp ->
        val matchesDate = when (dateMode) {
            ExpenseDateFilterMode.ALL -> true
            ExpenseDateFilterMode.TODAY -> exp.date == today
            ExpenseDateFilterMode.THIS_MONTH ->
                AttendanceCalculator.extractYearMonth(exp.date) == currentMonth
            ExpenseDateFilterMode.CUSTOM_RANGE -> {
                val expMillis = AttendanceCalculator.parseDateMillis(exp.date)
                if (expMillis != null && minMillis != null && maxMillis != null) {
                    expMillis in minMillis..maxMillis
                } else {
                    true
                }
            }
        }

        val matchesStatus = when (statusFilter) {
            ExpenseStatusFilter.ALL -> true
            ExpenseStatusFilter.PENDING -> exp.status == "PENDING"
            ExpenseStatusFilter.APPROVED -> exp.status == "APPROVED"
            ExpenseStatusFilter.CLAIMED -> exp.status == "APPROVED" && exp.isClaimed
            ExpenseStatusFilter.UNCLAIMED -> exp.status == "APPROVED" && !exp.isClaimed
            ExpenseStatusFilter.REJECTED -> exp.status == "REJECTED"
        }

        val matchesQuery = q.isEmpty() ||
            exp.title.lowercase().contains(q) ||
            exp.employeeName.lowercase().contains(q) ||
            exp.site.lowercase().contains(q) ||
            exp.category.lowercase().contains(q) ||
            exp.date.contains(q) ||
            exp.note.lowercase().contains(q)

        matchesDate && matchesStatus && matchesQuery
    }
}

fun buildExpensePeriodLabel(
    dateMode: ExpenseDateFilterMode,
    startDate: String,
    endDate: String
): String {
    return when (dateMode) {
        ExpenseDateFilterMode.ALL -> "All Dates"
        ExpenseDateFilterMode.TODAY -> "Today (${AttendanceCalculator.todayDateString()})"
        ExpenseDateFilterMode.THIS_MONTH -> AttendanceCalculator.currentYearMonth().displayLabel()
        ExpenseDateFilterMode.CUSTOM_RANGE -> "$startDate to $endDate"
    }
}

@Composable
fun ExpenseKpiSummaryGrid(
    expenses: List<ExpenseEntity>,
    modifier: Modifier = Modifier
) {
    val semantic = LocalAttendanceColors.current
    val totalSum = remember(expenses) { expenses.sumOf { it.amount } }
    val approvedSum = remember(expenses) {
        expenses.filter { it.status == "APPROVED" }.sumOf { it.amount }
    }
    val claimedSum = remember(expenses) {
        expenses.filter { it.status == "APPROVED" && it.isClaimed }.sumOf { it.amount }
    }
    val pendingSum = remember(expenses) {
        expenses.filter { it.status == "PENDING" }.sumOf { it.amount }
    }
    val unclaimedSum = (approvedSum - claimedSum).coerceAtLeast(0.0)

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            ExpenseMetricTile(
                label = stringResource(R.string.total_expense_kpi),
                value = "₹${AttendanceExportHelper.formatMoneyPlain(totalSum)}",
                subtitle = "${expenses.size} entries",
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
                borderColor = MaterialTheme.colorScheme.outlineVariant,
                valueColor = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier
                    .weight(1f)
                    .testTag("kpi_total_expense")
            )
            ExpenseMetricTile(
                label = stringResource(R.string.approved_expense_kpi),
                value = "₹${AttendanceExportHelper.formatMoneyPlain(approvedSum)}",
                subtitle = "${expenses.count { it.status == "APPROVED" }} approved",
                containerColor = semantic.fullDayContainer,
                borderColor = semantic.fullDayBorder,
                valueColor = semantic.fullDayAccent,
                modifier = Modifier
                    .weight(1f)
                    .testTag("kpi_approved_expense")
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            ExpenseMetricTile(
                label = stringResource(R.string.claimed_expense_kpi),
                value = "₹${AttendanceExportHelper.formatMoneyPlain(claimedSum)}",
                subtitle = stringResource(
                    R.string.unclaimed_balance_label,
                    AttendanceExportHelper.formatMoneyPlain(unclaimedSum)
                ),
                containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f),
                borderColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.35f),
                valueColor = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .weight(1f)
                    .testTag("kpi_claimed_expense")
            )
            ExpenseMetricTile(
                label = stringResource(R.string.pending_expense_kpi),
                value = "₹${AttendanceExportHelper.formatMoneyPlain(pendingSum)}",
                subtitle = "${expenses.count { it.status == "PENDING" }} pending",
                containerColor = semantic.overtimeContainer,
                borderColor = semantic.overtimeBorder,
                valueColor = semantic.overtimeAccent,
                modifier = Modifier
                    .weight(1f)
                    .testTag("kpi_pending_expense")
            )
        }
    }
}

@Composable
private fun ExpenseMetricTile(
    label: String,
    value: String,
    subtitle: String,
    containerColor: Color,
    borderColor: Color,
    valueColor: Color,
    modifier: Modifier = Modifier
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = containerColor),
        border = BorderStroke(1.dp, borderColor),
        shape = MaterialTheme.shapes.medium,
        modifier = modifier
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = value,
                style = MaterialTheme.typography.titleMedium.copy(fontFamily = JetBrainsMono),
                fontWeight = FontWeight.Bold,
                color = valueColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
fun ExpenseFilterAndDownloadPanel(
    filteredExpenses: List<ExpenseEntity>,
    dateMode: ExpenseDateFilterMode,
    onDateModeChange: (ExpenseDateFilterMode) -> Unit,
    startDate: String,
    endDate: String,
    onStartDateSelected: (String) -> Unit,
    onEndDateSelected: (String) -> Unit,
    statusFilter: ExpenseStatusFilter,
    onStatusFilterChange: (ExpenseStatusFilter) -> Unit,
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    filePrefix: String,
    shareSubject: String,
    onShowFeedback: (messageRes: Int, isError: Boolean) -> Unit,
    modifier: Modifier = Modifier,
    showSearch: Boolean = true
) {
    val context = LocalContext.current
    var pickingDateTarget by remember { mutableStateOf<String?>(null) }
    val periodLabel = remember(dateMode, startDate, endDate) {
        buildExpensePeriodLabel(dateMode, startDate, endDate)
    }

    val dateWiseCreateLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("text/csv")
    ) { uri ->
        if (uri != null) {
            val csv = AttendanceExportHelper.buildExpensesDateWiseCsv(filteredExpenses)
            if (AttendanceExportHelper.writeCsvToUri(context, uri, csv)) {
                onShowFeedback(R.string.msg_expense_downloaded, false)
            } else {
                onShowFeedback(R.string.msg_export_failed, true)
            }
        }
    }

    val reportWiseCreateLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("text/csv")
    ) { uri ->
        if (uri != null) {
            val csv = AttendanceExportHelper.buildExpensesReportCsv(filteredExpenses, periodLabel)
            if (AttendanceExportHelper.writeCsvToUri(context, uri, csv)) {
                onShowFeedback(R.string.msg_expense_downloaded, false)
            } else {
                onShowFeedback(R.string.msg_export_failed, true)
            }
        }
    }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // Date filter row
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            ExpenseDateFilterMode.entries.forEach { mode ->
                FilterChip(
                    selected = dateMode == mode,
                    onClick = { onDateModeChange(mode) },
                    label = { Text(stringResource(mode.labelRes)) },
                    modifier = Modifier.testTag("expense_date_filter_${mode.name.lowercase()}")
                )
            }
        }

        if (dateMode == ExpenseDateFilterMode.CUSTOM_RANGE) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = { pickingDateTarget = "start" },
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp),
                    modifier = Modifier
                        .weight(1f)
                        .minimumInteractiveComponentSize()
                        .testTag("expense_range_start_button")
                ) {
                    Icon(Icons.Filled.CalendarMonth, null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = stringResource(R.string.expense_from_date, startDate),
                        style = MaterialTheme.typography.labelMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                OutlinedButton(
                    onClick = { pickingDateTarget = "end" },
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp),
                    modifier = Modifier
                        .weight(1f)
                        .minimumInteractiveComponentSize()
                        .testTag("expense_range_end_button")
                ) {
                    Icon(Icons.Filled.CalendarMonth, null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = stringResource(R.string.expense_to_date, endDate),
                        style = MaterialTheme.typography.labelMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }

        // Status filter row
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            ExpenseStatusFilter.entries.forEach { status ->
                FilterChip(
                    selected = statusFilter == status,
                    onClick = { onStatusFilterChange(status) },
                    label = { Text(status.label) },
                    modifier = Modifier.testTag("expense_status_filter_${status.name.lowercase()}")
                )
            }
        }

        if (showSearch) {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = onSearchQueryChange,
                placeholder = { Text(stringResource(R.string.search_expenses_placeholder)) },
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { onSearchQueryChange("") }) {
                            Icon(Icons.Filled.Clear, contentDescription = "Clear search")
                        }
                    }
                },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("expense_search_input")
            )
        }

        // Download buttons row (Date-wise CSV, Report-wise CSV, Share)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            val todaySlug = AttendanceCalculator.todayDateString().replace("/", "-")
            FilledTonalButton(
                onClick = {
                    dateWiseCreateLauncher.launch("${filePrefix}_datewise_$todaySlug.csv")
                },
                enabled = filteredExpenses.isNotEmpty(),
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp),
                modifier = Modifier
                    .weight(1f)
                    .minimumInteractiveComponentSize()
                    .testTag("download_datewise_csv_button")
            ) {
                Icon(Icons.Filled.Download, null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(4.dp))
                Text(
                    text = stringResource(R.string.download_date_wise_csv),
                    style = MaterialTheme.typography.labelMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            FilledTonalButton(
                onClick = {
                    reportWiseCreateLauncher.launch("${filePrefix}_report_$todaySlug.csv")
                },
                enabled = filteredExpenses.isNotEmpty(),
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp),
                modifier = Modifier
                    .weight(1f)
                    .minimumInteractiveComponentSize()
                    .testTag("download_reportwise_csv_button")
            ) {
                Icon(Icons.Filled.Summarize, null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(4.dp))
                Text(
                    text = stringResource(R.string.download_report_wise_csv),
                    style = MaterialTheme.typography.labelMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            OutlinedButton(
                onClick = {
                    val summary = AttendanceExportHelper.buildExpensesSummaryText(
                        expenses = filteredExpenses,
                        periodLabel = periodLabel
                    )
                    AttendanceExportHelper.sharePlainText(context, shareSubject, summary)
                },
                enabled = filteredExpenses.isNotEmpty(),
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp),
                modifier = Modifier
                    .minimumInteractiveComponentSize()
                    .testTag("share_expenses_button")
            ) {
                Icon(
                    imageVector = Icons.Filled.Share,
                    contentDescription = stringResource(R.string.share_summary_button),
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }

    pickingDateTarget?.let { target ->
        AttendanceDatePickerModal(
            currentDateString = if (target == "start") startDate else endDate,
            onDismiss = { pickingDateTarget = null },
            onDateSelected = { millis ->
                val formatted = AttendanceCalculator.formatDateMillis(millis)
                if (target == "start") {
                    onStartDateSelected(formatted)
                } else {
                    onEndDateSelected(formatted)
                }
                pickingDateTarget = null
            }
        )
    }
}

@Composable
fun ExpenseHistoryCard(
    expense: ExpenseEntity,
    isAdmin: Boolean,
    onApprove: () -> Unit = {},
    onReject: () -> Unit = {},
    onToggleClaimed: (Boolean) -> Unit = {},
    onDelete: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val semantic = LocalAttendanceColors.current
    val (badgeBg, badgeFg) = when (expense.status) {
        "APPROVED" -> semantic.fullDayContainer to semantic.onFullDayContainer
        "REJECTED" -> semantic.shortDayContainer to semantic.onShortDayContainer
        else -> semantic.overtimeContainer to semantic.onOvertimeContainer
    }

    Card(
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = modifier
            .fillMaxWidth()
            .testTag("expense_card_${expense.id}")
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = expense.title,
                        style = MaterialTheme.typography.titleMedium
                    )
                    val who = if (expense.employeeName.isNotBlank()) "${expense.employeeName} · " else ""
                    Text(
                        text = "$who${expense.site} · ${expense.category} · ${expense.date}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Spacer(Modifier.width(8.dp))
                Text(
                    text = "₹${AttendanceExportHelper.formatMoneyPlain(expense.amount)}",
                    style = MaterialTheme.typography.titleMedium.copy(fontFamily = JetBrainsMono),
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            if (expense.note.isNotBlank()) {
                Text(
                    text = expense.note,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // Status & Claim badges row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        color = badgeBg,
                        contentColor = badgeFg,
                        shape = RoundedCornerShape(50)
                    ) {
                        Text(
                            text = expense.status,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                        )
                    }

                    if (expense.status == "APPROVED") {
                        val claimBg = if (expense.isClaimed) {
                            MaterialTheme.colorScheme.primaryContainer
                        } else {
                            MaterialTheme.colorScheme.surfaceVariant
                        }
                        val claimFg = if (expense.isClaimed) {
                            MaterialTheme.colorScheme.onPrimaryContainer
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        }
                        Surface(
                            color = claimBg,
                            contentColor = claimFg,
                            shape = RoundedCornerShape(50)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                                modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp)
                            ) {
                                if (expense.isClaimed) {
                                    Icon(
                                        imageVector = Icons.Filled.CheckCircle,
                                        contentDescription = null,
                                        modifier = Modifier.size(12.dp)
                                    )
                                }
                                Text(
                                    text = stringResource(
                                        if (expense.isClaimed) R.string.claimed_badge else R.string.unclaimed_badge
                                    ),
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }

                if (isAdmin && onDelete != null) {
                    IconButton(
                        onClick = onDelete,
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("delete_expense_button_${expense.id}")
                    ) {
                        Icon(
                            Icons.Filled.DeleteOutline,
                            stringResource(R.string.delete_record_action),
                            tint = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }

            // Admin-only approval & claim controls
            if (isAdmin && (expense.status == "PENDING" || expense.status == "APPROVED")) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (expense.status == "PENDING") {
                        FilledTonalButton(
                            onClick = onApprove,
                            colors = ButtonDefaults.filledTonalButtonColors(
                                containerColor = semantic.fullDayContainer,
                                contentColor = semantic.onFullDayContainer
                            ),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                            modifier = Modifier
                                .weight(1f)
                                .minimumInteractiveComponentSize()
                                .testTag("approve_expense_button_${expense.id}")
                        ) {
                            Icon(Icons.Filled.Check, null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text(stringResource(R.string.approve_action))
                        }
                        OutlinedButton(
                            onClick = onReject,
                            colors = ButtonDefaults.outlinedButtonColors(
                                contentColor = semantic.shortDayAccent
                            ),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                            modifier = Modifier
                                .weight(1f)
                                .minimumInteractiveComponentSize()
                                .testTag("reject_expense_button_${expense.id}")
                        ) {
                            Icon(Icons.Filled.Close, null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text(stringResource(R.string.reject_action))
                        }
                    } else if (expense.status == "APPROVED") {
                        if (!expense.isClaimed) {
                            FilledTonalButton(
                                onClick = { onToggleClaimed(true) },
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                                modifier = Modifier
                                    .minimumInteractiveComponentSize()
                                    .testTag("mark_claimed_button_${expense.id}")
                            ) {
                                Icon(Icons.Filled.Paid, null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(6.dp))
                                Text(stringResource(R.string.mark_claimed_action))
                            }
                        } else {
                            OutlinedButton(
                                onClick = { onToggleClaimed(false) },
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                                modifier = Modifier
                                    .minimumInteractiveComponentSize()
                                    .testTag("unmark_claimed_button_${expense.id}")
                            ) {
                                Icon(Icons.Filled.CheckCircle, null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("Claimed (Undo)")
                            }
                        }
                    }
                }
            }
        }
    }
}
