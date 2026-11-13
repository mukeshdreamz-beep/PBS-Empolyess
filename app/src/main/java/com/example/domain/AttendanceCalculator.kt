package com.example.domain

import com.example.data.AttendancePolicy
import java.text.ParseException
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

enum class AttendanceStatus(val storageValue: String) {
    PRESENT("PRESENT"),
    ABSENT("ABSENT"),
    LEAVE("LEAVE");

    companion object {
        fun fromStorage(value: String?): AttendanceStatus =
            entries.firstOrNull { it.storageValue == value } ?: PRESENT
    }
}

data class AttendanceBreakdown(
    val workedMinutes: Int,
    val isFullDay: Boolean,
    val overtimeMinutes: Int
)

data class YearMonthKey(val year: Int, val month: Int) { // month: 1..12
    fun shift(deltaMonths: Int): YearMonthKey {
        val cal = Calendar.getInstance().apply {
            set(Calendar.YEAR, year)
            set(Calendar.MONTH, month - 1)
            set(Calendar.DAY_OF_MONTH, 1)
            add(Calendar.MONTH, deltaMonths)
        }
        return YearMonthKey(cal.get(Calendar.YEAR), cal.get(Calendar.MONTH) + 1)
    }

    fun displayLabel(): String {
        val cal = Calendar.getInstance().apply {
            set(Calendar.YEAR, year)
            set(Calendar.MONTH, month - 1)
            set(Calendar.DAY_OF_MONTH, 1)
        }
        return SimpleDateFormat("MMMM yyyy", Locale.getDefault()).format(cal.time)
    }
}

object AttendanceCalculator {
    const val FULL_DAY_THRESHOLD_MINUTES = AttendancePolicy.DEFAULT_FULL_DAY_THRESHOLD_MINUTES
    const val OVERTIME_START_MINUTES = AttendancePolicy.DEFAULT_OVERTIME_START_MINUTES
    const val DEFAULT_IN_MINUTES = AttendancePolicy.DEFAULT_IN_MINUTES
    const val DEFAULT_OUT_MINUTES = AttendancePolicy.DEFAULT_OUT_MINUTES
    private const val MINUTES_PER_DAY = 24 * 60

    fun calculateWorkedMinutes(
        inMinutes: Int,
        outMinutes: Int,
        breakDeductionMinutes: Int = 0
    ): Int {
        val safeIn = ((inMinutes % MINUTES_PER_DAY) + MINUTES_PER_DAY) % MINUTES_PER_DAY
        val safeOut = ((outMinutes % MINUTES_PER_DAY) + MINUTES_PER_DAY) % MINUTES_PER_DAY
        val gross = if (safeOut >= safeIn) {
            safeOut - safeIn
        } else {
            MINUTES_PER_DAY - safeIn + safeOut
        }
        return if (gross == 0) 0 else (gross - breakDeductionMinutes.coerceAtLeast(0)).coerceAtLeast(0)
    }

    fun isFullDay(
        workedMinutes: Int,
        thresholdMinutes: Int = FULL_DAY_THRESHOLD_MINUTES
    ): Boolean = workedMinutes >= thresholdMinutes

    /**
     * Overtime is counted from the configured OT start time (default 7:00 PM / 19:00) onward.
     * The gap between shift end (6:15 PM) and OT start (7:00 PM) is not counted as overtime.
     */
    fun calculateOvertimeMinutes(
        outMinutes: Int,
        otStartMinutes: Int = OVERTIME_START_MINUTES
    ): Int {
        val safeOut = ((outMinutes % MINUTES_PER_DAY) + MINUTES_PER_DAY) % MINUTES_PER_DAY
        return if (safeOut >= otStartMinutes) safeOut - otStartMinutes else 0
    }

    fun calculateBreakdown(
        inMinutes: Int,
        outMinutes: Int,
        status: AttendanceStatus = AttendanceStatus.PRESENT,
        policy: AttendancePolicy = AttendancePolicy()
    ): AttendanceBreakdown {
        if (status != AttendanceStatus.PRESENT) {
            return AttendanceBreakdown(workedMinutes = 0, isFullDay = false, overtimeMinutes = 0)
        }
        val worked = calculateWorkedMinutes(
            inMinutes = inMinutes,
            outMinutes = outMinutes,
            breakDeductionMinutes = policy.breakDeductionMinutes
        )
        return AttendanceBreakdown(
            workedMinutes = worked,
            isFullDay = isFullDay(worked, policy.fullDayThresholdMinutes),
            overtimeMinutes = calculateOvertimeMinutes(outMinutes, policy.overtimeStartMinutes)
        )
    }

    fun currentMinutesOfDay(): Int {
        val cal = Calendar.getInstance()
        return cal.get(Calendar.HOUR_OF_DAY) * 60 + cal.get(Calendar.MINUTE)
    }

    fun currentYearMonth(): YearMonthKey {
        val cal = Calendar.getInstance()
        return YearMonthKey(cal.get(Calendar.YEAR), cal.get(Calendar.MONTH) + 1)
    }

    fun extractYearMonth(dateStr: String): YearMonthKey? {
        val millis = parseDateMillis(dateStr) ?: return null
        val cal = Calendar.getInstance().apply { timeInMillis = millis }
        return YearMonthKey(cal.get(Calendar.YEAR), cal.get(Calendar.MONTH) + 1)
    }

    fun formatTime12h(minutes: Int): String {
        val safe = ((minutes % MINUTES_PER_DAY) + MINUTES_PER_DAY) % MINUTES_PER_DAY
        val h = safe / 60
        val m = safe % 60
        val suffix = if (h < 12) "AM" else "PM"
        val hour = when (val mod = h % 12) {
            0 -> 12
            else -> mod
        }
        return String.format(Locale.US, "%d:%02d %s", hour, m, suffix)
    }

    fun formatDurationCompact(minutes: Int): String {
        val safe = minutes.coerceAtLeast(0)
        return "${safe / 60}h ${String.format(Locale.US, "%02d", safe % 60)}m"
    }

    fun formatDurationVerbose(minutes: Int): String {
        val safe = minutes.coerceAtLeast(0)
        return "${safe / 60} hr ${safe % 60} min"
    }

    fun todayDateString(): String = formatDateMillis(System.currentTimeMillis())

    fun yesterdayDateString(): String {
        val cal = Calendar.getInstance()
        cal.add(Calendar.DAY_OF_YEAR, -1)
        return formatDateMillis(cal.timeInMillis)
    }

    fun formatDateMillis(millis: Long): String {
        val sdf = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
        return sdf.format(Date(millis))
    }

    fun parseDateMillis(dateStr: String): Long? {
        val trimmed = dateStr.trim()
        if (!trimmed.matches(Regex("""\d{2}/\d{2}/\d{4}"""))) return null
        return try {
            val sdf = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).apply {
                isLenient = false
            }
            sdf.parse(trimmed)?.time
        } catch (_: ParseException) {
            null
        }
    }

    fun isValidDateString(dateStr: String): Boolean = parseDateMillis(dateStr) != null

    fun computeCheckInOutTimestamps(
        dateStr: String,
        inMinutes: Int,
        outMinutes: Int
    ): Pair<Long, Long> {
        val baseMillis = parseDateMillis(dateStr) ?: System.currentTimeMillis()
        val safeIn = ((inMinutes % MINUTES_PER_DAY) + MINUTES_PER_DAY) % MINUTES_PER_DAY
        val safeOut = ((outMinutes % MINUTES_PER_DAY) + MINUTES_PER_DAY) % MINUTES_PER_DAY

        val checkInCal = Calendar.getInstance().apply {
            timeInMillis = baseMillis
            set(Calendar.HOUR_OF_DAY, safeIn / 60)
            set(Calendar.MINUTE, safeIn % 60)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val checkOutCal = Calendar.getInstance().apply {
            timeInMillis = baseMillis
            if (safeOut < safeIn) {
                add(Calendar.DAY_OF_YEAR, 1)
            }
            set(Calendar.HOUR_OF_DAY, safeOut / 60)
            set(Calendar.MINUTE, safeOut % 60)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        return checkInCal.timeInMillis to checkOutCal.timeInMillis
    }
}
