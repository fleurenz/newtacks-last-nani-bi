package com.example.newtacks.models

import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.ServerTimestamp
import java.util.Date

data class InAppNotification(
    @DocumentId
    val id: String = "",
    val to: String = "",
    val title: String = "",
    val message: String = "",
    val targetFragment: String? = null,
    val read: Boolean = false,
    val notifiedTray: Boolean = false,
    @ServerTimestamp
    val timestamp: Date? = null
)