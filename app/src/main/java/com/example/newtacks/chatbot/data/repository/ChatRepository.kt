package com.example.newtacks.chatbot.data.repository

import com.example.newtacks.chatbot.data.remote.ChatApiService
import com.example.newtacks.chatbot.model.ChatRequest
import com.example.newtacks.chatbot.model.ChatResponse
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import retrofit2.Response

class ChatRepository(private val apiService: ChatApiService) {

    suspend fun sendMessage(message: String, role: String): Result<ChatResponse> = withContext(Dispatchers.IO) {
        try {
            val response = apiService.sendMessage(ChatRequest(message, role))
            if (response.isSuccessful && response.body() != null) {
                Result.success(response.body()!!)
            } else {
                Result.failure(Exception("Error: ${response.code()} ${response.message()}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun checkStatus(): Boolean = withContext(Dispatchers.IO) {
        try {
            apiService.checkStatus().isSuccessful
        } catch (e: Exception) {
            false
        }
    }
}
