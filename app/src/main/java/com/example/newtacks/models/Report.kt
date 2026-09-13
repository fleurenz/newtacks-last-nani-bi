package com.example.newtacks.models

import com.google.firebase.firestore.ServerTimestamp
import java.util.Date

data class Report(
    val reportId: String = "",
    val reporterId: String = "",
    val reporterName: String = "",
    val reporteeId: String = "",
    val reporteeName: String = "",
    val reason: String = "",
    val description: String = "",
    val evidenceUrl: String? = null,
    val status: String = "PENDING", // PENDING, REVIEWED, ACTION_TAKEN, DISMISSED
    @ServerTimestamp
    val timestamp: Date? = null
)