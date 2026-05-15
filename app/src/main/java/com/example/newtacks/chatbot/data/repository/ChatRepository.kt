package com.example.newtacks.chatbot.data.repository

import com.example.newtacks.chatbot.data.remote.ChatApiService
import com.example.newtacks.chatbot.model.ChatRequest
import com.example.newtacks.chatbot.model.ChatResponse
import com.example.newtacks.chatbot.presentation.state.ChatMessage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class ChatRepository(private val apiService: ChatApiService) {

    // In-memory storage for the current session
    private val sessionMessages = mutableListOf<ChatMessage>()

    fun getSessionMessages(): List<ChatMessage> = sessionMessages

    fun addMessageToSession(message: ChatMessage) {
        sessionMessages.add(message)
    }

    fun clearSession() {
        sessionMessages.clear()
    }

    suspend fun sendMessage(message: String, role: String): Result<ChatResponse> = withContext(Dispatchers.IO) {
        try {
            val response = apiService.sendMessage(ChatRequest(message, role))
            if (response.isSuccessful && response.body() != null) {
                val chatResponse = response.body()!!
                // Add bot response to session memory
                addMessageToSession(ChatMessage(chatResponse.reply, false))
                Result.success(chatResponse)
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

    companion object {
        @Volatile
        private var INSTANCE: ChatRepository? = null

        fun getInstance(apiService: ChatApiService): ChatRepository {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: ChatRepository(apiService).also { INSTANCE = it }
            }
        }
    }
}
