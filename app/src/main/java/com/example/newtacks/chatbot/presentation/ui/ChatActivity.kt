package com.example.newtacks.chatbot.presentation.ui

import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.newtacks.chatbot.data.remote.RetrofitClient
import com.example.newtacks.chatbot.data.repository.ChatRepository
import com.example.newtacks.chatbot.presentation.state.ChatMessage
import com.example.newtacks.chatbot.presentation.viewmodel.ChatViewModel
import com.example.newtacks.databinding.ActivityChatBinding

class ChatActivity : AppCompatActivity() {

    private lateinit var binding: ActivityChatBinding
    private lateinit var viewModel: ChatViewModel
    private val adapter = ChatAdapter()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityChatBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupViewModel()
        setupUI()
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

    private fun sendMessage(message: String) {
        adapter.addMessage(ChatMessage(message, true))
        binding.messageInput.text.clear()
        viewModel.sendMessage(message)
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
