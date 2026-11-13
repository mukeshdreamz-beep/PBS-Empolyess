package com.example.data

import android.content.Context
import android.util.Log
import com.example.R
import com.example.domain.AuthUtils
import com.google.android.gms.tasks.Task
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.suspendCancellableCoroutine
import org.json.JSONArray
import org.json.JSONObject
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

enum class OperationType(val value: String) {
    CREATE("create"),
    UPDATE("update"),
    DELETE("delete"),
    LIST("list"),
    GET("get"),
    WRITE("write")
}

data class WorkspaceRemoteConfig(
    val workspaceId: String,
    val ownerId: String,
    val workspaceName: String,
    val adminUids: List<String>,
    val authorizedAdminEmails: List<String>,
    val policy: AttendancePolicy
)

class FirestoreSyncRepository(
    private val db: FirebaseFirestore,
    private val auth: FirebaseAuth = Firebase.auth
) {
    constructor(context: Context) : this(
        db = FirebaseFirestore.getInstance(
            context.applicationContext.getString(R.string.firestore_database_id)
        ),
        auth = Firebase.auth
    )

    fun currentUid(): String? = auth.currentUser?.uid

    fun currentUserEmail(): String? = auth.currentUser?.email

    fun currentUserDisplayName(): String =
        auth.currentUser?.displayName?.takeIf { it.isNotBlank() }
            ?: auth.currentUser?.email?.substringBefore("@")?.takeIf { it.isNotBlank() }
            ?: "PBS User"

    private suspend fun <T> Task<T>.awaitResult(): T = suspendCancellableCoroutine { cont ->
        addOnCompleteListener { task ->
            if (task.isSuccessful) {
                @Suppress("UNCHECKED_CAST")
                cont.resume(task.result as T)
            } else {
                cont.resumeWithException(task.exception ?: IllegalStateException("Task failed"))
            }
        }
    }

    fun handleFirestoreError(
        exception: Throwable,
        operationType: OperationType,
        path: String?
    ): String {
        val causes = generateSequence(exception) { it.cause }
        val rootError = causes.firstOrNull { it is FirebaseFirestoreException }
            ?: causes.firstOrNull { it !is CancellationException }
            ?: exception

        val currentUser = auth.currentUser
        val providerInfoList = currentUser?.providerData?.map { provider ->
            JSONObject().apply {
                put("providerId", provider.providerId)
                put("email", provider.email)
            }
        } ?: emptyList()

        val authInfoJson = JSONObject().apply {
            put("userId", currentUser?.uid)
            put("email", currentUser?.email)
            put("emailVerified", currentUser?.isEmailVerified)
            put("tenantId", currentUser?.tenantId)
            put("providerInfo", JSONArray(providerInfoList))
        }

        val errorInfoJson = JSONObject().apply {
            put("error", rootError.message ?: rootError.toString())
            put("operationType", operationType.value)
            put("path", path)
            put("authInfo", authInfoJson)
        }

        val jsonString = errorInfoJson.toString()
        Log.e("FirestoreError", "Firestore Error: $jsonString")
        return jsonString
    }

    private fun requireUid(): String =
        auth.currentUser?.uid ?: throw IllegalStateException("User must be signed in with Google")

    private fun normalizeWorkspaceId(raw: String): String =
        PolicyPreferences.sanitizeWorkspaceId(raw)

    // -------------------------------------------------------------------------
    // User Profile & Workspace Bootstrapping
    // -------------------------------------------------------------------------

    suspend fun upsertUserProfile(workspaceId: String): Result<Unit> = runCatching {
        val uid = requireUid()
        val wsId = normalizeWorkspaceId(workspaceId)
        val docRef = db.collection("users").document(uid)
        val snap = try {
            docRef.get().awaitResult()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.GET, docRef.path)
            throw e
        }

        val email = auth.currentUser?.email?.takeIf { it.isNotBlank() }
        val displayName = currentUserDisplayName().take(100)
        if (!snap.exists()) {
            val payload = mutableMapOf<String, Any>(
                "userId" to uid,
                "displayName" to displayName,
                "activeWorkspaceId" to wsId,
                "serverCreatedAt" to FieldValue.serverTimestamp(),
                "serverUpdatedAt" to FieldValue.serverTimestamp()
            )
            if (email != null) payload["email"] = email.take(200)
            try {
                docRef.set(payload).awaitResult()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                handleFirestoreError(e, OperationType.CREATE, docRef.path)
                throw e
            }
        } else {
            val updates = mutableMapOf<String, Any>(
                "displayName" to displayName,
                "activeWorkspaceId" to wsId,
                "serverUpdatedAt" to FieldValue.serverTimestamp()
            )
            if (email != null) updates["email"] = email.take(200)
            try {
                docRef.update(updates).awaitResult()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                handleFirestoreError(e, OperationType.UPDATE, docRef.path)
                throw e
            }
        }
        Unit
    }

    suspend fun ensureAdminWorkspaceMembership(
        workspaceId: String,
        displayName: String,
        policy: AttendancePolicy
    ): Result<WorkspaceRemoteConfig> = runCatching {
        val uid = requireUid()
        val wsId = normalizeWorkspaceId(workspaceId)
        upsertUserProfile(wsId)

        val wsRef = db.collection("workspaces").document(wsId)
        val existingSnap = try {
            wsRef.get().awaitResult()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // If PERMISSION_DENIED on first-time read, document might already exist for another owner
            // or not exist yet. Try creating if it doesn't exist; rethrow if create also fails.
            null
        }

        if (existingSnap == null || !existingSnap.exists()) {
            val emailList = auth.currentUser?.email?.takeIf { it.isNotBlank() }
                ?.let { listOf(it.lowercase()) } ?: emptyList()
            val payload = mapOf<String, Any>(
                "ownerId" to uid,
                "workspaceName" to "PBS Employees ($wsId)".take(100),
                "adminUids" to listOf(uid),
                "authorizedAdminEmails" to emailList,
                "policyDefaultInMinutes" to policy.defaultInMinutes,
                "policyDefaultOutMinutes" to policy.defaultOutMinutes,
                "policyFullDayThresholdMinutes" to policy.fullDayThresholdMinutes.coerceIn(60, 960),
                "policyOvertimeStartMinutes" to policy.overtimeStartMinutes,
                "policyBreakDeductionMinutes" to policy.breakDeductionMinutes.coerceIn(0, 180),
                "serverCreatedAt" to FieldValue.serverTimestamp(),
                "serverUpdatedAt" to FieldValue.serverTimestamp()
            )
            try {
                wsRef.set(payload).awaitResult()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                handleFirestoreError(e, OperationType.CREATE, wsRef.path)
                throw e
            }
        } else {
            val currentAdmins = (existingSnap.get("adminUids") as? List<*>)
                ?.filterIsInstance<String>()
                ?.toMutableList()
                ?: mutableListOf()
            if (uid !in currentAdmins && currentAdmins.size < 10) {
                currentAdmins.add(uid)
                try {
                    wsRef.update(
                        mapOf(
                            "adminUids" to currentAdmins,
                            "serverUpdatedAt" to FieldValue.serverTimestamp()
                        )
                    ).awaitResult()
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    handleFirestoreError(e, OperationType.UPDATE, wsRef.path)
                }
            }
        }

        // Ensure member document has role = ADMIN
        val memberRef = wsRef.collection("members").document(uid)
        val memberSnap = try {
            memberRef.get().awaitResult()
        } catch (e: Exception) {
            null
        }
        val cleanName = displayName.trim().ifBlank { currentUserDisplayName() }.take(100)
        if (memberSnap == null || !memberSnap.exists()) {
            try {
                memberRef.set(
                    mapOf(
                        "userId" to uid,
                        "role" to "ADMIN",
                        "displayName" to cleanName,
                        "serverCreatedAt" to FieldValue.serverTimestamp(),
                        "serverUpdatedAt" to FieldValue.serverTimestamp()
                    )
                ).awaitResult()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                handleFirestoreError(e, OperationType.CREATE, memberRef.path)
                throw e
            }
        } else {
            try {
                memberRef.update(
                    mapOf(
                        "role" to "ADMIN",
                        "displayName" to cleanName,
                        "serverUpdatedAt" to FieldValue.serverTimestamp()
                    )
                ).awaitResult()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                handleFirestoreError(e, OperationType.UPDATE, memberRef.path)
                throw e
            }
        }

        val finalSnap = wsRef.get().awaitResult()
        parseWorkspaceConfig(wsId, finalSnap)
            ?: WorkspaceRemoteConfig(
                workspaceId = wsId,
                ownerId = uid,
                workspaceName = "PBS Employees ($wsId)",
                adminUids = listOf(uid),
                authorizedAdminEmails = emptyList(),
                policy = policy
            )
    }

    suspend fun joinWorkspaceAsEmployeeAndVerify(
        workspaceId: String,
        loginId: String,
        plainPassword: String
    ): Result<EmployeeEntity> = runCatching {
        val uid = requireUid()
        val wsId = normalizeWorkspaceId(workspaceId)
        val cleanLoginId = loginId.trim().uppercase()
        require(cleanLoginId.isNotEmpty() && plainPassword.isNotEmpty()) {
            "Login ID and password are required"
        }
        upsertUserProfile(wsId)

        val wsRef = db.collection("workspaces").document(wsId)
        val memberRef = wsRef.collection("members").document(uid)

        // Ensure member doc exists as EMPLOYEE so rules allow reading employee_auth/{loginId}
        val existingMember = try {
            memberRef.get().awaitResult()
        } catch (_: Exception) {
            null
        }
        if (existingMember != null && existingMember.exists() && existingMember.getString("role") == "ADMIN") {
            // Switching from ADMIN to EMPLOYEE on same Google account (e.g., dual-mode testing)
            try {
                memberRef.delete().awaitResult()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                handleFirestoreError(e, OperationType.DELETE, memberRef.path)
                throw e
            }
        }

        val memberExistsAfterClean = if (existingMember?.getString("role") == "ADMIN") false else (existingMember?.exists() == true)
        if (!memberExistsAfterClean) {
            try {
                memberRef.set(
                    mapOf(
                        "userId" to uid,
                        "role" to "EMPLOYEE",
                        "displayName" to cleanLoginId,
                        "serverCreatedAt" to FieldValue.serverTimestamp(),
                        "serverUpdatedAt" to FieldValue.serverTimestamp()
                    )
                ).awaitResult()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                handleFirestoreError(e, OperationType.CREATE, memberRef.path)
                throw e
            }
        }

        val authDocRef = wsRef.collection("employee_auth").document(cleanLoginId)
        val authSnap = try {
            authDocRef.get().awaitResult()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.GET, authDocRef.path)
            throw e
        }
        if (!authSnap.exists()) {
            throw IllegalArgumentException("INVALID_CREDENTIALS")
        }
        val storedHash = authSnap.getString("passwordHash") ?: ""
        if (storedHash.isBlank() || storedHash != AuthUtils.hashPassword(plainPassword)) {
            throw IllegalArgumentException("INVALID_CREDENTIALS")
        }

        val empRemoteId = authSnap.getString("employeeRemoteId")
            ?: AttendanceRepository.buildEmployeeRemoteId(cleanLoginId)
        val empLocalId = authSnap.getLong("employeeLocalId") ?: 1L
        val empName = authSnap.getString("name") ?: cleanLoginId
        val empSite = authSnap.getString("site") ?: EmployeeEntity.DEFAULT_SITE
        val empRole = authSnap.getString("role") ?: ""

        // Bind verified employeeLoginId & employeeLocalId onto member doc
        try {
            memberRef.update(
                mapOf(
                    "employeeLoginId" to cleanLoginId,
                    "employeeLocalId" to empLocalId,
                    "displayName" to empName.take(100),
                    "serverUpdatedAt" to FieldValue.serverTimestamp()
                )
            ).awaitResult()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, memberRef.path)
            throw e
        }

        // Claim the employee directory record so employee snapshot queries work seamlessly
        val empDocRef = wsRef.collection("employees").document(empRemoteId)
        try {
            empDocRef.update(
                mapOf(
                    "claimedByUid" to uid,
                    "serverUpdatedAt" to FieldValue.serverTimestamp()
                )
            ).awaitResult()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, empDocRef.path)
            throw e
        }

        val fullEmpSnap = try {
            empDocRef.get().awaitResult()
        } catch (_: Exception) {
            null
        }
        if (fullEmpSnap != null && fullEmpSnap.exists()) {
            parseEmployeeDoc(fullEmpSnap)
        } else {
            EmployeeEntity(
                id = empLocalId,
                name = empName,
                employeeCode = cleanLoginId,
                site = empSite,
                role = empRole,
                loginId = cleanLoginId,
                passwordHash = storedHash,
                remoteId = empRemoteId,
                claimedByUid = uid
            )
        }
    }

    suspend fun updateWorkspacePolicyAndEmails(
        workspaceId: String,
        policy: AttendancePolicy,
        authorizedAdminEmails: List<String>? = null
    ): Result<Unit> = runCatching {
        requireUid()
        val wsId = normalizeWorkspaceId(workspaceId)
        val wsRef = db.collection("workspaces").document(wsId)
        val updates = mutableMapOf<String, Any>(
            "policyDefaultInMinutes" to policy.defaultInMinutes,
            "policyDefaultOutMinutes" to policy.defaultOutMinutes,
            "policyFullDayThresholdMinutes" to policy.fullDayThresholdMinutes.coerceIn(60, 960),
            "policyOvertimeStartMinutes" to policy.overtimeStartMinutes,
            "policyBreakDeductionMinutes" to policy.breakDeductionMinutes.coerceIn(0, 180),
            "serverUpdatedAt" to FieldValue.serverTimestamp()
        )
        if (authorizedAdminEmails != null) {
            updates["authorizedAdminEmails"] = authorizedAdminEmails
                .map { it.trim().lowercase() }
                .filter { it.isNotBlank() }
                .distinct()
                .take(20)
        }
        try {
            wsRef.update(updates).awaitResult()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, wsRef.path)
            throw e
        }
        Unit
    }

    // -------------------------------------------------------------------------
    // Admin Account Sync
    // -------------------------------------------------------------------------

    suspend fun upsertAdminAccountRemote(
        workspaceId: String,
        admin: AdminAccountEntity,
        adminUids: List<String>
    ): Result<Unit> = runCatching {
        val uid = requireUid()
        val wsId = normalizeWorkspaceId(workspaceId)
        val docRef = db.collection("workspaces").document(wsId)
            .collection("admins").document("admin_${admin.mobile}")
        val effectiveAdminUids = (adminUids + uid).distinct().take(10)
        val existing = try {
            docRef.get().awaitResult()
        } catch (_: Exception) {
            null
        }
        if (existing == null || !existing.exists()) {
            val payload = mapOf<String, Any>(
                "ownerId" to uid,
                "adminUids" to effectiveAdminUids,
                "name" to admin.name.take(100),
                "mobile" to admin.mobile.take(15),
                "passwordHash" to admin.passwordHash,
                "createdAtMs" to admin.createdAt.coerceAtLeast(0L),
                "serverCreatedAt" to FieldValue.serverTimestamp(),
                "serverUpdatedAt" to FieldValue.serverTimestamp()
            )
            try {
                docRef.set(payload).awaitResult()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                handleFirestoreError(e, OperationType.CREATE, docRef.path)
                throw e
            }
        } else {
            try {
                docRef.update(
                    mapOf(
                        "adminUids" to effectiveAdminUids,
                        "name" to admin.name.take(100),
                        "mobile" to admin.mobile.take(15),
                        "passwordHash" to admin.passwordHash,
                        "serverUpdatedAt" to FieldValue.serverTimestamp()
                    )
                ).awaitResult()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                handleFirestoreError(e, OperationType.UPDATE, docRef.path)
                throw e
            }
        }
        Unit
    }

    // -------------------------------------------------------------------------
    // Employee Master Directory & Auth Lookup
    // -------------------------------------------------------------------------

    suspend fun upsertEmployeeRemote(
        workspaceId: String,
        employee: EmployeeEntity,
        adminUids: List<String>
    ): Result<Unit> = runCatching {
        val uid = requireUid()
        val wsId = normalizeWorkspaceId(workspaceId)
        val wsRef = db.collection("workspaces").document(wsId)
        val cleanLoginId = employee.loginId.trim().uppercase()
            .ifBlank { "PBS${employee.id}" }
        val empRemoteId = employee.remoteId.ifBlank {
            AttendanceRepository.buildEmployeeRemoteId(cleanLoginId)
        }
        val effectiveAdmins = (adminUids + uid).distinct().take(10)

        // 1. Upsert employee document
        val empRef = wsRef.collection("employees").document(empRemoteId)
        val existingEmp = try {
            empRef.get().awaitResult()
        } catch (_: Exception) {
            null
        }
        if (existingEmp == null || !existingEmp.exists()) {
            val payload = mutableMapOf<String, Any>(
                "localId" to employee.id.coerceAtLeast(1L),
                "adminUids" to effectiveAdmins,
                "claimedByUid" to employee.claimedByUid,
                "name" to employee.name.trim().ifBlank { cleanLoginId }.take(100),
                "employeeCode" to employee.employeeCode.trim().take(64),
                "site" to employee.site.trim().ifBlank { EmployeeEntity.DEFAULT_SITE }.take(100),
                "role" to employee.role.trim().take(100),
                "phone" to employee.phone.trim().take(30),
                "dailyWage" to employee.dailyWage.coerceAtLeast(0.0),
                "otHourlyRate" to employee.otHourlyRate.coerceAtLeast(0.0),
                "loginId" to cleanLoginId.take(64),
                "passwordHash" to employee.passwordHash.ifBlank { AuthUtils.hashPassword("pbs123") },
                "passwordUpdatedAt" to employee.passwordUpdatedAt.coerceAtLeast(0L),
                "createdAtMs" to employee.createdAt.coerceAtLeast(0L),
                "serverCreatedAt" to FieldValue.serverTimestamp(),
                "serverUpdatedAt" to FieldValue.serverTimestamp()
            )
            try {
                empRef.set(payload).awaitResult()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                handleFirestoreError(e, OperationType.CREATE, empRef.path)
                throw e
            }
        } else {
            val updates = mapOf<String, Any>(
                "adminUids" to effectiveAdmins,
                "claimedByUid" to (existingEmp.getString("claimedByUid")?.ifBlank { employee.claimedByUid } ?: employee.claimedByUid),
                "name" to employee.name.trim().ifBlank { cleanLoginId }.take(100),
                "employeeCode" to employee.employeeCode.trim().take(64),
                "site" to employee.site.trim().ifBlank { EmployeeEntity.DEFAULT_SITE }.take(100),
                "role" to employee.role.trim().take(100),
                "phone" to employee.phone.trim().take(30),
                "dailyWage" to employee.dailyWage.coerceAtLeast(0.0),
                "otHourlyRate" to employee.otHourlyRate.coerceAtLeast(0.0),
                "loginId" to cleanLoginId.take(64),
                "passwordHash" to employee.passwordHash.ifBlank { AuthUtils.hashPassword("pbs123") },
                "passwordUpdatedAt" to employee.passwordUpdatedAt.coerceAtLeast(0L),
                "serverUpdatedAt" to FieldValue.serverTimestamp()
            )
            try {
                empRef.update(updates).awaitResult()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                handleFirestoreError(e, OperationType.UPDATE, empRef.path)
                throw e
            }
        }

        // 2. Upsert employee_auth lookup document
        val authRef = wsRef.collection("employee_auth").document(cleanLoginId)
        val existingAuth = try {
            authRef.get().awaitResult()
        } catch (_: Exception) {
            null
        }
        val pwHash = employee.passwordHash.ifBlank { AuthUtils.hashPassword("pbs123") }
        if (existingAuth == null || !existingAuth.exists()) {
            try {
                authRef.set(
                    mapOf(
                        "loginId" to cleanLoginId.take(64),
                        "passwordHash" to pwHash,
                        "employeeRemoteId" to empRemoteId,
                        "employeeLocalId" to employee.id.coerceAtLeast(1L),
                        "name" to employee.name.trim().ifBlank { cleanLoginId }.take(100),
                        "site" to employee.site.trim().ifBlank { EmployeeEntity.DEFAULT_SITE }.take(100),
                        "role" to employee.role.trim().take(100),
                        "serverCreatedAt" to FieldValue.serverTimestamp(),
                        "serverUpdatedAt" to FieldValue.serverTimestamp()
                    )
                ).awaitResult()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                handleFirestoreError(e, OperationType.CREATE, authRef.path)
                throw e
            }
        } else {
            try {
                authRef.update(
                    mapOf(
                        "passwordHash" to pwHash,
                        "employeeRemoteId" to empRemoteId,
                        "employeeLocalId" to employee.id.coerceAtLeast(1L),
                        "name" to employee.name.trim().ifBlank { cleanLoginId }.take(100),
                        "site" to employee.site.trim().ifBlank { EmployeeEntity.DEFAULT_SITE }.take(100),
                        "role" to employee.role.trim().take(100),
                        "serverUpdatedAt" to FieldValue.serverTimestamp()
                    )
                ).awaitResult()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                handleFirestoreError(e, OperationType.UPDATE, authRef.path)
                throw e
            }
        }
        Unit
    }

    suspend fun deleteEmployeeRemote(
        workspaceId: String,
        employeeRemoteId: String,
        loginId: String
    ): Result<Unit> = runCatching {
        requireUid()
        val wsId = normalizeWorkspaceId(workspaceId)
        val wsRef = db.collection("workspaces").document(wsId)
        if (employeeRemoteId.isNotBlank()) {
            val empRef = wsRef.collection("employees").document(employeeRemoteId)
            try {
                empRef.delete().awaitResult()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                handleFirestoreError(e, OperationType.DELETE, empRef.path)
                throw e
            }
        }
        if (loginId.isNotBlank()) {
            val authRef = wsRef.collection("employee_auth").document(loginId.trim().uppercase())
            try {
                authRef.delete().awaitResult()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                handleFirestoreError(e, OperationType.DELETE, authRef.path)
            }
        }
        Unit
    }

    // -------------------------------------------------------------------------
    // Attendance Sync (Admin full control + Employee self check-in/out with proof)
    // -------------------------------------------------------------------------

    suspend fun submitEmployeeCheckInWithProof(
        workspaceId: String,
        employee: EmployeeEntity,
        entry: AttendanceEntryEntity,
        adminUids: List<String>
    ): Result<Unit> = runCatching {
        val uid = requireUid()
        val wsId = normalizeWorkspaceId(workspaceId)
        val empRemoteId = employee.remoteId.ifBlank {
            AttendanceRepository.buildEmployeeRemoteId(employee.loginId.ifBlank { employee.id.toString() })
        }
        val remoteId = entry.remoteId.ifBlank {
            AttendanceRepository.buildAttendanceRemoteId(empRemoteId, entry.date)
        }
        val docRef = db.collection("workspaces").document(wsId)
            .collection("attendance").document(remoteId)

        val payload = mutableMapOf<String, Any>(
            "adminUids" to adminUids.ifEmpty { listOf(uid) }.take(10),
            "employeeUid" to uid,
            "employeeId" to employee.id.coerceAtLeast(1L),
            "employeeRemoteId" to empRemoteId,
            "employeeLoginId" to employee.loginId.trim().uppercase(),
            "date" to entry.date,
            "inMinutes" to entry.inMinutes,
            "outMinutes" to entry.outMinutes,
            "status" to "PRESENT",
            "note" to entry.note.take(500),
            "hasCheckedIn" to true,
            "hasCheckedOut" to false,
            "markedByRole" to "EMPLOYEE",
            "checkInTimestamp" to entry.checkInTimestamp.coerceAtLeast(0L),
            "checkOutTimestamp" to entry.checkOutTimestamp.coerceAtLeast(0L),
            "checkInPhotoBase64" to entry.checkInPhotoBase64.take(195000),
            "checkInLat" to requireNotNull(entry.checkInLat) { "Check-in GPS latitude required" },
            "checkInLng" to requireNotNull(entry.checkInLng) { "Check-in GPS longitude required" },
            "checkInAddress" to entry.checkInAddress.take(300),
            "updatedAtMs" to entry.updatedAt.coerceAtLeast(0L),
            "serverCreatedAt" to FieldValue.serverTimestamp(),
            "serverUpdatedAt" to FieldValue.serverTimestamp()
        )
        try {
            docRef.set(payload).awaitResult()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, docRef.path)
            throw e
        }
        Unit
    }

    suspend fun submitEmployeeCheckOutWithProof(
        workspaceId: String,
        entry: AttendanceEntryEntity,
        employeeRemoteId: String
    ): Result<Unit> = runCatching {
        requireUid()
        val wsId = normalizeWorkspaceId(workspaceId)
        val remoteId = entry.remoteId.ifBlank {
            AttendanceRepository.buildAttendanceRemoteId(employeeRemoteId, entry.date)
        }
        val docRef = db.collection("workspaces").document(wsId)
            .collection("attendance").document(remoteId)

        val updates = mapOf<String, Any>(
            "outMinutes" to entry.outMinutes,
            "checkOutTimestamp" to entry.checkOutTimestamp.coerceAtLeast(0L),
            "hasCheckedOut" to true,
            "note" to entry.note.take(500),
            "checkOutPhotoBase64" to entry.checkOutPhotoBase64.take(195000),
            "checkOutLat" to requireNotNull(entry.checkOutLat) { "Check-out GPS latitude required" },
            "checkOutLng" to requireNotNull(entry.checkOutLng) { "Check-out GPS longitude required" },
            "checkOutAddress" to entry.checkOutAddress.take(300),
            "updatedAtMs" to entry.updatedAt.coerceAtLeast(0L),
            "serverUpdatedAt" to FieldValue.serverTimestamp()
        )
        try {
            docRef.update(updates).awaitResult()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docRef.path)
            throw e
        }
        Unit
    }

    suspend fun upsertAttendanceByAdmin(
        workspaceId: String,
        employee: EmployeeEntity,
        entry: AttendanceEntryEntity,
        adminUids: List<String>
    ): Result<Unit> = runCatching {
        val uid = requireUid()
        val wsId = normalizeWorkspaceId(workspaceId)
        val empRemoteId = employee.remoteId.ifBlank {
            AttendanceRepository.buildEmployeeRemoteId(employee.loginId.ifBlank { employee.id.toString() })
        }
        val remoteId = entry.remoteId.ifBlank {
            AttendanceRepository.buildAttendanceRemoteId(empRemoteId, entry.date)
        }
        val docRef = db.collection("workspaces").document(wsId)
            .collection("attendance").document(remoteId)
        val effectiveAdmins = (adminUids + uid).distinct().take(10)

        val existing = try {
            docRef.get().awaitResult()
        } catch (_: Exception) {
            null
        }
        if (existing == null || !existing.exists()) {
            val payload = mutableMapOf<String, Any>(
                "adminUids" to effectiveAdmins,
                "employeeUid" to employee.claimedByUid,
                "employeeId" to employee.id.coerceAtLeast(1L),
                "employeeRemoteId" to empRemoteId,
                "employeeLoginId" to employee.loginId.trim().uppercase(),
                "date" to entry.date,
                "inMinutes" to entry.inMinutes,
                "outMinutes" to entry.outMinutes,
                "status" to entry.status,
                "note" to entry.note.take(500),
                "hasCheckedIn" to entry.hasCheckedIn,
                "hasCheckedOut" to entry.hasCheckedOut,
                "markedByRole" to "ADMIN",
                "checkInTimestamp" to entry.checkInTimestamp.coerceAtLeast(0L),
                "checkOutTimestamp" to entry.checkOutTimestamp.coerceAtLeast(0L),
                "updatedAtMs" to entry.updatedAt.coerceAtLeast(0L),
                "serverCreatedAt" to FieldValue.serverTimestamp(),
                "serverUpdatedAt" to FieldValue.serverTimestamp()
            )
            if (entry.checkInPhotoBase64.isNotBlank()) payload["checkInPhotoBase64"] = entry.checkInPhotoBase64.take(195000)
            entry.checkInLat?.let { payload["checkInLat"] = it }
            entry.checkInLng?.let { payload["checkInLng"] = it }
            if (entry.checkInAddress.isNotBlank()) payload["checkInAddress"] = entry.checkInAddress.take(300)
            if (entry.checkOutPhotoBase64.isNotBlank()) payload["checkOutPhotoBase64"] = entry.checkOutPhotoBase64.take(195000)
            entry.checkOutLat?.let { payload["checkOutLat"] = it }
            entry.checkOutLng?.let { payload["checkOutLng"] = it }
            if (entry.checkOutAddress.isNotBlank()) payload["checkOutAddress"] = entry.checkOutAddress.take(300)
            try {
                docRef.set(payload).awaitResult()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                handleFirestoreError(e, OperationType.CREATE, docRef.path)
                throw e
            }
        } else {
            val updates = mutableMapOf<String, Any>(
                "adminUids" to effectiveAdmins,
                "employeeUid" to (existing.getString("employeeUid")?.ifBlank { employee.claimedByUid } ?: employee.claimedByUid),
                "employeeLoginId" to employee.loginId.trim().uppercase(),
                "inMinutes" to entry.inMinutes,
                "outMinutes" to entry.outMinutes,
                "status" to entry.status,
                "note" to entry.note.take(500),
                "hasCheckedIn" to entry.hasCheckedIn,
                "hasCheckedOut" to entry.hasCheckedOut,
                "markedByRole" to "ADMIN",
                "checkInTimestamp" to entry.checkInTimestamp.coerceAtLeast(0L),
                "checkOutTimestamp" to entry.checkOutTimestamp.coerceAtLeast(0L),
                "updatedAtMs" to entry.updatedAt.coerceAtLeast(0L),
                "serverUpdatedAt" to FieldValue.serverTimestamp()
            )
            if (entry.checkInPhotoBase64.isNotBlank()) updates["checkInPhotoBase64"] = entry.checkInPhotoBase64.take(195000)
            entry.checkInLat?.let { updates["checkInLat"] = it }
            entry.checkInLng?.let { updates["checkInLng"] = it }
            if (entry.checkInAddress.isNotBlank()) updates["checkInAddress"] = entry.checkInAddress.take(300)
            if (entry.checkOutPhotoBase64.isNotBlank()) updates["checkOutPhotoBase64"] = entry.checkOutPhotoBase64.take(195000)
            entry.checkOutLat?.let { updates["checkOutLat"] = it }
            entry.checkOutLng?.let { updates["checkOutLng"] = it }
            if (entry.checkOutAddress.isNotBlank()) updates["checkOutAddress"] = entry.checkOutAddress.take(300)
            try {
                docRef.update(updates).awaitResult()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                handleFirestoreError(e, OperationType.UPDATE, docRef.path)
                throw e
            }
        }
        Unit
    }

    suspend fun deleteAttendanceRemote(workspaceId: String, remoteId: String): Result<Unit> = runCatching {
        requireUid()
        if (remoteId.isBlank()) return@runCatching
        val wsId = normalizeWorkspaceId(workspaceId)
        val docRef = db.collection("workspaces").document(wsId)
            .collection("attendance").document(remoteId)
        try {
            docRef.delete().awaitResult()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, docRef.path)
            throw e
        }
        Unit
    }

    // -------------------------------------------------------------------------
    // Expenses & Approvals Sync
    // -------------------------------------------------------------------------

    suspend fun upsertExpenseRemote(
        workspaceId: String,
        expense: ExpenseEntity,
        employeeLoginId: String,
        adminUids: List<String>,
        isAdminCaller: Boolean
    ): Result<Unit> = runCatching {
        val uid = requireUid()
        val wsId = normalizeWorkspaceId(workspaceId)
        val remoteId = expense.remoteId.ifBlank { AttendanceRepository.buildRandomRemoteId("exp") }
        val docRef = db.collection("workspaces").document(wsId)
            .collection("expenses").document(remoteId)
        val effectiveAdmins = (if (isAdminCaller) adminUids + uid else adminUids)
            .ifEmpty { listOf(uid) }.distinct().take(10)

        val existing = try {
            docRef.get().awaitResult()
        } catch (_: Exception) {
            null
        }
        if (existing == null || !existing.exists()) {
            val payload = mutableMapOf<String, Any>(
                "adminUids" to effectiveAdmins,
                "employeeUid" to expense.employeeUid.ifBlank { uid },
                "localId" to expense.id.coerceAtLeast(1L),
                "employeeName" to expense.employeeName.take(100),
                "site" to expense.site.ifBlank { EmployeeEntity.DEFAULT_SITE }.take(100),
                "title" to expense.title.take(150),
                "category" to expense.category.ifBlank { "Other" }.take(64),
                "amount" to expense.amount.coerceAtLeast(0.01),
                "date" to expense.date,
                "status" to if (isAdminCaller) expense.status else "PENDING",
                "isClaimed" to if (isAdminCaller) expense.isClaimed else false,
                "approvedAt" to if (isAdminCaller) expense.approvedAt else 0L,
                "claimedAt" to if (isAdminCaller) expense.claimedAt else 0L,
                "createdAtMs" to expense.createdAt.coerceAtLeast(0L),
                "serverCreatedAt" to FieldValue.serverTimestamp(),
                "serverUpdatedAt" to FieldValue.serverTimestamp()
            )
            expense.employeeId?.takeIf { it >= 0L }?.let { payload["employeeId"] = it }
            if (employeeLoginId.isNotBlank()) payload["employeeLoginId"] = employeeLoginId.trim().uppercase()
            if (expense.note.isNotBlank()) payload["note"] = expense.note.take(500)
            try {
                docRef.set(payload).awaitResult()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                handleFirestoreError(e, OperationType.CREATE, docRef.path)
                throw e
            }
        } else {
            val updates = mutableMapOf<String, Any>(
                "adminUids" to effectiveAdmins,
                "status" to expense.status,
                "isClaimed" to expense.isClaimed,
                "approvedAt" to expense.approvedAt,
                "claimedAt" to expense.claimedAt,
                "note" to expense.note.take(500),
                "serverUpdatedAt" to FieldValue.serverTimestamp()
            )
            try {
                docRef.update(updates).awaitResult()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                handleFirestoreError(e, OperationType.UPDATE, docRef.path)
                throw e
            }
        }
        Unit
    }

    suspend fun deleteExpenseRemote(workspaceId: String, remoteId: String): Result<Unit> = runCatching {
        requireUid()
        if (remoteId.isBlank()) return@runCatching
        val wsId = normalizeWorkspaceId(workspaceId)
        val docRef = db.collection("workspaces").document(wsId)
            .collection("expenses").document(remoteId)
        try {
            docRef.delete().awaitResult()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, docRef.path)
            throw e
        }
        Unit
    }

    suspend fun upsertApprovalRemote(
        workspaceId: String,
        approval: ApprovalRequestEntity,
        employeeLoginId: String,
        adminUids: List<String>,
        isAdminCaller: Boolean
    ): Result<Unit> = runCatching {
        val uid = requireUid()
        val wsId = normalizeWorkspaceId(workspaceId)
        val remoteId = approval.remoteId.ifBlank { AttendanceRepository.buildRandomRemoteId("apr") }
        val docRef = db.collection("workspaces").document(wsId)
            .collection("approvals").document(remoteId)
        val effectiveAdmins = (if (isAdminCaller) adminUids + uid else adminUids)
            .ifEmpty { listOf(uid) }.distinct().take(10)

        val existing = try {
            docRef.get().awaitResult()
        } catch (_: Exception) {
            null
        }
        if (existing == null || !existing.exists()) {
            val payload = mapOf<String, Any>(
                "adminUids" to effectiveAdmins,
                "employeeUid" to approval.employeeUid.ifBlank { uid },
                "localId" to approval.id.coerceAtLeast(1L),
                "employeeId" to approval.employeeId.coerceAtLeast(1L),
                "employeeLoginId" to employeeLoginId.trim().uppercase(),
                "employeeName" to approval.employeeName.take(100),
                "type" to approval.type,
                "date" to approval.date,
                "inMinutes" to approval.inMinutes,
                "outMinutes" to approval.outMinutes,
                "reason" to approval.reason.ifBlank { "Request" }.take(500),
                "status" to if (isAdminCaller) approval.status else "PENDING",
                "createdAtMs" to approval.createdAt.coerceAtLeast(0L),
                "serverCreatedAt" to FieldValue.serverTimestamp(),
                "serverUpdatedAt" to FieldValue.serverTimestamp()
            )
            try {
                docRef.set(payload).awaitResult()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                handleFirestoreError(e, OperationType.CREATE, docRef.path)
                throw e
            }
        } else {
            try {
                docRef.update(
                    mapOf(
                        "adminUids" to effectiveAdmins,
                        "status" to approval.status,
                        "serverUpdatedAt" to FieldValue.serverTimestamp()
                    )
                ).awaitResult()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                handleFirestoreError(e, OperationType.UPDATE, docRef.path)
                throw e
            }
        }
        Unit
    }

    // -------------------------------------------------------------------------
    // Real-Time Firestore Snapshot Listeners (Aligned with firestore.rules)
    // -------------------------------------------------------------------------

    fun observeWorkspaceConfig(workspaceId: String): Flow<WorkspaceRemoteConfig?> {
        val wsId = normalizeWorkspaceId(workspaceId)
        val docRef = db.collection("workspaces").document(wsId)
        return docRef.snapshots()
            .map { snap -> parseWorkspaceConfig(wsId, snap) }
            .catch { error ->
                handleFirestoreError(error, OperationType.GET, docRef.path)
                emit(null)
            }
    }

    fun observeEmployees(workspaceId: String, isAdmin: Boolean): Flow<List<EmployeeEntity>> {
        val uid = requireUid()
        val wsId = normalizeWorkspaceId(workspaceId)
        val col = db.collection("workspaces").document(wsId).collection("employees")
        val query = if (isAdmin) {
            col.whereArrayContains("adminUids", uid)
        } else {
            col.whereEqualTo("claimedByUid", uid)
        }
        return query.snapshots()
            .map { snap -> snap.documents.map { parseEmployeeDoc(it) } }
            .catch { error ->
                handleFirestoreError(error, OperationType.LIST, col.path)
                emit(emptyList())
            }
    }

    fun observeAttendance(workspaceId: String, isAdmin: Boolean): Flow<List<RemoteAttendancePayload>> {
        val uid = requireUid()
        val wsId = normalizeWorkspaceId(workspaceId)
        val col = db.collection("workspaces").document(wsId).collection("attendance")
        val query = if (isAdmin) {
            col.whereArrayContains("adminUids", uid)
        } else {
            col.whereEqualTo("employeeUid", uid)
        }
        return query.snapshots()
            .map { snap -> snap.documents.mapNotNull { parseAttendanceDoc(it) } }
            .catch { error ->
                handleFirestoreError(error, OperationType.LIST, col.path)
                emit(emptyList())
            }
    }

    fun observeExpenses(workspaceId: String, isAdmin: Boolean): Flow<List<RemoteExpensePayload>> {
        val uid = requireUid()
        val wsId = normalizeWorkspaceId(workspaceId)
        val col = db.collection("workspaces").document(wsId).collection("expenses")
        val query = if (isAdmin) {
            col.whereArrayContains("adminUids", uid)
        } else {
            col.whereEqualTo("employeeUid", uid)
        }
        return query.snapshots()
            .map { snap -> snap.documents.mapNotNull { parseExpenseDoc(it) } }
            .catch { error ->
                handleFirestoreError(error, OperationType.LIST, col.path)
                emit(emptyList())
            }
    }

    fun observeApprovals(workspaceId: String, isAdmin: Boolean): Flow<List<RemoteApprovalPayload>> {
        val uid = requireUid()
        val wsId = normalizeWorkspaceId(workspaceId)
        val col = db.collection("workspaces").document(wsId).collection("approvals")
        val query = if (isAdmin) {
            col.whereArrayContains("adminUids", uid)
        } else {
            col.whereEqualTo("employeeUid", uid)
        }
        return query.snapshots()
            .map { snap -> snap.documents.mapNotNull { parseApprovalDoc(it) } }
            .catch { error ->
                handleFirestoreError(error, OperationType.LIST, col.path)
                emit(emptyList())
            }
    }

    fun observeAdminAccounts(workspaceId: String): Flow<List<AdminAccountEntity>> {
        val uid = requireUid()
        val wsId = normalizeWorkspaceId(workspaceId)
        val col = db.collection("workspaces").document(wsId).collection("admins")
        return col.whereArrayContains("adminUids", uid)
            .snapshots()
            .map { snap ->
                snap.documents.mapNotNull { doc ->
                    val data = doc.getData(DocumentSnapshot.ServerTimestampBehavior.ESTIMATE) ?: return@mapNotNull null
                    val mobile = data["mobile"] as? String ?: return@mapNotNull null
                    AdminAccountEntity(
                        id = 0L,
                        name = data["name"] as? String ?: "Admin",
                        mobile = mobile,
                        passwordHash = data["passwordHash"] as? String ?: "",
                        createdAt = (data["createdAtMs"] as? Number)?.toLong() ?: 0L
                    )
                }
            }
            .catch { error ->
                handleFirestoreError(error, OperationType.LIST, col.path)
                emit(emptyList())
            }
    }

    // -------------------------------------------------------------------------
    // Safe Snapshot Parsers
    // -------------------------------------------------------------------------

    private fun parseWorkspaceConfig(wsId: String, snap: DocumentSnapshot): WorkspaceRemoteConfig? {
        if (!snap.exists()) return null
        val data = snap.getData(DocumentSnapshot.ServerTimestampBehavior.ESTIMATE) ?: return null
        val adminUids = (data["adminUids"] as? List<*>)?.filterIsInstance<String>() ?: emptyList()
        val emails = (data["authorizedAdminEmails"] as? List<*>)?.filterIsInstance<String>() ?: emptyList()
        val policy = AttendancePolicy(
            defaultInMinutes = (data["policyDefaultInMinutes"] as? Number)?.toInt()
                ?: AttendancePolicy.DEFAULT_IN_MINUTES,
            defaultOutMinutes = (data["policyDefaultOutMinutes"] as? Number)?.toInt()
                ?: AttendancePolicy.DEFAULT_OUT_MINUTES,
            fullDayThresholdMinutes = (data["policyFullDayThresholdMinutes"] as? Number)?.toInt()
                ?: AttendancePolicy.DEFAULT_FULL_DAY_THRESHOLD_MINUTES,
            overtimeStartMinutes = (data["policyOvertimeStartMinutes"] as? Number)?.toInt()
                ?: AttendancePolicy.DEFAULT_OVERTIME_START_MINUTES,
            breakDeductionMinutes = (data["policyBreakDeductionMinutes"] as? Number)?.toInt()
                ?: AttendancePolicy.DEFAULT_BREAK_DEDUCTION_MINUTES
        )
        return WorkspaceRemoteConfig(
            workspaceId = wsId,
            ownerId = data["ownerId"] as? String ?: "",
            workspaceName = data["workspaceName"] as? String ?: "PBS Employees",
            adminUids = adminUids,
            authorizedAdminEmails = emails,
            policy = policy
        )
    }

    private fun parseEmployeeDoc(snap: DocumentSnapshot): EmployeeEntity {
        val data = snap.getData(DocumentSnapshot.ServerTimestampBehavior.ESTIMATE) ?: emptyMap()
        return EmployeeEntity(
            id = (data["localId"] as? Number)?.toLong() ?: 0L,
            name = data["name"] as? String ?: "",
            employeeCode = data["employeeCode"] as? String ?: "",
            site = data["site"] as? String ?: EmployeeEntity.DEFAULT_SITE,
            role = data["role"] as? String ?: "",
            phone = data["phone"] as? String ?: "",
            dailyWage = (data["dailyWage"] as? Number)?.toDouble() ?: 0.0,
            otHourlyRate = (data["otHourlyRate"] as? Number)?.toDouble() ?: 0.0,
            loginId = data["loginId"] as? String ?: "",
            passwordHash = data["passwordHash"] as? String ?: "",
            passwordUpdatedAt = (data["passwordUpdatedAt"] as? Number)?.toLong() ?: 0L,
            remoteId = snap.id,
            claimedByUid = data["claimedByUid"] as? String ?: "",
            createdAt = (data["createdAtMs"] as? Number)?.toLong() ?: 0L
        )
    }

    private fun parseAttendanceDoc(snap: DocumentSnapshot): RemoteAttendancePayload? {
        val data = snap.getData(DocumentSnapshot.ServerTimestampBehavior.ESTIMATE) ?: return null
        val date = data["date"] as? String ?: return null
        val employeeRemoteId = data["employeeRemoteId"] as? String ?: return null
        return RemoteAttendancePayload(
            remoteId = snap.id,
            employeeUid = data["employeeUid"] as? String ?: "",
            employeeId = (data["employeeId"] as? Number)?.toLong() ?: 0L,
            employeeRemoteId = employeeRemoteId,
            employeeLoginId = data["employeeLoginId"] as? String ?: "",
            date = date,
            inMinutes = (data["inMinutes"] as? Number)?.toInt() ?: 0,
            outMinutes = (data["outMinutes"] as? Number)?.toInt() ?: 0,
            status = data["status"] as? String ?: "PRESENT",
            note = data["note"] as? String ?: "",
            hasCheckedIn = data["hasCheckedIn"] as? Boolean ?: true,
            hasCheckedOut = data["hasCheckedOut"] as? Boolean ?: true,
            markedByRole = data["markedByRole"] as? String ?: "ADMIN",
            checkInTimestamp = (data["checkInTimestamp"] as? Number)?.toLong() ?: 0L,
            checkOutTimestamp = (data["checkOutTimestamp"] as? Number)?.toLong() ?: 0L,
            checkInPhotoBase64 = data["checkInPhotoBase64"] as? String ?: "",
            checkInLat = (data["checkInLat"] as? Number)?.toDouble(),
            checkInLng = (data["checkInLng"] as? Number)?.toDouble(),
            checkInAddress = data["checkInAddress"] as? String ?: "",
            checkOutPhotoBase64 = data["checkOutPhotoBase64"] as? String ?: "",
            checkOutLat = (data["checkOutLat"] as? Number)?.toDouble(),
            checkOutLng = (data["checkOutLng"] as? Number)?.toDouble(),
            checkOutAddress = data["checkOutAddress"] as? String ?: "",
            updatedAtMs = (data["updatedAtMs"] as? Number)?.toLong() ?: 0L
        )
    }

    private fun parseExpenseDoc(snap: DocumentSnapshot): RemoteExpensePayload? {
        val data = snap.getData(DocumentSnapshot.ServerTimestampBehavior.ESTIMATE) ?: return null
        val title = data["title"] as? String ?: return null
        return RemoteExpensePayload(
            remoteId = snap.id,
            employeeUid = data["employeeUid"] as? String ?: "",
            localId = (data["localId"] as? Number)?.toLong() ?: 0L,
            employeeId = (data["employeeId"] as? Number)?.toLong(),
            employeeLoginId = data["employeeLoginId"] as? String ?: "",
            employeeName = data["employeeName"] as? String ?: "",
            site = data["site"] as? String ?: EmployeeEntity.DEFAULT_SITE,
            title = title,
            category = data["category"] as? String ?: "Other",
            amount = (data["amount"] as? Number)?.toDouble() ?: 0.0,
            date = data["date"] as? String ?: "",
            status = data["status"] as? String ?: "PENDING",
            isClaimed = data["isClaimed"] as? Boolean ?: false,
            approvedAt = (data["approvedAt"] as? Number)?.toLong() ?: 0L,
            claimedAt = (data["claimedAt"] as? Number)?.toLong() ?: 0L,
            note = data["note"] as? String ?: "",
            createdAtMs = (data["createdAtMs"] as? Number)?.toLong() ?: 0L
        )
    }

    private fun parseApprovalDoc(snap: DocumentSnapshot): RemoteApprovalPayload? {
        val data = snap.getData(DocumentSnapshot.ServerTimestampBehavior.ESTIMATE) ?: return null
        val type = data["type"] as? String ?: return null
        return RemoteApprovalPayload(
            remoteId = snap.id,
            employeeUid = data["employeeUid"] as? String ?: "",
            localId = (data["localId"] as? Number)?.toLong() ?: 0L,
            employeeId = (data["employeeId"] as? Number)?.toLong() ?: 0L,
            employeeLoginId = data["employeeLoginId"] as? String ?: "",
            employeeName = data["employeeName"] as? String ?: "",
            type = type,
            date = data["date"] as? String ?: "",
            inMinutes = (data["inMinutes"] as? Number)?.toInt() ?: 0,
            outMinutes = (data["outMinutes"] as? Number)?.toInt() ?: 0,
            reason = data["reason"] as? String ?: "",
            status = data["status"] as? String ?: "PENDING",
            createdAtMs = (data["createdAtMs"] as? Number)?.toLong() ?: 0L
        )
    }
}
