package com.example.newtacks.chatbot.presentation.viewmodel

import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.example.newtacks.chatbot.presentation.state.ChatMessage
import com.google.firebase.firestore.FirebaseFirestore
import java.util.*

class ChatViewModel : ViewModel() {

    private val db = FirebaseFirestore.getInstance()
    val messages = MutableLiveData<MutableList<ChatMessage>>(mutableListOf())
    val isLoading = MutableLiveData<Boolean>(false)

    init {
        // Initial welcome message
        val welcome = ChatMessage("Hey! I'm Tey, your STRACT AI assistant. How can I help you today?", false)
        messages.value?.add(welcome)
    }

    fun sendMessage(text: String, role: String) {
        if (text.isBlank()) return

        // 1. Add user message to UI
        val userMsg = ChatMessage(text, true)
        val currentList = messages.value ?: mutableListOf()
        currentList.add(userMsg)
        messages.value = currentList

        // 2. Search Firestore for keywords filtered by ROLE
        isLoading.value = true
        val upperRole = role.uppercase(Locale.getDefault())
        
        db.collection("chatbot_knowledge")
            .whereIn("role", listOf(upperRole, "ALL")) // Filter by role or general info
            .get()
            .addOnSuccessListener { snapshots ->
                var responseFound = false
                val userTextLower = text.lowercase(Locale.getDefault())

                for (doc in snapshots.documents) {
                    val keywords = doc.get("keywords") as? List<String> ?: emptyList()
                    val response = doc.getString("response") ?: ""

                    // Check if any keyword matches within the user's sentence
                    if (keywords.any { userTextLower.contains(it.lowercase(Locale.getDefault())) }) {
                        addBotMessage(response)
                        responseFound = true
                        break
                    }
                }

                if (!responseFound) {
                    addBotMessage("I'm sorry, I don't have role-specific information on that. Try asking about payments, verification, or how to get started!")
                }
                isLoading.value = false
            }.addOnFailureListener {
                addBotMessage("I'm having trouble connecting to my knowledge base. Please try again later.")
                isLoading.value = false
            }
    }

    private fun addBotMessage(text: String) {
        val currentList = messages.value ?: mutableListOf()
        currentList.add(ChatMessage(text, false))
        messages.value = currentList
    }
}