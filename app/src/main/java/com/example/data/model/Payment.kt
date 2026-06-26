package com.example.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "payments")
data class Payment(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val clientId: Long,
    val amount: Double,
    val serviceName: String,
    val date: Long = System.currentTimeMillis(),
    val notes: String = ""
)
