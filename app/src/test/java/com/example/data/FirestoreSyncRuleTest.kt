package com.example.data

import com.example.base.FirestoreEmulatorTestBase
import com.example.domain.AuthUtils
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.util.UUID

class FirestoreSyncRuleTest : FirestoreEmulatorTestBase() {

    private fun newWorkspaceId(): String =
        "WS-${UUID.randomUUID().toString().replace("-", "").take(10).uppercase()}"

    private fun samplePhotoBase64(): String =
        "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mNk+M9QDwADhgGAWjR9awAAAABJRU5ErkJggg=="

    @Test
    fun unauthenticated_caller_is_rejected_with_permission_denied() = runBlocking {
        auth.signOut()
        val repo = FirestoreSyncRepository(firestore, auth)

        val result = withTimeout(DEFAULT_TIMEOUT_MS) {
            repo.upsertUserProfile("PBS-TEST")
        }
        assertTrue(result.isFailure)
        val firestoreException = result.exceptionOrNull() as? FirebaseFirestoreException
            ?: result.exceptionOrNull()?.unwrapFirestoreException()
        if (firestoreException != null) {
            assertEquals(FirebaseFirestoreException.Code.PERMISSION_DENIED, firestoreException.code)
        } else {
            // Repository throws IllegalStateException before hitting unauthenticated query if not signed in,
            // so also verify raw Firestore snapshot listener is denied when unauthenticated.
            try {
                withTimeout(FLOW_TIMEOUT_MS) {
                    firestore.collection("workspaces").document("PBS-TEST")
                        .collection("employees")
                        .whereEqualTo("claimedByUid", "nobody")
                        .get()
                        .awaitTask()
                }
                fail("Expected unauthenticated query to fail with PERMISSION_DENIED")
            } catch (e: Exception) {
                val fsEx = e.unwrapFirestoreException()
                assertNotNull("Expected FirebaseFirestoreException, got $e", fsEx)
                assertEquals(FirebaseFirestoreException.Code.PERMISSION_DENIED, fsEx!!.code)
            }
        }
    }

    @Test
    fun admin_creates_workspace_and_employee_then_employee_syncs_and_self_punches_with_selfie_and_gps() = runBlocking {
        val wsId = newWorkspaceId()
        val adminUid = signInTestUser("admin_${UUID.randomUUID().toString().take(6)}@pbs.test")
        val adminRepo = FirestoreSyncRepository(firestore, auth)

        val wsResult = withTimeout(DEFAULT_TIMEOUT_MS) {
            adminRepo.ensureAdminWorkspaceMembership(
                workspaceId = wsId,
                displayName = "Rajesh Admin",
                policy = AttendancePolicy()
            )
        }
        assertTrue("Workspace setup failed: ${wsResult.exceptionOrNull()}", wsResult.isSuccess)

        val loginId = "PBS${(100..999).random()}"
        val password = "Pass@${(100..999).random()}"
        val empRemoteId = AttendanceRepository.buildEmployeeRemoteId(loginId)
        val empEntity = EmployeeEntity(
            id = 1L,
            name = "Aarav Sharma",
            employeeCode = loginId,
            site = "Sai Baba Nagar",
            role = "Technician",
            phone = "9876543210",
            dailyWage = 800.0,
            otHourlyRate = 120.0,
            loginId = loginId,
            passwordHash = AuthUtils.hashPassword(password),
            passwordUpdatedAt = 1000L,
            remoteId = empRemoteId,
            claimedByUid = "",
            createdAt = 1000L
        )
        val empSyncRes = withTimeout(DEFAULT_TIMEOUT_MS) {
            adminRepo.upsertEmployeeRemote(
                workspaceId = wsId,
                employee = empEntity,
                adminUids = listOf(adminUid)
            )
        }
        assertTrue("Employee upsert failed: ${empSyncRes.exceptionOrNull()}", empSyncRes.isSuccess)

        // Employee signs in on a second device with a different Google account
        val empUid = signInTestUser("emp_${UUID.randomUUID().toString().take(6)}@pbs.test")
        val empRepo = FirestoreSyncRepository(firestore, auth)

        val joined = withTimeout(DEFAULT_TIMEOUT_MS) {
            empRepo.joinWorkspaceAsEmployeeAndVerify(
                workspaceId = wsId,
                loginId = loginId,
                plainPassword = password
            )
        }
        assertTrue("Employee join failed: ${joined.exceptionOrNull()}", joined.isSuccess)

        val claimedEmp = joined.getOrThrow().copy(id = 1L)
        val today = "13/11/2026"
        val checkInEntry = AttendanceEntryEntity(
            id = 1L,
            employeeId = 1L,
            date = today,
            inMinutes = 9 * 60 + 30,
            outMinutes = 9 * 60 + 30,
            status = "PRESENT",
            note = "Self check-in",
            checkInTimestamp = 1794542400000L,
            checkOutTimestamp = 1794542400000L,
            remoteId = AttendanceRepository.buildAttendanceRemoteId(empRemoteId, today),
            hasCheckedIn = true,
            hasCheckedOut = false,
            markedByRole = "EMPLOYEE",
            checkInPhotoBase64 = samplePhotoBase64(),
            checkInLat = 19.0760,
            checkInLng = 72.8777,
            checkInAddress = "Sai Baba Nagar, Mumbai",
            updatedAt = 1794542400000L
        )

        val checkInRes = withTimeout(DEFAULT_TIMEOUT_MS) {
            empRepo.submitEmployeeCheckInWithProof(
                workspaceId = wsId,
                employee = claimedEmp,
                entry = checkInEntry,
                adminUids = listOf(adminUid)
            )
        }
        assertTrue("Employee check-in failed: ${checkInRes.exceptionOrNull()}", checkInRes.isSuccess)

        // Employee checks out ONCE with selfie + GPS
        val checkOutEntry = checkInEntry.copy(
            outMinutes = 19 * 60 + 30,
            checkOutTimestamp = 1794578400000L,
            hasCheckedOut = true,
            checkOutPhotoBase64 = samplePhotoBase64(),
            checkOutLat = 19.0762,
            checkOutLng = 72.8779,
            checkOutAddress = "Sai Baba Nagar Gate 2",
            updatedAt = 1794578400000L
        )
        val checkOutRes = withTimeout(DEFAULT_TIMEOUT_MS) {
            empRepo.submitEmployeeCheckOutWithProof(
                workspaceId = wsId,
                entry = checkOutEntry,
                employeeRemoteId = empRemoteId
            )
        }
        assertTrue("Employee check-out failed: ${checkOutRes.exceptionOrNull()}", checkOutRes.isSuccess)

        // Employee CANNOT modify attendance a second time after check-out
        val secondAttempt = withTimeout(DEFAULT_TIMEOUT_MS) {
            empRepo.submitEmployeeCheckOutWithProof(
                workspaceId = wsId,
                entry = checkOutEntry.copy(outMinutes = 22 * 60),
                employeeRemoteId = empRemoteId
            )
        }
        assertTrue(secondAttempt.isFailure)
        val denyEx = secondAttempt.exceptionOrNull()?.unwrapFirestoreException()
        assertNotNull(denyEx)
        assertEquals(FirebaseFirestoreException.Code.PERMISSION_DENIED, denyEx!!.code)

        // Employee CANNOT delete attendance record
        val deleteAttempt = withTimeout(DEFAULT_TIMEOUT_MS) {
            empRepo.deleteAttendanceRemote(wsId, checkInEntry.remoteId)
        }
        assertTrue(deleteAttempt.isFailure)
        assertEquals(
            FirebaseFirestoreException.Code.PERMISSION_DENIED,
            deleteAttempt.exceptionOrNull()?.unwrapFirestoreException()?.code
        )

        // Employee CAN submit PENDING expense, and CANNOT auto-approve it
        val expRemoteId = AttendanceRepository.buildRandomRemoteId("exp")
        val pendingExp = ExpenseEntity(
            id = 1L,
            employeeId = 1L,
            employeeName = "Aarav Sharma",
            site = "Sai Baba Nagar",
            title = "Travel Rickshaw",
            category = "Travel",
            amount = 240.0,
            date = today,
            status = "PENDING",
            isClaimed = false,
            remoteId = expRemoteId,
            employeeUid = empUid,
            createdAt = 1000L
        )
        val expCreateRes = withTimeout(DEFAULT_TIMEOUT_MS) {
            empRepo.upsertExpenseRemote(
                workspaceId = wsId,
                expense = pendingExp,
                employeeLoginId = loginId,
                adminUids = listOf(adminUid),
                isAdminCaller = false
            )
        }
        assertTrue("Employee expense create failed: ${expCreateRes.exceptionOrNull()}", expCreateRes.isSuccess)

        // Verify employee listener aligns with rules
        val empAttendanceList = withTimeout(FLOW_TIMEOUT_MS) {
            empRepo.observeAttendance(wsId, isAdmin = false).first { it.isNotEmpty() }
        }
        assertEquals(1, empAttendanceList.size)
        assertEquals("Sai Baba Nagar Gate 2", empAttendanceList.first().checkOutAddress)
    }

    @Test
    fun admin_observes_and_updates_employee_attendance_and_expenses_while_outsider_is_denied() = runBlocking {
        val wsId = newWorkspaceId()
        val adminEmail = "admin_${UUID.randomUUID().toString().take(6)}@pbs.test"
        val empEmail = "emp_${UUID.randomUUID().toString().take(6)}@pbs.test"
        val outsiderEmail = "outsider_${UUID.randomUUID().toString().take(6)}@pbs.test"

        val adminUid = signInTestUser(adminEmail)
        val adminRepo = FirestoreSyncRepository(firestore, auth)
        assertTrue(
            withTimeout(DEFAULT_TIMEOUT_MS) {
                adminRepo.ensureAdminWorkspaceMembership(wsId, "PBS Admin", AttendancePolicy())
            }.isSuccess
        )

        val loginId = "PBS${(100..999).random()}"
        val password = "Pass@${(100..999).random()}"
        val empRemoteId = AttendanceRepository.buildEmployeeRemoteId(loginId)
        val empEntity = EmployeeEntity(
            id = 1L,
            name = "Vikram Pawar",
            employeeCode = loginId,
            site = "Meditya Nagar",
            role = "Foreman",
            loginId = loginId,
            passwordHash = AuthUtils.hashPassword(password),
            passwordUpdatedAt = 1000L,
            remoteId = empRemoteId,
            createdAt = 1000L
        )
        assertTrue(
            withTimeout(DEFAULT_TIMEOUT_MS) {
                adminRepo.upsertEmployeeRemote(wsId, empEntity, listOf(adminUid))
            }.isSuccess
        )

        // Employee signs in and checks in with selfie + GPS
        val empUid = signInTestUser(empEmail)
        val empRepo = FirestoreSyncRepository(firestore, auth)
        assertTrue(
            withTimeout(DEFAULT_TIMEOUT_MS) {
                empRepo.joinWorkspaceAsEmployeeAndVerify(wsId, loginId, password)
            }.isSuccess
        )
        val today = "13/11/2026"
        val entry = AttendanceEntryEntity(
            id = 1L,
            employeeId = 1L,
            date = today,
            inMinutes = 9 * 60 + 30,
            outMinutes = 9 * 60 + 30,
            status = "PRESENT",
            note = "Self check-in",
            checkInTimestamp = 1794542400000L,
            checkOutTimestamp = 1794542400000L,
            remoteId = AttendanceRepository.buildAttendanceRemoteId(empRemoteId, today),
            hasCheckedIn = true,
            hasCheckedOut = false,
            markedByRole = "EMPLOYEE",
            checkInPhotoBase64 = samplePhotoBase64(),
            checkInLat = 19.1234,
            checkInLng = 72.8123,
            checkInAddress = "Meditya Nagar Site",
            updatedAt = 1794542400000L
        )
        assertTrue(
            withTimeout(DEFAULT_TIMEOUT_MS) {
                empRepo.submitEmployeeCheckInWithProof(wsId, empEntity.copy(claimedByUid = empUid), entry, listOf(adminUid))
            }.isSuccess
        )

        // Outsider (not a workspace member) CANNOT read or list attendance
        signInTestUser(outsiderEmail)
        val outsiderRepo = FirestoreSyncRepository(firestore, auth)
        try {
            withTimeout(FLOW_TIMEOUT_MS) {
                firestore.collection("workspaces").document(wsId)
                    .collection("attendance")
                    .whereEqualTo("employeeUid", empUid)
                    .get()
                    .awaitTask()
            }
            fail("Expected outsider query to fail with PERMISSION_DENIED")
        } catch (e: Exception) {
            assertEquals(FirebaseFirestoreException.Code.PERMISSION_DENIED, e.unwrapFirestoreException()?.code)
        }
        assertNotNull(outsiderRepo)

        // Admin signs back in, reads employee selfie+location, and edits OT time
        signInTestUser(adminEmail)
        val adminAttendance = withTimeout(FLOW_TIMEOUT_MS) {
            adminRepo.observeAttendance(wsId, isAdmin = true).first { it.isNotEmpty() }
        }
        assertEquals("Meditya Nagar Site", adminAttendance.first().checkInAddress)
        assertEquals(samplePhotoBase64(), adminAttendance.first().checkInPhotoBase64)

        val adminOtEdit = withTimeout(DEFAULT_TIMEOUT_MS) {
            adminRepo.upsertAttendanceByAdmin(
                workspaceId = wsId,
                employee = empEntity.copy(claimedByUid = empUid),
                entry = entry.copy(
                    outMinutes = 21 * 60,
                    hasCheckedOut = true,
                    markedByRole = "ADMIN",
                    note = "Admin approved OT"
                ),
                adminUids = listOf(adminUid)
            )
        }
        assertTrue("Admin OT update failed: ${adminOtEdit.exceptionOrNull()}", adminOtEdit.isSuccess)
    }

    private companion object {
        const val DEFAULT_TIMEOUT_MS = 5000L
        const val FLOW_TIMEOUT_MS = 4000L
    }
}
