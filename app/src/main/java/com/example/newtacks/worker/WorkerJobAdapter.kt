package com.example.newtacks.worker

import android.view.*
import android.widget.Button
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.newtacks.R
import com.example.newtacks.models.Job

class WorkerJobAdapter(
    private val jobs: List<Job>,
    private val onAccept: (Job) -> Unit
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

        val job = jobs[position]

        holder.title.text = job.jobTitle
        holder.amount.text = "₱${job.offeredAmount}"
        holder.location.text = job.clientAddress

        holder.acceptBtn.setOnClickListener {
            onAccept(job)
        }
    }

    override fun getItemCount(): Int = jobs.size
}