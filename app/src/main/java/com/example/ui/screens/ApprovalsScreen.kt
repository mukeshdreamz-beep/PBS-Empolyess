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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
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
import com.example.data.ApprovalRequestEntity
import com.example.data.ExpenseEntity
import com.example.domain.AttendanceCalculator
import com.example.domain.AttendanceExportHelper
import com.example.ui.AttendanceUiState
import com.example.ui.theme.LocalAttendanceColors

@Composable
fun ApprovalsScreen(
    uiState: AttendanceUiState,
    onFilterChange: (String) -> Unit,
    onResolveApproval: (ApprovalRequestEntity, Boolean) -> Unit,
    onResolveExpense: (Long, Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    val filter = uiState.approvalFilter
    val filteredRequests = uiState.approvals.filter {
        when (filter) {
            "PENDING" -> it.status == "PENDING"
            "RESOLVED" -> it.status != "PENDING"
            else -> true
        }
    }
    val filteredExpenses = uiState.expenses.filter {
        when (filter) {
            "PENDING" -> it.status == "PENDING"
            "RESOLVED" -> it.status != "PENDING"
            else -> true
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
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 1.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = stringResource(R.string.approvals_title),
                        style = MaterialTheme.typography.titleMedium
                    )
                    Text(
                        text = stringResource(R.string.approvals_subtitle),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(
                            selected = filter == "PENDING",
                            onClick = { onFilterChange("PENDING") },
                            label = { Text("Pending (${uiState.dashboardPendingCount})") }
                        )
                        FilterChip(
                            selected = filter == "RESOLVED",
                            onClick = { onFilterChange("RESOLVED") },
                            label = { Text("Resolved") }
                        )
                        FilterChip(
                            selected = filter == "ALL",
                            onClick = { onFilterChange("ALL") },
                            label = { Text("All") }
                        )
                    }
                }
            }

            if (filteredRequests.isEmpty() && filteredExpenses.isEmpty()) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = Icons.Filled.CheckCircle,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(56.dp)
                    )
                    Spacer(Modifier.height(12.dp))
                    Text(
                        text = stringResource(R.string.empty_approvals_title),
                        style = MaterialTheme.typography.titleMedium,
                        textAlign = TextAlign.Center
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = stringResource(R.string.empty_approvals_subtitle),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(filteredRequests, key = { "req_${it.id}" }) { req ->
                        ApprovalRequestCard(
                            request = req,
                            onApprove = { onResolveApproval(req, true) },
                            onReject = { onResolveApproval(req, false) }
                        )
                    }
                    items(filteredExpenses, key = { "exp_${it.id}" }) { exp ->
                        ExpenseApprovalCard(
                            expense = exp,
                            onApprove = { onResolveExpense(exp.id, true) },
                            onReject = { onResolveExpense(exp.id, false) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ApprovalRequestCard(
    request: ApprovalRequestEntity,
    onApprove: () -> Unit,
    onReject: () -> Unit
) {
    val semantic = LocalAttendanceColors.current
    val (badgeBg, badgeFg) = when (request.status) {
        "APPROVED" -> semantic.fullDayContainer to semantic.onFullDayContainer
        "REJECTED" -> semantic.shortDayContainer to semantic.onShortDayContainer
        else -> semantic.overtimeContainer to semantic.onOvertimeContainer
    }
    Card(
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("approval_card_${request.id}")
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "${request.employeeName} · ${request.type.replace('_', ' ')}",
                        style = MaterialTheme.typography.titleMedium
                    )
                    Text(
                        text = if (request.type == "LEAVE") {
                            "Date: ${request.date}"
                        } else {
                            "${request.date} · ${AttendanceCalculator.formatTime12h(request.inMinutes)} – ${AttendanceCalculator.formatTime12h(request.outMinutes)}"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Surface(color = badgeBg, contentColor = badgeFg, shape = RoundedCornerShape(50)) {
                    Text(
                        text = request.status,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                    )
                }
            }

            if (request.reason.isNotBlank()) {
                Text(request.reason, style = MaterialTheme.typography.bodyMedium)
            }

            if (request.status == "PENDING") {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedButton(
                        onClick = onReject,
                        modifier = Modifier.weight(1f).minimumInteractiveComponentSize()
                    ) {
                        Icon(Icons.Filled.Close, null, modifier = Modifier.size(16.dp), tint = semantic.shortDayAccent)
                        Spacer(Modifier.width(4.dp))
                        Text(stringResource(R.string.reject_action), color = semantic.shortDayAccent)
                    }
                    Button(
                        onClick = onApprove,
                        colors = ButtonDefaults.buttonColors(containerColor = semantic.fullDayAccent),
                        modifier = Modifier.weight(1f).minimumInteractiveComponentSize()
                    ) {
                        Icon(Icons.Filled.Check, null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text(stringResource(R.string.approve_action))
                    }
                }
            }
        }
    }
}

@Composable
private fun ExpenseApprovalCard(
    expense: ExpenseEntity,
    onApprove: () -> Unit,
    onReject: () -> Unit
) {
    val semantic = LocalAttendanceColors.current
    val (badgeBg, badgeFg) = when (expense.status) {
        "APPROVED" -> semantic.fullDayContainer to semantic.onFullDayContainer
        "REJECTED" -> semantic.shortDayContainer to semantic.onShortDayContainer
        else -> semantic.overtimeContainer to semantic.onOvertimeContainer
    }
    Card(
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "EXPENSE · ${expense.title}",
                        style = MaterialTheme.typography.titleMedium
                    )
                    Text(
                        text = "${expense.employeeName.ifBlank { expense.site }} · ${expense.date} · ₹${AttendanceExportHelper.formatMoneyPlain(expense.amount)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Surface(color = badgeBg, contentColor = badgeFg, shape = RoundedCornerShape(50)) {
                    Text(
                        text = expense.status,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                    )
                }
            }
            if (expense.status == "PENDING") {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedButton(
                        onClick = onReject,
                        modifier = Modifier.weight(1f).minimumInteractiveComponentSize()
                    ) {
                        Text(stringResource(R.string.reject_action), color = semantic.shortDayAccent)
                    }
                    Button(
                        onClick = onApprove,
                        colors = ButtonDefaults.buttonColors(containerColor = semantic.fullDayAccent),
                        modifier = Modifier.weight(1f).minimumInteractiveComponentSize()
                    ) {
                        Text(stringResource(R.string.approve_action))
                    }
                }
            }
        }
    }
}
