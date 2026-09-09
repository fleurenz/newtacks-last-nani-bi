package com.example.newtacks.chatbot.presentation.ui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import coil.load
import coil.transform.CircleCropTransformation
import com.example.newtacks.R
import com.example.newtacks.models.ChatMessage
import com.google.firebase.auth.FirebaseAuth
import java.text.SimpleDateFormat
import java.util.*

class TransactionChatAdapter : ListAdapter<ChatMessage, TransactionChatAdapter.ViewHolder>(DiffCallback()) {

    private val currentUid = FirebaseAuth.getInstance().currentUser?.uid
    
    private var myProfileUrl: String? = null
    private var otherProfileUrl: String? = null

    fun setProfileImages(mine: String?, other: String?) {
        myProfileUrl = mine
        otherProfileUrl = other
        notifyDataSetChanged()
    }

    override fun getItemViewType(position: Int): Int {
        return if (getItem(position).senderId == currentUid) VIEW_TYPE_ME else VIEW_TYPE_THEM
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val layout = if (viewType == VIEW_TYPE_ME) R.layout.item_chat_right else R.layout.item_chat_left
        val view = LayoutInflater.from(parent.context).inflate(layout, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val message = getItem(position)
        val isMe = message.senderId == currentUid
        holder.bind(message, if (isMe) myProfileUrl else otherProfileUrl)
    }

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        private val tvMessage: TextView = view.findViewById(R.id.tvMessage)
        private val tvTimestamp: TextView = view.findViewById(R.id.tvTimestamp)
        private val ivProfile: ImageView? = view.findViewById(R.id.ivChatProfile)
        private val sdf = SimpleDateFormat("hh:mm a", Locale.getDefault())

        fun bind(message: ChatMessage, profileUrl: String?) {
            tvMessage.text = message.text
            tvTimestamp.text = sdf.format(Date(message.timestamp))
            
            ivProfile?.let { iv ->
                iv.load(profileUrl) {
                    crossfade(true)
                    placeholder(R.drawable.ic_person_placeholder)
                    error(R.drawable.ic_person_placeholder)
                    transformations(CircleCropTransformation())
                }
            }
        }
    }

    class DiffCallback : DiffUtil.ItemCallback<ChatMessage>() {
        override fun areItemsTheSame(oldItem: ChatMessage, newItem: ChatMessage) = oldItem.messageId == newItem.messageId
        override fun areContentsTheSame(oldItem: ChatMessage, newItem: ChatMessage) = oldItem == newItem
    }

    companion object {
        private const val VIEW_TYPE_ME = 1
        private const val VIEW_TYPE_THEM = 2
    }
}