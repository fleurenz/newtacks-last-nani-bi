package com.example.newtacks.models

data class Receipt(

    val receiptId: String = "",
    val jobId: String = "",

    val clientId: String = "",
    val workerId: String = "",

    val clientName: String = "",
    val workerName: String = "",

    val jobTitle: String = "",
    val serviceCategory: String = "",

    val amount: Double = 0.0,

    val createdAt: Long = System.currentTimeMillis(),
    val completedAt: Long = System.currentTimeMillis()
)