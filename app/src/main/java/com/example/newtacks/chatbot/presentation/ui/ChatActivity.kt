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
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.newtacks.R
import com.example.newtacks.chatbot.data.remote.RetrofitClient
import com.example.newtacks.chatbot.data.repository.ChatRepository
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

        // ✅ Step 1 — status bar color FIRST (before inflation)
        window.statusBarColor = android.graphics.Color.parseColor("#002E6B")
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowCompat.getInsetsController(window, window.decorView)
            .isAppearanceLightStatusBars = false

        // ✅ Step 2 — inflate BEFORE any findViewById or binding
        binding = ActivityChatBinding.inflate(layoutInflater)
        setContentView(binding.root)

        userRole = intent.getStringExtra("USER_ROLE") ?: "unknown"

        // ✅ Step 3 — insets AFTER setContentView
        ViewCompat.setOnApplyWindowInsetsListener(binding.chatRoot) { _, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            val ime = insets.getInsets(WindowInsetsCompat.Type.ime())

            // Status bar → push AppBar down
            binding.appBarLayout.updatePadding(top = systemBars.top)

            // Nav bar + keyboard → move input card up
            binding.inputCard.updateLayoutParams<ViewGroup.MarginLayoutParams> {
                val bottomInset = maxOf(systemBars.bottom, ime.bottom)
                bottomMargin = bottomInset + (resources.displayMetrics.density * 12).toInt()
            }

            insets
        }

        setupViewModel()
        setupUI()
        setupPrePrompts()
        observeViewModel()
    }

    private fun setupViewModel() {
        val repository = ChatRepository.getInstance(RetrofitClient.chatApiService)
        val factory = object : ViewModelProvider.Factory {
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return ChatViewModel(repository) as T
            }
        }
        viewModel = ViewModelProvider(this, factory)[ChatViewModel::class.java]
    }

    private fun setupUI() {
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = "" // Clear default title to use our custom layout
        binding.toolbar.setNavigationOnClickListener { finish() }

        binding.chatRecyclerView.adapter = adapter
        binding.chatRecyclerView.layoutManager = LinearLayoutManager(this).apply {
            stackFromEnd = true
        }

        // Load existing session messages
        val existingMessages = viewModel.sessionMessages
        if (existingMessages.isNotEmpty()) {
            adapter.setMessages(existingMessages)
            binding.chatRecyclerView.scrollToPosition(adapter.itemCount - 1)
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
            "worker" -> listOf(
                "What is my next task",
                "How can I get jobs",
                "What to do when I accepted a job"
            )
            "client" -> listOf(
                "What is my project status",
                "How can I request for a fix in my home",
                "Are the information within the job creation necessary to be filled up",
                "How can I make sure that I get the best work for my requests"
            )
            "company" -> listOf(
                "How is the company performance"
            )
            else -> emptyList()
        }

        if (prompts.isEmpty()) {
            binding.prePromptScroll.visibility = View.GONE
            return
        }

        prompts.forEach { prompt ->
            val button = MaterialButton(
                this,
                null,
                com.google.android.material.R.attr.materialButtonOutlinedStyle
            ).apply {
                text = prompt
                textSize = 12f
                isAllCaps = false
                isSingleLine = true  // ✅ prevent text wrapping
                ellipsize = android.text.TextUtils.TruncateAt.END
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    dpToPx(40)  // ✅ fixed height
                ).apply {
                    marginEnd = dpToPx(8)  // ✅ space between buttons
                }
                setOnClickListener {
                    sendMessage(prompt)
                }
            }
            binding.prePromptContainer.addView(button)
        }
    }
    private fun dpToPx(dp: Int): Int {
        return (dp * resources.displayMetrics.density).toInt()
    }


    private fun sendMessage(message: String) {
        adapter.addMessage(ChatMessage(message, true))
        binding.messageInput.text.clear()
        viewModel.sendMessage(message, userRole)
        binding.chatRecyclerView.smoothScrollToPosition(adapter.itemCount - 1)
    }

    private fun observeViewModel() {
        viewModel.chatResponse.observe(this) { result ->
            result.onSuccess { response ->
                adapter.addMessage(ChatMessage(response.reply, false))
                binding.chatRecyclerView.smoothScrollToPosition(adapter.itemCount - 1)
            }.onFailure { error ->
                adapter.addMessage(ChatMessage("Error: ${error.message}", false))
                binding.chatRecyclerView.smoothScrollToPosition(adapter.itemCount - 1)
            }
        }

        viewModel.isLoading.observe(this) { isLoading ->
            binding.progressBar.visibility = if (isLoading) View.VISIBLE else View.GONE
            binding.sendButton.isEnabled = !isLoading
        }
    }
}
