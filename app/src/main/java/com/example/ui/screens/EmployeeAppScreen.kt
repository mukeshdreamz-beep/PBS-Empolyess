package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Login
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.EditCalendar
import androidx.compose.material.icons.filled.EventBusy
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SecondaryTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.EmployeeEntity
import com.example.domain.AttendanceCalculator
import com.example.domain.AttendanceStatus
import com.example.domain.SelfieLocationHelper
import com.example.ui.AttendanceUiState
import com.example.ui.components.AttendanceDatePickerModal
import com.example.ui.components.AttendanceProofDialog
import com.example.ui.components.AttendanceRecordCard
import com.example.ui.components.EmployeeInitialAvatar
import com.example.ui.components.ExpenseDateFilterMode
import com.example.ui.components.ExpenseFilterAndDownloadPanel
import com.example.ui.components.ExpenseHistoryCard
import com.example.ui.components.ExpenseKpiSummaryGrid
import com.example.ui.components.ExpenseStatusFilter
import com.example.ui.components.OvertimeBadge
import com.example.ui.components.SelfieAttendanceDialog
import com.example.ui.components.StatusPill
import com.example.ui.components.TimePickerModalDialog
import com.example.ui.components.filterExpenses
import com.example.ui.theme.JetBrainsMono

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EmployeeAppScreen(
    uiState: AttendanceUiState,
    onSelectEmployeeProfile: (Long) -> Unit,
    onOpenAddEmployee: () -> Unit,
    onSelfCheckInOutWithProof: (
        employeeId: Long,
        isCheckOut: Boolean,
        photoBase64: String,
        lat: Double,
        lng: Double,
        address: String
    ) -> Unit,
    onSubmitApprovalRequest: (
        employeeId: Long,
        type: String,
        date: String,
        inMinutes: Int,
        outMinutes: Int,
        reason: String
    ) -> Unit,
    onSubmitExpense: (
        employeeId: Long,
        site: String,
        title: String,
        category: String,
        amount: Double,
        date: String,
        note: String
    ) -> Unit,
    onShowFeedback: (messageRes: Int, isError: Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    val profile = uiState.activeEmployeeAppProfile
    var selectedTab by remember { mutableIntStateOf(0) } // 0 = My Shift, 1 = My Expenses
    var showLeaveDialog by remember { mutableStateOf(false) }
    var showCorrectionDialog by remember { mutableStateOf(false) }
    var showExpenseDialog by remember { mutableStateOf(false) }
    var selfieDialogIsCheckOut by remember { mutableStateOf<Boolean?>(null) }
    var showTodayProofDialog by remember { mutableStateOf(false) }

    var expDateMode by remember { mutableStateOf(ExpenseDateFilterMode.ALL) }
    var expStartDate by remember { mutableStateOf(AttendanceCalculator.yesterdayDateString()) }
    var expEndDate by remember { mutableStateOf(AttendanceCalculator.todayDateString()) }
    var expStatusFilter by remember { mutableStateOf(ExpenseStatusFilter.ALL) }
    var expSearchQuery by remember { mutableStateOf("") }

    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.TopCenter
    ) {
        if (uiState.employees.isEmpty() || profile == null) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = stringResource(R.string.no_employees_yet),
                    style = MaterialTheme.typography.titleMedium
                )
                Spacer(modifier = Modifier.height(12.dp))
                Button(
                    onClick = onOpenAddEmployee,
                    modifier = Modifier.minimumInteractiveComponentSize()
                ) {
                    Icon(Icons.Filled.PersonAdd, null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.add_employee_button))
                }
            }
        } else {
            val today = AttendanceCalculator.todayDateString()
            val todayRecord = uiState.allRecords.firstOrNull {
                it.employeeId == profile.id && it.date == today
            }
            val isAdminLocked = todayRecord?.markedByRole == "ADMIN"
            val hasCheckedInToday = todayRecord?.hasCheckedIn == true
            val hasCheckedOutToday = todayRecord?.hasCheckedOut == true
            val canCheckIn = todayRecord == null || (!hasCheckedInToday && !isAdminLocked)
            val canCheckOut = todayRecord != null && hasCheckedInToday && !hasCheckedOutToday && !isAdminLocked

            val myRecords = uiState.allRecords.filter { it.employeeId == profile.id }
            val myExpenses = remember(uiState.expenses, profile.id) {
                uiState.expenses.filter { it.employeeId == profile.id }
            }
            val filteredMyExpenses = remember(
                myExpenses,
                expDateMode,
                expStartDate,
                expEndDate,
                expStatusFilter,
                expSearchQuery
            ) {
                filterExpenses(
                    expenses = myExpenses,
                    dateMode = expDateMode,
                    startDate = expStartDate,
                    endDate = expEndDate,
                    statusFilter = expStatusFilter,
                    searchQuery = expSearchQuery
                )
            }

            Column(
                modifier = Modifier
                    .widthIn(max = 760.dp)
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // Authenticated employee profile banner
                Card(
                    shape = MaterialTheme.shapes.large,
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        EmployeeInitialAvatar(name = profile.name)
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = stringResource(R.string.employee_app_welcome),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                text = "${profile.name} · ${profile.site}",
                                style = MaterialTheme.typography.titleMedium
                            )
                            val meta = listOfNotNull(
                                profile.loginId.takeIf { it.isNotBlank() }?.let { "Login ID: $it" },
                                profile.role.takeIf { it.isNotBlank() }
                            ).joinToString(" · ")
                            if (meta.isNotBlank()) {
                                Text(
                                    text = meta,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }

                // My Shift | My Expenses tab bar
                SecondaryTabRow(
                    selectedTabIndex = selectedTab,
                    containerColor = MaterialTheme.colorScheme.surface,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Tab(
                        selected = selectedTab == 0,
                        onClick = { selectedTab = 0 },
                        text = { Text(stringResource(R.string.tab_my_shift)) },
                        modifier = Modifier.testTag("employee_tab_shift")
                    )
                    Tab(
                        selected = selectedTab == 1,
                        onClick = { selectedTab = 1 },
                        text = {
                            val badge = if (myExpenses.isNotEmpty()) " (${myExpenses.size})" else ""
                            Text("${stringResource(R.string.tab_my_expenses)}$badge")
                        },
                        modifier = Modifier.testTag("employee_tab_expenses")
                    )
                }

                if (selectedTab == 0) {
                    // Today's Self Punch Card
                    Card(
                        shape = MaterialTheme.shapes.large,
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column {
                                    Text(
                                        text = stringResource(R.string.employee_punch_card_title),
                                        style = MaterialTheme.typography.titleMedium
                                    )
                                    Text(
                                        text = today,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                if (todayRecord != null) {
                                    val st = AttendanceStatus.fromStorage(todayRecord.status)
                                    val isInProgress = st == AttendanceStatus.PRESENT &&
                                        todayRecord.hasCheckedIn && !todayRecord.hasCheckedOut
                                    val br = AttendanceCalculator.calculateBreakdown(
                                        todayRecord.inMinutes,
                                        if (isInProgress) todayRecord.inMinutes else todayRecord.outMinutes,
                                        st,
                                        uiState.policy
                                    )
                                    StatusPill(
                                        status = st,
                                        isFullDay = br.isFullDay,
                                        isInProgress = isInProgress
                                    )
                                }
                            }

                            if (todayRecord != null && AttendanceStatus.fromStorage(todayRecord.status) == AttendanceStatus.PRESENT) {
                                val isInProgress = todayRecord.hasCheckedIn && !todayRecord.hasCheckedOut
                                val br = AttendanceCalculator.calculateBreakdown(
                                    todayRecord.inMinutes,
                                    if (isInProgress) todayRecord.inMinutes else todayRecord.outMinutes,
                                    AttendanceStatus.PRESENT,
                                    uiState.policy
                                )
                                Surface(
                                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                                    shape = MaterialTheme.shapes.medium,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(12.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column {
                                            Text("IN", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                            Text(
                                                AttendanceCalculator.formatTime12h(todayRecord.inMinutes),
                                                style = MaterialTheme.typography.titleMedium.copy(fontFamily = JetBrainsMono)
                                            )
                                        }
                                        Column {
                                            Text("OUT", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                            Text(
                                                text = if (isInProgress) {
                                                    stringResource(R.string.out_pending_label)
                                                } else {
                                                    AttendanceCalculator.formatTime12h(todayRecord.outMinutes)
                                                },
                                                style = MaterialTheme.typography.titleMedium.copy(fontFamily = JetBrainsMono),
                                                color = if (isInProgress) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                                            )
                                        }
                                        if (!isInProgress) {
                                            OvertimeBadge(overtimeMinutes = br.overtimeMinutes)
                                        }
                                    }
                                }

                                if (todayRecord.hasAnyProof) {
                                    val thumb = remember(todayRecord.checkInPhotoBase64, todayRecord.checkOutPhotoBase64) {
                                        SelfieLocationHelper.decodeBase64ToImageBitmap(
                                            todayRecord.checkOutPhotoBase64.ifBlank { todayRecord.checkInPhotoBase64 }
                                        )
                                    }
                                    val locLabel = todayRecord.checkOutAddress.ifBlank { todayRecord.checkInAddress }
                                    Surface(
                                        onClick = { showTodayProofDialog = true },
                                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f),
                                        shape = MaterialTheme.shapes.small,
                                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(10.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                                        ) {
                                            if (thumb != null) {
                                                Image(
                                                    bitmap = thumb,
                                                    contentDescription = stringResource(R.string.captured_selfie_cd),
                                                    contentScale = ContentScale.Crop,
                                                    modifier = Modifier
                                                        .size(40.dp)
                                                        .clip(RoundedCornerShape(8.dp))
                                                )
                                            } else {
                                                Icon(Icons.Filled.CameraAlt, null, tint = MaterialTheme.colorScheme.primary)
                                            }
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(
                                                    text = stringResource(R.string.view_proof_button),
                                                    style = MaterialTheme.typography.labelMedium,
                                                    fontWeight = FontWeight.Bold,
                                                    color = MaterialTheme.colorScheme.primary
                                                )
                                                if (locLabel.isNotBlank()) {
                                                    Row(
                                                        verticalAlignment = Alignment.CenterVertically,
                                                        horizontalArrangement = Arrangement.spacedBy(3.dp)
                                                    ) {
                                                        Icon(Icons.Filled.LocationOn, null, modifier = Modifier.size(13.dp))
                                                        Text(
                                                            text = locLabel,
                                                            style = MaterialTheme.typography.bodySmall,
                                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                            maxLines = 1,
                                                            overflow = TextOverflow.Ellipsis
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Button(
                                    onClick = { selfieDialogIsCheckOut = false },
                                    enabled = canCheckIn,
                                    modifier = Modifier
                                        .weight(1f)
                                        .heightIn(min = 48.dp)
                                        .testTag("employee_check_in_now_button")
                                ) {
                                    Icon(Icons.AutoMirrored.Filled.Login, null, modifier = Modifier.size(18.dp))
                                    Spacer(Modifier.width(6.dp))
                                    Text(
                                        text = stringResource(
                                            if (hasCheckedInToday) R.string.checked_in_done else R.string.punch_in_today
                                        )
                                    )
                                }
                                FilledTonalButton(
                                    onClick = { selfieDialogIsCheckOut = true },
                                    enabled = canCheckOut,
                                    modifier = Modifier
                                        .weight(1f)
                                        .heightIn(min = 48.dp)
                                        .testTag("employee_check_out_now_button")
                                ) {
                                    Icon(Icons.AutoMirrored.Filled.Logout, null, modifier = Modifier.size(18.dp))
                                    Spacer(Modifier.width(6.dp))
                                    Text(
                                        text = stringResource(
                                            if (hasCheckedOutToday) R.string.checked_out_done else R.string.punch_out_today
                                        )
                                    )
                                }
                            }

                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Lock,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(14.dp)
                                )
                                Text(
                                    text = stringResource(R.string.employee_lock_notice),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                OutlinedButton(
                                    onClick = { showLeaveDialog = true },
                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp),
                                    modifier = Modifier
                                        .weight(1f)
                                        .minimumInteractiveComponentSize()
                                        .testTag("employee_request_leave_button")
                                ) {
                                    Icon(Icons.Filled.EventBusy, null, modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(4.dp))
                                    Text(stringResource(R.string.request_leave_button), style = MaterialTheme.typography.labelMedium)
                                }
                                OutlinedButton(
                                    onClick = { showCorrectionDialog = true },
                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp),
                                    modifier = Modifier
                                        .weight(1f)
                                        .minimumInteractiveComponentSize()
                                        .testTag("employee_fix_timing_button")
                                ) {
                                    Icon(Icons.Filled.EditCalendar, null, modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(4.dp))
                                    Text(stringResource(R.string.request_correction_button), style = MaterialTheme.typography.labelMedium)
                                }
                                OutlinedButton(
                                    onClick = { showExpenseDialog = true },
                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp),
                                    modifier = Modifier
                                        .weight(1f)
                                        .minimumInteractiveComponentSize()
                                        .testTag("employee_submit_expense_button")
                                ) {
                                    Icon(Icons.AutoMirrored.Filled.ReceiptLong, null, modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(4.dp))
                                    Text(stringResource(R.string.submit_expense_button), style = MaterialTheme.typography.labelMedium)
                                }
                            }
                        }
                    }

                    Text(
                        text = stringResource(R.string.my_history_title),
                        style = MaterialTheme.typography.titleMedium
                    )

                    if (myRecords.isEmpty()) {
                        Text(
                            text = stringResource(R.string.empty_records_subtitle),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        myRecords.take(15).forEach { rec ->
                            AttendanceRecordCard(
                                record = rec,
                                policy = uiState.policy,
                                onEdit = {},
                                onDelete = {},
                                allowModify = false
                            )
                        }
                    }
                } else {
                    // My Expenses Tab
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = stringResource(R.string.expenses_title),
                                style = MaterialTheme.typography.titleMedium
                            )
                            Text(
                                text = stringResource(R.string.expense_submitted_only_note),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Spacer(Modifier.width(8.dp))
                        Button(
                            onClick = { showExpenseDialog = true },
                            modifier = Modifier
                                .minimumInteractiveComponentSize()
                                .testTag("employee_tab_submit_expense_button")
                        ) {
                            Icon(Icons.Filled.Add, null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(4.dp))
                            Text(stringResource(R.string.submit_expense_button))
                        }
                    }

                    ExpenseKpiSummaryGrid(expenses = filteredMyExpenses)

                    val empSlug = profile.name.lowercase().replace(Regex("[^a-z0-9]+"), "_").trim('_')
                        .ifEmpty { "employee" }
                    ExpenseFilterAndDownloadPanel(
                        filteredExpenses = filteredMyExpenses,
                        dateMode = expDateMode,
                        onDateModeChange = { expDateMode = it },
                        startDate = expStartDate,
                        endDate = expEndDate,
                        onStartDateSelected = { expStartDate = it },
                        onEndDateSelected = { expEndDate = it },
                        statusFilter = expStatusFilter,
                        onStatusFilterChange = { expStatusFilter = it },
                        searchQuery = expSearchQuery,
                        onSearchQueryChange = { expSearchQuery = it },
                        filePrefix = "pbs_${empSlug}_expenses",
                        shareSubject = "PBS Expenses — ${profile.name}",
                        onShowFeedback = onShowFeedback,
                        showSearch = false
                    )

                    if (myExpenses.isEmpty()) {
                        Surface(
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = stringResource(R.string.empty_expenses_subtitle),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(16.dp)
                            )
                        }
                    } else if (filteredMyExpenses.isEmpty()) {
                        Text(
                            text = stringResource(R.string.empty_filtered_expenses),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        filteredMyExpenses.forEach { expense ->
                            ExpenseHistoryCard(
                                expense = expense,
                                isAdmin = false
                            )
                        }
                    }
                }
            }

            if (showTodayProofDialog && todayRecord != null) {
                AttendanceProofDialog(
                    record = todayRecord,
                    onDismiss = { showTodayProofDialog = false }
                )
            }
        }
    }

    val pendingPunchType = selfieDialogIsCheckOut
    if (pendingPunchType != null && profile != null) {
        SelfieAttendanceDialog(
            isCheckOut = pendingPunchType,
            employeeName = profile.name,
            onDismiss = { selfieDialogIsCheckOut = null },
            onConfirmPunch = { photoBase64, lat, lng, address ->
                selfieDialogIsCheckOut = null
                onSelfCheckInOutWithProof(
                    profile.id,
                    pendingPunchType,
                    photoBase64,
                    lat,
                    lng,
                    address
                )
            }
        )
    }

    if (showLeaveDialog && profile != null) {
        LeaveOrCorrectionDialog(
            isCorrection = false,
            defaultIn = uiState.policy.defaultInMinutes,
            defaultOut = uiState.policy.defaultOutMinutes,
            onDismiss = { showLeaveDialog = false },
            onSubmit = { date, inMin, outMin, reason ->
                onSubmitApprovalRequest(profile.id, "LEAVE", date, inMin, outMin, reason)
                showLeaveDialog = false
            }
        )
    }

    if (showCorrectionDialog && profile != null) {
        LeaveOrCorrectionDialog(
            isCorrection = true,
            defaultIn = uiState.policy.defaultInMinutes,
            defaultOut = uiState.policy.defaultOutMinutes,
            onDismiss = { showCorrectionDialog = false },
            onSubmit = { date, inMin, outMin, reason ->
                onSubmitApprovalRequest(profile.id, "ATTENDANCE_CORRECTION", date, inMin, outMin, reason)
                showCorrectionDialog = false
            }
        )
    }

    if (showExpenseDialog && profile != null) {
        QuickExpenseSubmitDialog(
            employee = profile,
            onDismiss = { showExpenseDialog = false },
            onSubmit = { title, category, amount, date, note ->
                onSubmitExpense(profile.id, profile.site, title, category, amount, date, note)
                selectedTab = 1
                showExpenseDialog = false
            }
        )
    }
}

@Composable
private fun LeaveOrCorrectionDialog(
    isCorrection: Boolean,
    defaultIn: Int,
    defaultOut: Int,
    onDismiss: () -> Unit,
    onSubmit: (date: String, inMinutes: Int, outMinutes: Int, reason: String) -> Unit
) {
    var date by remember { mutableStateOf(AttendanceCalculator.todayDateString()) }
    var inMins by remember { mutableIntStateOf(defaultIn) }
    var outMins by remember { mutableIntStateOf(defaultOut) }
    var reason by remember { mutableStateOf("") }
    var pickTarget by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(if (isCorrection) "Request Timing Correction" else "Request Leave")
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = date,
                    onValueChange = { date = it },
                    label = { Text(stringResource(R.string.date_label)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                if (isCorrection) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(
                            onClick = { pickTarget = "in" },
                            modifier = Modifier.weight(1f).minimumInteractiveComponentSize()
                        ) {
                            Text("IN ${AttendanceCalculator.formatTime12h(inMins)}")
                        }
                        OutlinedButton(
                            onClick = { pickTarget = "out" },
                            modifier = Modifier.weight(1f).minimumInteractiveComponentSize()
                        ) {
                            Text("OUT ${AttendanceCalculator.formatTime12h(outMins)}")
                        }
                    }
                }
                OutlinedTextField(
                    value = reason,
                    onValueChange = { reason = it },
                    label = { Text("Reason *") },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (AttendanceCalculator.isValidDateString(date) && reason.isNotBlank()) {
                        onSubmit(date.trim(), inMins, outMins, reason.trim())
                    }
                },
                enabled = AttendanceCalculator.isValidDateString(date) && reason.isNotBlank(),
                modifier = Modifier.minimumInteractiveComponentSize()
            ) {
                Text("Submit")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel_action))
            }
        }
    )

    pickTarget?.let { target ->
        TimePickerModalDialog(
            title = if (target == "in") stringResource(R.string.select_time_in_title) else stringResource(R.string.select_time_out_title),
            initialMinutes = if (target == "in") inMins else outMins,
            onDismiss = { pickTarget = null },
            onConfirm = { picked ->
                if (target == "in") inMins = picked else outMins = picked
                pickTarget = null
            }
        )
    }
}

@Composable
private fun QuickExpenseSubmitDialog(
    employee: EmployeeEntity,
    onDismiss: () -> Unit,
    onSubmit: (title: String, category: String, amount: Double, date: String, note: String) -> Unit
) {
    var title by remember { mutableStateOf("") }
    var category by remember { mutableStateOf("Travel") }
    var amountText by remember { mutableStateOf("") }
    var date by remember { mutableStateOf(AttendanceCalculator.todayDateString()) }
    var note by remember { mutableStateOf("") }
    var showDatePicker by remember { mutableStateOf(false) }

    val categories = listOf("Travel", "Food", "Site Material", "Tools", "Other")

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("${stringResource(R.string.submit_expense_button)} (${employee.site})") },
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
                Text(
                    text = stringResource(R.string.expense_submitted_only_note),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = {
            val amount = amountText.toDoubleOrNull() ?: 0.0
            Button(
                onClick = { onSubmit(title.trim(), category.trim(), amount, date.trim(), note.trim()) },
                enabled = title.isNotBlank() && amount > 0.0 && AttendanceCalculator.isValidDateString(date),
                modifier = Modifier.minimumInteractiveComponentSize()
            ) {
                Text("Submit")
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
