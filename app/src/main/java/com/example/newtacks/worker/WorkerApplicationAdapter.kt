package com.example.newtacks.worker

import android.content.res.ColorStateList
import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.newtacks.R
import com.example.newtacks.models.Application
import com.google.firebase.firestore.FirebaseFirestore
import java.text.SimpleDateFormat
import java.util.*

class WorkerApplicationAdapter(
    private var dataList: List<Application>,
    private val onItemClick: (Application) -> Unit
) : RecyclerView.Adapter<WorkerApplicationAdapter.ViewHolder>() {

    private val db = FirebaseFirestore.getInstance()

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val tvJobTitle: TextView = view.findViewById(R.id.tvJobTitle)
        val tvCompanyAndDate: TextView = view.findViewById(R.id.tvCompanyAndDate)
        val tvBadge: TextView = view.findViewById(R.id.tvStatusBadge)
        val tvStatusMessage: TextView = view.findViewById(R.id.tvStatusMessage)
        val btnViewDetails: TextView = view.findViewById(R.id.btnViewDetails)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_worker_application, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val app = dataList[position]
        
        // Title handled in adapter but usually we'd pass rate in model or fetch it.
        // For now using the model's jobTitle
        holder.tvJobTitle.text = app.jobTitle
        
        val sdf = SimpleDateFormat("MMMM d", Locale.getDefault())
        val dateStr = sdf.format(Date(app.createdAt))
        
        // Fetch company name asynchronously
        holder.tvCompanyAndDate.text = "Loading... • Applied $dateStr"
        db.collection("users").document(app.companyId).get().addOnSuccessListener { doc ->
            val companyName = doc.getString("companyName") ?: doc.getString("name") ?: "Company"
            holder.tvCompanyAndDate.text = "$companyName • Applied $dateStr"
        }

        updateStatusUI(holder, app)

        holder.itemView.setOnClickListener { onItemClick(app) }
    }

    private fun updateStatusUI(holder: ViewHolder, app: Application) {
        val tvBadge = holder.tvBadge
        val tvMsg = holder.tvStatusMessage
        val btnDetails = holder.btnViewDetails
        
        btnDetails.visibility = View.GONE

        when (app.status) {
            "APPLIED" -> {
                tvBadge.text = "Pending"
                tvBadge.backgroundTintList = ColorStateList.valueOf(Color.parseColor("#FFF1E2"))
                tvBadge.setTextColor(Color.parseColor("#F59E0B"))
                
                tvMsg.text = "Waiting for company response"
                tvMsg.setTextColor(Color.parseColor("#F59E0B"))
            }
            "INTERVIEW_SCHEDULED" -> {
                tvBadge.text = "Interview"
                tvBadge.backgroundTintList = ColorStateList.valueOf(Color.parseColor("#EBF2FF"))
                tvBadge.setTextColor(Color.parseColor("#1C6EC6"))
                
                if (app.workerResponse == null) {
                    tvMsg.text = "Please confirm your schedule"
                } else {
                    val timeSdf = SimpleDateFormat("MMM d, h:mm a", Locale.getDefault())
                    val timeStr = if (app.interviewDate != null) timeSdf.format(Date(app.interviewDate)) else "TBD"
                    tvMsg.text = "Interview scheduled on $timeStr"
                }
                tvMsg.setTextColor(Color.parseColor("#1C6EC6"))
                btnDetails.visibility = View.VISIBLE
            }
            "HIRED" -> {
                tvBadge.text = "Hired"
                tvBadge.backgroundTintList = ColorStateList.valueOf(Color.parseColor("#DCFCE7"))
                tvBadge.setTextColor(Color.parseColor("#166534"))
                
                tvMsg.text = "Congratulations! You're hired."
                tvMsg.setTextColor(Color.parseColor("#166534"))
            }
            "REJECTED" -> {
                tvBadge.text = "Rejected"
                tvBadge.backgroundTintList = ColorStateList.valueOf(Color.parseColor("#FEE2E2"))
                tvBadge.setTextColor(Color.parseColor("#991B1B"))
                
                tvMsg.text = "Did not meet the requirements"
                tvMsg.setTextColor(Color.parseColor("#991B1B"))
            }
            else -> {
                tvBadge.text = app.status
                tvBadge.backgroundTintList = ColorStateList.valueOf(Color.parseColor("#E2E8F0"))
                tvBadge.setTextColor(Color.parseColor("#475569"))
                tvMsg.text = ""
            }
        }
    }

    override fun getItemCount() = dataList.size

    fun updateData(newData: List<Application>) {
        this.dataList = newData
        notifyDataSetChanged()
    }
}
