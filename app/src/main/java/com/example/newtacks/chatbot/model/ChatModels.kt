package com.example.newtacks.chatbot.model

import com.google.gson.annotations.SerializedName

data class ChatRequest(
    @SerializedName("message")
    val message: String,
    @SerializedName("role")
    val role: String
)

data class ChatResponse(
    @SerializedName("reply")
    val reply: String
)
