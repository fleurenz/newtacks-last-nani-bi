package com.example.newtacks.utils

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.view.View
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.example.newtacks.R
import com.example.newtacks.models.User
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

class LegalDocumentActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_legal_document)

        val title = intent.getStringExtra(EXTRA_TITLE) ?: "Document"
        val lastUpdated = intent.getStringExtra(EXTRA_LAST_UPDATED) ?: ""
        val body = intent.getStringExtra(EXTRA_BODY) ?: ""

        val toolbar = findViewById<Toolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayShowTitleEnabled(false)
        toolbar.setNavigationOnClickListener { finish() }

        findViewById<TextView>(R.id.tvToolbarTitle).text = title
        findViewById<TextView>(R.id.tvLastUpdated).text = lastUpdated
        findViewById<TextView>(R.id.tvDocumentBody).text = body

        // Robust Inset Handling
        val statusBarSpacer = findViewById<View>(R.id.statusBarSpacer)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(android.R.id.content)) { _, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            statusBarSpacer.layoutParams.height = systemBars.top
            statusBarSpacer.requestLayout()
            insets
        }

        determineTheme()
    }

    private fun determineTheme() {
        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return
        FirebaseFirestore.getInstance().collection("users").document(uid).get().addOnSuccessListener { doc ->
            val user = doc.toObject(User::class.java)
            val themeColor = when (user?.role) {
                "WORKER"  -> Color.parseColor("#0F325E")
                "COMPANY" -> Color.parseColor("#1C6EC6")
                else      -> Color.parseColor("#004A99")
            }
            findViewById<View>(R.id.statusBarSpacer).setBackgroundColor(themeColor)
            findViewById<Toolbar>(R.id.toolbar).setBackgroundColor(themeColor)
        }
    }

    companion object {
        private const val EXTRA_TITLE = "extra_title"
        private const val EXTRA_LAST_UPDATED = "extra_last_updated"
        private const val EXTRA_BODY = "extra_body"

        const val TITLE_TERMS = "Terms of Service"
        const val TITLE_PRIVACY = "Privacy Policy"

        fun start(context: Context, title: String, lastUpdated: String, body: String) {
            val intent = Intent(context, LegalDocumentActivity::class.java).apply {
                putExtra(EXTRA_TITLE, title)
                putExtra(EXTRA_LAST_UPDATED, lastUpdated)
                putExtra(EXTRA_BODY, body)
            }
            context.startActivity(intent)
        }
    }
}
