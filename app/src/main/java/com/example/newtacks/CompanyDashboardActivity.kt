package com.example.newtacks

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.example.newtacks.chatbot.presentation.ui.ChatActivity
import com.google.android.material.floatingactionbutton.FloatingActionButton

class CompanyDashboardActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_company_dashboard)

        findViewById<FloatingActionButton>(R.id.fabChat).setOnClickListener {
            val intent = Intent(this, ChatActivity::class.java)
            intent.putExtra("USER_ROLE", "company")
            startActivity(intent)
        }
    }
}