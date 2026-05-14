package com.example.newtacks.chatbot.model

import com.google.gson.annotations.SerializedName

data class ChatRequest(
    @SerializedName("message")
    val message: String
)

data class ChatResponse(
    @SerializedName("reply")
    val reply: String
)
