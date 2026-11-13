package com.example.data

import com.example.domain.AttendanceCalculator
import com.example.domain.AttendanceStatus
import com.example.domain.AuthUtils
import kotlinx.coroutines.flow.Flow
import java.util.UUID

sealed interface AuthResult {
    data class Success(val session: AuthSession) : AuthResult
    data class Error(val reason: AuthErrorReason) : AuthResult
}

enum class AuthErrorReason {
    INVALID_CREDENTIALS,
    MOBILE_ALREADY_EXISTS,
    INVALID_MOBILE,
    WEAK_PASSWORD,
    NAME_REQUIRED,
    LOGIN_ID_ALREADY_EXISTS,
    NO_PASSWORD_SET
}

sealed interface SelfPunchResult {
    data class Success(val entry: AttendanceEntryEntity) : SelfPunchResult
    data class Rejected(val reason: SelfPunchRejectReason) : SelfPunchResult
}

enum class SelfPunchRejectReason {
    ALREADY_CHECKED_IN,
    NOT_CHECKED_IN_YET,
    ALREADY_CHECKED_OUT,
    LOCKED_BY_ADMIN,
    SAME_IN_OUT_TIME,
    EMPLOYEE_NOT_FOUND
}

data class CreatedEmployeeResult(
    val employeeId: Long,
    val remoteId: String,
    val loginId: String,
    val plainPassword: String
)

class AttendanceRepository(private val dao: AttendanceDao) {

    val employees: Flow<List<EmployeeEntity>> = dao.observeEmployees()

    val records: Flow<List<AttendanceRecordItem>> = dao.observeAttendanceRecords()

    val expenses: Flow<List<ExpenseEntity>> = dao.observeExpenses()

    val approvals: Flow<List<ApprovalRequestEntity>> = dao.observeApprovalRequests()

    fun observeRecordsForEmployee(employeeId: Long): Flow<List<AttendanceRecordItem>> =
        dao.observeRecordsForEmployee(employeeId)

    fun observeRecordsByDate(date: String): Flow<List<AttendanceRecordItem>> =
        dao.observeRecordsByDate(date)

    suspend fun getEmployeeById(employeeId: Long): EmployeeEntity? =
        dao.getEmployeeById(employeeId)

    suspend fun getAllAdminAccounts(): List<AdminAccountEntity> =
        dao.getAllAdminAccountsList()

    suspend fun getAllEmployees(): List<EmployeeEntity> =
        dao.getAllEmployeesList()

    suspend fun getAllAttendance(): List<AttendanceEntryEntity> =
        dao.getAllAttendanceList()

    suspend fun getAllExpenses(): List<ExpenseEntity> =
        dao.getAllExpensesList()

    suspend fun getAllApprovals(): List<ApprovalRequestEntity> =
        dao.getAllApprovalsList()

    suspend fun getExpenseById(id: Long): ExpenseEntity? =
        dao.getExpenseById(id)

    suspend fun getApprovalById(id: Long): ApprovalRequestEntity? =
        dao.getApprovalById(id)

    suspend fun ensureDefaultAdminSeeded() {
        val existingDefault = dao.getAdminByMobile(AuthUtils.DEFAULT_ADMIN_MOBILE)
        if (existingDefault == null) {
            dao.insertAdminAccount(
                AdminAccountEntity(
                    name = AuthUtils.DEFAULT_ADMIN_NAME,
                    mobile = AuthUtils.DEFAULT_ADMIN_MOBILE,
                    passwordHash = AuthUtils.hashPassword(AuthUtils.DEFAULT_ADMIN_PASSWORD),
                    createdAt = System.currentTimeMillis()
                )
            )
        }
    }

    suspend fun loginAdmin(
        mobileInput: String,
        password: String,
        workspaceId: String = AuthSession.DEFAULT_WORKSPACE_ID
    ): AuthResult {
        ensureDefaultAdminSeeded()
        val mobile = AuthUtils.normalizeMobile(mobileInput)
        if (mobile.length != 10) {
            return AuthResult.Error(AuthErrorReason.INVALID_MOBILE)
        }
        val admin = dao.getAdminByMobile(mobile)
            ?: return AuthResult.Error(AuthErrorReason.INVALID_CREDENTIALS)
        val inputHash = AuthUtils.hashPassword(password)
        if (admin.passwordHash != inputHash) {
            return AuthResult.Error(AuthErrorReason.INVALID_CREDENTIALS)
        }
        return AuthResult.Success(
            AuthSession(
                role = AuthRole.ADMIN,
                userId = admin.id,
                displayName = admin.name,
                identifier = admin.mobile,
                workspaceId = PolicyPreferences.sanitizeWorkspaceId(workspaceId)
            )
        )
    }

    suspend fun signUpAdmin(
        name: String,
        mobileInput: String,
        password: String,
        workspaceId: String = AuthSession.DEFAULT_WORKSPACE_ID
    ): AuthResult {
        ensureDefaultAdminSeeded()
        val trimmedName = name.trim()
        if (trimmedName.isEmpty()) {
            return AuthResult.Error(AuthErrorReason.NAME_REQUIRED)
        }
        val mobile = AuthUtils.normalizeMobile(mobileInput)
        if (mobile.length != 10) {
            return AuthResult.Error(AuthErrorReason.INVALID_MOBILE)
        }
        if (password.length < 6) {
            return AuthResult.Error(AuthErrorReason.WEAK_PASSWORD)
        }
        if (dao.getAdminByMobile(mobile) != null) {
            return AuthResult.Error(AuthErrorReason.MOBILE_ALREADY_EXISTS)
        }
        val id = dao.insertAdminAccount(
            AdminAccountEntity(
                name = trimmedName,
                mobile = mobile,
                passwordHash = AuthUtils.hashPassword(password),
                createdAt = System.currentTimeMillis()
            )
        )
        return AuthResult.Success(
            AuthSession(
                role = AuthRole.ADMIN,
                userId = id,
                displayName = trimmedName,
                identifier = mobile,
                workspaceId = PolicyPreferences.sanitizeWorkspaceId(workspaceId)
            )
        )
    }

    suspend fun loginEmployee(
        loginIdInput: String,
        password: String,
        workspaceId: String = AuthSession.DEFAULT_WORKSPACE_ID
    ): AuthResult {
        val trimmedId = loginIdInput.trim()
        if (trimmedId.isEmpty() || password.isEmpty()) {
            return AuthResult.Error(AuthErrorReason.INVALID_CREDENTIALS)
        }
        val employee = dao.getEmployeeByLoginId(trimmedId)
            ?: return AuthResult.Error(AuthErrorReason.INVALID_CREDENTIALS)
        if (employee.passwordHash.isBlank()) {
            return AuthResult.Error(AuthErrorReason.NO_PASSWORD_SET)
        }
        val inputHash = AuthUtils.hashPassword(password)
        if (employee.passwordHash != inputHash) {
            return AuthResult.Error(AuthErrorReason.INVALID_CREDENTIALS)
        }
        return AuthResult.Success(
            AuthSession(
                role = AuthRole.EMPLOYEE,
                userId = employee.id,
                displayName = employee.name,
                identifier = employee.loginId,
                workspaceId = PolicyPreferences.sanitizeWorkspaceId(workspaceId)
            )
        )
    }

    suspend fun addEmployeeWithCredentials(
        name: String,
        employeeCode: String = "",
        site: String = EmployeeEntity.DEFAULT_SITE,
        role: String = "",
        phone: String = "",
        dailyWage: Double = 0.0,
        otHourlyRate: Double = 0.0,
        requestedLoginId: String = "",
        requestedPassword: String = ""
    ): CreatedEmployeeResult {
        val existingIds = dao.getAllEmployeeLoginIds().toSet()
        val count = dao.getEmployeeCount()
        val resolvedLoginId = if (requestedLoginId.isNotBlank() &&
            existingIds.none { it.equals(requestedLoginId.trim(), ignoreCase = true) }
        ) {
            requestedLoginId.trim().uppercase()
        } else {
            AuthUtils.generateEmployeeLoginId(
                employeeCode = employeeCode,
                existingLoginIds = existingIds,
                existingCount = count
            )
        }
        val plainPassword = requestedPassword.trim().ifBlank { AuthUtils.generateReadablePassword() }
        val now = System.currentTimeMillis()
        val remoteId = buildEmployeeRemoteId(resolvedLoginId)
        val id = dao.insertEmployee(
            EmployeeEntity(
                name = name.trim(),
                employeeCode = employeeCode.trim().ifEmpty { resolvedLoginId },
                site = site.trim().ifEmpty { EmployeeEntity.DEFAULT_SITE },
                role = role.trim(),
                phone = phone.trim(),
                dailyWage = dailyWage.coerceAtLeast(0.0),
                otHourlyRate = otHourlyRate.coerceAtLeast(0.0),
                loginId = resolvedLoginId,
                passwordHash = AuthUtils.hashPassword(plainPassword),
                passwordUpdatedAt = now,
                remoteId = remoteId,
                claimedByUid = "",
                createdAt = now
            )
        )
        return CreatedEmployeeResult(
            employeeId = id,
            remoteId = remoteId,
            loginId = resolvedLoginId,
            plainPassword = plainPassword
        )
    }

    suspend fun addEmployee(
        name: String,
        employeeCode: String = "",
        site: String = EmployeeEntity.DEFAULT_SITE,
        role: String = "",
        phone: String = "",
        dailyWage: Double = 0.0,
        otHourlyRate: Double = 0.0
    ): Long {
        return addEmployeeWithCredentials(
            name = name,
            employeeCode = employeeCode,
            site = site,
            role = role,
            phone = phone,
            dailyWage = dailyWage,
            otHourlyRate = otHourlyRate
        ).employeeId
    }

    suspend fun resetEmployeePassword(
        employee: EmployeeEntity,
        newPlainPassword: String
    ): Pair<String, String> {
        val trimmedPassword = newPlainPassword.trim().ifBlank { AuthUtils.generateReadablePassword() }
        val now = System.currentTimeMillis()
        if (employee.loginId.isBlank()) {
            val existingIds = dao.getAllEmployeeLoginIds().toSet()
            val count = dao.getEmployeeCount()
            val newLoginId = AuthUtils.generateEmployeeLoginId(
                employeeCode = employee.employeeCode,
                existingLoginIds = existingIds,
                existingCount = count
            )
            dao.updateEmployee(
                employee.copy(
                    loginId = newLoginId,
                    passwordHash = AuthUtils.hashPassword(trimmedPassword),
                    passwordUpdatedAt = now,
                    remoteId = employee.remoteId.ifBlank { buildEmployeeRemoteId(newLoginId) }
                )
            )
            return newLoginId to trimmedPassword
        }
        dao.updateEmployeePassword(
            employeeId = employee.id,
            passwordHash = AuthUtils.hashPassword(trimmedPassword),
            updatedAt = now
        )
        return employee.loginId to trimmedPassword
    }

    suspend fun updateEmployee(
        employee: EmployeeEntity,
        name: String,
        employeeCode: String,
        site: String,
        role: String,
        phone: String,
        dailyWage: Double,
        otHourlyRate: Double
    ) {
        val ensuredRemoteId = employee.remoteId.ifBlank {
            buildEmployeeRemoteId(employee.loginId.ifBlank { employee.id.toString() })
        }
        dao.updateEmployee(
            employee.copy(
                name = name.trim(),
                employeeCode = employeeCode.trim(),
                site = site.trim().ifEmpty { EmployeeEntity.DEFAULT_SITE },
                role = role.trim(),
                phone = phone.trim(),
                dailyWage = dailyWage.coerceAtLeast(0.0),
                otHourlyRate = otHourlyRate.coerceAtLeast(0.0),
                remoteId = ensuredRemoteId
            )
        )
    }

    suspend fun deleteEmployee(employeeId: Long) {
        dao.deleteEmployeeById(employeeId)
    }

    suspend fun saveAttendance(
        employeeId: Long,
        date: String,
        inMinutes: Int,
        outMinutes: Int,
        status: AttendanceStatus = AttendanceStatus.PRESENT,
        note: String = ""
    ): Long {
        val trimmedDate = date.trim()
        val effectiveIn = if (status == AttendanceStatus.PRESENT) inMinutes else 0
        val effectiveOut = if (status == AttendanceStatus.PRESENT) outMinutes else 0
        val (checkInTs, checkOutTs) = AttendanceCalculator.computeCheckInOutTimestamps(
            dateStr = trimmedDate,
            inMinutes = effectiveIn,
            outMinutes = effectiveOut
        )
        val existing = dao.getEntryForEmployeeAndDate(employeeId, trimmedDate)
        val emp = dao.getEmployeeById(employeeId)
        val empRemoteId = emp?.remoteId?.ifBlank { buildEmployeeRemoteId(emp.loginId.ifBlank { emp.id.toString() }) }
            ?: "emp_$employeeId"
        val remoteId = existing?.remoteId?.ifBlank { buildAttendanceRemoteId(empRemoteId, trimmedDate) }
            ?: buildAttendanceRemoteId(empRemoteId, trimmedDate)

        return dao.upsertAttendanceEntry(
            AttendanceEntryEntity(
                id = existing?.id ?: 0L,
                employeeId = employeeId,
                date = trimmedDate,
                inMinutes = effectiveIn,
                outMinutes = effectiveOut,
                status = status.storageValue,
                note = note.trim(),
                checkInTimestamp = checkInTs,
                checkOutTimestamp = checkOutTs,
                remoteId = remoteId,
                hasCheckedIn = true,
                hasCheckedOut = true,
                markedByRole = "ADMIN",
                checkInPhotoBase64 = existing?.checkInPhotoBase64 ?: "",
                checkInLat = existing?.checkInLat,
                checkInLng = existing?.checkInLng,
                checkInAddress = existing?.checkInAddress ?: "",
                checkOutPhotoBase64 = existing?.checkOutPhotoBase64 ?: "",
                checkOutLat = existing?.checkOutLat,
                checkOutLng = existing?.checkOutLng,
                checkOutAddress = existing?.checkOutAddress ?: "",
                updatedAt = System.currentTimeMillis()
            )
        )
    }

    suspend fun recordSelfCheckInWithProof(
        employeeId: Long,
        date: String,
        inMinutes: Int,
        photoBase64: String,
        lat: Double,
        lng: Double,
        address: String
    ): SelfPunchResult {
        val employee = dao.getEmployeeById(employeeId)
            ?: return SelfPunchResult.Rejected(SelfPunchRejectReason.EMPLOYEE_NOT_FOUND)
        val trimmedDate = date.trim()
        val existing = dao.getEntryForEmployeeAndDate(employeeId, trimmedDate)
        if (existing != null) {
            return if (existing.markedByRole == "ADMIN") {
                SelfPunchResult.Rejected(SelfPunchRejectReason.LOCKED_BY_ADMIN)
            } else {
                SelfPunchResult.Rejected(SelfPunchRejectReason.ALREADY_CHECKED_IN)
            }
        }
        val empRemoteId = employee.remoteId.ifBlank {
            buildEmployeeRemoteId(employee.loginId.ifBlank { employee.id.toString() })
        }
        val remoteId = buildAttendanceRemoteId(empRemoteId, trimmedDate)
        val (checkInTs, _) = AttendanceCalculator.computeCheckInOutTimestamps(
            dateStr = trimmedDate,
            inMinutes = inMinutes,
            outMinutes = inMinutes
        )
        val entry = AttendanceEntryEntity(
            employeeId = employeeId,
            date = trimmedDate,
            inMinutes = inMinutes,
            outMinutes = inMinutes,
            status = AttendanceStatus.PRESENT.storageValue,
            note = "Self check-in (Selfie + GPS verified)",
            checkInTimestamp = checkInTs,
            checkOutTimestamp = checkInTs,
            remoteId = remoteId,
            hasCheckedIn = true,
            hasCheckedOut = false,
            markedByRole = "EMPLOYEE",
            checkInPhotoBase64 = photoBase64,
            checkInLat = lat,
            checkInLng = lng,
            checkInAddress = address.trim(),
            checkOutPhotoBase64 = "",
            checkOutLat = null,
            checkOutLng = null,
            checkOutAddress = "",
            updatedAt = System.currentTimeMillis()
        )
        val insertedId = dao.upsertAttendanceEntry(entry)
        return SelfPunchResult.Success(entry.copy(id = insertedId))
    }

    suspend fun recordSelfCheckOutWithProof(
        employeeId: Long,
        date: String,
        outMinutes: Int,
        photoBase64: String,
        lat: Double,
        lng: Double,
        address: String
    ): SelfPunchResult {
        dao.getEmployeeById(employeeId)
            ?: return SelfPunchResult.Rejected(SelfPunchRejectReason.EMPLOYEE_NOT_FOUND)
        val trimmedDate = date.trim()
        val existing = dao.getEntryForEmployeeAndDate(employeeId, trimmedDate)
            ?: return SelfPunchResult.Rejected(SelfPunchRejectReason.NOT_CHECKED_IN_YET)
        if (existing.markedByRole == "ADMIN") {
            return SelfPunchResult.Rejected(SelfPunchRejectReason.LOCKED_BY_ADMIN)
        }
        if (!existing.hasCheckedIn) {
            return SelfPunchResult.Rejected(SelfPunchRejectReason.NOT_CHECKED_IN_YET)
        }
        if (existing.hasCheckedOut) {
            return SelfPunchResult.Rejected(SelfPunchRejectReason.ALREADY_CHECKED_OUT)
        }
        if (outMinutes == existing.inMinutes) {
            return SelfPunchResult.Rejected(SelfPunchRejectReason.SAME_IN_OUT_TIME)
        }
        val (checkInTs, checkOutTs) = AttendanceCalculator.computeCheckInOutTimestamps(
            dateStr = trimmedDate,
            inMinutes = existing.inMinutes,
            outMinutes = outMinutes
        )
        val updated = existing.copy(
            outMinutes = outMinutes,
            checkInTimestamp = checkInTs,
            checkOutTimestamp = checkOutTs,
            hasCheckedOut = true,
            note = "Self check-in & check-out (Selfie + GPS verified)",
            checkOutPhotoBase64 = photoBase64,
            checkOutLat = lat,
            checkOutLng = lng,
            checkOutAddress = address.trim(),
            updatedAt = System.currentTimeMillis()
        )
        dao.upsertAttendanceEntry(updated)
        return SelfPunchResult.Success(updated)
    }

    suspend fun saveRollCallBatch(
        employeeIds: List<Long>,
        date: String,
        inMinutes: Int,
        outMinutes: Int,
        status: AttendanceStatus = AttendanceStatus.PRESENT
    ) {
        for (empId in employeeIds) {
            saveAttendance(
                employeeId = empId,
                date = date,
                inMinutes = inMinutes,
                outMinutes = outMinutes,
                status = status,
                note = ""
            )
        }
    }

    suspend fun restoreEntry(entry: AttendanceRecordItem) {
        val empRemoteId = entry.employeeRemoteId.ifBlank {
            buildEmployeeRemoteId(entry.employeeLoginId.ifBlank { entry.employeeId.toString() })
        }
        dao.upsertAttendanceEntry(
            AttendanceEntryEntity(
                employeeId = entry.employeeId,
                date = entry.date,
                inMinutes = entry.inMinutes,
                outMinutes = entry.outMinutes,
                status = entry.status,
                note = entry.note,
                checkInTimestamp = entry.checkInTimestamp,
                checkOutTimestamp = entry.checkOutTimestamp,
                remoteId = entry.remoteId.ifBlank { buildAttendanceRemoteId(empRemoteId, entry.date) },
                hasCheckedIn = entry.hasCheckedIn,
                hasCheckedOut = entry.hasCheckedOut,
                markedByRole = entry.markedByRole,
                checkInPhotoBase64 = entry.checkInPhotoBase64,
                checkInLat = entry.checkInLat,
                checkInLng = entry.checkInLng,
                checkInAddress = entry.checkInAddress,
                checkOutPhotoBase64 = entry.checkOutPhotoBase64,
                checkOutLat = entry.checkOutLat,
                checkOutLng = entry.checkOutLng,
                checkOutAddress = entry.checkOutAddress,
                updatedAt = entry.updatedAt
            )
        )
    }

    suspend fun deleteAttendanceEntry(entryId: Long) {
        dao.deleteAttendanceEntryById(entryId)
    }

    // Expenses
    suspend fun addExpense(
        employeeId: Long?,
        employeeName: String,
        site: String,
        title: String,
        category: String,
        amount: Double,
        date: String,
        status: String = "PENDING",
        note: String = "",
        employeeUid: String = ""
    ): Long {
        val now = System.currentTimeMillis()
        val remoteId = buildRandomRemoteId("exp")
        return dao.insertExpense(
            ExpenseEntity(
                employeeId = employeeId,
                employeeName = employeeName.trim(),
                site = site.trim().ifEmpty { EmployeeEntity.DEFAULT_SITE },
                title = title.trim(),
                category = category.trim(),
                amount = amount.coerceAtLeast(0.0),
                date = date.trim(),
                status = status,
                isClaimed = false,
                approvedAt = if (status == "APPROVED") now else 0L,
                claimedAt = 0L,
                note = note.trim(),
                remoteId = remoteId,
                employeeUid = employeeUid,
                createdAt = now
            )
        )
    }

    suspend fun updateExpenseStatus(expenseId: Long, status: String) {
        val now = System.currentTimeMillis()
        dao.updateExpenseStatus(
            expenseId = expenseId,
            status = status,
            approvedAt = if (status == "APPROVED") now else 0L
        )
    }

    suspend fun updateExpenseClaimed(expenseId: Long, isClaimed: Boolean) {
        dao.updateExpenseClaimed(
            expenseId = expenseId,
            isClaimed = isClaimed,
            claimedAt = if (isClaimed) System.currentTimeMillis() else 0L
        )
    }

    suspend fun deleteExpense(expenseId: Long) {
        dao.deleteExpenseById(expenseId)
    }

    // Approvals
    suspend fun submitApprovalRequest(
        employeeId: Long,
        employeeName: String,
        type: String,
        date: String,
        inMinutes: Int,
        outMinutes: Int,
        reason: String,
        employeeUid: String = ""
    ): Long {
        return dao.insertApprovalRequest(
            ApprovalRequestEntity(
                employeeId = employeeId,
                employeeName = employeeName.trim(),
                type = type,
                date = date.trim(),
                inMinutes = inMinutes,
                outMinutes = outMinutes,
                reason = reason.trim(),
                status = "PENDING",
                remoteId = buildRandomRemoteId("apr"),
                employeeUid = employeeUid,
                createdAt = System.currentTimeMillis()
            )
        )
    }

    suspend fun resolveApprovalRequest(
        request: ApprovalRequestEntity,
        approved: Boolean
    ) {
        val newStatus = if (approved) "APPROVED" else "REJECTED"
        dao.updateApprovalStatus(request.id, newStatus)
        if (approved) {
            when (request.type) {
                "LEAVE" -> saveAttendance(
                    employeeId = request.employeeId,
                    date = request.date,
                    inMinutes = 0,
                    outMinutes = 0,
                    status = AttendanceStatus.LEAVE,
                    note = "Approved leave: ${request.reason}".trimEnd(':', ' ')
                )
                "ATTENDANCE_CORRECTION", "OVERTIME" -> saveAttendance(
                    employeeId = request.employeeId,
                    date = request.date,
                    inMinutes = request.inMinutes,
                    outMinutes = request.outMinutes,
                    status = AttendanceStatus.PRESENT,
                    note = "Approved ${request.type.lowercase().replace('_', ' ')}: ${request.reason}".trimEnd(':', ' ')
                )
            }
        }
    }

    suspend fun deleteApprovalRequest(requestId: Long) {
        dao.deleteApprovalRequestById(requestId)
    }

    // Room <-> Firestore Sync Reconciliation
    suspend fun reconcileRemoteEmployees(remoteEmployees: List<EmployeeEntity>) {
        if (remoteEmployees.isEmpty()) return
        for (remote in remoteEmployees) {
            val local = dao.getEmployeeByRemoteId(remote.remoteId)
                ?: dao.getEmployeeByLoginId(remote.loginId)
            if (local == null) {
                dao.insertEmployee(remote.copy(id = 0L))
            } else {
                dao.updateEmployee(
                    local.copy(
                        name = remote.name,
                        employeeCode = remote.employeeCode,
                        site = remote.site,
                        role = remote.role,
                        phone = remote.phone,
                        dailyWage = remote.dailyWage,
                        otHourlyRate = remote.otHourlyRate,
                        loginId = remote.loginId,
                        passwordHash = remote.passwordHash,
                        passwordUpdatedAt = remote.passwordUpdatedAt,
                        remoteId = remote.remoteId,
                        claimedByUid = remote.claimedByUid.ifBlank { local.claimedByUid }
                    )
                )
            }
        }
    }

    suspend fun reconcileRemoteAttendance(remoteRecords: List<RemoteAttendancePayload>) {
        if (remoteRecords.isEmpty()) return
        for (item in remoteRecords) {
            val emp = dao.getEmployeeByRemoteId(item.employeeRemoteId)
                ?: item.employeeLoginId.takeIf { it.isNotBlank() }?.let { dao.getEmployeeByLoginId(it) }
                ?: continue
            val existingByDate = dao.getEntryForEmployeeAndDate(emp.id, item.date)
            val existingByRemote = dao.getAttendanceByRemoteId(item.remoteId)
            val localId = existingByDate?.id ?: existingByRemote?.id ?: 0L
            dao.upsertAttendanceEntry(
                AttendanceEntryEntity(
                    id = localId,
                    employeeId = emp.id,
                    date = item.date,
                    inMinutes = item.inMinutes,
                    outMinutes = item.outMinutes,
                    status = item.status,
                    note = item.note,
                    checkInTimestamp = item.checkInTimestamp,
                    checkOutTimestamp = item.checkOutTimestamp,
                    remoteId = item.remoteId,
                    hasCheckedIn = item.hasCheckedIn,
                    hasCheckedOut = item.hasCheckedOut,
                    markedByRole = item.markedByRole,
                    checkInPhotoBase64 = item.checkInPhotoBase64,
                    checkInLat = item.checkInLat,
                    checkInLng = item.checkInLng,
                    checkInAddress = item.checkInAddress,
                    checkOutPhotoBase64 = item.checkOutPhotoBase64,
                    checkOutLat = item.checkOutLat,
                    checkOutLng = item.checkOutLng,
                    checkOutAddress = item.checkOutAddress,
                    updatedAt = item.updatedAtMs
                )
            )
        }
    }

    suspend fun reconcileRemoteExpenses(remoteExpenses: List<RemoteExpensePayload>) {
        if (remoteExpenses.isEmpty()) return
        for (item in remoteExpenses) {
            val localEmp = item.employeeLoginId.takeIf { it.isNotBlank() }?.let { dao.getEmployeeByLoginId(it) }
                ?: item.employeeId?.let { dao.getEmployeeById(it) }
            val existing = dao.getExpenseByRemoteId(item.remoteId)
            dao.insertExpense(
                ExpenseEntity(
                    id = existing?.id ?: 0L,
                    employeeId = localEmp?.id ?: existing?.employeeId ?: item.employeeId,
                    employeeName = item.employeeName,
                    site = item.site,
                    title = item.title,
                    category = item.category,
                    amount = item.amount,
                    date = item.date,
                    status = item.status,
                    isClaimed = item.isClaimed,
                    approvedAt = item.approvedAt,
                    claimedAt = item.claimedAt,
                    note = item.note,
                    remoteId = item.remoteId,
                    employeeUid = item.employeeUid,
                    createdAt = item.createdAtMs
                )
            )
        }
    }

    suspend fun reconcileRemoteApprovals(remoteApprovals: List<RemoteApprovalPayload>) {
        if (remoteApprovals.isEmpty()) return
        for (item in remoteApprovals) {
            val localEmp = item.employeeLoginId.takeIf { it.isNotBlank() }?.let { dao.getEmployeeByLoginId(it) }
                ?: dao.getEmployeeById(item.employeeId)
                ?: continue
            val existing = dao.getApprovalByRemoteId(item.remoteId)
            dao.insertApprovalRequest(
                ApprovalRequestEntity(
                    id = existing?.id ?: 0L,
                    employeeId = localEmp.id,
                    employeeName = item.employeeName,
                    type = item.type,
                    date = item.date,
                    inMinutes = item.inMinutes,
                    outMinutes = item.outMinutes,
                    reason = item.reason,
                    status = item.status,
                    remoteId = item.remoteId,
                    employeeUid = item.employeeUid,
                    createdAt = item.createdAtMs
                )
            )
        }
    }

    suspend fun upsertRemoteAdminAccounts(admins: List<AdminAccountEntity>) {
        for (admin in admins) {
            if (dao.getAdminByMobile(admin.mobile) == null) {
                dao.upsertAdminAccount(admin.copy(id = 0L))
            }
        }
    }

    companion object {
        fun buildEmployeeRemoteId(loginOrKey: String): String {
            val clean = loginOrKey.trim().uppercase().replace(Regex("[^A-Z0-9_\\-]"), "_")
            return "emp_${clean.ifBlank { UUID.randomUUID().toString().replace("-", "").take(12) }}".take(100)
        }

        fun buildAttendanceRemoteId(employeeRemoteId: String, dateStr: String): String {
            val safeEmp = employeeRemoteId.replace(Regex("[^A-Za-z0-9_\\-]"), "_")
            val safeDate = dateStr.trim().replace("/", "-")
            return "att_${safeEmp}_${safeDate}".take(120)
        }

        fun buildRandomRemoteId(prefix: String): String =
            "${prefix}_${UUID.randomUUID().toString().replace("-", "").take(20)}"
    }
}

data class RemoteAttendancePayload(
    val remoteId: String,
    val employeeUid: String,
    val employeeId: Long,
    val employeeRemoteId: String,
    val employeeLoginId: String,
    val date: String,
    val inMinutes: Int,
    val outMinutes: Int,
    val status: String,
    val note: String,
    val hasCheckedIn: Boolean,
    val hasCheckedOut: Boolean,
    val markedByRole: String,
    val checkInTimestamp: Long,
    val checkOutTimestamp: Long,
    val checkInPhotoBase64: String,
    val checkInLat: Double?,
    val checkInLng: Double?,
    val checkInAddress: String,
    val checkOutPhotoBase64: String,
    val checkOutLat: Double?,
    val checkOutLng: Double?,
    val checkOutAddress: String,
    val updatedAtMs: Long
)

data class RemoteExpensePayload(
    val remoteId: String,
    val employeeUid: String,
    val localId: Long,
    val employeeId: Long?,
    val employeeLoginId: String,
    val employeeName: String,
    val site: String,
    val title: String,
    val category: String,
    val amount: Double,
    val date: String,
    val status: String,
    val isClaimed: Boolean,
    val approvedAt: Long,
    val claimedAt: Long,
    val note: String,
    val createdAtMs: Long
)

data class RemoteApprovalPayload(
    val remoteId: String,
    val employeeUid: String,
    val localId: Long,
    val employeeId: Long,
    val employeeLoginId: String,
    val employeeName: String,
    val type: String,
    val date: String,
    val inMinutes: Int,
    val outMinutes: Int,
    val reason: String,
    val status: String,
    val createdAtMs: Long
)

