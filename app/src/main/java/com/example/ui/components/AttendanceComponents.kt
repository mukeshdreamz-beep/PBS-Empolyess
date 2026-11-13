package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.EventBusy
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.data.AttendancePolicy
import com.example.data.AttendanceRecordItem
import com.example.data.EmployeeEntity
import com.example.domain.AttendanceCalculator
import com.example.domain.AttendanceExportHelper
import com.example.domain.AttendanceStatus
import com.example.domain.AuthUtils
import com.example.domain.SelfieLocationHelper
import com.example.ui.GeneratedCredentialsDialogState
import com.example.ui.theme.JetBrainsMono
import com.example.ui.theme.LocalAttendanceColors

@Composable
fun StatusPill(
    status: AttendanceStatus,
    isFullDay: Boolean,
    isInProgress: Boolean = false,
    modifier: Modifier = Modifier
) {
    val semantic = LocalAttendanceColors.current
    val (containerColor, contentColor, icon, label) = when (status) {
        AttendanceStatus.ABSENT -> Quad(
            semantic.shortDayContainer,
            semantic.onShortDayContainer,
            Icons.Filled.EventBusy,
            stringResource(R.string.status_absent_badge)
        )
        AttendanceStatus.LEAVE -> Quad(
            MaterialTheme.colorScheme.secondaryContainer,
            MaterialTheme.colorScheme.onSecondaryContainer,
            Icons.Filled.EventBusy,
            stringResource(R.string.status_leave_badge)
        )
        AttendanceStatus.PRESENT -> when {
            isInProgress -> Quad(
                MaterialTheme.colorScheme.primaryContainer,
                MaterialTheme.colorScheme.onPrimaryContainer,
                Icons.Filled.Schedule,
                stringResource(R.string.shift_in_progress)
            )
            isFullDay -> Quad(
                semantic.fullDayContainer,
                semantic.onFullDayContainer,
                Icons.Filled.CheckCircle,
                stringResource(R.string.status_full_day)
            )
            else -> Quad(
                semantic.shortDayContainer,
                semantic.onShortDayContainer,
                Icons.Filled.WarningAmber,
                stringResource(R.string.status_less_than_full)
            )
        }
    }

    Surface(
        color = containerColor,
        contentColor = contentColor,
        shape = RoundedCornerShape(50),
        modifier = modifier
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp),
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(14.dp)
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

private data class Quad<A, B, C, D>(val first: A, val second: B, val third: C, val fourth: D)

@Composable
fun OvertimeBadge(
    overtimeMinutes: Int,
    modifier: Modifier = Modifier
) {
    val semantic = LocalAttendanceColors.current
    val hasOt = overtimeMinutes > 0
    val containerColor = if (hasOt) semantic.overtimeContainer else MaterialTheme.colorScheme.surfaceVariant
    val contentColor = if (hasOt) semantic.onOvertimeContainer else MaterialTheme.colorScheme.onSurfaceVariant

    Surface(
        color = containerColor,
        contentColor = contentColor,
        shape = RoundedCornerShape(50),
        modifier = modifier
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
        ) {
            Icon(
                imageVector = Icons.Filled.AccessTime,
                contentDescription = null,
                modifier = Modifier.size(13.dp)
            )
            Text(
                text = "OT ${AttendanceCalculator.formatDurationCompact(overtimeMinutes)}",
                style = MaterialTheme.typography.labelSmall.copy(fontFamily = JetBrainsMono),
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
fun AttendanceRecordCard(
    record: AttendanceRecordItem,
    policy: AttendancePolicy,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    allowModify: Boolean = true,
    modifier: Modifier = Modifier
) {
    val status = remember(record.status) { AttendanceStatus.fromStorage(record.status) }
    val isInProgress = status == AttendanceStatus.PRESENT && record.hasCheckedIn && !record.hasCheckedOut
    val breakdown = remember(record.inMinutes, record.outMinutes, status, policy, isInProgress) {
        if (isInProgress) {
            AttendanceCalculator.calculateBreakdown(
                inMinutes = record.inMinutes,
                outMinutes = record.inMinutes,
                status = status,
                policy = policy
            )
        } else {
            AttendanceCalculator.calculateBreakdown(
                inMinutes = record.inMinutes,
                outMinutes = record.outMinutes,
                status = status,
                policy = policy
            )
        }
    }
    var showProofDialog by remember { mutableStateOf(false) }
    val inThumbBitmap = remember(record.checkInPhotoBase64) {
        SelfieLocationHelper.decodeBase64ToImageBitmap(record.checkInPhotoBase64)
    }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag("attendance_record_card_${record.id}"),
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    EmployeeInitialAvatar(name = record.employeeName)
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = if (record.employeeCode.isNotBlank()) {
                                "${record.employeeName} (${record.employeeCode})"
                            } else {
                                record.employeeName
                            },
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        val meta = listOf(record.date, record.employeeSite, record.employeeRole)
                            .filter { it.isNotBlank() }
                            .joinToString(" · ")
                        Text(
                            text = meta,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
                if (allowModify) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(
                            onClick = onEdit,
                            modifier = Modifier
                                .minimumInteractiveComponentSize()
                                .testTag("edit_record_button_${record.id}")
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Edit,
                                contentDescription = stringResource(R.string.edit_record_action),
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                        IconButton(
                            onClick = onDelete,
                            modifier = Modifier
                                .minimumInteractiveComponentSize()
                                .testTag("delete_record_button_${record.id}")
                        ) {
                            Icon(
                                imageVector = Icons.Filled.DeleteOutline,
                                contentDescription = stringResource(R.string.delete_record_action),
                                tint = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                }
            }

            if (status == AttendanceStatus.PRESENT) {
                Spacer(modifier = Modifier.height(12.dp))
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                    shape = MaterialTheme.shapes.small,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = stringResource(R.string.time_in_label),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = AttendanceCalculator.formatTime12h(record.inMinutes),
                                style = MaterialTheme.typography.titleSmall.copy(fontFamily = JetBrainsMono),
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                        Icon(
                            imageVector = Icons.Filled.Schedule,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(16.dp)
                        )
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = stringResource(R.string.time_out_label),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = if (isInProgress) {
                                    stringResource(R.string.out_pending_label)
                                } else {
                                    AttendanceCalculator.formatTime12h(record.outMinutes)
                                },
                                style = MaterialTheme.typography.titleSmall.copy(fontFamily = JetBrainsMono),
                                color = if (isInProgress) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                            )
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Text(
                                text = stringResource(R.string.working_time_label),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = if (isInProgress) {
                                    stringResource(R.string.out_pending_label)
                                } else {
                                    AttendanceCalculator.formatDurationCompact(breakdown.workedMinutes)
                                },
                                style = MaterialTheme.typography.titleSmall.copy(fontFamily = JetBrainsMono),
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                StatusPill(
                    status = status,
                    isFullDay = breakdown.isFullDay,
                    isInProgress = isInProgress
                )
                if (status == AttendanceStatus.PRESENT && !isInProgress) {
                    OvertimeBadge(overtimeMinutes = breakdown.overtimeMinutes)
                }
            }

            if (record.hasAnyProof || record.markedByRole == "EMPLOYEE") {
                Spacer(modifier = Modifier.height(10.dp))
                val locText = record.checkOutAddress.ifBlank { record.checkInAddress }.ifBlank {
                    val lat = record.checkOutLat ?: record.checkInLat
                    val lng = record.checkOutLng ?: record.checkInLng
                    if (lat != null && lng != null) SelfieLocationHelper.formatCoords(lat, lng) else ""
                }
                Surface(
                    onClick = { showProofDialog = true },
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f),
                    shape = MaterialTheme.shapes.small,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("view_proof_button_${record.id}")
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 10.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        if (inThumbBitmap != null) {
                            Image(
                                bitmap = inThumbBitmap,
                                contentDescription = stringResource(R.string.captured_selfie_cd),
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .size(38.dp)
                                    .clip(RoundedCornerShape(8.dp))
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Filled.CameraAlt,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = stringResource(R.string.view_proof_button),
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                            if (locText.isNotBlank()) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(3.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.LocationOn,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(13.dp)
                                    )
                                    Text(
                                        text = locText,
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

            if (record.note.isNotBlank()) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = record.note,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }

    if (showProofDialog) {
        AttendanceProofDialog(
            record = record,
            onDismiss = { showProofDialog = false }
        )
    }
}

@Composable
fun EmployeeInitialAvatar(
    name: String,
    modifier: Modifier = Modifier
) {
    val initial = name.trim().firstOrNull()?.uppercaseChar()?.toString() ?: "?"
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .size(40.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.primaryContainer)
    ) {
        Text(
            text = initial,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onPrimaryContainer
        )
    }
}

@Composable
fun TimeSelectionTile(
    label: String,
    minutes: Int,
    enabled: Boolean = true,
    onClick: () -> Unit,
    onPunchNow: () -> Unit,
    testTag: String,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier
            .heightIn(min = 84.dp)
            .clip(MaterialTheme.shapes.medium)
            .clickable(enabled = enabled, onClick = onClick)
            .testTag(testTag),
        shape = MaterialTheme.shapes.medium,
        color = if (enabled) {
            MaterialTheme.colorScheme.surface
        } else {
            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        },
        border = BorderStroke(
            1.dp,
            if (enabled) MaterialTheme.colorScheme.outline else MaterialTheme.colorScheme.outlineVariant
        )
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(5.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.Schedule,
                        contentDescription = null,
                        tint = if (enabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(15.dp)
                    )
                    Text(
                        text = label,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontWeight = FontWeight.Bold
                    )
                }
                if (enabled) {
                    Surface(
                        onClick = onPunchNow,
                        color = MaterialTheme.colorScheme.primaryContainer,
                        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                        shape = RoundedCornerShape(50),
                        modifier = Modifier.testTag("${testTag}_now")
                    ) {
                        Text(
                            text = stringResource(R.string.punch_now_button),
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = if (enabled) AttendanceCalculator.formatTime12h(minutes) else "--:--",
                style = MaterialTheme.typography.titleLarge.copy(
                    fontFamily = JetBrainsMono,
                    fontSize = 19.sp
                ),
                fontWeight = FontWeight.Bold,
                color = if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
fun EmployeeFormDialog(
    existingEmployee: EmployeeEntity? = null,
    availableSites: List<String> = EmployeeEntity.PRESET_SITES,
    existingLoginIds: Set<String> = emptySet(),
    existingEmployeeCount: Int = 0,
    onDismiss: () -> Unit,
    onConfirm: (
        name: String,
        code: String,
        site: String,
        role: String,
        phone: String,
        dailyWage: Double,
        otHourlyRate: Double,
        loginId: String,
        password: String
    ) -> Unit
) {
    var name by remember(existingEmployee) { mutableStateOf(existingEmployee?.name ?: "") }
    var code by remember(existingEmployee) { mutableStateOf(existingEmployee?.employeeCode ?: "") }
    var site by remember(existingEmployee) {
        mutableStateOf(existingEmployee?.site ?: EmployeeEntity.DEFAULT_SITE)
    }
    var role by remember(existingEmployee) { mutableStateOf(existingEmployee?.role ?: "") }
    var phone by remember(existingEmployee) { mutableStateOf(existingEmployee?.phone ?: "") }
    var dailyWageText by remember(existingEmployee) {
        mutableStateOf(
            existingEmployee?.dailyWage?.takeIf { it > 0 }?.toInt()?.toString() ?: ""
        )
    }
    var otRateText by remember(existingEmployee) {
        mutableStateOf(
            existingEmployee?.otHourlyRate?.takeIf { it > 0 }?.toInt()?.toString() ?: ""
        )
    }
    var loginId by remember(existingEmployee) {
        mutableStateOf(
            existingEmployee?.loginId ?: AuthUtils.generateEmployeeLoginId(
                employeeCode = "",
                existingLoginIds = existingLoginIds,
                existingCount = existingEmployeeCount
            )
        )
    }
    var loginIdEdited by remember(existingEmployee) { mutableStateOf(false) }
    var generatedPassword by remember(existingEmployee) {
        mutableStateOf(AuthUtils.generateReadablePassword())
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                stringResource(
                    if (existingEmployee == null) {
                        R.string.add_employee_dialog_title
                    } else {
                        R.string.edit_employee_dialog_title
                    }
                )
            )
        },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(stringResource(R.string.employee_name_label)) },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("employee_name_input")
                )
                OutlinedTextField(
                    value = code,
                    onValueChange = { newCode ->
                        code = newCode
                        if (existingEmployee == null && !loginIdEdited && newCode.trim().isNotBlank()) {
                            loginId = AuthUtils.generateEmployeeLoginId(
                                employeeCode = newCode,
                                existingLoginIds = existingLoginIds,
                                existingCount = existingEmployeeCount
                            )
                        }
                    },
                    label = { Text(stringResource(R.string.employee_code_label)) },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("employee_code_input")
                )
                Text(
                    text = stringResource(R.string.employee_site_label),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    availableSites.forEach { preset ->
                        FilterChip(
                            selected = site.equals(preset, ignoreCase = true),
                            onClick = { site = preset },
                            label = { Text(preset) }
                        )
                    }
                }
                OutlinedTextField(
                    value = site,
                    onValueChange = { site = it },
                    label = { Text(stringResource(R.string.employee_site_label)) },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("employee_site_input")
                )
                OutlinedTextField(
                    value = role,
                    onValueChange = { role = it },
                    label = { Text(stringResource(R.string.employee_role_label)) },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("employee_role_input")
                )
                OutlinedTextField(
                    value = phone,
                    onValueChange = { phone = it },
                    label = { Text(stringResource(R.string.employee_phone_label)) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("employee_phone_input")
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = dailyWageText,
                        onValueChange = { dailyWageText = it.filter { ch -> ch.isDigit() || ch == '.' } },
                        label = { Text(stringResource(R.string.employee_daily_wage_label)) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = otRateText,
                        onValueChange = { otRateText = it.filter { ch -> ch.isDigit() || ch == '.' } },
                        label = { Text(stringResource(R.string.employee_ot_rate_label)) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                }

                if (existingEmployee == null) {
                    HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                    OutlinedTextField(
                        value = loginId,
                        onValueChange = {
                            loginId = it.uppercase()
                            loginIdEdited = true
                        },
                        label = { Text(stringResource(R.string.employee_login_id_label)) },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("employee_login_id_generate_input")
                    )
                    OutlinedTextField(
                        value = generatedPassword,
                        onValueChange = { generatedPassword = it },
                        label = { Text(stringResource(R.string.employee_password_label)) },
                        trailingIcon = {
                            IconButton(
                                onClick = { generatedPassword = AuthUtils.generateReadablePassword() },
                                modifier = Modifier.testTag("regenerate_employee_password_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Refresh,
                                    contentDescription = stringResource(R.string.regenerate_password_cd)
                                )
                            }
                        },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("employee_password_generate_input")
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (name.isNotBlank()) {
                        onConfirm(
                            name.trim(),
                            code.trim(),
                            site.trim().ifEmpty { EmployeeEntity.DEFAULT_SITE },
                            role.trim(),
                            phone.trim(),
                            dailyWageText.toDoubleOrNull() ?: 0.0,
                            otRateText.toDoubleOrNull() ?: 0.0,
                            loginId.trim(),
                            generatedPassword.trim()
                        )
                    }
                },
                enabled = name.isNotBlank() && (existingEmployee != null || generatedPassword.trim().length >= 4),
                modifier = Modifier
                    .minimumInteractiveComponentSize()
                    .testTag("confirm_add_employee_button")
            ) {
                Text(
                    stringResource(
                        if (existingEmployee == null) R.string.add_action else R.string.save_action
                    )
                )
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.minimumInteractiveComponentSize()
            ) {
                Text(stringResource(R.string.cancel_action))
            }
        }
    )
}

@Composable
fun ResetEmployeePasswordDialog(
    employee: EmployeeEntity,
    onDismiss: () -> Unit,
    onConfirm: (newPassword: String) -> Unit
) {
    var newPassword by remember(employee.id) {
        mutableStateOf(AuthUtils.generateReadablePassword())
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Filled.Key, contentDescription = null) },
        title = { Text(stringResource(R.string.reset_password_dialog_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = "${employee.name} (Login ID: ${employee.loginId.ifBlank { employee.employeeCode.ifBlank { "Auto" } }})",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold
                )
                OutlinedTextField(
                    value = newPassword,
                    onValueChange = { newPassword = it },
                    label = { Text(stringResource(R.string.new_password_label)) },
                    trailingIcon = {
                        IconButton(
                            onClick = { newPassword = AuthUtils.generateReadablePassword() },
                            modifier = Modifier.testTag("reset_password_regenerate_button")
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Refresh,
                                contentDescription = stringResource(R.string.regenerate_password_cd)
                            )
                        }
                    },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("reset_password_input")
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(newPassword.trim()) },
                enabled = newPassword.trim().length >= 4,
                modifier = Modifier
                    .minimumInteractiveComponentSize()
                    .testTag("confirm_reset_password_button")
            ) {
                Text(stringResource(R.string.reset_password_action))
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.minimumInteractiveComponentSize()
            ) {
                Text(stringResource(R.string.cancel_action))
            }
        }
    )
}

@Suppress("DEPRECATION")
@Composable
fun GeneratedCredentialsDialog(
    state: GeneratedCredentialsDialogState,
    onCopied: () -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val shareText = remember(state) {
        "PBS Employees App Login\n" +
            "Company Sync Code: ${state.workspaceId}\n" +
            "Employee: ${state.employeeName} (${state.site})\n" +
            "Login ID: ${state.loginId}\n" +
            "Password: ${state.plainPassword}"
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Filled.Key, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
        title = {
            Text(
                stringResource(
                    if (state.isReset) R.string.credentials_reset_title else R.string.credentials_created_title
                )
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = "${state.employeeName} · ${state.site}",
                    style = MaterialTheme.typography.titleSmall
                )
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                    shape = MaterialTheme.shapes.medium,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Sync Code", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(
                                text = state.workspaceId,
                                style = MaterialTheme.typography.titleSmall.copy(fontFamily = JetBrainsMono),
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Login ID", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(
                                text = state.loginId,
                                style = MaterialTheme.typography.titleSmall.copy(fontFamily = JetBrainsMono),
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Password", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(
                                text = state.plainPassword,
                                style = MaterialTheme.typography.titleSmall.copy(fontFamily = JetBrainsMono),
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = {
                            clipboard.setText(AnnotatedString(shareText))
                            onCopied()
                        },
                        modifier = Modifier
                            .weight(1f)
                            .minimumInteractiveComponentSize()
                            .testTag("copy_credentials_button")
                    ) {
                        Icon(Icons.Filled.ContentCopy, null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(stringResource(R.string.copy_credentials_button))
                    }
                    OutlinedButton(
                        onClick = {
                            AttendanceExportHelper.sharePlainText(
                                context = context,
                                subject = "PBS Login — ${state.employeeName}",
                                text = shareText
                            )
                        },
                        modifier = Modifier
                            .weight(1f)
                            .minimumInteractiveComponentSize()
                            .testTag("share_credentials_button")
                    ) {
                        Icon(Icons.Filled.Share, null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(stringResource(R.string.share_summary_button))
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onDismiss,
                modifier = Modifier
                    .minimumInteractiveComponentSize()
                    .testTag("close_credentials_dialog_button")
            ) {
                Text(stringResource(R.string.confirm_action))
            }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TimePickerModalDialog(
    title: String,
    initialMinutes: Int,
    onDismiss: () -> Unit,
    onConfirm: (Int) -> Unit
) {
    val state = rememberTimePickerState(
        initialHour = (initialMinutes / 60).coerceIn(0, 23),
        initialMinute = (initialMinutes % 60).coerceIn(0, 59),
        is24Hour = false
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium
            )
        },
        text = {
            Box(
                modifier = Modifier.fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                TimePicker(state = state)
            }
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(state.hour * 60 + state.minute) },
                modifier = Modifier
                    .minimumInteractiveComponentSize()
                    .testTag("confirm_time_picker_button")
            ) {
                Text(stringResource(R.string.confirm_action))
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.minimumInteractiveComponentSize()
            ) {
                Text(stringResource(R.string.cancel_action))
            }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AttendanceDatePickerModal(
    currentDateString: String,
    onDismiss: () -> Unit,
    onDateSelected: (Long) -> Unit
) {
    val initialMillis = remember(currentDateString) {
        AttendanceCalculator.parseDateMillis(currentDateString) ?: System.currentTimeMillis()
    }
    val datePickerState = rememberDatePickerState(initialSelectedDateMillis = initialMillis)

    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            Button(
                onClick = {
                    val selected = datePickerState.selectedDateMillis
                    if (selected != null) {
                        onDateSelected(selected)
                    }
                    onDismiss()
                },
                modifier = Modifier
                    .minimumInteractiveComponentSize()
                    .testTag("confirm_date_picker_button")
            ) {
                Text(stringResource(R.string.confirm_action))
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.minimumInteractiveComponentSize()
            ) {
                Text(stringResource(R.string.cancel_action))
            }
        }
    ) {
        DatePicker(state = datePickerState)
    }
}
