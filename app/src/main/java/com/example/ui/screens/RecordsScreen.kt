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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.AttendanceRecordItem
import com.example.domain.AttendanceCalculator
import com.example.domain.YearMonthKey
import com.example.ui.AttendanceUiState
import com.example.ui.components.AttendanceRecordCard
import com.example.ui.theme.JetBrainsMono
import com.example.ui.theme.LocalAttendanceColors

@Composable
fun RecordsScreen(
    uiState: AttendanceUiState,
    onFilterEmployeeChange: (Long?) -> Unit,
    onFilterMonthChange: (YearMonthKey?) -> Unit,
    onSearchQueryChange: (String) -> Unit,
    onExportFilteredCsv: () -> Unit,
    onEditRecord: (AttendanceRecordItem) -> Unit,
    onDeleteRecord: (AttendanceRecordItem) -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.TopCenter
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = 840.dp)
                .fillMaxSize()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                SummaryMetricsRow(
                    entryCount = uiState.filteredRecords.size,
                    fullDayCount = uiState.fullDayCount,
                    absentOrLeaveCount = uiState.absentOrLeaveCount,
                    totalWorkedMinutes = uiState.totalWorkedMinutes,
                    totalOvertimeMinutes = uiState.totalOvertimeMinutes
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        FilterChip(
                            selected = uiState.filterMonth == null,
                            onClick = { onFilterMonthChange(null) },
                            label = { Text(stringResource(R.string.all_months_chip)) },
                            modifier = Modifier.testTag("filter_all_months_chip")
                        )
                        val activeMonth = uiState.filterMonth ?: AttendanceCalculator.currentYearMonth()
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconButton(
                                onClick = { onFilterMonthChange(activeMonth.shift(-1)) },
                                modifier = Modifier.minimumInteractiveComponentSize()
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                                    contentDescription = stringResource(R.string.prev_month_cd)
                                )
                            }
                            FilterChip(
                                selected = uiState.filterMonth != null,
                                onClick = {
                                    onFilterMonthChange(
                                        if (uiState.filterMonth == null) {
                                            AttendanceCalculator.currentYearMonth()
                                        } else {
                                            null
                                        }
                                    )
                                },
                                label = { Text(activeMonth.displayLabel()) }
                            )
                            IconButton(
                                onClick = { onFilterMonthChange(activeMonth.shift(1)) },
                                modifier = Modifier.minimumInteractiveComponentSize()
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                                    contentDescription = stringResource(R.string.next_month_cd)
                                )
                            }
                        }
                    }

                    FilledTonalButton(
                        onClick = onExportFilteredCsv,
                        enabled = uiState.filteredRecords.isNotEmpty(),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("export_records_csv_button")
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Download,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(5.dp))
                        Text(stringResource(R.string.export_csv_button))
                    }
                }

                OutlinedTextField(
                    value = uiState.searchQuery,
                    onValueChange = onSearchQueryChange,
                    placeholder = { Text(stringResource(R.string.search_records_placeholder)) },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Filled.Search,
                            contentDescription = null
                        )
                    },
                    trailingIcon = {
                        if (uiState.searchQuery.isNotEmpty()) {
                            IconButton(
                                onClick = { onSearchQueryChange("") },
                                modifier = Modifier.minimumInteractiveComponentSize()
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Clear,
                                    contentDescription = stringResource(R.string.cancel_action)
                                )
                            }
                        }
                    },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("records_search_input")
                )

                if (uiState.employees.isNotEmpty()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        FilterChip(
                            selected = uiState.filterEmployeeId == null,
                            onClick = { onFilterEmployeeChange(null) },
                            label = { Text(stringResource(R.string.all_employees_filter)) },
                            modifier = Modifier.testTag("filter_all_employees_chip")
                        )
                        uiState.employees.forEach { employee ->
                            FilterChip(
                                selected = uiState.filterEmployeeId == employee.id,
                                onClick = {
                                    onFilterEmployeeChange(
                                        if (uiState.filterEmployeeId == employee.id) null else employee.id
                                    )
                                },
                                label = { Text(employee.name) },
                                modifier = Modifier.testTag("filter_employee_chip_${employee.id}")
                            )
                        }
                    }
                }
            }

            if (uiState.filteredRecords.isEmpty()) {
                EmptyRecordsState(
                    hasAnyRecords = uiState.allRecords.isNotEmpty(),
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp)
                )
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .testTag("records_lazy_list"),
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 24.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(
                        items = uiState.filteredRecords,
                        key = { it.id }
                    ) { record ->
                        AttendanceRecordCard(
                            record = record,
                            policy = uiState.policy,
                            onEdit = { onEditRecord(record) },
                            onDelete = { onDeleteRecord(record) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SummaryMetricsRow(
    entryCount: Int,
    fullDayCount: Int,
    absentOrLeaveCount: Int,
    totalWorkedMinutes: Int,
    totalOvertimeMinutes: Int
) {
    val semantic = LocalAttendanceColors.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        MetricChipCard(
            label = stringResource(R.string.summary_entries),
            value = entryCount.toString(),
            accentColor = MaterialTheme.colorScheme.primary
        )
        MetricChipCard(
            label = stringResource(R.string.summary_full_days),
            value = fullDayCount.toString(),
            accentColor = semantic.fullDayAccent
        )
        MetricChipCard(
            label = stringResource(R.string.summary_absent_leave),
            value = absentOrLeaveCount.toString(),
            accentColor = semantic.shortDayAccent
        )
        MetricChipCard(
            label = stringResource(R.string.summary_total_worked),
            value = AttendanceCalculator.formatDurationCompact(totalWorkedMinutes),
            accentColor = MaterialTheme.colorScheme.onSurface
        )
        MetricChipCard(
            label = stringResource(R.string.summary_total_ot),
            value = AttendanceCalculator.formatDurationCompact(totalOvertimeMinutes),
            accentColor = semantic.overtimeAccent
        )
    }
}

@Composable
private fun MetricChipCard(
    label: String,
    value: String,
    accentColor: androidx.compose.ui.graphics.Color,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.widthIn(min = 78.dp),
        shape = MaterialTheme.shapes.small,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1
            )
            Text(
                text = value,
                style = MaterialTheme.typography.titleSmall.copy(fontFamily = JetBrainsMono),
                fontWeight = FontWeight.Bold,
                color = accentColor,
                maxLines = 1
            )
        }
    }
}

@Composable
private fun EmptyRecordsState(
    hasAnyRecords: Boolean,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Image(
            painter = painterResource(id = R.drawable.img_empty_records_1794596688877),
            contentDescription = stringResource(R.string.empty_records_title),
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .size(width = 210.dp, height = 158.dp)
                .clip(MaterialTheme.shapes.large)
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = stringResource(
                if (hasAnyRecords) R.string.empty_filtered_title else R.string.empty_records_title
            ),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = stringResource(
                if (hasAnyRecords) R.string.empty_filtered_subtitle else R.string.empty_records_subtitle
            ),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.widthIn(max = 320.dp)
        )
    }
}
