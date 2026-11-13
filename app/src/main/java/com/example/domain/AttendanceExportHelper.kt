package com.example.domain

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import com.example.data.AttendancePolicy
import com.example.data.AttendanceRecordItem
import com.example.data.ExpenseEntity
import java.io.File
import java.util.Locale

data class EmployeeMonthlySummary(
    val employeeId: Long,
    val employeeName: String,
    val employeeCode: String,
    val employeeSite: String,
    val employeeRole: String,
    val dailyWage: Double,
    val otHourlyRate: Double,
    val totalEntries: Int,
    val fullDays: Int,
    val shortDays: Int,
    val absentDays: Int,
    val leaveDays: Int,
    val totalWorkedMinutes: Int,
    val totalOvertimeMinutes: Int,
    val approvedExpenses: Double,
    val baseEarnings: Double,
    val overtimePay: Double,
    val netPayable: Double
)

object AttendanceExportHelper {

    fun buildCsvContent(
        records: List<AttendanceRecordItem>,
        policy: AttendancePolicy
    ): String {
        val sb = StringBuilder()
        sb.appendLine("Employee Name,Employee Code,Site,Role,Date,Status,Time In,Time Out,Worked Minutes,Worked Formatted,Day Result,Overtime Minutes,Overtime Formatted,Note,Check-In Location,Check-Out Location,Selfie Proof")
        for (record in records) {
            val status = AttendanceStatus.fromStorage(record.status)
            val breakdown = AttendanceCalculator.calculateBreakdown(
                inMinutes = record.inMinutes,
                outMinutes = record.outMinutes,
                status = status,
                policy = policy
            )
            val timeIn = if (status == AttendanceStatus.PRESENT) {
                AttendanceCalculator.formatTime12h(record.inMinutes)
            } else {
                "-"
            }
            val timeOut = when {
                status != AttendanceStatus.PRESENT -> "-"
                record.hasCheckedIn && !record.hasCheckedOut -> "Pending"
                else -> AttendanceCalculator.formatTime12h(record.outMinutes)
            }
            val dayResult = when (status) {
                AttendanceStatus.ABSENT -> "ABSENT"
                AttendanceStatus.LEAVE -> "ON LEAVE"
                AttendanceStatus.PRESENT -> when {
                    record.hasCheckedIn && !record.hasCheckedOut -> "CHECKED IN"
                    breakdown.isFullDay -> "FULL DAY"
                    else -> "LESS THAN FULL DAY"
                }
            }
            val inLoc = record.checkInAddress.ifBlank {
                if (record.checkInLat != null && record.checkInLng != null) {
                    SelfieLocationHelper.formatCoords(record.checkInLat, record.checkInLng)
                } else ""
            }
            val outLoc = record.checkOutAddress.ifBlank {
                if (record.checkOutLat != null && record.checkOutLng != null) {
                    SelfieLocationHelper.formatCoords(record.checkOutLat, record.checkOutLng)
                } else ""
            }
            val selfieStatus = when {
                record.checkInPhotoBase64.isNotBlank() && record.checkOutPhotoBase64.isNotBlank() -> "IN + OUT Selfie"
                record.checkInPhotoBase64.isNotBlank() -> "IN Selfie"
                record.checkOutPhotoBase64.isNotBlank() -> "OUT Selfie"
                else -> record.markedByRole
            }

            val row = listOf(
                escapeCsv(record.employeeName),
                escapeCsv(record.employeeCode),
                escapeCsv(record.employeeSite),
                escapeCsv(record.employeeRole),
                escapeCsv(record.date),
                escapeCsv(status.storageValue),
                escapeCsv(timeIn),
                escapeCsv(timeOut),
                breakdown.workedMinutes.toString(),
                escapeCsv(AttendanceCalculator.formatDurationCompact(breakdown.workedMinutes)),
                escapeCsv(dayResult),
                breakdown.overtimeMinutes.toString(),
                escapeCsv(AttendanceCalculator.formatDurationCompact(breakdown.overtimeMinutes)),
                escapeCsv(record.note),
                escapeCsv(inLoc),
                escapeCsv(outLoc),
                escapeCsv(selfieStatus)
            ).joinToString(",")
            sb.appendLine(row)
        }
        return sb.toString()
    }

    fun buildSalaryCsvContent(
        monthLabel: String,
        summaries: List<EmployeeMonthlySummary>
    ): String {
        val sb = StringBuilder()
        sb.appendLine("Month,Employee Name,Code,Site,Full Days,Short Days,Absent,Leave,Total Worked,Total OT,Base Pay,OT Pay,Approved Expenses,Net Payable")
        for (s in summaries) {
            val row = listOf(
                escapeCsv(monthLabel),
                escapeCsv(s.employeeName),
                escapeCsv(s.employeeCode),
                escapeCsv(s.employeeSite),
                s.fullDays.toString(),
                s.shortDays.toString(),
                s.absentDays.toString(),
                s.leaveDays.toString(),
                escapeCsv(AttendanceCalculator.formatDurationCompact(s.totalWorkedMinutes)),
                escapeCsv(AttendanceCalculator.formatDurationCompact(s.totalOvertimeMinutes)),
                formatMoneyPlain(s.baseEarnings),
                formatMoneyPlain(s.overtimePay),
                formatMoneyPlain(s.approvedExpenses),
                formatMoneyPlain(s.netPayable)
            ).joinToString(",")
            sb.appendLine(row)
        }
        return sb.toString()
    }

    fun buildExpensesDateWiseCsv(expenses: List<ExpenseEntity>): String {
        val sorted = expenses.sortedWith(
            compareBy<ExpenseEntity> { AttendanceCalculator.parseDateMillis(it.date) ?: it.createdAt }
                .thenBy { it.createdAt }
        )
        val sb = StringBuilder()
        sb.appendLine("Date,Title,Employee,Site,Category,Amount,Approval Status,Claim Status,Note")
        var totalAmount = 0.0
        var totalApproved = 0.0
        var totalClaimed = 0.0
        for (exp in sorted) {
            totalAmount += exp.amount
            if (exp.status == "APPROVED") {
                totalApproved += exp.amount
                if (exp.isClaimed) totalClaimed += exp.amount
            }
            val claimLabel = when {
                exp.status != "APPROVED" -> "N/A"
                exp.isClaimed -> "CLAIMED"
                else -> "UNCLAIMED"
            }
            val row = listOf(
                escapeCsv(exp.date),
                escapeCsv(exp.title),
                escapeCsv(exp.employeeName.ifBlank { "Site General" }),
                escapeCsv(exp.site),
                escapeCsv(exp.category),
                formatMoneyPlain(exp.amount),
                escapeCsv(exp.status),
                escapeCsv(claimLabel),
                escapeCsv(exp.note)
            ).joinToString(",")
            sb.appendLine(row)
        }
        sb.appendLine(
            "TOTAL,,,,${sorted.size} items,${formatMoneyPlain(totalAmount)},Approved: ${formatMoneyPlain(totalApproved)},Claimed: ${formatMoneyPlain(totalClaimed)},"
        )
        return sb.toString()
    }

    fun buildExpensesReportCsv(
        expenses: List<ExpenseEntity>,
        periodLabel: String
    ): String {
        val sb = StringBuilder()
        sb.appendLine("Period,Employee,Site,Entries,Total Expense,Approved,Pending,Rejected,Claimed,Unclaimed Approved")
        val grouped = expenses.groupBy {
            (it.employeeName.ifBlank { "Site General" }) to it.site
        }
        var grandTotal = 0.0
        var grandApproved = 0.0
        var grandPending = 0.0
        var grandRejected = 0.0
        var grandClaimed = 0.0
        var grandUnclaimed = 0.0

        for ((key, list) in grouped) {
            val total = list.sumOf { it.amount }
            val approved = list.filter { it.status == "APPROVED" }.sumOf { it.amount }
            val pending = list.filter { it.status == "PENDING" }.sumOf { it.amount }
            val rejected = list.filter { it.status == "REJECTED" }.sumOf { it.amount }
            val claimed = list.filter { it.status == "APPROVED" && it.isClaimed }.sumOf { it.amount }
            val unclaimed = (approved - claimed).coerceAtLeast(0.0)

            grandTotal += total
            grandApproved += approved
            grandPending += pending
            grandRejected += rejected
            grandClaimed += claimed
            grandUnclaimed += unclaimed

            val row = listOf(
                escapeCsv(periodLabel),
                escapeCsv(key.first),
                escapeCsv(key.second),
                list.size.toString(),
                formatMoneyPlain(total),
                formatMoneyPlain(approved),
                formatMoneyPlain(pending),
                formatMoneyPlain(rejected),
                formatMoneyPlain(claimed),
                formatMoneyPlain(unclaimed)
            ).joinToString(",")
            sb.appendLine(row)
        }
        sb.appendLine(
            listOf(
                escapeCsv(periodLabel),
                "GRAND TOTAL",
                "All Sites",
                expenses.size.toString(),
                formatMoneyPlain(grandTotal),
                formatMoneyPlain(grandApproved),
                formatMoneyPlain(grandPending),
                formatMoneyPlain(grandRejected),
                formatMoneyPlain(grandClaimed),
                formatMoneyPlain(grandUnclaimed)
            ).joinToString(",")
        )
        return sb.toString()
    }

    fun buildExpensesSummaryText(
        expenses: List<ExpenseEntity>,
        periodLabel: String
    ): String {
        val total = expenses.sumOf { it.amount }
        val approved = expenses.filter { it.status == "APPROVED" }.sumOf { it.amount }
        val pending = expenses.filter { it.status == "PENDING" }.sumOf { it.amount }
        val claimed = expenses.filter { it.status == "APPROVED" && it.isClaimed }.sumOf { it.amount }
        val sb = StringBuilder()
        sb.appendLine("PBS Expense Report — $periodLabel")
        sb.appendLine("Total Expense: ₹${formatMoneyPlain(total)}")
        sb.appendLine("Approved: ₹${formatMoneyPlain(approved)} | Claimed: ₹${formatMoneyPlain(claimed)} | Pending: ₹${formatMoneyPlain(pending)}")
        sb.appendLine("----------------------------------------")
        for (exp in expenses) {
            val claim = if (exp.status == "APPROVED") {
                if (exp.isClaimed) " [CLAIMED]" else " [UNCLAIMED]"
            } else {
                ""
            }
            sb.appendLine("${exp.date} | ${exp.title} (${exp.employeeName.ifBlank { exp.site }}) — ₹${formatMoneyPlain(exp.amount)} [${exp.status}]$claim")
        }
        return sb.toString()
    }

    fun buildSummaryText(
        title: String,
        summaries: List<EmployeeMonthlySummary>
    ): String {
        val sb = StringBuilder()
        sb.appendLine("PBS Employees — $title")
        sb.appendLine("----------------------------------------")
        for (item in summaries) {
            val codePart = if (item.employeeCode.isNotBlank()) " (${item.employeeCode})" else ""
            sb.appendLine("${item.employeeName}$codePart • ${item.employeeSite}")
            sb.appendLine(
                "  Full: ${item.fullDays} | Short: ${item.shortDays} | Abs/Lve: ${item.absentDays + item.leaveDays}"
            )
            sb.appendLine(
                "  Worked: ${AttendanceCalculator.formatDurationCompact(item.totalWorkedMinutes)} | OT: ${AttendanceCalculator.formatDurationCompact(item.totalOvertimeMinutes)}"
            )
            if (item.netPayable > 0.0) {
                sb.appendLine("  Net Payable: ₹${formatMoneyPlain(item.netPayable)}")
            }
        }
        return sb.toString()
    }

    fun formatMoneyPlain(amount: Double): String = String.format(Locale.US, "%.2f", amount)

    fun writeCsvToUri(context: Context, uri: Uri, content: String): Boolean {
        return try {
            context.contentResolver.openOutputStream(uri)?.use { stream ->
                stream.write(content.toByteArray(Charsets.UTF_8))
                stream.flush()
            } != null
        } catch (_: Exception) {
            false
        }
    }

    fun shareCsv(
        context: Context,
        fileName: String,
        csvContent: String
    ): Boolean {
        return try {
            val exportDir = File(context.cacheDir, "exports").apply { mkdirs() }
            val file = File(exportDir, fileName)
            file.writeText(csvContent, Charsets.UTF_8)
            val uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                file
            )
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "text/csv"
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_SUBJECT, "PBS Employees Report")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(Intent.createChooser(intent, "Export CSV Report"))
            true
        } catch (_: Exception) {
            false
        }
    }

    fun sharePlainText(context: Context, subject: String, text: String) {
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, subject)
            putExtra(Intent.EXTRA_TEXT, text)
        }
        context.startActivity(Intent.createChooser(intent, "Share Report"))
    }

    private fun escapeCsv(value: String): String {
        val clean = value.replace("\r", " ").replace("\n", " ")
        return if (clean.contains(",") || clean.contains("\"")) {
            "\"${clean.replace("\"", "\"\"")}\""
        } else {
            clean
        }
    }
}
