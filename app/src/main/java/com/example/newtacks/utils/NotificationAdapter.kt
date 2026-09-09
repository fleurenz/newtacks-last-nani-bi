package com.example.newtacks.utils

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.newtacks.R
import com.example.newtacks.models.InAppNotification
import java.text.SimpleDateFormat
import java.util.Locale

class NotificationAdapter(
    private val notifications: List<InAppNotification>,
    private val onClick: (InAppNotification) -> Unit
) : RecyclerView.Adapter<NotificationAdapter.ViewHolder>() {

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val title: TextView = view.findViewById(R.id.tvNotifTitle)
        val message: TextView = view.findViewById(R.id.tvNotifMessage)
        val time: TextView = view.findViewById(R.id.tvNotifTime)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_notification, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val notif = notifications[position]
        holder.title.text = notif.title
        holder.message.text = notif.message
        
        val sdf = SimpleDateFormat("MMM dd, h:mm a", Locale.getDefault())
        holder.time.text = notif.timestamp?.let { sdf.format(it) } ?: "Just now"

        holder.itemView.setOnClickListener { onClick(notif) }
    }

    override fun getItemCount() = notifications.size
}