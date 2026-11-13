package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.data.AttendanceRecordItem
import com.example.data.EmployeeEntity
import com.example.domain.AttendanceCalculator
import com.example.domain.AttendanceStatus
import com.example.ui.AttendanceUiState
import com.example.ui.EntryMode
import com.example.ui.components.AttendanceDatePickerModal
import com.example.ui.components.AttendanceRecordCard
import com.example.ui.components.EmployeeInitialAvatar
import com.example.ui.components.OvertimeBadge
import com.example.ui.components.StatusPill
import com.example.ui.components.TimePickerModalDialog
import com.example.ui.components.TimeSelectionTile
import com.example.ui.theme.JetBrainsMono
import com.example.ui.theme.LocalAttendanceColors

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EntryScreen(
    uiState: AttendanceUiState,
    onEntryModeChange: (EntryMode) -> Unit,
    onOpenAddEmployee: () -> Unit,
    onSelectEmployee: (Long) -> Unit,
    onDateTextChange: (String) -> Unit,
    onDateMillisSelected: (Long) -> Unit,
    onStatusSelected: (AttendanceStatus) -> Unit,
    onInMinutesSelected: (Int) -> Unit,
    onOutMinutesSelected: (Int) -> Unit,
    onPunchInNow: () -> Unit,
    onPunchOutNow: () -> Unit,
    onNoteChange: (String) -> Unit,
    onResetStandardShift: () -> Unit,
    onSaveAttendance: () -> Unit,
    onQuickMarkRollCall: (employeeId: Long, status: AttendanceStatus, inMinutes: Int, outMinutes: Int) -> Unit,
    onMarkAllFullDayRollCall: () -> Unit,
    onEditRecord: (AttendanceRecordItem) -> Unit,
    onDeleteRecord: (AttendanceRecordItem) -> Unit,
    onViewAllRecords: () -> Unit,
    modifier: Modifier = Modifier
) {
    var showInPicker by remember { mutableStateOf(false) }
    var showOutPicker by remember { mutableStateOf(false) }
    var showDatePicker by remember { mutableStateOf(false) }

    Column(modifier = modifier.fillMaxSize()) {
        Surface(
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 1.dp,
            modifier = Modifier.fillMaxWidth()
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                contentAlignment = Alignment.Center
            ) {
                SingleChoiceSegmentedButtonRow(
                    modifier = Modifier.widthIn(max = 420.dp).fillMaxWidth()
                ) {
                    SegmentedButton(
                        selected = uiState.entryMode == EntryMode.SINGLE,
                        onClick = { onEntryModeChange(EntryMode.SINGLE) },
                        shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
                        modifier = Modifier.testTag("mode_single_entry_tab")
                    ) {
                        Text(stringResource(R.string.mode_single_entry))
                    }
                    SegmentedButton(
                        selected = uiState.entryMode == EntryMode.ROLL_CALL,
                        onClick = { onEntryModeChange(EntryMode.ROLL_CALL) },
                        shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
                        modifier = Modifier.testTag("mode_roll_call_tab")
                    ) {
                        Text(stringResource(R.string.mode_roll_call))
                    }
                }
            }
        }

        if (uiState.entryMode == EntryMode.ROLL_CALL) {
            RollCallContent(
                uiState = uiState,
                onDateTextChange = onDateTextChange,
                onOpenDatePicker = { showDatePicker = true },
                onQuickMarkRollCall = onQuickMarkRollCall,
                onMarkAllFullDay = onMarkAllFullDayRollCall,
                onEditRecord = onEditRecord,
                onOpenAddEmployee = onOpenAddEmployee,
                modifier = Modifier.weight(1f)
            )
        } else {
            BoxWithConstraints(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentAlignment = Alignment.TopCenter
            ) {
                val isWide = maxWidth >= 760.dp

                if (isWide) {
                    Row(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 24.dp, vertical = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(24.dp)
                    ) {
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .verticalScroll(rememberScrollState()),
                            verticalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            EntryFormCard(
                                uiState = uiState,
                                onOpenAddEmployee = onOpenAddEmployee,
                                onSelectEmployee = onSelectEmployee,
                                onDateTextChange = onDateTextChange,
                                onOpenDatePicker = { showDatePicker = true },
                                onStatusSelected = onStatusSelected,
                                onOpenInPicker = { showInPicker = true },
                                onOpenOutPicker = { showOutPicker = true },
                                onPunchInNow = onPunchInNow,
                                onPunchOutNow = onPunchOutNow,
                                onNoteChange = onNoteChange,
                                onResetStandardShift = onResetStandardShift
                            )
                            SaveSection(
                                uiState = uiState,
                                onSaveAttendance = onSaveAttendance
                            )
                        }

                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .verticalScroll(rememberScrollState()),
                            verticalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            CalculationSummaryCard(uiState = uiState)
                            RecentEntriesSection(
                                uiState = uiState,
                                records = uiState.allRecords.take(4),
                                totalCount = uiState.allRecords.size,
                                onEditRecord = onEditRecord,
                                onDeleteRecord = onDeleteRecord,
                                onViewAllRecords = onViewAllRecords
                            )
                            RulesFooterCard(uiState = uiState)
                        }
                    }
                } else {
                    Column(
                        modifier = Modifier
                            .widthIn(max = 600.dp)
                            .fillMaxWidth()
                            .verticalScroll(rememberScrollState())
                            .padding(horizontal = 16.dp, vertical = 14.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        EntryFormCard(
                            uiState = uiState,
                            onOpenAddEmployee = onOpenAddEmployee,
                            onSelectEmployee = onSelectEmployee,
                            onDateTextChange = onDateTextChange,
                            onOpenDatePicker = { showDatePicker = true },
                            onStatusSelected = onStatusSelected,
                            onOpenInPicker = { showInPicker = true },
                            onOpenOutPicker = { showOutPicker = true },
                            onPunchInNow = onPunchInNow,
                            onPunchOutNow = onPunchOutNow,
                            onNoteChange = onNoteChange,
                            onResetStandardShift = onResetStandardShift
                        )

                        CalculationSummaryCard(uiState = uiState)

                        SaveSection(
                            uiState = uiState,
                            onSaveAttendance = onSaveAttendance
                        )

                        RecentEntriesSection(
                            uiState = uiState,
                            records = uiState.allRecords.take(3),
                            totalCount = uiState.allRecords.size,
                            onEditRecord = onEditRecord,
                            onDeleteRecord = onDeleteRecord,
                            onViewAllRecords = onViewAllRecords
                        )

                        RulesFooterCard(uiState = uiState)
                    }
                }
            }
        }
    }

    if (showInPicker) {
        TimePickerModalDialog(
            title = stringResource(R.string.select_time_in_title),
            initialMinutes = uiState.inMinutes,
            onDismiss = { showInPicker = false },
            onConfirm = { picked ->
                onInMinutesSelected(picked)
                showInPicker = false
            }
        )
    }

    if (showOutPicker) {
        TimePickerModalDialog(
            title = stringResource(R.string.select_time_out_title),
            initialMinutes = uiState.outMinutes,
            onDismiss = { showOutPicker = false },
            onConfirm = { picked ->
                onOutMinutesSelected(picked)
                showOutPicker = false
            }
        )
    }

    if (showDatePicker) {
        AttendanceDatePickerModal(
            currentDateString = uiState.dateText,
            onDismiss = { showDatePicker = false },
            onDateSelected = onDateMillisSelected
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EntryFormCard(
    uiState: AttendanceUiState,
    onOpenAddEmployee: () -> Unit,
    onSelectEmployee: (Long) -> Unit,
    onDateTextChange: (String) -> Unit,
    onOpenDatePicker: () -> Unit,
    onStatusSelected: (AttendanceStatus) -> Unit,
    onOpenInPicker: () -> Unit,
    onOpenOutPicker: () -> Unit,
    onPunchInNow: () -> Unit,
    onPunchOutNow: () -> Unit,
    onNoteChange: (String) -> Unit,
    onResetStandardShift: () -> Unit
) {
    var employeeDropdownExpanded by remember { mutableStateOf(false) }

    Card(
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
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
                Text(
                    text = stringResource(R.string.section_employee),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary
                )
                FilledTonalButton(
                    onClick = onOpenAddEmployee,
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .testTag("entry_add_employee_button")
                ) {
                    Icon(
                        imageVector = Icons.Filled.PersonAdd,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(stringResource(R.string.add_employee_button))
                }
            }

            if (uiState.employees.isEmpty()) {
                Surface(
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f),
                    shape = MaterialTheme.shapes.medium,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Add,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = stringResource(R.string.no_employees_yet),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                }
            } else {
                ExposedDropdownMenuBox(
                    expanded = employeeDropdownExpanded,
                    onExpandedChange = { employeeDropdownExpanded = it },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    val selectedDisplay = uiState.selectedEmployee?.let { emp ->
                        if (emp.employeeCode.isNotBlank()) "${emp.name} (${emp.employeeCode})" else emp.name
                    } ?: ""
                    OutlinedTextField(
                        value = selectedDisplay,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text(stringResource(R.string.select_employee_prompt)) },
                        trailingIcon = {
                            ExposedDropdownMenuDefaults.TrailingIcon(
                                expanded = employeeDropdownExpanded
                            )
                        },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .menuAnchor(MenuAnchorType.PrimaryNotEditable)
                            .testTag("employee_selector_field")
                    )
                    ExposedDropdownMenu(
                        expanded = employeeDropdownExpanded,
                        onDismissRequest = { employeeDropdownExpanded = false }
                    ) {
                        uiState.employees.forEach { emp ->
                            DropdownMenuItem(
                                text = {
                                    Column {
                                        Text(
                                            text = if (emp.employeeCode.isNotBlank()) {
                                                "${emp.name} (${emp.employeeCode})"
                                            } else {
                                                emp.name
                                            },
                                            style = MaterialTheme.typography.bodyLarge
                                        )
                                        if (emp.role.isNotBlank()) {
                                            Text(
                                                text = emp.role,
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                },
                                onClick = {
                                    onSelectEmployee(emp.id)
                                    employeeDropdownExpanded = false
                                }
                            )
                        }
                    }
                }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

            OutlinedTextField(
                value = uiState.dateText,
                onValueChange = onDateTextChange,
                label = { Text(stringResource(R.string.date_label)) },
                singleLine = true,
                isError = uiState.dateText.isNotBlank() &&
                    !AttendanceCalculator.isValidDateString(uiState.dateText),
                trailingIcon = {
                    IconButton(
                        onClick = onOpenDatePicker,
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("open_date_picker_button")
                    ) {
                        Icon(
                            imageVector = Icons.Filled.CalendarMonth,
                            contentDescription = stringResource(R.string.pick_date_cd),
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("attendance_date_input")
            )

            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                AssistChip(
                    onClick = { onDateTextChange(AttendanceCalculator.todayDateString()) },
                    label = { Text(stringResource(R.string.today_chip)) },
                    modifier = Modifier.testTag("today_date_chip")
                )
                AssistChip(
                    onClick = { onDateTextChange(AttendanceCalculator.yesterdayDateString()) },
                    label = { Text(stringResource(R.string.yesterday_chip)) },
                    modifier = Modifier.testTag("yesterday_date_chip")
                )
                Spacer(modifier = Modifier.weight(1f))
                TextButton(
                    onClick = onResetStandardShift,
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .testTag("reset_shift_button")
                ) {
                    Icon(
                        imageVector = Icons.Filled.RestartAlt,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = stringResource(R.string.reset_standard_shift),
                        style = MaterialTheme.typography.labelMedium
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterChip(
                    selected = uiState.entryStatus == AttendanceStatus.PRESENT,
                    onClick = { onStatusSelected(AttendanceStatus.PRESENT) },
                    label = { Text(stringResource(R.string.status_present)) },
                    modifier = Modifier.testTag("status_present_chip")
                )
                FilterChip(
                    selected = uiState.entryStatus == AttendanceStatus.ABSENT,
                    onClick = { onStatusSelected(AttendanceStatus.ABSENT) },
                    label = { Text(stringResource(R.string.status_absent)) },
                    modifier = Modifier.testTag("status_absent_chip")
                )
                FilterChip(
                    selected = uiState.entryStatus == AttendanceStatus.LEAVE,
                    onClick = { onStatusSelected(AttendanceStatus.LEAVE) },
                    label = { Text(stringResource(R.string.status_leave)) },
                    modifier = Modifier.testTag("status_leave_chip")
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                val isPresent = uiState.entryStatus == AttendanceStatus.PRESENT
                TimeSelectionTile(
                    label = stringResource(R.string.time_in_label),
                    minutes = uiState.inMinutes,
                    enabled = isPresent,
                    onClick = onOpenInPicker,
                    onPunchNow = onPunchInNow,
                    testTag = "time_in_button",
                    modifier = Modifier.weight(1f)
                )
                TimeSelectionTile(
                    label = stringResource(R.string.time_out_label),
                    minutes = uiState.outMinutes,
                    enabled = isPresent,
                    onClick = onOpenOutPicker,
                    onPunchNow = onPunchOutNow,
                    testTag = "time_out_button",
                    modifier = Modifier.weight(1f)
                )
            }

            OutlinedTextField(
                value = uiState.entryNote,
                onValueChange = onNoteChange,
                label = { Text(stringResource(R.string.notes_label)) },
                placeholder = { Text(stringResource(R.string.notes_placeholder)) },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("attendance_note_input")
            )
        }
    }
}

@Composable
private fun RollCallContent(
    uiState: AttendanceUiState,
    onDateTextChange: (String) -> Unit,
    onOpenDatePicker: () -> Unit,
    onQuickMarkRollCall: (Long, AttendanceStatus, Int, Int) -> Unit,
    onMarkAllFullDay: () -> Unit,
    onEditRecord: (AttendanceRecordItem) -> Unit,
    onOpenAddEmployee: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.TopCenter
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = 760.dp)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Card(
                shape = MaterialTheme.shapes.medium,
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = stringResource(R.string.roll_call_subtitle, uiState.dateText),
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                    OutlinedTextField(
                        value = uiState.dateText,
                        onValueChange = onDateTextChange,
                        label = { Text(stringResource(R.string.date_label)) },
                        singleLine = true,
                        trailingIcon = {
                            IconButton(
                                onClick = onOpenDatePicker,
                                modifier = Modifier.minimumInteractiveComponentSize()
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.CalendarMonth,
                                    contentDescription = stringResource(R.string.pick_date_cd),
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    )
                    if (uiState.employees.isNotEmpty()) {
                        Button(
                            onClick = onMarkAllFullDay,
                            modifier = Modifier
                                .fillMaxWidth()
                                .minimumInteractiveComponentSize()
                                .testTag("roll_call_mark_all_button")
                        ) {
                            Icon(
                                imageVector = Icons.Filled.DoneAll,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(stringResource(R.string.roll_call_mark_all_full))
                        }
                    }
                }
            }

            if (uiState.employees.isEmpty()) {
                Card(
                    shape = MaterialTheme.shapes.medium,
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.roll_call_empty),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        FilledTonalButton(onClick = onOpenAddEmployee) {
                            Text(stringResource(R.string.add_employee_button))
                        }
                    }
                }
            } else {
                uiState.employees.forEach { employee ->
                    val existing = uiState.recordsForEntryDate[employee.id]
                    RollCallEmployeeRow(
                        employee = employee,
                        existingRecord = existing,
                        uiState = uiState,
                        onQuickMark = { status, inMin, outMin ->
                            onQuickMarkRollCall(employee.id, status, inMin, outMin)
                        },
                        onCustomize = {
                            if (existing != null) {
                                onEditRecord(existing)
                            }
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun RollCallEmployeeRow(
    employee: EmployeeEntity,
    existingRecord: AttendanceRecordItem?,
    uiState: AttendanceUiState,
    onQuickMark: (AttendanceStatus, Int, Int) -> Unit,
    onCustomize: () -> Unit
) {
    Card(
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("roll_call_card_${employee.id}")
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    EmployeeInitialAvatar(name = employee.name)
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            text = employee.name,
                            style = MaterialTheme.typography.titleMedium
                        )
                        Text(
                            text = if (existingRecord != null) {
                                val st = AttendanceStatus.fromStorage(existingRecord.status)
                                if (st == AttendanceStatus.PRESENT) {
                                    "Logged: ${AttendanceCalculator.formatTime12h(existingRecord.inMinutes)} – ${AttendanceCalculator.formatTime12h(existingRecord.outMinutes)}"
                                } else {
                                    "Logged: ${st.name}"
                                }
                            } else {
                                "Not logged yet for ${uiState.dateText}"
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                if (existingRecord != null) {
                    val st = AttendanceStatus.fromStorage(existingRecord.status)
                    val br = AttendanceCalculator.calculateBreakdown(
                        existingRecord.inMinutes,
                        existingRecord.outMinutes,
                        st,
                        uiState.policy
                    )
                    StatusPill(status = st, isFullDay = br.isFullDay)
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                FilledTonalButton(
                    onClick = {
                        onQuickMark(
                            AttendanceStatus.PRESENT,
                            uiState.policy.defaultInMinutes,
                            uiState.policy.defaultOutMinutes
                        )
                    },
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                    modifier = Modifier.weight(1f).minimumInteractiveComponentSize()
                ) {
                    Icon(Icons.Filled.CheckCircle, null, modifier = Modifier.size(15.dp))
                    Spacer(Modifier.width(4.dp))
                    Text(
                        stringResource(R.string.roll_call_full_shift),
                        style = MaterialTheme.typography.labelSmall
                    )
                }
                OutlinedButton(
                    onClick = {
                        val now = AttendanceCalculator.currentMinutesOfDay()
                        val out = uiState.policy.defaultOutMinutes.coerceAtLeast((now + 60).coerceAtMost(23 * 60 + 59))
                        onQuickMark(AttendanceStatus.PRESENT, now, out)
                    },
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp),
                    modifier = Modifier.weight(0.9f).minimumInteractiveComponentSize()
                ) {
                    Text(
                        stringResource(R.string.roll_call_clock_in_now),
                        style = MaterialTheme.typography.labelSmall
                    )
                }
                OutlinedButton(
                    onClick = { onQuickMark(AttendanceStatus.ABSENT, 0, 0) },
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp),
                    modifier = Modifier.weight(0.8f).minimumInteractiveComponentSize()
                ) {
                    Text(
                        stringResource(R.string.roll_call_mark_absent),
                        style = MaterialTheme.typography.labelSmall
                    )
                }
                OutlinedButton(
                    onClick = { onQuickMark(AttendanceStatus.LEAVE, 0, 0) },
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp),
                    modifier = Modifier.weight(0.8f).minimumInteractiveComponentSize()
                ) {
                    Text(
                        stringResource(R.string.roll_call_mark_leave),
                        style = MaterialTheme.typography.labelSmall
                    )
                }
            }

            if (existingRecord != null) {
                TextButton(
                    onClick = onCustomize,
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                    modifier = Modifier.align(Alignment.End)
                ) {
                    Text(
                        text = stringResource(R.string.edit_record_action),
                        style = MaterialTheme.typography.labelMedium
                    )
                }
            }
        }
    }
}

@Composable
private fun CalculationSummaryCard(uiState: AttendanceUiState) {
    val semantic = LocalAttendanceColors.current
    val breakdown = uiState.breakdown
    val policy = uiState.policy

    Card(
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("calculation_summary_card")
    ) {
        Column {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        Brush.horizontalGradient(
                            colors = listOf(
                                MaterialTheme.colorScheme.primary,
                                if (uiState.entryStatus == AttendanceStatus.PRESENT && breakdown.isFullDay) {
                                    semantic.fullDayAccent
                                } else {
                                    MaterialTheme.colorScheme.secondary
                                }
                            )
                        )
                    )
                    .padding(horizontal = 16.dp, vertical = 12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = stringResource(R.string.calculation_title),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                    StatusPill(
                        status = uiState.entryStatus,
                        isFullDay = breakdown.isFullDay
                    )
                }
            }

            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Text(
                            text = stringResource(R.string.working_time_label),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = AttendanceCalculator.formatDurationVerbose(breakdown.workedMinutes),
                            style = MaterialTheme.typography.headlineSmall.copy(
                                fontFamily = JetBrainsMono,
                                fontSize = 22.sp
                            ),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Text(
                            text = stringResource(R.string.overtime_label),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        OvertimeBadge(overtimeMinutes = breakdown.overtimeMinutes)
                    }
                }

                Text(
                    text = stringResource(
                        R.string.ot_rule_note,
                        AttendanceCalculator.formatTime12h(policy.overtimeStartMinutes),
                        AttendanceCalculator.formatDurationVerbose(policy.fullDayThresholdMinutes)
                    ) + " " + stringResource(R.string.ot_gap_note),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun SaveSection(
    uiState: AttendanceUiState,
    onSaveAttendance: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        AnimatedVisibility(visible = uiState.willReplaceExisting) {
            Surface(
                color = MaterialTheme.colorScheme.tertiaryContainer,
                contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
                shape = MaterialTheme.shapes.medium,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.Info,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Text(
                        text = stringResource(R.string.replacing_existing_notice),
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }

        Button(
            onClick = onSaveAttendance,
            shape = MaterialTheme.shapes.medium,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 52.dp)
                .testTag("save_attendance_button")
        ) {
            Icon(
                imageVector = Icons.Filled.Save,
                contentDescription = null,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = stringResource(
                    if (uiState.willReplaceExisting) {
                        R.string.update_attendance_button
                    } else {
                        R.string.save_attendance_button
                    }
                ),
                style = MaterialTheme.typography.labelLarge
            )
        }
    }
}

@Composable
private fun RecentEntriesSection(
    uiState: AttendanceUiState,
    records: List<AttendanceRecordItem>,
    totalCount: Int,
    onEditRecord: (AttendanceRecordItem) -> Unit,
    onDeleteRecord: (AttendanceRecordItem) -> Unit,
    onViewAllRecords: () -> Unit
) {
    if (records.isEmpty()) return

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = stringResource(R.string.recent_entries_title),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onBackground
            )
            if (totalCount > records.size) {
                TextButton(
                    onClick = onViewAllRecords,
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .testTag("view_all_records_button")
                ) {
                    Text("View all ($totalCount)")
                }
            }
        }
        records.forEach { record ->
            AttendanceRecordCard(
                record = record,
                policy = uiState.policy,
                onEdit = { onEditRecord(record) },
                onDelete = { onDeleteRecord(record) }
            )
        }
    }
}

@Composable
private fun RulesFooterCard(uiState: AttendanceUiState) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
        shape = MaterialTheme.shapes.medium,
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(
            text = stringResource(
                R.string.rules_footer,
                AttendanceCalculator.formatDurationVerbose(uiState.policy.fullDayThresholdMinutes),
                AttendanceCalculator.formatTime12h(uiState.policy.overtimeStartMinutes),
                uiState.policy.breakDeductionMinutes
            ),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(14.dp)
        )
    }
}
