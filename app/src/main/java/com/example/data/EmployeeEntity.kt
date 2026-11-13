package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "employees")
data class EmployeeEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,
    val name: String,
    val employeeCode: String = "",
    val site: String = DEFAULT_SITE,
    val role: String = "",
    val phone: String = "",
    val dailyWage: Double = 0.0,
    val otHourlyRate: Double = 0.0,
    val loginId: String = "",
    val passwordHash: String = "",
    val passwordUpdatedAt: Long = 0L,
    val remoteId: String = "",
    val claimedByUid: String = "",
    val createdAt: Long = System.currentTimeMillis()
) {
    companion object {
        const val DEFAULT_SITE = "Sai Baba Nagar"
        val PRESET_SITES = listOf("Sai Baba Nagar", "Meditya Nagar", "Other Sites")
    }
}
