package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "expenses")
data class ExpenseEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,
    val employeeId: Long? = null,
    val employeeName: String = "",
    val site: String = EmployeeEntity.DEFAULT_SITE,
    val title: String,
    val category: String = "Site Material",
    val amount: Double,
    val date: String,
    val status: String = "PENDING", // PENDING, APPROVED, REJECTED
    val isClaimed: Boolean = false,
    val approvedAt: Long = 0L,
    val claimedAt: Long = 0L,
    val note: String = "",
    val remoteId: String = "",
    val employeeUid: String = "",
    val createdAt: Long = System.currentTimeMillis()
)
