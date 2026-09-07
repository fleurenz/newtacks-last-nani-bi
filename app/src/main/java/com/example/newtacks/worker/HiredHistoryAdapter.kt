package com.example.newtacks.worker

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.newtacks.R
import com.example.newtacks.models.Application
import java.text.SimpleDateFormat
import java.util.*

class HiredHistoryAdapter(
    private val history: List<Application>
) : RecyclerView.Adapter<HiredHistoryAdapter.ViewHolder>() {

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val tvTitle: TextView = view.findViewById(R.id.tvJobTitle)
        val tvCompany: TextView = view.findViewById(R.id.tvCompanyName)
        val tvDate: TextView = view.findViewById(R.id.tvHiredDate)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_hired_history, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = history[position]
        holder.tvTitle.text = item.jobTitle
        holder.tvCompany.text = "Company ID: ${item.companyId}"

        val sdf = SimpleDateFormat("MMMM dd, yyyy", Locale.getDefault())
        holder.tvDate.text = "Hired on ${sdf.format(Date(item.createdAt))}"
    }

    override fun getItemCount() = history.size
}
