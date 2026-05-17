package com.example.newtacks.chatbot.presentation.viewmodel

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.newtacks.chatbot.data.repository.ChatRepository
import com.example.newtacks.chatbot.model.ChatResponse
import com.example.newtacks.chatbot.presentation.state.ChatMessage
import kotlinx.coroutines.launch

class ChatViewModel(private val repository: ChatRepository) : ViewModel() {

    private val _chatResponse = MutableLiveData<Result<ChatResponse>>()
    val chatResponse: LiveData<Result<ChatResponse>> = _chatResponse

    private val _isLoading = MutableLiveData<Boolean>()
    val isLoading: LiveData<Boolean> = _isLoading

    // Expose session messages from repository
    val sessionMessages: List<ChatMessage> get() = repository.getSessionMessages()

    fun addLocalMessage(message: String, isUser: Boolean) {
        repository.addMessageToSession(ChatMessage(message, isUser))
    }

    fun sendMessage(message: String, role: String) {
        // Save user message to session immediately for UI consistency
        repository.addMessageToSession(ChatMessage(message, true))

        viewModelScope.launch {
            _isLoading.value = true
            val result = repository.sendMessage(message, role)
            _chatResponse.value = result
            _isLoading.value = false
        }
    }
}
