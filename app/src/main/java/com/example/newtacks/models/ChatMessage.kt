package com.example.newtacks.models

data class ChatMessage(
    val messageId: String = "",
    val jobId: String = "",
    val workerId: String = "",
    val senderId: String = "",
    val receiverId: String = "",
    val text: String = "",
    val timestamp: Long = System.currentTimeMillis()
)