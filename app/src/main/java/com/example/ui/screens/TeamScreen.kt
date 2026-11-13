package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.EmployeeEntity
import com.example.domain.AttendanceCalculator
import com.example.domain.AttendanceExportHelper
import com.example.domain.AttendanceStatus
import com.example.ui.AttendanceUiState
import com.example.ui.components.EmployeeInitialAvatar
import com.example.ui.components.ResetEmployeePasswordDialog
import com.example.ui.theme.JetBrainsMono
import com.example.ui.theme.LocalAttendanceColors

@Composable
fun TeamScreen(
    uiState: AttendanceUiState,
    onOpenAddEmployee: () -> Unit,
    onEditEmployee: (EmployeeEntity) -> Unit,
    onResetEmployeePassword: (EmployeeEntity, String) -> Unit,
    onDeleteEmployee: (Long) -> Unit,
    onViewEmployeeRecords: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    var pendingDeleteEmployee by remember { mutableStateOf<EmployeeEntity?>(null) }
    var pendingResetEmployee by remember { mutableStateOf<EmployeeEntity?>(null) }
    var selectedSiteFilter by remember { mutableStateOf<String?>(null) }

    val displayedEmployees = remember(uiState.employees, selectedSiteFilter) {
        if (selectedSiteFilter == null) {
            uiState.employees
        } else {
            uiState.employees.filter { it.site.equals(selectedSiteFilter, ignoreCase = true) }
        }
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
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = stringResource(R.string.team_title),
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = stringResource(R.string.team_subtitle),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Button(
                            onClick = onOpenAddEmployee,
                            modifier = Modifier
                                .minimumInteractiveComponentSize()
                                .testTag("team_add_employee_button")
                        ) {
                            Icon(
                                imageVector = Icons.Filled.PersonAdd,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(stringResource(R.string.add_action))
                        }
                    }

                    if (uiState.employees.isNotEmpty()) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            FilterChip(
                                selected = selectedSiteFilter == null,
                                onClick = { selectedSiteFilter = null },
                                label = { Text(stringResource(R.string.all_sites_label)) }
                            )
                            uiState.availableSites.forEach { site ->
                                FilterChip(
                                    selected = selectedSiteFilter == site,
                                    onClick = {
                                        selectedSiteFilter = if (selectedSiteFilter == site) null else site
                                    },
                                    label = { Text(site) }
                                )
                            }
                        }
                    }
                }
            }

            if (displayedEmployees.isEmpty()) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = Icons.Filled.Group,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(56.dp)
                    )
                    Spacer(modifier = Modifier.height(14.dp))
                    Text(
                        text = stringResource(R.string.empty_team_title),
                        style = MaterialTheme.typography.titleMedium,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = stringResource(R.string.empty_team_subtitle),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.widthIn(max = 300.dp)
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .testTag("team_lazy_list"),
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 88.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(
                        items = displayedEmployees,
                        key = { it.id }
                    ) { employee ->
                        val empRecords = uiState.allRecords.filter { it.employeeId == employee.id }
                        var fullDays = 0
                        var otMinutes = 0
                        var absentLeave = 0
                        for (rec in empRecords) {
                            val st = AttendanceStatus.fromStorage(rec.status)
                            if (st != AttendanceStatus.PRESENT) {
                                absentLeave++
                            } else {
                                val br = AttendanceCalculator.calculateBreakdown(
                                    inMinutes = rec.inMinutes,
                                    outMinutes = rec.outMinutes,
                                    status = st,
                                    policy = uiState.policy
                                )
                                if (br.isFullDay) fullDays++
                                otMinutes += br.overtimeMinutes
                            }
                        }

                        EmployeeCard(
                            employee = employee,
                            shiftCount = empRecords.size,
                            fullDayCount = fullDays,
                            absentLeaveCount = absentLeave,
                            overtimeMinutes = otMinutes,
                            onViewRecords = { onViewEmployeeRecords(employee.id) },
                            onEditClick = { onEditEmployee(employee) },
                            onResetPasswordClick = { pendingResetEmployee = employee },
                            onDeleteClick = { pendingDeleteEmployee = employee }
                        )
                    }
                }
            }
        }

        FloatingActionButton(
            onClick = onOpenAddEmployee,
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(20.dp)
                .testTag("team_fab_add_employee")
        ) {
            Icon(
                imageVector = Icons.Filled.PersonAdd,
                contentDescription = stringResource(R.string.add_employee_button)
            )
        }
    }

    pendingDeleteEmployee?.let { target ->
        val recordCount = uiState.allRecords.count { it.employeeId == target.id }
        AlertDialog(
            onDismissRequest = { pendingDeleteEmployee = null },
            title = { Text(stringResource(R.string.delete_employee_confirm_title)) },
            text = {
                Text(
                    stringResource(
                        R.string.delete_employee_confirm_body,
                        target.name,
                        recordCount
                    )
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        onDeleteEmployee(target.id)
                        pendingDeleteEmployee = null
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = MaterialTheme.colorScheme.onError
                    ),
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .testTag("confirm_delete_employee_button")
                ) {
                    Text(stringResource(R.string.remove_action))
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { pendingDeleteEmployee = null },
                    modifier = Modifier.minimumInteractiveComponentSize()
                ) {
                    Text(stringResource(R.string.cancel_action))
                }
            }
        )
    }

    pendingResetEmployee?.let { target ->
        ResetEmployeePasswordDialog(
            employee = target,
            onDismiss = { pendingResetEmployee = null },
            onConfirm = { newPass ->
                onResetEmployeePassword(target, newPass)
                pendingResetEmployee = null
            }
        )
    }
}

@Composable
private fun EmployeeCard(
    employee: EmployeeEntity,
    shiftCount: Int,
    fullDayCount: Int,
    absentLeaveCount: Int,
    overtimeMinutes: Int,
    onViewRecords: () -> Unit,
    onEditClick: () -> Unit,
    onResetPasswordClick: () -> Unit,
    onDeleteClick: () -> Unit
) {
    val semantic = LocalAttendanceColors.current
    Card(
        onClick = onViewRecords,
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("employee_card_${employee.id}")
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            EmployeeInitialAvatar(name = employee.name)
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = if (employee.employeeCode.isNotBlank()) {
                        "${employee.name} (${employee.employeeCode})"
                    } else {
                        employee.name
                    },
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                val metaParts = listOf(employee.site, employee.role, employee.phone).filter { it.isNotBlank() }
                if (metaParts.isNotEmpty()) {
                    Text(
                        text = metaParts.joinToString(" · "),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (employee.loginId.isNotBlank()) {
                    Text(
                        text = "Login ID: ${employee.loginId}",
                        style = MaterialTheme.typography.labelSmall.copy(fontFamily = JetBrainsMono),
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.SemiBold
                    )
                }
                if (employee.dailyWage > 0 || employee.otHourlyRate > 0) {
                    Text(
                        text = "Daily ₹${AttendanceExportHelper.formatMoneyPlain(employee.dailyWage)} · OT ₹${AttendanceExportHelper.formatMoneyPlain(employee.otHourlyRate)}/hr",
                        style = MaterialTheme.typography.labelSmall.copy(fontFamily = JetBrainsMono),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = "$shiftCount entries",
                        style = MaterialTheme.typography.labelSmall.copy(fontFamily = JetBrainsMono),
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = "$fullDayCount full",
                        style = MaterialTheme.typography.labelSmall.copy(fontFamily = JetBrainsMono),
                        color = semantic.fullDayAccent,
                        fontWeight = FontWeight.SemiBold
                    )
                    if (absentLeaveCount > 0) {
                        Text(
                            text = "$absentLeaveCount abs/lve",
                            style = MaterialTheme.typography.labelSmall.copy(fontFamily = JetBrainsMono),
                            color = semantic.shortDayAccent,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                    Text(
                        text = "OT ${AttendanceCalculator.formatDurationCompact(overtimeMinutes)}",
                        style = MaterialTheme.typography.labelSmall.copy(fontFamily = JetBrainsMono),
                        color = semantic.overtimeAccent,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
            IconButton(
                onClick = onResetPasswordClick,
                modifier = Modifier
                    .minimumInteractiveComponentSize()
                    .testTag("reset_password_button_${employee.id}")
            ) {
                Icon(
                    imageVector = Icons.Filled.Key,
                    contentDescription = stringResource(R.string.reset_password_action),
                    tint = MaterialTheme.colorScheme.tertiary
                )
            }
            IconButton(
                onClick = onEditClick,
                modifier = Modifier
                    .minimumInteractiveComponentSize()
                    .testTag("edit_employee_button_${employee.id}")
            ) {
                Icon(
                    imageVector = Icons.Filled.Edit,
                    contentDescription = stringResource(R.string.edit_employee_dialog_title),
                    tint = MaterialTheme.colorScheme.primary
                )
            }
            IconButton(
                onClick = onDeleteClick,
                modifier = Modifier
                    .minimumInteractiveComponentSize()
                    .testTag("delete_employee_button_${employee.id}")
            ) {
                Icon(
                    imageVector = Icons.Filled.DeleteOutline,
                    contentDescription = stringResource(R.string.remove_action),
                    tint = MaterialTheme.colorScheme.error
                )
            }
        }
    }
}
