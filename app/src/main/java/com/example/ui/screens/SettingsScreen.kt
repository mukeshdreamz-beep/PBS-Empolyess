package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.AppThemeMode
import com.example.data.AttendancePolicy
import com.example.domain.AttendanceCalculator
import com.example.ui.AttendanceUiState
import com.example.ui.components.TimePickerModalDialog
import com.example.ui.theme.JetBrainsMono

@Composable
fun SettingsScreen(
    uiState: AttendanceUiState,
    onSavePolicy: (AttendancePolicy) -> Unit,
    onThemeModeChange: (AppThemeMode) -> Unit,
    onResetDefaults: () -> Unit,
    onLoadSampleWorkforce: () -> Unit,
    onUpdateAuthorizedAdminEmails: (String) -> Unit = {},
    onGoogleSignOut: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val policy = uiState.policy
    var defaultIn by remember(policy) { mutableIntStateOf(policy.defaultInMinutes) }
    var defaultOut by remember(policy) { mutableIntStateOf(policy.defaultOutMinutes) }
    var otStart by remember(policy) { mutableIntStateOf(policy.overtimeStartMinutes) }
    var fullDayHoursText by remember(policy) {
        mutableStateOf((policy.fullDayThresholdMinutes / 60).toString())
    }
    var fullDayMinsText by remember(policy) {
        mutableStateOf((policy.fullDayThresholdMinutes % 60).toString())
    }
    var breakMinsText by remember(policy) {
        mutableStateOf(policy.breakDeductionMinutes.toString())
    }
    val remoteEmailsJoined = uiState.workspaceConfig?.authorizedAdminEmails?.joinToString(", ").orEmpty()
    var coAdminEmailsText by remember(remoteEmailsJoined) {
        mutableStateOf(remoteEmailsJoined)
    }
    var pickerTarget by remember { mutableStateOf<String?>(null) }

    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.TopCenter
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = 700.dp)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Cloud Sync & Workspace Card
            Card(
                shape = MaterialTheme.shapes.large,
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.CloudSync,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = stringResource(R.string.cloud_sync_card_title),
                            style = MaterialTheme.typography.titleMedium
                        )
                    }
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        shape = MaterialTheme.shapes.small,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text(
                                text = "${stringResource(R.string.workspace_code_label)}: ${uiState.authSession.workspaceId}",
                                style = MaterialTheme.typography.labelLarge.copy(fontFamily = JetBrainsMono),
                                fontWeight = FontWeight.Bold
                            )
                            if (uiState.googleUserEmail.isNotBlank()) {
                                Text(
                                    text = stringResource(R.string.google_account_connected, uiState.googleUserEmail),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }

                    OutlinedTextField(
                        value = coAdminEmailsText,
                        onValueChange = { coAdminEmailsText = it },
                        label = { Text(stringResource(R.string.co_admin_emails_label)) },
                        supportingText = { Text(stringResource(R.string.co_admin_emails_hint)) },
                        singleLine = false,
                        maxLines = 3,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("co_admin_emails_input")
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        OutlinedButton(
                            onClick = onGoogleSignOut,
                            modifier = Modifier
                                .weight(1f)
                                .minimumInteractiveComponentSize()
                                .testTag("settings_switch_google_button")
                        ) {
                            Icon(Icons.Filled.SwapHoriz, null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(stringResource(R.string.google_signout_button))
                        }
                        Button(
                            onClick = { onUpdateAuthorizedAdminEmails(coAdminEmailsText) },
                            modifier = Modifier
                                .weight(1f)
                                .minimumInteractiveComponentSize()
                                .testTag("save_co_admin_emails_button")
                        ) {
                            Icon(Icons.Filled.Save, null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(stringResource(R.string.save_action))
                        }
                    }
                }
            }

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
                    Text(
                        text = stringResource(R.string.theme_mode_label),
                        style = MaterialTheme.typography.titleMedium
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        AppThemeMode.entries.forEach { mode ->
                            FilterChip(
                                selected = policy.themeMode == mode,
                                onClick = { onThemeModeChange(mode) },
                                label = { Text(mode.name.lowercase().replaceFirstChar { it.uppercase() }) },
                                modifier = Modifier.testTag("theme_mode_${mode.name.lowercase()}")
                            )
                        }
                    }
                }
            }

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
                    Text(
                        text = stringResource(R.string.policy_settings_title),
                        style = MaterialTheme.typography.titleMedium
                    )

                    SettingsTimeRow(
                        label = stringResource(R.string.policy_default_in),
                        minutes = defaultIn,
                        onClick = { pickerTarget = "in" }
                    )
                    SettingsTimeRow(
                        label = stringResource(R.string.policy_default_out),
                        minutes = defaultOut,
                        onClick = { pickerTarget = "out" }
                    )
                    SettingsTimeRow(
                        label = stringResource(R.string.policy_ot_start),
                        minutes = otStart,
                        onClick = { pickerTarget = "ot" }
                    )

                    Text(
                        text = stringResource(R.string.policy_full_day_hours),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = fullDayHoursText,
                            onValueChange = { fullDayHoursText = it.filter(Char::isDigit).take(2) },
                            label = { Text("Hours") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedTextField(
                            value = fullDayMinsText,
                            onValueChange = { fullDayMinsText = it.filter(Char::isDigit).take(2) },
                            label = { Text("Mins") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                    }

                    OutlinedTextField(
                        value = breakMinsText,
                        onValueChange = { breakMinsText = it.filter(Char::isDigit).take(3) },
                        label = { Text(stringResource(R.string.policy_break_minutes)) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        OutlinedButton(
                            onClick = onResetDefaults,
                            modifier = Modifier
                                .weight(1f)
                                .minimumInteractiveComponentSize()
                        ) {
                            Icon(Icons.Filled.RestartAlt, null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(stringResource(R.string.policy_reset_defaults))
                        }
                        Button(
                            onClick = {
                                val hrs = (fullDayHoursText.toIntOrNull() ?: 8).coerceIn(1, 16)
                                val mins = (fullDayMinsText.toIntOrNull() ?: 45).coerceIn(0, 59)
                                val breakMins = (breakMinsText.toIntOrNull() ?: 0).coerceIn(0, 180)
                                onSavePolicy(
                                    AttendancePolicy(
                                        defaultInMinutes = defaultIn,
                                        defaultOutMinutes = defaultOut,
                                        fullDayThresholdMinutes = hrs * 60 + mins,
                                        overtimeStartMinutes = otStart,
                                        breakDeductionMinutes = breakMins,
                                        themeMode = policy.themeMode
                                    )
                                )
                            },
                            modifier = Modifier
                                .weight(1f)
                                .minimumInteractiveComponentSize()
                                .testTag("save_settings_policy_button")
                        ) {
                            Icon(Icons.Filled.Save, null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(stringResource(R.string.save_action))
                        }
                    }
                }
            }

            Card(
                shape = MaterialTheme.shapes.large,
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = "Sample Workforce & Site Data",
                        style = MaterialTheme.typography.titleMedium
                    )
                    Text(
                        text = "Populate sample employees across Sai Baba Nagar, Meditya Nagar, and Other Sites with attendance, expenses, and pending approvals.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    OutlinedButton(
                        onClick = onLoadSampleWorkforce,
                        modifier = Modifier
                            .fillMaxWidth()
                            .minimumInteractiveComponentSize()
                            .testTag("settings_load_sample_button")
                    ) {
                        Icon(Icons.Filled.AutoAwesome, null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.load_demo_data_button))
                    }
                }
            }
        }
    }

    pickerTarget?.let { target ->
        val initial = when (target) {
            "in" -> defaultIn
            "out" -> defaultOut
            else -> otStart
        }
        TimePickerModalDialog(
            title = stringResource(R.string.policy_settings_title),
            initialMinutes = initial,
            onDismiss = { pickerTarget = null },
            onConfirm = { picked ->
                when (target) {
                    "in" -> defaultIn = picked
                    "out" -> defaultOut = picked
                    else -> otStart = picked
                }
                pickerTarget = null
            }
        )
    }
}

@Composable
private fun SettingsTimeRow(
    label: String,
    minutes: Int,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface
        )
        OutlinedButton(
            onClick = onClick,
            modifier = Modifier.minimumInteractiveComponentSize()
        ) {
            Text(
                text = AttendanceCalculator.formatTime12h(minutes),
                style = MaterialTheme.typography.labelLarge.copy(fontFamily = JetBrainsMono)
            )
        }
    }
}
