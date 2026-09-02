package com.example.newtacks.worker

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import coil.load
import com.example.newtacks.R
import com.example.newtacks.models.HiringPost

class HiringAdapter(
    private val posts: List<HiringPost>,
    private val currentUserId: String?,
    private val onItemClick: (HiringPost) -> Unit
) : RecyclerView.Adapter<HiringAdapter.ViewHolder>() {

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val ivImage: ImageView = view.findViewById(R.id.ivHiringImage)
        val tvTitle: TextView = view.findViewById(R.id.jobTitle)
        val tvCompany: TextView = view.findViewById(R.id.tvCompanyName)
        val layoutTags: LinearLayout = view.findViewById(R.id.layoutHiringTags)
        
        // Kept for compatibility if they exist in XML as hidden
        val tvAmount: TextView? = view.findViewById(R.id.jobAmount)
        val tvServices: TextView? = view.findViewById(R.id.tvServices)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_hiring_post, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val post = posts[position]
        
        // Header info: Title + Rate
        holder.tvTitle.text = "${post.jobTitle} • ₱${post.dailyRate}/day"
        
        // Company / Location
        holder.tvCompany.text = "${post.companyName} / ${post.companyAddress}"
        
        // Placeholder image for now
        holder.ivImage.load(R.drawable.bg_image_placeholder)
        
        // Dynamic tags
        holder.layoutTags.removeAllViews()
        post.serviceCategories.forEach { category ->
            val tagView = LayoutInflater.from(holder.itemView.context)
                .inflate(R.layout.item_hiring_tag, holder.layoutTags, false) as TextView
            tagView.text = category
            holder.layoutTags.addView(tagView)
        }
        
        holder.itemView.setOnClickListener { onItemClick(post) }
    }

    override fun getItemCount() = posts.size
}
