package com.example.newtacks.company

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.newtacks.R
import com.example.newtacks.models.HiringPost
import java.text.SimpleDateFormat
import java.util.*

class CompanyPostAdapter(
    private var posts: List<HiringPost>,
    private val onEditClick: (HiringPost) -> Unit,
    private val onItemClick: (HiringPost) -> Unit
) : RecyclerView.Adapter<CompanyPostAdapter.ViewHolder>() {

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val tvTitle: TextView = view.findViewById(R.id.tvPostTitle)
        val tvApplicants: TextView = view.findViewById(R.id.tvApplicantCount)
        val tvDateAndRate: TextView = view.findViewById(R.id.tvPostDateAndRate)
        val tvLocation: TextView = view.findViewById(R.id.tvPostLocation)
        val btnEdit: ImageView = view.findViewById(R.id.btnEditPost)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_company_post, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val post = posts[position]
        
        holder.tvTitle.text = post.jobTitle
        holder.tvApplicants.text = "${post.acceptedWorkers.size}/${post.vacancies} Positions Filled • ${post.applicants.size} Applicants"
        
        val sdf = SimpleDateFormat("MMMM dd", Locale.getDefault())
        val dateStr = sdf.format(Date(post.createdAt))
        holder.tvDateAndRate.text = "Posted $dateStr  •  ₱${post.dailyRate.toInt()}/day"
        
        holder.tvLocation.text = post.companyAddress.split(",").firstOrNull()?.trim() ?: post.companyAddress

        holder.btnEdit.setOnClickListener { onEditClick(post) }
        holder.itemView.setOnClickListener { onItemClick(post) }
    }

    override fun getItemCount() = posts.size

    fun updateData(newPosts: List<HiringPost>) {
        this.posts = newPosts
        notifyDataSetChanged()
    }
}
