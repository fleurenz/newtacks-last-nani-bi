package com.example.newtacks.chatbot.data.remote

import com.example.newtacks.chatbot.model.ChatRequest
import com.example.newtacks.chatbot.model.ChatResponse
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST

interface ChatApiService {
    @GET("/")
    suspend fun checkStatus(): Response<Unit>

    @POST("/chat")
    suspend fun sendMessage(@Body request: ChatRequest): Response<ChatResponse>
}
