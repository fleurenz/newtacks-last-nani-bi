package com.example.newtacks.company

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import coil.load
import coil.transform.CircleCropTransformation
import com.example.newtacks.R
import com.example.newtacks.models.User
import com.google.android.material.button.MaterialButton

class ApplicantAdapter(
    private val applicants: List<User>,
    private val onProfileClick: (User) -> Unit
) : RecyclerView.Adapter<ApplicantAdapter.ViewHolder>() {

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val ivProfile: ImageView = view.findViewById(R.id.ivApplicantProfile)
        val tvName: TextView = view.findViewById(R.id.tvApplicantName)
        val tvRating: TextView = view.findViewById(R.id.tvApplicantRating)
        val btnView: MaterialButton = view.findViewById(R.id.btnViewProfile)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_applicant, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val user = applicants[position]
        holder.tvName.text = user.name
        holder.tvRating.text = "⭐ %.1f".format(user.rating)
        
        holder.ivProfile.load(user.profileImage) {
            crossfade(true)
            placeholder(R.drawable.ic_person_placeholder)
            transformations(CircleCropTransformation())
        }

        holder.btnView.setOnClickListener { onProfileClick(user) }
    }

    override fun getItemCount() = applicants.size
}