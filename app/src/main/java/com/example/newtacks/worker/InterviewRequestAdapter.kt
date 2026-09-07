package com.example.newtacks.worker

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.newtacks.R
import com.example.newtacks.models.Application
import com.google.android.material.button.MaterialButton
import java.text.SimpleDateFormat
import java.util.*

class InterviewRequestAdapter(
    private val requests: List<Application>,
    private val onAction: (Application, String) -> Unit
) : RecyclerView.Adapter<InterviewRequestAdapter.ViewHolder>() {

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val tvTitle: TextView = view.findViewById(R.id.tvJobTitle)
        val tvCompany: TextView = view.findViewById(R.id.tvCompanyName)
        val tvDate: TextView = view.findViewById(R.id.tvInterviewDate)
        val tvLocation: TextView = view.findViewById(R.id.tvInterviewLocation)
        val layoutActions: View = view.findViewById(R.id.layoutActions)
        val tvResponse: TextView = view.findViewById(R.id.tvResponseStatus)
        val btnAccept: MaterialButton = view.findViewById(R.id.btnAccept)
        val btnReschedule: MaterialButton = view.findViewById(R.id.btnReschedule)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_interview_request, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val req = requests[position]
        holder.tvTitle.text = req.jobTitle
        
        // Fetch company name asynchronously or pass it in model? 
        // For now using placeholder or ID. 
        // In real app, you'd fetch the company profile.
        holder.tvCompany.text = "Company ID: ${req.companyId}"

        val sdf = SimpleDateFormat("MMMM dd, yyyy 'at' hh:mm a", Locale.getDefault())
        holder.tvDate.text = if (req.interviewDate != null) sdf.format(Date(req.interviewDate)) else "TBD"
        holder.tvLocation.text = req.interviewLocation ?: "Company Office"

        if (req.workerResponse == null) {
            holder.layoutActions.visibility = View.VISIBLE
            holder.tvResponse.visibility = View.GONE
        } else {
            holder.layoutActions.visibility = View.GONE
            holder.tvResponse.visibility = View.VISIBLE
            holder.tvResponse.text = if (req.workerResponse == "ACCEPTED") "Date Accepted" else "Reschedule Requested"
        }

        holder.btnAccept.setOnClickListener { onAction(req, "ACCEPTED") }
        holder.btnReschedule.setOnClickListener { onAction(req, "RESCHEDULE") }
    }

    override fun getItemCount() = requests.size
}
