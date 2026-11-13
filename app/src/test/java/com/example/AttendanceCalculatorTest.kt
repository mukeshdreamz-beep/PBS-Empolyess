package com.example

import com.example.domain.AttendanceCalculator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AttendanceCalculatorTest {

    @Test
    fun `standard 9_30 AM to 6_15 PM shift is full day with zero OT`() {
        val inMins = 9 * 60 + 30
        val outMins = 18 * 60 + 15
        val breakdown = AttendanceCalculator.calculateBreakdown(inMins, outMins)

        assertEquals(525, breakdown.workedMinutes)
        assertTrue(breakdown.isFullDay)
        assertEquals(0, breakdown.overtimeMinutes)
    }

    @Test
    fun `gap between 6_15 PM and 7_00 PM is not counted as overtime`() {
        val inMins = 9 * 60 + 30
        val outMins = 18 * 60 + 59 // 6:59 PM
        val breakdown = AttendanceCalculator.calculateBreakdown(inMins, outMins)

        assertTrue(breakdown.isFullDay)
        assertEquals(0, breakdown.overtimeMinutes)
    }

    @Test
    fun `overtime starts strictly from 7_00 PM onward`() {
        val inMins = 9 * 60 + 30
        val outMins = 20 * 60 + 30 // 8:30 PM -> 90 mins OT
        val breakdown = AttendanceCalculator.calculateBreakdown(inMins, outMins)

        assertEquals(660, breakdown.workedMinutes)
        assertTrue(breakdown.isFullDay)
        assertEquals(90, breakdown.overtimeMinutes)
    }

    @Test
    fun `less than 8h 45m shift is not full day`() {
        val inMins = 9 * 60 + 30
        val outMins = 18 * 60 + 14 // 524 mins
        val breakdown = AttendanceCalculator.calculateBreakdown(inMins, outMins)

        assertEquals(524, breakdown.workedMinutes)
        assertFalse(breakdown.isFullDay)
    }

    @Test
    fun `overnight shift duration wraps across midnight`() {
        val inMins = 22 * 60 // 10:00 PM
        val outMins = 7 * 60 // 7:00 AM
        val worked = AttendanceCalculator.calculateWorkedMinutes(inMins, outMins)

        assertEquals(9 * 60, worked)
        assertTrue(AttendanceCalculator.isFullDay(worked))
    }

    @Test
    fun `date validation rejects invalid calendar dates`() {
        assertTrue(AttendanceCalculator.isValidDateString("13/11/2026"))
        assertFalse(AttendanceCalculator.isValidDateString("31/02/2026"))
        assertFalse(AttendanceCalculator.isValidDateString("2026-11-13"))
        assertFalse(AttendanceCalculator.isValidDateString(""))
    }

    @Test
    fun `break deduction and non-present status calculate accurately`() {
        val policyWithBreak = com.example.data.AttendancePolicy(
            breakDeductionMinutes = 30
        )
        val present = AttendanceCalculator.calculateBreakdown(
            inMinutes = 9 * 60 + 30,
            outMinutes = 18 * 60 + 15,
            status = com.example.domain.AttendanceStatus.PRESENT,
            policy = policyWithBreak
        )
        assertEquals(495, present.workedMinutes)
        assertFalse(present.isFullDay)

        val absent = AttendanceCalculator.calculateBreakdown(
            inMinutes = 9 * 60 + 30,
            outMinutes = 20 * 60,
            status = com.example.domain.AttendanceStatus.ABSENT,
            policy = policyWithBreak
        )
        assertEquals(0, absent.workedMinutes)
        assertFalse(absent.isFullDay)
        assertEquals(0, absent.overtimeMinutes)
    }
}
