package com.example.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface AttendanceDao {

    // Admin Accounts
    @Query("SELECT COUNT(*) FROM admin_accounts")
    suspend fun getAdminCount(): Int

    @Query("SELECT * FROM admin_accounts WHERE mobile = :mobile LIMIT 1")
    suspend fun getAdminByMobile(mobile: String): AdminAccountEntity?

    @Query("SELECT * FROM admin_accounts ORDER BY id ASC")
    suspend fun getAllAdminAccountsList(): List<AdminAccountEntity>

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertAdminAccount(admin: AdminAccountEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAdminAccount(admin: AdminAccountEntity): Long

    // Employees
    @Query("SELECT * FROM employees ORDER BY name COLLATE NOCASE ASC")
    fun observeEmployees(): Flow<List<EmployeeEntity>>

    @Query("SELECT * FROM employees ORDER BY id ASC")
    suspend fun getAllEmployeesList(): List<EmployeeEntity>

    @Query("SELECT * FROM employees WHERE id = :employeeId LIMIT 1")
    suspend fun getEmployeeById(employeeId: Long): EmployeeEntity?

    @Query("SELECT * FROM employees WHERE remoteId = :remoteId LIMIT 1")
    suspend fun getEmployeeByRemoteId(remoteId: String): EmployeeEntity?

    @Query("SELECT * FROM employees WHERE UPPER(loginId) = UPPER(:loginId) LIMIT 1")
    suspend fun getEmployeeByLoginId(loginId: String): EmployeeEntity?

    @Query("SELECT loginId FROM employees WHERE loginId != ''")
    suspend fun getAllEmployeeLoginIds(): List<String>

    @Query("SELECT COUNT(*) FROM employees")
    suspend fun getEmployeeCount(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertEmployee(employee: EmployeeEntity): Long

    @Update
    suspend fun updateEmployee(employee: EmployeeEntity)

    @Query("UPDATE employees SET passwordHash = :passwordHash, passwordUpdatedAt = :updatedAt WHERE id = :employeeId")
    suspend fun updateEmployeePassword(employeeId: Long, passwordHash: String, updatedAt: Long)

    @Query("UPDATE employees SET remoteId = :remoteId, claimedByUid = :claimedByUid WHERE id = :employeeId")
    suspend fun updateEmployeeSyncFields(employeeId: Long, remoteId: String, claimedByUid: String)

    @Query("DELETE FROM employees WHERE id = :employeeId")
    suspend fun deleteEmployeeById(employeeId: Long)

    @Query("DELETE FROM employees WHERE remoteId = :remoteId")
    suspend fun deleteEmployeeByRemoteId(remoteId: String)

    // Attendance Entries
    @Query(
        """
        SELECT 
            a.id AS id,
            a.employeeId AS employeeId,
            e.name AS employeeName,
            e.employeeCode AS employeeCode,
            e.site AS employeeSite,
            e.role AS employeeRole,
            e.phone AS employeePhone,
            e.loginId AS employeeLoginId,
            e.remoteId AS employeeRemoteId,
            a.date AS date,
            a.inMinutes AS inMinutes,
            a.outMinutes AS outMinutes,
            a.status AS status,
            a.note AS note,
            a.checkInTimestamp AS checkInTimestamp,
            a.checkOutTimestamp AS checkOutTimestamp,
            a.remoteId AS remoteId,
            a.hasCheckedIn AS hasCheckedIn,
            a.hasCheckedOut AS hasCheckedOut,
            a.markedByRole AS markedByRole,
            a.checkInPhotoBase64 AS checkInPhotoBase64,
            a.checkInLat AS checkInLat,
            a.checkInLng AS checkInLng,
            a.checkInAddress AS checkInAddress,
            a.checkOutPhotoBase64 AS checkOutPhotoBase64,
            a.checkOutLat AS checkOutLat,
            a.checkOutLng AS checkOutLng,
            a.checkOutAddress AS checkOutAddress,
            a.updatedAt AS updatedAt
        FROM attendance_entries a
        INNER JOIN employees e ON a.employeeId = e.id
        ORDER BY a.updatedAt DESC, a.id DESC
        """
    )
    fun observeAttendanceRecords(): Flow<List<AttendanceRecordItem>>

    @Query(
        """
        SELECT 
            a.id AS id,
            a.employeeId AS employeeId,
            e.name AS employeeName,
            e.employeeCode AS employeeCode,
            e.site AS employeeSite,
            e.role AS employeeRole,
            e.phone AS employeePhone,
            e.loginId AS employeeLoginId,
            e.remoteId AS employeeRemoteId,
            a.date AS date,
            a.inMinutes AS inMinutes,
            a.outMinutes AS outMinutes,
            a.status AS status,
            a.note AS note,
            a.checkInTimestamp AS checkInTimestamp,
            a.checkOutTimestamp AS checkOutTimestamp,
            a.remoteId AS remoteId,
            a.hasCheckedIn AS hasCheckedIn,
            a.hasCheckedOut AS hasCheckedOut,
            a.markedByRole AS markedByRole,
            a.checkInPhotoBase64 AS checkInPhotoBase64,
            a.checkInLat AS checkInLat,
            a.checkInLng AS checkInLng,
            a.checkInAddress AS checkInAddress,
            a.checkOutPhotoBase64 AS checkOutPhotoBase64,
            a.checkOutLat AS checkOutLat,
            a.checkOutLng AS checkOutLng,
            a.checkOutAddress AS checkOutAddress,
            a.updatedAt AS updatedAt
        FROM attendance_entries a
        INNER JOIN employees e ON a.employeeId = e.id
        WHERE a.employeeId = :employeeId
        ORDER BY a.checkInTimestamp DESC, a.id DESC
        """
    )
    fun observeRecordsForEmployee(employeeId: Long): Flow<List<AttendanceRecordItem>>

    @Query(
        """
        SELECT 
            a.id AS id,
            a.employeeId AS employeeId,
            e.name AS employeeName,
            e.employeeCode AS employeeCode,
            e.site AS employeeSite,
            e.role AS employeeRole,
            e.phone AS employeePhone,
            e.loginId AS employeeLoginId,
            e.remoteId AS employeeRemoteId,
            a.date AS date,
            a.inMinutes AS inMinutes,
            a.outMinutes AS outMinutes,
            a.status AS status,
            a.note AS note,
            a.checkInTimestamp AS checkInTimestamp,
            a.checkOutTimestamp AS checkOutTimestamp,
            a.remoteId AS remoteId,
            a.hasCheckedIn AS hasCheckedIn,
            a.hasCheckedOut AS hasCheckedOut,
            a.markedByRole AS markedByRole,
            a.checkInPhotoBase64 AS checkInPhotoBase64,
            a.checkInLat AS checkInLat,
            a.checkInLng AS checkInLng,
            a.checkInAddress AS checkInAddress,
            a.checkOutPhotoBase64 AS checkOutPhotoBase64,
            a.checkOutLat AS checkOutLat,
            a.checkOutLng AS checkOutLng,
            a.checkOutAddress AS checkOutAddress,
            a.updatedAt AS updatedAt
        FROM attendance_entries a
        INNER JOIN employees e ON a.employeeId = e.id
        WHERE a.date = :date
        ORDER BY a.checkInTimestamp DESC, a.id DESC
        """
    )
    fun observeRecordsByDate(date: String): Flow<List<AttendanceRecordItem>>

    @Query("SELECT * FROM attendance_entries ORDER BY updatedAt DESC")
    suspend fun getAllAttendanceList(): List<AttendanceEntryEntity>

    @Query("SELECT * FROM attendance_entries WHERE id = :entryId LIMIT 1")
    suspend fun getAttendanceById(entryId: Long): AttendanceEntryEntity?

    @Query("SELECT * FROM attendance_entries WHERE remoteId = :remoteId LIMIT 1")
    suspend fun getAttendanceByRemoteId(remoteId: String): AttendanceEntryEntity?

    @Query("SELECT * FROM attendance_entries WHERE employeeId = :employeeId AND date = :date LIMIT 1")
    suspend fun getEntryForEmployeeAndDate(employeeId: Long, date: String): AttendanceEntryEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAttendanceEntry(entry: AttendanceEntryEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAttendanceEntries(entries: List<AttendanceEntryEntity>)

    @Query("DELETE FROM attendance_entries WHERE id = :entryId")
    suspend fun deleteAttendanceEntryById(entryId: Long)

    @Query("DELETE FROM attendance_entries WHERE remoteId = :remoteId")
    suspend fun deleteAttendanceEntryByRemoteId(remoteId: String)

    // Expenses
    @Query("SELECT * FROM expenses ORDER BY createdAt DESC, id DESC")
    fun observeExpenses(): Flow<List<ExpenseEntity>>

    @Query("SELECT * FROM expenses ORDER BY createdAt DESC")
    suspend fun getAllExpensesList(): List<ExpenseEntity>

    @Query("SELECT * FROM expenses WHERE id = :expenseId LIMIT 1")
    suspend fun getExpenseById(expenseId: Long): ExpenseEntity?

    @Query("SELECT * FROM expenses WHERE remoteId = :remoteId LIMIT 1")
    suspend fun getExpenseByRemoteId(remoteId: String): ExpenseEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertExpense(expense: ExpenseEntity): Long

    @Query("UPDATE expenses SET status = :status, approvedAt = :approvedAt, isClaimed = CASE WHEN :status != 'APPROVED' THEN 0 ELSE isClaimed END, claimedAt = CASE WHEN :status != 'APPROVED' THEN 0 ELSE claimedAt END WHERE id = :expenseId")
    suspend fun updateExpenseStatus(expenseId: Long, status: String, approvedAt: Long)

    @Query("UPDATE expenses SET isClaimed = :isClaimed, claimedAt = :claimedAt WHERE id = :expenseId AND status = 'APPROVED'")
    suspend fun updateExpenseClaimed(expenseId: Long, isClaimed: Boolean, claimedAt: Long)

    @Query("DELETE FROM expenses WHERE id = :expenseId")
    suspend fun deleteExpenseById(expenseId: Long)

    @Query("DELETE FROM expenses WHERE remoteId = :remoteId")
    suspend fun deleteExpenseByRemoteId(remoteId: String)

    // Approvals
    @Query("SELECT * FROM approval_requests ORDER BY createdAt DESC, id DESC")
    fun observeApprovalRequests(): Flow<List<ApprovalRequestEntity>>

    @Query("SELECT * FROM approval_requests ORDER BY createdAt DESC")
    suspend fun getAllApprovalsList(): List<ApprovalRequestEntity>

    @Query("SELECT * FROM approval_requests WHERE id = :requestId LIMIT 1")
    suspend fun getApprovalById(requestId: Long): ApprovalRequestEntity?

    @Query("SELECT * FROM approval_requests WHERE remoteId = :remoteId LIMIT 1")
    suspend fun getApprovalByRemoteId(remoteId: String): ApprovalRequestEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertApprovalRequest(request: ApprovalRequestEntity): Long

    @Query("UPDATE approval_requests SET status = :status WHERE id = :requestId")
    suspend fun updateApprovalStatus(requestId: Long, status: String)

    @Query("DELETE FROM approval_requests WHERE id = :requestId")
    suspend fun deleteApprovalRequestById(requestId: Long)

    @Query("DELETE FROM approval_requests WHERE remoteId = :remoteId")
    suspend fun deleteApprovalRequestByRemoteId(remoteId: String)
}
