package com.example.data.model

data class TransactionItem(
    val paymentId: Long,
    val clientId: Long,
    val clientName: String,
    val clientPhone: String,
    val clientPhotoPath: String?,
    val amount: Double,
    val serviceName: String,
    val date: Long,
    val notes: String
)
