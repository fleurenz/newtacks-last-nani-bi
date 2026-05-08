package com.example.newtacks.models

import com.google.firebase.firestore.PropertyName

data class Review(
    val reviewId: String = "",
    val jobId: String = "",
    val clientId: String = "",
    val clientName: String = "",
    val workerId: String = "",
    val rating: Float = 0f,
    val comment: String = "",
    @get:PropertyName("isAnonymous")
    @PropertyName("isAnonymous")
    val isAnonymous: Boolean = false,
    val createdAt: Long = System.currentTimeMillis()
)
