package com.example.newtacks.worker

import android.view.*
import android.widget.Button
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.newtacks.R
import com.example.newtacks.models.FeedOpportunity

class WorkerJobAdapter(
    private val opportunities: List<FeedOpportunity>,
    private val onClick: (FeedOpportunity) -> Unit
) : RecyclerView.Adapter<WorkerJobAdapter.JobViewHolder>() {

    class JobViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val title: TextView = view.findViewById(R.id.jobTitle)
        val amount: TextView = view.findViewById(R.id.jobAmount)
        val location: TextView = view.findViewById(R.id.jobLocation)
        val acceptBtn: Button = view.findViewById(R.id.btnAccept)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): JobViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_worker_job, parent, false)
        return JobViewHolder(view)
    }

    override fun onBindViewHolder(holder: JobViewHolder, position: Int) {
        val opportunity = opportunities[position]

        holder.title.text = opportunity.title
        holder.amount.text = opportunity.amount
        holder.location.text = opportunity.location

        holder.acceptBtn.text = "View on Map"
        
        holder.itemView.setOnClickListener {
            onClick(opportunity)
        }
        
        holder.acceptBtn.setOnClickListener {
            onClick(opportunity)
        }
    }

    override fun getItemCount(): Int = opportunities.size
}
