package com.example.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "clients")
data class Client(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val phoneNumber: String, // Unique identifier for duplicate detection
    val email: String = "",
    val profilePhotoPath: String? = null,
    val createdAt: Long = System.currentTimeMillis()
)
