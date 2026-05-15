package com.example.newtacks.chatbot.presentation.ui

import android.os.Bundle
import android.view.View
import android.widget.LinearLayout
import androidx.appcompat.app.AppCompatActivity
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
        binding = ActivityChatBinding.inflate(layoutInflater)
        setContentView(binding.root)

        userRole = intent.getStringExtra("USER_ROLE") ?: "unknown"

        setupViewModel()
        setupUI()
        setupPrePrompts()
        observeViewModel()
    }

    private fun setupViewModel() {
        val repository = ChatRepository(RetrofitClient.chatApiService)
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
            val button = MaterialButton(this, null, com.google.android.material.R.attr.materialButtonOutlinedStyle).apply {
                text = prompt
                textSize = 12f
                isAllCaps = false
                val params = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply {
                    setMargins(8, 0, 8, 0)
                }
                layoutParams = params
                setOnClickListener {
                    sendMessage(prompt)
                }
            }
            binding.prePromptContainer.addView(button)
        }
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
