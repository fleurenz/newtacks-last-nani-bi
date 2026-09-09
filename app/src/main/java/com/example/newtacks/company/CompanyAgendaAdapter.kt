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
import com.example.newtacks.models.Application
import com.example.newtacks.models.User
import java.text.SimpleDateFormat
import java.util.*

class CompanyAgendaAdapter(
    private var agendaItems: List<Pair<Application, User?>>,
    private val onItemClick: (Application) -> Unit
) : RecyclerView.Adapter<CompanyAgendaAdapter.ViewHolder>() {

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val ivProfile: ImageView = view.findViewById(R.id.ivWorkerProfile)
        val tvWorkerName: TextView = view.findViewById(R.id.tvWorkerName)
        val tvInterviewTime: TextView = view.findViewById(R.id.tvInterviewTime)
        val tvJobTitle: TextView = view.findViewById(R.id.tvJobTitle)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_company_agenda, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val (app, user) = agendaItems[position]

        holder.tvWorkerName.text = user?.name ?: "Worker"
        holder.tvJobTitle.text = app.jobTitle

        val timeSdf = SimpleDateFormat("h:mm a", Locale.getDefault())
        holder.tvInterviewTime.text = app.interviewDate?.let { timeSdf.format(Date(it)) } ?: "TBD"

        holder.ivProfile.load(user?.profileImage) {
            crossfade(true)
            placeholder(R.drawable.ic_user_placeholder)
            transformations(CircleCropTransformation())
        }

        holder.itemView.setOnClickListener { onItemClick(app) }
    }

    override fun getItemCount() = agendaItems.size

    fun updateData(newData: List<Pair<Application, User?>>) {
        this.agendaItems = newData
        notifyDataSetChanged()
    }
}
