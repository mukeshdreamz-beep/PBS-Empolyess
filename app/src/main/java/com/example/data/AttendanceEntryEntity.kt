package com.example.data

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "attendance_entries",
    foreignKeys = [
        ForeignKey(
            entity = EmployeeEntity::class,
            parentColumns = ["id"],
            childColumns = ["employeeId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["employeeId", "date"], unique = true),
        Index(value = ["employeeId"]),
        Index(value = ["checkInTimestamp"])
    ]
)
data class AttendanceEntryEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,
    val employeeId: Long,
    val date: String,
    val inMinutes: Int,
    val outMinutes: Int,
    val status: String = "PRESENT",
    val note: String = "",
    val checkInTimestamp: Long = 0L,
    val checkOutTimestamp: Long = 0L,
    val remoteId: String = "",
    val hasCheckedIn: Boolean = true,
    val hasCheckedOut: Boolean = true,
    val markedByRole: String = "ADMIN",
    val checkInPhotoBase64: String = "",
    val checkInLat: Double? = null,
    val checkInLng: Double? = null,
    val checkInAddress: String = "",
    val checkOutPhotoBase64: String = "",
    val checkOutLat: Double? = null,
    val checkOutLng: Double? = null,
    val checkOutAddress: String = "",
    val updatedAt: Long = System.currentTimeMillis()
)
