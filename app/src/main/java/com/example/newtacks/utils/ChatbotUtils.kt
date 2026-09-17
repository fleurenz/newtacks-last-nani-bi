package com.example.newtacks.utils

import android.content.Context
import android.content.Intent
import com.example.newtacks.chatbot.presentation.ui.ChatActivity
import com.google.android.material.floatingactionbutton.FloatingActionButton

object ChatbotUtils {
    fun setupChatbot(context: Context, fab: FloatingActionButton, role: String) {
        fab.setOnClickListener {
            val intent = Intent(context, ChatActivity::class.java)
            intent.putExtra("USER_ROLE", role)
            context.startActivity(intent)
        }
    }
}