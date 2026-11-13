package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Summarize
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.domain.AttendanceCalculator
import com.example.domain.EmployeeMonthlySummary
import com.example.ui.AttendanceUiState
import com.example.ui.components.EmployeeInitialAvatar
import com.example.ui.theme.JetBrainsMono
import com.example.ui.theme.LocalAttendanceColors

@Composable
fun SummaryScreen(
    uiState: AttendanceUiState,
    onShiftMonth: (Int) -> Unit,
    onExportMonthlyCsv: () -> Unit,
    onShareMonthlySummary: () -> Unit,
    onSelectEmployeeRecords: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    val monthLabel = uiState.summaryMonth.displayLabel()
    val hasAnyEntries = uiState.monthlySummaries.any { it.totalEntries > 0 }

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
                Column(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = stringResource(R.string.monthly_report_title),
                                style = MaterialTheme.typography.titleMedium
                            )
                            Text(
                                text = stringResource(R.string.monthly_report_subtitle),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconButton(
                                onClick = { onShiftMonth(-1) },
                                modifier = Modifier
                                    .minimumInteractiveComponentSize()
                                    .testTag("summary_prev_month_button")
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                                    contentDescription = stringResource(R.string.prev_month_cd)
                                )
                            }
                            Text(
                                text = monthLabel,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                            IconButton(
                                onClick = { onShiftMonth(1) },
                                modifier = Modifier
                                    .minimumInteractiveComponentSize()
                                    .testTag("summary_next_month_button")
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                                    contentDescription = stringResource(R.string.next_month_cd)
                                )
                            }
                        }

                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(
                                onClick = onShareMonthlySummary,
                                enabled = hasAnyEntries,
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                                modifier = Modifier
                                    .minimumInteractiveComponentSize()
                                    .testTag("share_monthly_summary_button")
                            ) {
                                Icon(Icons.Filled.Share, null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(5.dp))
                                Text(stringResource(R.string.share_summary_button))
                            }
                            FilledTonalButton(
                                onClick = onExportMonthlyCsv,
                                enabled = hasAnyEntries,
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                                modifier = Modifier
                                    .minimumInteractiveComponentSize()
                                    .testTag("export_monthly_csv_button")
                            ) {
                                Icon(Icons.Filled.Download, null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(5.dp))
                                Text(stringResource(R.string.export_csv_button))
                            }
                        }
                    }
                }
            }

            if (uiState.monthlySummaries.isEmpty() || !hasAnyEntries) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = Icons.Filled.Summarize,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(56.dp)
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = stringResource(R.string.no_monthly_data_title),
                        style = MaterialTheme.typography.titleMedium,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = stringResource(R.string.no_monthly_data_subtitle),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.widthIn(max = 320.dp)
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .testTag("summary_lazy_list"),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(
                        items = uiState.monthlySummaries,
                        key = { it.employeeId }
                    ) { summary ->
                        EmployeeMonthlySummaryCard(
                            summary = summary,
                            onClick = { onSelectEmployeeRecords(summary.employeeId) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun EmployeeMonthlySummaryCard(
    summary: EmployeeMonthlySummary,
    onClick: () -> Unit
) {
    val semantic = LocalAttendanceColors.current
    Card(
        onClick = onClick,
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("monthly_summary_card_${summary.employeeId}")
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                EmployeeInitialAvatar(name = summary.employeeName)
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = if (summary.employeeCode.isNotBlank()) {
                            "${summary.employeeName} (${summary.employeeCode})"
                        } else {
                            summary.employeeName
                        },
                        style = MaterialTheme.typography.titleMedium
                    )
                    if (summary.employeeRole.isNotBlank()) {
                        Text(
                            text = summary.employeeRole,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                Text(
                    text = "${summary.totalEntries} days",
                    style = MaterialTheme.typography.labelMedium.copy(fontFamily = JetBrainsMono),
                    color = MaterialTheme.colorScheme.primary
                )
            }

            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
                shape = MaterialTheme.shapes.small,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    SummaryStatColumn(
                        label = "Full",
                        value = summary.fullDays.toString(),
                        color = semantic.fullDayAccent
                    )
                    SummaryStatColumn(
                        label = "Short",
                        value = summary.shortDays.toString(),
                        color = semantic.shortDayAccent
                    )
                    SummaryStatColumn(
                        label = "Abs/Lve",
                        value = (summary.absentDays + summary.leaveDays).toString(),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    SummaryStatColumn(
                        label = "Worked",
                        value = AttendanceCalculator.formatDurationCompact(summary.totalWorkedMinutes),
                        color = MaterialTheme.colorScheme.primary
                    )
                    SummaryStatColumn(
                        label = "OT",
                        value = AttendanceCalculator.formatDurationCompact(summary.totalOvertimeMinutes),
                        color = semantic.overtimeAccent
                    )
                }
            }
        }
    }
}

@Composable
private fun SummaryStatColumn(
    label: String,
    value: String,
    color: androidx.compose.ui.graphics.Color
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = MaterialTheme.typography.titleSmall.copy(fontFamily = JetBrainsMono),
            fontWeight = FontWeight.Bold,
            color = color
        )
    }
}
