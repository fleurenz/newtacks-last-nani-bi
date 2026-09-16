package com.example.newtacks.models

data class VerificationRequest(
    val requestId: String = "",
    val workerId: String = "",
    val workerName: String = "",
    val type: String = "", // "SKILL" or "GENERAL_CERT"
    val skillName: String? = null,
    val certName: String? = null,
    val certType: String? = null,
    val fileUrl: String = "",
    val status: String = "PENDING", // PENDING, APPROVED, REJECTED
    val adminComment: String? = null,
    val timestamp: Long = System.currentTimeMillis()
)