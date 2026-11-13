package com.example.ui.screens

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.data.AttendanceRecordItem
import com.example.domain.YearMonthKey
import com.example.ui.AttendanceUiState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReportsScreen(
    uiState: AttendanceUiState,
    onFilterEmployeeChange: (Long?) -> Unit,
    onFilterMonthChange: (YearMonthKey?) -> Unit,
    onSearchQueryChange: (String) -> Unit,
    onShiftSummaryMonth: (Int) -> Unit,
    onExportFilteredCsv: () -> Unit,
    onExportMonthlyCsv: () -> Unit,
    onShareMonthlySummary: () -> Unit,
    onEditRecord: (AttendanceRecordItem) -> Unit,
    onDeleteRecord: (AttendanceRecordItem) -> Unit,
    modifier: Modifier = Modifier
) {
    var subTab by remember { mutableIntStateOf(0) } // 0 = Attendance Log, 1 = Monthly Summary

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
                        selected = subTab == 0,
                        onClick = { subTab = 0 },
                        shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
                        modifier = Modifier.testTag("reports_log_tab")
                    ) {
                        Text("Attendance Log")
                    }
                    SegmentedButton(
                        selected = subTab == 1,
                        onClick = { subTab = 1 },
                        shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
                        modifier = Modifier.testTag("reports_monthly_tab")
                    ) {
                        Text("Monthly Summary")
                    }
                }
            }
        }

        if (subTab == 0) {
            RecordsScreen(
                uiState = uiState,
                onFilterEmployeeChange = onFilterEmployeeChange,
                onFilterMonthChange = onFilterMonthChange,
                onSearchQueryChange = onSearchQueryChange,
                onExportFilteredCsv = onExportFilteredCsv,
                onEditRecord = onEditRecord,
                onDeleteRecord = onDeleteRecord,
                modifier = Modifier.weight(1f)
            )
        } else {
            SummaryScreen(
                uiState = uiState,
                onShiftMonth = onShiftSummaryMonth,
                onExportMonthlyCsv = onExportMonthlyCsv,
                onShareMonthlySummary = onShareMonthlySummary,
                onSelectEmployeeRecords = { empId ->
                    onFilterEmployeeChange(empId)
                    onFilterMonthChange(uiState.summaryMonth)
                    subTab = 0
                },
                modifier = Modifier.weight(1f)
            )
        }
    }
}
