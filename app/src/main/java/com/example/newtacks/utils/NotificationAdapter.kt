package com.example.newtacks.utils

import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.newtacks.R
import com.example.newtacks.models.InAppNotification
import com.google.android.material.card.MaterialCardView
import java.text.SimpleDateFormat
import java.util.Locale

class NotificationAdapter(
    private var notifications: List<InAppNotification>,
    private val onClick: (InAppNotification) -> Unit
) : RecyclerView.Adapter<NotificationAdapter.ViewHolder>() {

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val card: MaterialCardView = view.findViewById(R.id.cardNotif)
        val indicator: View = view.findViewById(R.id.viewUnreadIndicator)
        val title: TextView = view.findViewById(R.id.tvNotifTitle)
        val message: TextView = view.findViewById(R.id.tvNotifMessage)
        val time: TextView = view.findViewById(R.id.tvNotifTime)
        val newBanner: TextView = view.findViewById(R.id.tvNewBanner)
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

        // Handle Read/Unread Status
        if (notif.read) {
            holder.card.setCardBackgroundColor(Color.WHITE)
            holder.indicator.visibility = View.GONE
            holder.newBanner.visibility = View.GONE
            holder.title.setTextColor(Color.parseColor("#1E293B"))
            holder.message.setTextColor(Color.parseColor("#64748B"))
        } else {
            // Unread: Soft blue background + indicator
            holder.card.setCardBackgroundColor(Color.parseColor("#F1F5F9"))
            holder.indicator.visibility = View.VISIBLE
            holder.newBanner.visibility = View.VISIBLE
            holder.title.setTextColor(Color.parseColor("#004A99")) // Brand color
            holder.message.setTextColor(Color.parseColor("#334155"))
        }

        holder.itemView.setOnClickListener { onClick(notif) }
    }

    override fun getItemCount() = notifications.size

    fun updateData(newList: List<InAppNotification>) {
        this.notifications = newList
        notifyDataSetChanged()
    }
}
