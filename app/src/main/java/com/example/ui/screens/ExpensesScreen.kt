package com.example.ui.screens

import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.EmployeeEntity
import com.example.domain.AttendanceCalculator
import com.example.ui.AttendanceUiState
import com.example.ui.components.AttendanceDatePickerModal
import com.example.ui.components.ExpenseDateFilterMode
import com.example.ui.components.ExpenseFilterAndDownloadPanel
import com.example.ui.components.ExpenseHistoryCard
import com.example.ui.components.ExpenseKpiSummaryGrid
import com.example.ui.components.ExpenseStatusFilter
import com.example.ui.components.filterExpenses

@Composable
fun ExpensesScreen(
    uiState: AttendanceUiState,
    onAddExpense: (
        employeeId: Long?,
        site: String,
        title: String,
        category: String,
        amount: Double,
        date: String,
        note: String,
        autoApprove: Boolean
    ) -> Unit,
    onSetExpenseStatus: (Long, Boolean) -> Unit,
    onSetExpenseClaimed: (Long, Boolean) -> Unit,
    onDeleteExpense: (Long) -> Unit,
    onShowFeedback: (messageRes: Int, isError: Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    var showAddDialog by remember { mutableStateOf(false) }
    var dateMode by remember { mutableStateOf(ExpenseDateFilterMode.ALL) }
    var startDate by remember { mutableStateOf(AttendanceCalculator.yesterdayDateString()) }
    var endDate by remember { mutableStateOf(AttendanceCalculator.todayDateString()) }
    var statusFilter by remember { mutableStateOf(ExpenseStatusFilter.ALL) }
    var searchQuery by remember { mutableStateOf("") }

    val filteredExpenses = remember(
        uiState.expenses,
        dateMode,
        startDate,
        endDate,
        statusFilter,
        searchQuery
    ) {
        filterExpenses(
            expenses = uiState.expenses,
            dateMode = dateMode,
            startDate = startDate,
            endDate = endDate,
            statusFilter = statusFilter,
            searchQuery = searchQuery
        )
    }

    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.TopCenter
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = 840.dp)
                .fillMaxSize()
        ) {
            Surface(
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 1.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = stringResource(R.string.expenses_title),
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.weight(1f)
                    )
                    Button(
                        onClick = { showAddDialog = true },
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("add_expense_button")
                    ) {
                        Icon(Icons.Filled.Add, null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(stringResource(R.string.add_expense_button))
                    }
                }
            }

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                item(key = "kpi_grid") {
                    ExpenseKpiSummaryGrid(expenses = filteredExpenses)
                }

                item(key = "filters_and_download") {
                    ExpenseFilterAndDownloadPanel(
                        filteredExpenses = filteredExpenses,
                        dateMode = dateMode,
                        onDateModeChange = { dateMode = it },
                        startDate = startDate,
                        endDate = endDate,
                        onStartDateSelected = { startDate = it },
                        onEndDateSelected = { endDate = it },
                        statusFilter = statusFilter,
                        onStatusFilterChange = { statusFilter = it },
                        searchQuery = searchQuery,
                        onSearchQueryChange = { searchQuery = it },
                        filePrefix = "pbs_expenses_all",
                        shareSubject = "PBS Expense Claims Report",
                        onShowFeedback = onShowFeedback
                    )
                }

                if (uiState.expenses.isEmpty()) {
                    item(key = "empty_all") {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 24.dp, vertical = 32.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ReceiptLong,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(56.dp)
                            )
                            Spacer(Modifier.height(12.dp))
                            Text(
                                text = stringResource(R.string.empty_expenses_title),
                                style = MaterialTheme.typography.titleMedium,
                                textAlign = TextAlign.Center
                            )
                            Spacer(Modifier.height(6.dp))
                            Text(
                                text = stringResource(R.string.empty_expenses_subtitle),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                } else if (filteredExpenses.isEmpty()) {
                    item(key = "empty_filtered") {
                        Text(
                            text = stringResource(R.string.empty_filtered_expenses),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 24.dp)
                        )
                    }
                } else {
                    items(filteredExpenses, key = { it.id }) { expense ->
                        ExpenseHistoryCard(
                            expense = expense,
                            isAdmin = true,
                            onApprove = { onSetExpenseStatus(expense.id, true) },
                            onReject = { onSetExpenseStatus(expense.id, false) },
                            onToggleClaimed = { claimed -> onSetExpenseClaimed(expense.id, claimed) },
                            onDelete = { onDeleteExpense(expense.id) }
                        )
                    }
                }
            }
        }
    }

    if (showAddDialog) {
        AddExpenseDialog(
            employees = uiState.employees,
            sites = uiState.availableSites,
            onDismiss = { showAddDialog = false },
            onConfirm = { empId, site, title, cat, amount, date, note, autoApprove ->
                onAddExpense(empId, site, title, cat, amount, date, note, autoApprove)
                showAddDialog = false
            }
        )
    }
}

@Composable
private fun AddExpenseDialog(
    employees: List<EmployeeEntity>,
    sites: List<String>,
    onDismiss: () -> Unit,
    onConfirm: (
        employeeId: Long?,
        site: String,
        title: String,
        category: String,
        amount: Double,
        date: String,
        note: String,
        autoApprove: Boolean
    ) -> Unit
) {
    var selectedEmployeeId by remember { mutableStateOf(employees.firstOrNull()?.id) }
    var site by remember {
        mutableStateOf(employees.firstOrNull()?.site ?: sites.firstOrNull() ?: EmployeeEntity.DEFAULT_SITE)
    }
    var title by remember { mutableStateOf("") }
    var category by remember { mutableStateOf("Site Material") }
    var amountText by remember { mutableStateOf("") }
    var date by remember { mutableStateOf(AttendanceCalculator.todayDateString()) }
    var note by remember { mutableStateOf("") }
    var autoApprove by remember { mutableStateOf(true) }
    var showDatePicker by remember { mutableStateOf(false) }

    val categories = listOf("Site Material", "Travel", "Food", "Tools", "Other")

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.add_expense_button)) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text(stringResource(R.string.expense_title_label)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = amountText,
                    onValueChange = { amountText = it.filter { ch -> ch.isDigit() || ch == '.' } },
                    label = { Text(stringResource(R.string.expense_amount_label)) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    categories.forEach { cat ->
                        FilterChip(
                            selected = category == cat,
                            onClick = { category = cat },
                            label = { Text(cat) }
                        )
                    }
                }
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    sites.forEach { s ->
                        FilterChip(
                            selected = site == s,
                            onClick = { site = s },
                            label = { Text(s) }
                        )
                    }
                }
                if (employees.isNotEmpty()) {
                    Row(
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        FilterChip(
                            selected = selectedEmployeeId == null,
                            onClick = { selectedEmployeeId = null },
                            label = { Text("Site General") }
                        )
                        employees.forEach { emp ->
                            FilterChip(
                                selected = selectedEmployeeId == emp.id,
                                onClick = {
                                    selectedEmployeeId = emp.id
                                    site = emp.site
                                },
                                label = { Text(emp.name) }
                            )
                        }
                    }
                }
                OutlinedTextField(
                    value = date,
                    onValueChange = { date = it },
                    label = { Text(stringResource(R.string.date_label)) },
                    trailingIcon = {
                        IconButton(onClick = { showDatePicker = true }) {
                            Icon(Icons.Filled.CalendarMonth, stringResource(R.string.pick_date_cd))
                        }
                    },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it },
                    label = { Text("Note (optional)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Approve immediately",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Switch(
                        checked = autoApprove,
                        onCheckedChange = { autoApprove = it }
                    )
                }
            }
        },
        confirmButton = {
            val amount = amountText.toDoubleOrNull() ?: 0.0
            Button(
                onClick = {
                    onConfirm(
                        selectedEmployeeId,
                        site,
                        title.trim(),
                        category,
                        amount,
                        date.trim(),
                        note.trim(),
                        autoApprove
                    )
                },
                enabled = title.isNotBlank() && amount > 0.0 && AttendanceCalculator.isValidDateString(date),
                modifier = Modifier.minimumInteractiveComponentSize()
            ) {
                Text(stringResource(R.string.save_action))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel_action))
            }
        }
    )

    if (showDatePicker) {
        AttendanceDatePickerModal(
            currentDateString = date,
            onDismiss = { showDatePicker = false },
            onDateSelected = { millis ->
                date = AttendanceCalculator.formatDateMillis(millis)
                showDatePicker = false
            }
        )
    }
}
