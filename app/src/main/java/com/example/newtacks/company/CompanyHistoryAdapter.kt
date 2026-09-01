package com.example.newtacks.company

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.newtacks.R
import com.example.newtacks.models.HiringPost
import java.text.SimpleDateFormat
import java.util.*

class CompanyHistoryAdapter(private val historyList: List<HiringPost>) :
    RecyclerView.Adapter<CompanyHistoryAdapter.ViewHolder>() {

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val tvTitle: TextView = view.findViewById(R.id.jobTitle)
        val tvAmount: TextView = view.findViewById(R.id.jobAmount)
        val tvDate: TextView = view.findViewById(R.id.jobLocation) // Reusing field for date
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_worker_job, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = historyList[position]
        holder.tvTitle.text = item.jobTitle
        holder.tvAmount.text = "₱${item.dailyRate}/day"
        
        val sdf = SimpleDateFormat("MMM dd, yyyy", Locale.getDefault())
        holder.tvDate.text = "Completed: ${sdf.format(Date(item.createdAt))}"
        
        // Disable the button for history
        holder.itemView.findViewById<View>(R.id.btnAccept).visibility = View.GONE
    }

    override fun getItemCount() = historyList.size
}