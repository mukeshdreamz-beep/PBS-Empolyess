package com.example.data

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "approval_requests",
    foreignKeys = [
        ForeignKey(
            entity = EmployeeEntity::class,
            parentColumns = ["id"],
            childColumns = ["employeeId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["employeeId"])]
)
data class ApprovalRequestEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,
    val employeeId: Long,
    val employeeName: String,
    val type: String, // LEAVE, ATTENDANCE_CORRECTION, OVERTIME
    val date: String,
    val inMinutes: Int = 0,
    val outMinutes: Int = 0,
    val reason: String,
    val status: String = "PENDING", // PENDING, APPROVED, REJECTED
    val remoteId: String = "",
    val employeeUid: String = "",
    val createdAt: Long = System.currentTimeMillis()
)
