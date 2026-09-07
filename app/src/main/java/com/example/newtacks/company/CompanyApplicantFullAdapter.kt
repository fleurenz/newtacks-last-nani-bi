package com.example.newtacks.company

import android.content.res.ColorStateList
import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import coil.load
import coil.transform.CircleCropTransformation
import com.example.newtacks.R
import com.example.newtacks.models.Application
import com.example.newtacks.models.User

class CompanyApplicantFullAdapter(
    private var dataList: List<Pair<User, Application>>,
    private val onItemClick: (User, Application) -> Unit
) : RecyclerView.Adapter<CompanyApplicantFullAdapter.ViewHolder>() {

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val ivProfile: ImageView = view.findViewById(R.id.ivWorkerProfile)
        val tvName: TextView = view.findViewById(R.id.tvWorkerName)
        val tvBadge: TextView = view.findViewById(R.id.tvStatusBadge)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_company_applicant_full, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val (user, app) = dataList[position]
        
        holder.tvName.text = user.name
        holder.ivProfile.load(user.profileImage) {
            crossfade(true)
            placeholder(R.drawable.ic_user_placeholder)
            transformations(CircleCropTransformation())
        }

        updateBadge(holder.tvBadge, app.status)

        holder.itemView.setOnClickListener { onItemClick(user, app) }
    }

    private fun updateBadge(tvBadge: TextView, status: String) {
        when (status) {
            "APPLIED" -> {
                tvBadge.text = "New"
                tvBadge.backgroundTintList = ColorStateList.valueOf(Color.parseColor("#BFDBFE"))
                tvBadge.setTextColor(Color.parseColor("#1E40AF"))
            }
            "INTERVIEW_SCHEDULED" -> {
                tvBadge.text = "Interview"
                tvBadge.backgroundTintList = ColorStateList.valueOf(Color.parseColor("#FEF3C7"))
                tvBadge.setTextColor(Color.parseColor("#92400E"))
            }
            "HIRED" -> {
                tvBadge.text = "Hired"
                tvBadge.backgroundTintList = ColorStateList.valueOf(Color.parseColor("#DCFCE7"))
                tvBadge.setTextColor(Color.parseColor("#166534"))
            }
            "REJECTED" -> {
                tvBadge.text = "Rejected"
                tvBadge.backgroundTintList = ColorStateList.valueOf(Color.parseColor("#FEE2E2"))
                tvBadge.setTextColor(Color.parseColor("#991B1B"))
            }
            else -> {
                tvBadge.text = status
                tvBadge.backgroundTintList = ColorStateList.valueOf(Color.parseColor("#E2E8F0"))
                tvBadge.setTextColor(Color.parseColor("#475569"))
            }
        }
    }

    override fun getItemCount() = dataList.size

    fun updateData(newData: List<Pair<User, Application>>) {
        this.dataList = newData
        notifyDataSetChanged()
    }
}
