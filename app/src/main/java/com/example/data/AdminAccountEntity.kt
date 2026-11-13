package com.example.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "admin_accounts",
    indices = [Index(value = ["mobile"], unique = true)]
)
data class AdminAccountEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,
    val name: String,
    val mobile: String,
    val passwordHash: String,
    val createdAt: Long = System.currentTimeMillis()
)
