package com.example.newtacks.worker

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import coil.load
import coil.transform.CircleCropTransformation
import com.example.newtacks.R
import com.example.newtacks.models.Review
import com.google.firebase.firestore.FirebaseFirestore

class WorkerReviewAdapter(private val reviews: List<Review>) :
    RecyclerView.Adapter<WorkerReviewAdapter.ViewHolder>() {

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val ivProfile: ImageView = view.findViewById(R.id.ivClientProfile)
        val tvName: TextView = view.findViewById(R.id.tvClientName)
        val tvComment: TextView = view.findViewById(R.id.tvReviewComment)
        val layoutStars: LinearLayout = view.findViewById(R.id.layoutStars)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_worker_review, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val review = reviews[position]
        
        if (review.isAnonymous) {
            holder.tvName.text = "Anonymous"
            holder.ivProfile.setImageResource(R.drawable.ic_user_placeholder)
        } else {
            holder.tvName.text = review.clientName
            // Fetch client profile image from firestore
            FirebaseFirestore.getInstance().collection("users").document(review.clientId)
                .get().addOnSuccessListener { doc ->
                    val profileImg = doc.getString("profileImage") ?: ""
                    holder.ivProfile.load(profileImg) {
                        crossfade(true)
                        placeholder(R.drawable.ic_user_placeholder)
                        transformations(CircleCropTransformation())
                    }
                }
        }

        holder.tvComment.text = review.comment
        
        // Dynamic stars
        holder.layoutStars.removeAllViews()
        val rating = review.rating.toInt()
        for (i in 1..5) {
            val star = ImageView(holder.itemView.context)
            val size = (14 * holder.itemView.context.resources.displayMetrics.density).toInt()
            star.layoutParams = LinearLayout.LayoutParams(size, size).apply {
                marginEnd = (2 * holder.itemView.context.resources.displayMetrics.density).toInt()
            }
            star.setImageResource(R.drawable.ic_star)
            if (i <= rating) {
                star.setColorFilter(android.graphics.Color.parseColor("#EAB308"))
            } else {
                star.setColorFilter(android.graphics.Color.parseColor("#CBD5E1"))
            }
            holder.layoutStars.addView(star)
        }
    }

    override fun getItemCount() = reviews.size
}