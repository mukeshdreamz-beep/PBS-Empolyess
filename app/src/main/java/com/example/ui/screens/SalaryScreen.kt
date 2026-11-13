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
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
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
import com.example.domain.AttendanceExportHelper
import com.example.domain.EmployeeMonthlySummary
import com.example.ui.AttendanceUiState
import com.example.ui.components.EmployeeInitialAvatar
import com.example.ui.theme.JetBrainsMono
import com.example.ui.theme.LocalAttendanceColors

@Composable
fun SalaryScreen(
    uiState: AttendanceUiState,
    onShiftMonth: (Int) -> Unit,
    onExportPayrollCsv: () -> Unit,
    onSharePayrollSummary: () -> Unit,
    modifier: Modifier = Modifier
) {
    val semantic = LocalAttendanceColors.current
    val totalPayroll = uiState.monthlySummaries.sumOf { it.netPayable }
    val totalOtPay = uiState.monthlySummaries.sumOf { it.overtimePay }

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
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = stringResource(R.string.salary_title),
                        style = MaterialTheme.typography.titleMedium
                    )
                    Text(
                        text = stringResource(R.string.salary_subtitle),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconButton(
                                onClick = { onShiftMonth(-1) },
                                modifier = Modifier.minimumInteractiveComponentSize()
                            ) {
                                Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, stringResource(R.string.prev_month_cd))
                            }
                            Text(
                                text = uiState.summaryMonth.displayLabel(),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                            IconButton(
                                onClick = { onShiftMonth(1) },
                                modifier = Modifier.minimumInteractiveComponentSize()
                            ) {
                                Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, stringResource(R.string.next_month_cd))
                            }
                        }

                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(
                                onClick = onSharePayrollSummary,
                                enabled = uiState.monthlySummaries.isNotEmpty(),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                                modifier = Modifier.minimumInteractiveComponentSize()
                            ) {
                                Icon(Icons.Filled.Share, null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(4.dp))
                                Text(stringResource(R.string.share_summary_button))
                            }
                            FilledTonalButton(
                                onClick = onExportPayrollCsv,
                                enabled = uiState.monthlySummaries.isNotEmpty(),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                                modifier = Modifier
                                    .minimumInteractiveComponentSize()
                                    .testTag("export_payroll_csv_button")
                            ) {
                                Icon(Icons.Filled.Download, null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(4.dp))
                                Text(stringResource(R.string.export_payroll_csv))
                            }
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Card(
                            colors = CardDefaults.cardColors(containerColor = semantic.kpiBlueContainer),
                            border = BorderStroke(1.dp, semantic.kpiBlueBorder),
                            modifier = Modifier.weight(1f)
                        ) {
                            Column(Modifier.padding(12.dp)) {
                                Text("Total Net Payroll", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text(
                                    text = "₹${AttendanceExportHelper.formatMoneyPlain(totalPayroll)}",
                                    style = MaterialTheme.typography.titleMedium.copy(fontFamily = JetBrainsMono),
                                    fontWeight = FontWeight.Bold,
                                    color = semantic.kpiBlueValue
                                )
                            }
                        }
                        Card(
                            colors = CardDefaults.cardColors(containerColor = semantic.overtimeContainer),
                            border = BorderStroke(1.dp, semantic.overtimeBorder),
                            modifier = Modifier.weight(1f)
                        ) {
                            Column(Modifier.padding(12.dp)) {
                                Text("Total OT Pay", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text(
                                    text = "₹${AttendanceExportHelper.formatMoneyPlain(totalOtPay)}",
                                    style = MaterialTheme.typography.titleMedium.copy(fontFamily = JetBrainsMono),
                                    fontWeight = FontWeight.Bold,
                                    color = semantic.overtimeAccent
                                )
                            }
                        }
                    }
                }
            }

            if (uiState.monthlySummaries.isEmpty()) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = Icons.Filled.Payments,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(56.dp)
                    )
                    Spacer(Modifier.height(12.dp))
                    Text(
                        text = stringResource(R.string.no_employees_yet),
                        style = MaterialTheme.typography.titleMedium,
                        textAlign = TextAlign.Center
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(uiState.monthlySummaries, key = { it.employeeId }) { item ->
                        SalaryBreakdownCard(summary = item)
                    }
                }
            }
        }
    }
}

@Composable
private fun SalaryBreakdownCard(summary: EmployeeMonthlySummary) {
    val semantic = LocalAttendanceColors.current
    Card(
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    EmployeeInitialAvatar(name = summary.employeeName)
                    Column {
                        Text(summary.employeeName, style = MaterialTheme.typography.titleMedium)
                        Text(
                            text = "${summary.employeeSite} · Daily ₹${AttendanceExportHelper.formatMoneyPlain(summary.dailyWage)} · OT ₹${AttendanceExportHelper.formatMoneyPlain(summary.otHourlyRate)}/hr",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                Text(
                    text = "₹${AttendanceExportHelper.formatMoneyPlain(summary.netPayable)}",
                    style = MaterialTheme.typography.titleLarge.copy(fontFamily = JetBrainsMono),
                    fontWeight = FontWeight.Bold,
                    color = semantic.fullDayAccent
                )
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text("Days (Full/Short)", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(
                        "${summary.fullDays}F / ${summary.shortDays}S",
                        style = MaterialTheme.typography.labelLarge.copy(fontFamily = JetBrainsMono)
                    )
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Base Pay", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(
                        "₹${AttendanceExportHelper.formatMoneyPlain(summary.baseEarnings)}",
                        style = MaterialTheme.typography.labelLarge.copy(fontFamily = JetBrainsMono)
                    )
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("OT (${AttendanceCalculator.formatDurationCompact(summary.totalOvertimeMinutes)})", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(
                        "₹${AttendanceExportHelper.formatMoneyPlain(summary.overtimePay)}",
                        style = MaterialTheme.typography.labelLarge.copy(fontFamily = JetBrainsMono),
                        color = semantic.overtimeAccent
                    )
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text("Expenses", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(
                        "₹${AttendanceExportHelper.formatMoneyPlain(summary.approvedExpenses)}",
                        style = MaterialTheme.typography.labelLarge.copy(fontFamily = JetBrainsMono)
                    )
                }
            }
        }
    }
}
