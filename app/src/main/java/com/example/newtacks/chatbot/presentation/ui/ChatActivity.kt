package com.example.newtacks.chatbot.presentation.ui

import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import androidx.core.view.updateLayoutParams
import androidx.core.view.updatePadding
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.newtacks.R
import com.example.newtacks.chatbot.presentation.state.ChatMessage
import com.example.newtacks.chatbot.presentation.viewmodel.ChatViewModel
import com.example.newtacks.databinding.ActivityChatBinding
import com.google.android.material.button.MaterialButton

class ChatActivity : AppCompatActivity() {

    private lateinit var binding: ActivityChatBinding
    private lateinit var viewModel: ChatViewModel
    private val adapter = ChatAdapter()
    private var userRole: String = "unknown"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        window.statusBarColor = android.graphics.Color.parseColor("#002E6B")
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowCompat.getInsetsController(window, window.decorView).isAppearanceLightStatusBars = false

        binding = ActivityChatBinding.inflate(layoutInflater)
        setContentView(binding.root)

        userRole = intent.getStringExtra("USER_ROLE") ?: "unknown"

        ViewCompat.setOnApplyWindowInsetsListener(binding.chatRoot) { _, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            val ime = insets.getInsets(WindowInsetsCompat.Type.ime())

            binding.appBarLayout.updatePadding(top = systemBars.top)
            binding.inputCard.updateLayoutParams<ViewGroup.MarginLayoutParams> {
                val bottomInset = maxOf(systemBars.bottom, ime.bottom)
                bottomMargin = bottomInset + (resources.displayMetrics.density * 12).toInt()
            }
            insets
        }

        viewModel = ViewModelProvider(this)[ChatViewModel::class.java]

        setupUI()
        setupPrePrompts()
        observeViewModel()
    }

    private fun setupUI() {
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = ""
        binding.toolbar.setNavigationOnClickListener { finish() }

        binding.chatRecyclerView.adapter = adapter
        binding.chatRecyclerView.layoutManager = LinearLayoutManager(this).apply {
            stackFromEnd = true
        }

        binding.sendButton.setOnClickListener {
            val message = binding.messageInput.text.toString().trim()
            if (message.isNotEmpty()) {
                sendMessage(message)
            }
        }
    }

    private fun setupPrePrompts() {
        val prompts = when (userRole.lowercase()) {
            "worker" -> listOf("How can I get jobs", "What to do when I accepted a job")
            "client" -> listOf("How can I request for a fix", "What are the payment methods")
            "company" -> listOf("How can I verify workers")
            else -> emptyList()
        }

        if (prompts.isEmpty()) {
            binding.prePromptScroll.visibility = View.GONE
            return
        }

        prompts.forEach { prompt ->
            val button = MaterialButton(this, null, com.google.android.material.R.attr.materialButtonOutlinedStyle).apply {
                text = prompt
                textSize = 12f
                isAllCaps = false
                layoutParams = LinearLayout.LayoutParams(dpToPx(160), LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                    marginEnd = dpToPx(8)
                }
                setOnClickListener { sendMessage(prompt) }
            }
            binding.prePromptContainer.addView(button)
        }
    }

    private fun dpToPx(dp: Int): Int = (dp * resources.displayMetrics.density).toInt()

    private fun sendMessage(message: String) {
        viewModel.sendMessage(message, userRole)
        binding.messageInput.text.clear()
        binding.chatRecyclerView.smoothScrollToPosition(adapter.itemCount - 1)
    }

    private fun observeViewModel() {
        viewModel.messages.observe(this) { messages ->
            adapter.setMessages(messages)
            binding.chatRecyclerView.scrollToPosition(adapter.itemCount - 1)
        }

        viewModel.isLoading.observe(this) { isLoading ->
            binding.progressBar.visibility = if (isLoading) View.VISIBLE else View.GONE
            binding.sendButton.isEnabled = !isLoading
        }
    }
}