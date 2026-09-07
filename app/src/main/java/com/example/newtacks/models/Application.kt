package com.example.newtacks.models

data class Application(
    val applicationId: String = "",
    val hiringId: String = "",
    val companyId: String = "",
    val workerId: String = "",
    val jobTitle: String = "",
    val status: String = "APPLIED", // APPLIED, INTERVIEW_SCHEDULED, HIRED, REJECTED
    val interviewDate: Long? = null,
    val interviewLocation: String? = null,
    val workerResponse: String? = null, // ACCEPTED, RESCHEDULE
    val createdAt: Long = System.currentTimeMillis()
)
