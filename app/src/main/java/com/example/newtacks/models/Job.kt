package com.example.newtacks.models

data class Job(

    val jobId: String = "",

    // CLIENT

    val clientId: String = "",
    val clientName: String = "",
    val clientAddress: String = "",

    // WORKER

    val workerId: String? = null,
    val workerName: String? = null,

    // JOB DETAILS

    val jobTitle: String = "",
    val serviceCategory: String = "",
    val description: String = "",

    // SCHEDULE

    val scheduledDate: String = "",
    val scheduledTime: String = "",
    val estimatedDurationHours: Double = 0.0,

    // PAYMENT

    val offeredAmount: Double = 0.0,

    // LIFECYCLE

    val status: String = "AVAILABLE",

    // IMAGES
    val jobImages: List<String> = emptyList(),

    // TIMESTAMPS
    val createdAt: Long = System.currentTimeMillis(),
    val acceptedAt: Long? = null,
    val completedAt: Long? = null
)