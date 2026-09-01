package com.example.newtacks.worker

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.newtacks.R
import com.example.newtacks.models.HiringPost

class HiringAdapter(
    private val posts: List<HiringPost>,
    private val currentUserId: String?,
    private val onItemClick: (HiringPost) -> Unit
) : RecyclerView.Adapter<HiringAdapter.ViewHolder>() {

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val tvTitle: TextView = view.findViewById(R.id.jobTitle)
        val tvAmount: TextView = view.findViewById(R.id.jobAmount)
        val tvCompany: TextView = view.findViewById(R.id.tvCompanyName)
        val tvServices: TextView = view.findViewById(R.id.tvServices)
        val btnAccept: com.google.android.material.button.MaterialButton = view.findViewById(R.id.btnAccept)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_hiring_post, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val post = posts[position]
        holder.tvTitle.text = post.jobTitle
        holder.tvAmount.text = "₱${post.dailyRate}/day"
        holder.tvCompany.text = post.companyName.uppercase()
        holder.tvServices.text = post.serviceCategories.joinToString(", ")
        
        val hasApplied = currentUserId != null && post.applicants.contains(currentUserId)
        
        if (hasApplied) {
            holder.btnAccept.text = "Applied"
            holder.btnAccept.isEnabled = false
            holder.btnAccept.alpha = 0.6f
        } else {
            holder.btnAccept.text = "View Details"
            holder.btnAccept.isEnabled = true
            holder.btnAccept.alpha = 1.0f
        }
        
        holder.itemView.setOnClickListener { onItemClick(post) }
        holder.btnAccept.setOnClickListener { onItemClick(post) }
    }

    override fun getItemCount() = posts.size
}
