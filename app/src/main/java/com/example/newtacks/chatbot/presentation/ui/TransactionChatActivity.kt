package com.example.newtacks.chatbot.presentation.ui

import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updateLayoutParams
import androidx.core.view.updatePadding
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import coil.load
import coil.transform.CircleCropTransformation
import com.example.newtacks.R
import com.example.newtacks.models.ChatMessage
import com.example.newtacks.models.User
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

class TransactionChatActivity : AppCompatActivity() {

    private lateinit var rvChat: RecyclerView
    private lateinit var etMessage: EditText
    private lateinit var btnSend: View
    private lateinit var adapter: TransactionChatAdapter

    private val db = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()

    private var jobId: String = ""
    private var workerId: String = "" // The current worker involved in the job
    private var otherUserId: String = ""
    private var jobTitle: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // ✅ Step 1 — Edge-to-edge
        window.statusBarColor = android.graphics.Color.parseColor("#002E6B")
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowCompat.getInsetsController(window, window.decorView)
            .isAppearanceLightStatusBars = false

        setContentView(R.layout.activity_transaction_chat)

        jobId = intent.getStringExtra("JOB_ID") ?: ""
        workerId = intent.getStringExtra("WORKER_ID") ?: ""
        otherUserId = intent.getStringExtra("OTHER_USER_ID") ?: ""
        jobTitle = intent.getStringExtra("JOB_TITLE") ?: "Chat"

        if (jobId.isEmpty()) {
            finish()
            return
        }

        // ✅ Step 2 — Insets listener
        val chatRoot = findViewById<View>(R.id.chatRoot)
        val appBarLayout = findViewById<View>(R.id.appBarLayout)
        val inputCard = findViewById<View>(R.id.inputCard)

        ViewCompat.setOnApplyWindowInsetsListener(chatRoot) { _, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            val ime = insets.getInsets(WindowInsetsCompat.Type.ime())

            // Status bar → push AppBar down
            appBarLayout.updatePadding(top = systemBars.top)

            // Nav bar + keyboard → move input card up
            inputCard.updateLayoutParams<ViewGroup.MarginLayoutParams> {
                val bottomInset = maxOf(systemBars.bottom, ime.bottom)
                bottomMargin = bottomInset + (resources.displayMetrics.density * 12).toInt()
            }

            insets
        }

        val toolbar = findViewById<androidx.appcompat.widget.Toolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.title = "" // Using custom layout
        toolbar.setNavigationOnClickListener { finish() }

        rvChat = findViewById(R.id.rvChat)
        etMessage = findViewById(R.id.etMessage)
        btnSend = findViewById(R.id.btnSend)

        adapter = TransactionChatAdapter()
        rvChat.layoutManager = LinearLayoutManager(this).apply {
            stackFromEnd = true
        }
        rvChat.adapter = adapter

        btnSend.setOnClickListener { sendMessage() }

        setupHeader()
        listenForMessages()
        checkJobStatus()
    }

    private fun setupHeader() {
        val tvName = findViewById<TextView>(R.id.tvOtherUserName)
        val tvJob = findViewById<TextView>(R.id.tvJobTitleHeader)
        val ivOther = findViewById<ImageView>(R.id.ivOtherUser)

        tvJob.text = jobTitle

        if (otherUserId.isNotEmpty()) {
            db.collection("users").document(otherUserId).get()
                .addOnSuccessListener { doc ->
                    val user = doc.toObject(User::class.java)
                    if (user != null) {
                        tvName.text = user.name
                        ivOther.load(user.profileImage) {
                            crossfade(true)
                            placeholder(R.drawable.ic_user_placeholder)
                            transformations(CircleCropTransformation())
                        }
                    }
                }
        }
    }

    private fun listenForMessages() {
        db.collection("chats")
            .whereEqualTo("jobId", jobId)
            .whereEqualTo("workerId", workerId) // Isolates the session to this specific worker
            .addSnapshotListener { snapshots, e ->
                if (e != null) {
                    android.util.Log.e("Chat", "Error: ${e.message}")
                    return@addSnapshotListener
                }
                
                val messages = snapshots?.toObjects(ChatMessage::class.java) ?: emptyList()
                val sortedMessages = messages.sortedBy { it.timestamp }
                
                // Mark received messages as read
                val currentUid = auth.currentUser?.uid
                snapshots?.documents?.forEach { doc ->
                    val msg = doc.toObject(ChatMessage::class.java)
                    if (msg != null && msg.receiverId == currentUid && !msg.read) {
                        doc.reference.update("read", true)
                    }
                }

                adapter.submitList(sortedMessages)
                if (sortedMessages.isNotEmpty()) {
                    rvChat.smoothScrollToPosition(sortedMessages.size - 1)
                }
            }
    }

    private fun checkJobStatus() {
        // Close chat input if job is completed or cancelled
        db.collection("jobs").document(jobId)
            .addSnapshotListener { snapshot, _ ->
                val status = snapshot?.getString("status") ?: ""
                val isClosed = status == "COMPLETED" || status == "CANCELLED"
                
                etMessage.isEnabled = !isClosed
                btnSend.isEnabled = !isClosed
                if (isClosed) {
                    etMessage.hint = "Chat is closed for this job"
                }
            }
    }

    private fun sendMessage() {
        val text = etMessage.text.toString().trim()
        if (text.isEmpty()) return

        val currentUid = auth.currentUser?.uid ?: return
        val messageId = db.collection("chats").document().id
        val message = ChatMessage(
            messageId = messageId,
            jobId = jobId,
            workerId = workerId, // Save the worker ID with every message
            senderId = currentUid,
            receiverId = otherUserId,
            text = text,
            timestamp = System.currentTimeMillis()
        )

        etMessage.setText("")
        db.collection("chats").document(messageId).set(message)
            .addOnSuccessListener {
                // Send notification to the other user
                com.example.newtacks.utils.NotificationHelper.sendNotification(
                    otherUserId,
                    "New Message: $jobTitle",
                    text
                )
            }
            .addOnFailureListener {
                Toast.makeText(this, "Failed to send message", Toast.LENGTH_SHORT).show()
            }
    }
}