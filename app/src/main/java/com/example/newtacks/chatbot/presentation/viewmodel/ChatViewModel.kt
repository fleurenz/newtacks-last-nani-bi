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

        val userMsg = ChatMessage(text, true)
        val currentList = messages.value ?: mutableListOf()
        currentList.add(userMsg)
        messages.value = currentList

        isLoading.value = true
        val upperRole = role.uppercase(Locale.getDefault())
        
        db.collection("chatbot_knowledge").get().addOnSuccessListener { snapshots ->
            val userTextLower = text.lowercase(Locale.getDefault())
            var bestResponse: String? = null
            var longestMatchLength = 0

            for (doc in snapshots.documents) {
                val docRole = (doc.getString("role") ?: doc.getString("Role") ?: "ALL").uppercase(Locale.getDefault())
                if (docRole != upperRole && docRole != "ALL") continue

                val keywords = doc.get("keywords") as? List<String> ?: emptyList()
                val response = doc.getString("response") ?: ""

                for (keyword in keywords) {
                    val kwLower = keyword.lowercase(Locale.getDefault())
                    if (userTextLower.contains(kwLower)) {
                        if (kwLower.length > longestMatchLength) {
                            longestMatchLength = kwLower.length
                            bestResponse = response
                        }
                    }
                }
            }

            if (bestResponse != null) {
                addBotMessage(bestResponse)
            } else {
                addBotMessage("I'm sorry, I don't have role-specific information on that. I've noted your question so I can learn the answer soon!")
                logUnansweredQuestion(text, role)
            }
            isLoading.value = false
        }.addOnFailureListener {
            addBotMessage("I'm having trouble connecting to my knowledge base. Please try again later.")
            isLoading.value = false
        }
    }

    private fun logUnansweredQuestion(text: String, role: String) {
        val data = mapOf(
            "question" to text,
            "role" to role,
            "timestamp" to System.currentTimeMillis()
        )
        db.collection("unanswered_questions").add(data)
    }

    private fun addBotMessage(text: String) {
        val currentList = messages.value ?: mutableListOf()
        currentList.add(ChatMessage(text, false))
        messages.value = currentList
    }
}