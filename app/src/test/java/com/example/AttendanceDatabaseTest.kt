package com.example

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.AppDatabase
import com.example.data.AttendancePolicy
import com.example.data.AttendanceRepository
import com.example.data.AuthErrorReason
import com.example.data.AuthResult
import com.example.data.AuthRole
import com.example.data.SelfPunchRejectReason
import com.example.data.SelfPunchResult
import com.example.domain.AttendanceExportHelper
import com.example.domain.AttendanceStatus
import com.example.domain.AuthUtils
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class AttendanceDatabaseTest {

    private lateinit var database: AppDatabase
    private lateinit var repository: AttendanceRepository

    @Before
    fun setup() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repository = AttendanceRepository(database.attendanceDao())
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun `persists check-in and check-out timestamps and replaces same employee date`() = runTest {
        val empId = repository.addEmployee(
            name = "Aarav Sharma",
            employeeCode = "PBS-01",
            site = "Sai Baba Nagar",
            role = "Operations",
            phone = "9876543210",
            dailyWage = 800.0,
            otHourlyRate = 120.0
        )

        repository.saveAttendance(
            employeeId = empId,
            date = "13/11/2026",
            inMinutes = 9 * 60 + 30,
            outMinutes = 18 * 60 + 15,
            status = AttendanceStatus.PRESENT,
            note = "Regular shift"
        )

        val firstRecords = repository.records.first()
        assertEquals(1, firstRecords.size)
        val first = firstRecords.first()
        assertEquals("Aarav Sharma", first.employeeName)
        assertEquals("Sai Baba Nagar", first.employeeSite)
        assertEquals("PBS-01", first.employeeCode)
        assertEquals("Regular shift", first.note)
        assertTrue(first.checkInTimestamp > 0L)
        assertEquals(525 * 60_000L, first.checkOutTimestamp - first.checkInTimestamp)

        // Second save for same employee + date should replace previous entry
        repository.saveAttendance(
            employeeId = empId,
            date = "13/11/2026",
            inMinutes = 9 * 60 + 30,
            outMinutes = 20 * 60 + 0,
            status = AttendanceStatus.PRESENT,
            note = "Extended OT"
        )

        val updatedRecords = repository.records.first()
        assertEquals(1, updatedRecords.size)
        val updated = updatedRecords.first()
        assertEquals(20 * 60, updated.outMinutes)
        assertEquals("Extended OT", updated.note)
        assertEquals(630 * 60_000L, updated.checkOutTimestamp - updated.checkInTimestamp)

        val csv = AttendanceExportHelper.buildCsvContent(updatedRecords, AttendancePolicy())
        assertTrue(csv.contains("Aarav Sharma"))
        assertTrue(csv.contains("Sai Baba Nagar"))
        assertTrue(csv.contains("Extended OT"))
    }

    @Test
    fun `expenses and leave approvals persist and approving leave logs attendance`() = runTest {
        val empId = repository.addEmployee(
            name = "Priya Nair",
            employeeCode = "PBS-02",
            site = "Meditya Nagar",
            role = "Supervisor"
        )

        repository.addExpense(
            employeeId = empId,
            employeeName = "Priya Nair",
            site = "Meditya Nagar",
            title = "Site Hardware",
            category = "Site Material",
            amount = 450.0,
            date = "13/11/2026",
            status = "PENDING",
            note = "Bolts"
        )
        val expenses = repository.expenses.first()
        assertEquals(1, expenses.size)
        assertEquals("PENDING", expenses.first().status)

        repository.updateExpenseStatus(expenses.first().id, "APPROVED")
        assertEquals("APPROVED", repository.expenses.first().first().status)

        repository.submitApprovalRequest(
            employeeId = empId,
            employeeName = "Priya Nair",
            type = "LEAVE",
            date = "14/11/2026",
            inMinutes = 0,
            outMinutes = 0,
            reason = "Personal"
        )
        val req = repository.approvals.first().first()
        repository.resolveApprovalRequest(req, approved = true)

        assertEquals("APPROVED", repository.approvals.first().first().status)
        val empRecords = repository.observeRecordsForEmployee(empId).first()
        assertEquals(1, empRecords.size)
        assertEquals("LEAVE", empRecords.first().status)
    }

    @Test
    fun `expense claim status updates reset on rejection and generate datewise and reportwise csv`() = runTest {
        val empId = repository.addEmployee(
            name = "Rajesh Patil",
            employeeCode = "PBS-101",
            site = "Sai Baba Nagar",
            role = "Supervisor"
        )

        val exp1Id = repository.addExpense(
            employeeId = empId,
            employeeName = "Rajesh Patil",
            site = "Sai Baba Nagar",
            title = "Fuel",
            category = "Travel",
            amount = 500.0,
            date = "14/11/2026",
            status = "PENDING",
            note = "Generator"
        )
        val exp2Id = repository.addExpense(
            employeeId = empId,
            employeeName = "Rajesh Patil",
            site = "Sai Baba Nagar",
            title = "Cement",
            category = "Site Material",
            amount = 1200.0,
            date = "12/11/2026",
            status = "APPROVED",
            note = "Bags"
        )

        repository.updateExpenseStatus(exp1Id, "APPROVED")
        repository.updateExpenseClaimed(exp1Id, true)

        val afterClaim = repository.expenses.first().associateBy { it.id }
        assertEquals(true, afterClaim[exp1Id]?.isClaimed)
        assertTrue((afterClaim[exp1Id]?.claimedAt ?: 0L) > 0L)
        assertEquals(false, afterClaim[exp2Id]?.isClaimed)

        val allExpenses = repository.expenses.first()
        val dateWiseCsv = AttendanceExportHelper.buildExpensesDateWiseCsv(allExpenses)
        val cementIndex = dateWiseCsv.indexOf("Cement")
        val fuelIndex = dateWiseCsv.indexOf("Fuel")
        assertTrue(cementIndex in 0 until fuelIndex)
        assertTrue(dateWiseCsv.contains("UNCLAIMED"))
        assertTrue(dateWiseCsv.contains("CLAIMED"))
        assertTrue(dateWiseCsv.contains("Approved: 1700.00"))
        assertTrue(dateWiseCsv.contains("Claimed: 500.00"))

        val reportCsv = AttendanceExportHelper.buildExpensesReportCsv(allExpenses, "November 2026")
        assertTrue(reportCsv.contains("Rajesh Patil,Sai Baba Nagar,2,1700.00,1700.00,0.00,0.00,500.00,1200.00"))

        // Rejecting an already-claimed expense clears isClaimed
        repository.updateExpenseStatus(exp1Id, "REJECTED")
        val afterReject = repository.expenses.first().associateBy { it.id }
        assertEquals("REJECTED", afterReject[exp1Id]?.status)
        assertEquals(false, afterReject[exp1Id]?.isClaimed)
        assertEquals(0L, afterReject[exp1Id]?.claimedAt)
    }

    @Test
    fun `admin login signup and employee credential generation with password reset work`() = runTest {
        // Default Admin login: 9988221707 / AdmiN@6227#
        val defaultLogin = repository.loginAdmin(
            AuthUtils.DEFAULT_ADMIN_MOBILE,
            AuthUtils.DEFAULT_ADMIN_PASSWORD
        )
        assertTrue(defaultLogin is AuthResult.Success)
        assertEquals(AuthRole.ADMIN, (defaultLogin as AuthResult.Success).session.role)

        val badLogin = repository.loginAdmin("9988221707", "wrongpass")
        assertEquals(AuthResult.Error(AuthErrorReason.INVALID_CREDENTIALS), badLogin)

        // Admin sign up with new mobile
        val signup = repository.signUpAdmin("Site Manager", "9876543210", "Pass@123")
        assertTrue(signup is AuthResult.Success)
        val managerLogin = repository.loginAdmin("9876543210", "Pass@123")
        assertTrue(managerLogin is AuthResult.Success)

        // Duplicate mobile blocked
        val dupSignup = repository.signUpAdmin("Other", "9988221707", "Pass@123")
        assertEquals(AuthResult.Error(AuthErrorReason.MOBILE_ALREADY_EXISTS), dupSignup)

        // Admin creates employee and generates Login ID + Password
        val created = repository.addEmployeeWithCredentials(
            name = "Vikram Pawar",
            site = "Sai Baba Nagar",
            role = "Technician"
        )
        assertTrue(created.loginId.isNotBlank())
        assertTrue(created.plainPassword.length >= 4)

        val empLogin = repository.loginEmployee(created.loginId.lowercase(), created.plainPassword)
        assertTrue(empLogin is AuthResult.Success)
        assertEquals(AuthRole.EMPLOYEE, (empLogin as AuthResult.Success).session.role)
        assertEquals(created.employeeId, empLogin.session.userId)

        // Admin resets employee password
        val empEntity = repository.employees.first().first { it.id == created.employeeId }
        val (resetLoginId, resetPass) = repository.resetEmployeePassword(empEntity, "NewPass@99")
        assertEquals(created.loginId, resetLoginId)
        assertEquals("NewPass@99", resetPass)

        // Old password rejected, new password accepted
        val oldPassAttempt = repository.loginEmployee(created.loginId, created.plainPassword)
        assertEquals(AuthResult.Error(AuthErrorReason.INVALID_CREDENTIALS), oldPassAttempt)

        val newPassAttempt = repository.loginEmployee(created.loginId, "NewPass@99")
        assertTrue(newPassAttempt is AuthResult.Success)
    }

    @Test
    fun `employee self check-in and check-out store selfie gps proof and respect locks`() = runTest {
        val created = repository.addEmployeeWithCredentials(
            name = "Sandeep Verma",
            site = "Sai Baba Nagar",
            role = "Technician"
        )
        val empId = created.employeeId

        // 1. Check-out without check-in on a date -> NOT_CHECKED_IN_YET
        val noCheckInOut = repository.recordSelfCheckOutWithProof(
            employeeId = empId,
            date = "14/11/2026",
            outMinutes = 18 * 60 + 15,
            photoBase64 = "out_photo_b64",
            lat = 19.23,
            lng = 72.85,
            address = "Borivali West"
        )
        assertEquals(
            SelfPunchResult.Rejected(SelfPunchRejectReason.NOT_CHECKED_IN_YET),
            noCheckInOut
        )

        // 2. First check-in succeeds and captures selfie + GPS
        val firstIn = repository.recordSelfCheckInWithProof(
            employeeId = empId,
            date = "14/11/2026",
            inMinutes = 9 * 60 + 30,
            photoBase64 = "in_photo_b64",
            lat = 19.2288,
            lng = 72.8541,
            address = "Sai Baba Nagar, Borivali West"
        )
        assertTrue(firstIn is SelfPunchResult.Success)
        val inEntry = (firstIn as SelfPunchResult.Success).entry
        assertTrue(inEntry.hasCheckedIn)
        assertEquals(false, inEntry.hasCheckedOut)
        assertEquals("EMPLOYEE", inEntry.markedByRole)
        assertEquals("in_photo_b64", inEntry.checkInPhotoBase64)
        assertEquals("Sai Baba Nagar, Borivali West", inEntry.checkInAddress)

        // 3. Second check-in same day -> ALREADY_CHECKED_IN
        val secondIn = repository.recordSelfCheckInWithProof(
            employeeId = empId,
            date = "14/11/2026",
            inMinutes = 10 * 60,
            photoBase64 = "dup_photo",
            lat = 19.22,
            lng = 72.85,
            address = "Duplicate"
        )
        assertEquals(
            SelfPunchResult.Rejected(SelfPunchRejectReason.ALREADY_CHECKED_IN),
            secondIn
        )

        // 4. Check-out at exact same minute as check-in -> SAME_IN_OUT_TIME
        val sameMinuteOut = repository.recordSelfCheckOutWithProof(
            employeeId = empId,
            date = "14/11/2026",
            outMinutes = 9 * 60 + 30,
            photoBase64 = "out_photo_b64",
            lat = 19.2290,
            lng = 72.8545,
            address = "Site Gate"
        )
        assertEquals(
            SelfPunchResult.Rejected(SelfPunchRejectReason.SAME_IN_OUT_TIME),
            sameMinuteOut
        )

        // 5. Valid check-out succeeds and stores check-out selfie + GPS
        val validOut = repository.recordSelfCheckOutWithProof(
            employeeId = empId,
            date = "14/11/2026",
            outMinutes = 18 * 60 + 45,
            photoBase64 = "out_photo_b64",
            lat = 19.2290,
            lng = 72.8545,
            address = "Site Gate, Borivali West"
        )
        assertTrue(validOut is SelfPunchResult.Success)
        val outEntry = (validOut as SelfPunchResult.Success).entry
        assertTrue(outEntry.hasCheckedIn)
        assertTrue(outEntry.hasCheckedOut)
        assertEquals("in_photo_b64", outEntry.checkInPhotoBase64)
        assertEquals("out_photo_b64", outEntry.checkOutPhotoBase64)
        assertEquals("Site Gate, Borivali West", outEntry.checkOutAddress)

        // 6. Second check-out same day -> ALREADY_CHECKED_OUT
        val secondOut = repository.recordSelfCheckOutWithProof(
            employeeId = empId,
            date = "14/11/2026",
            outMinutes = 19 * 60,
            photoBase64 = "out2",
            lat = 19.23,
            lng = 72.85,
            address = "Other"
        )
        assertEquals(
            SelfPunchResult.Rejected(SelfPunchRejectReason.ALREADY_CHECKED_OUT),
            secondOut
        )

        // 7. Admin edits entry -> markedByRole becomes ADMIN while preserving proof, locking employee punch
        repository.saveAttendance(
            employeeId = empId,
            date = "14/11/2026",
            inMinutes = 9 * 60 + 30,
            outMinutes = 19 * 60 + 30,
            status = AttendanceStatus.PRESENT,
            note = "Admin OT adjusted"
        )
        val afterAdmin = repository.observeRecordsForEmployee(empId).first().first()
        assertEquals("ADMIN", afterAdmin.markedByRole)
        assertEquals("in_photo_b64", afterAdmin.checkInPhotoBase64)
        assertEquals("out_photo_b64", afterAdmin.checkOutPhotoBase64)

        val lockedIn = repository.recordSelfCheckInWithProof(
            employeeId = empId,
            date = "14/11/2026",
            inMinutes = 9 * 60,
            photoBase64 = "x",
            lat = 19.0,
            lng = 72.0,
            address = "x"
        )
        assertEquals(
            SelfPunchResult.Rejected(SelfPunchRejectReason.LOCKED_BY_ADMIN),
            lockedIn
        )
    }
}
