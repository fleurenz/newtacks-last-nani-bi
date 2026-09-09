package com.example.newtacks.client

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import androidx.fragment.app.Fragment
import com.example.newtacks.utils.NotificationHelper
import com.example.newtacks.ClientDashboardActivity
import com.example.newtacks.CreateJobActivity
import com.example.newtacks.R
import com.example.newtacks.models.Job
import com.example.newtacks.models.User
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration

class ClientHomeFragment : Fragment() {

    private val firestore = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()
    private var activeJobListener: ListenerRegistration? = null

    private lateinit var tvUserName: TextView
    private lateinit var tvActiveJobTitle: TextView
    private lateinit var tvActiveJobStatus: TextView
    private lateinit var cardActiveRequest: View

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val view = inflater.inflate(R.layout.fragment_client_home, container, false)

        tvUserName = view.findViewById(R.id.tvUserName)
        tvActiveJobTitle = view.findViewById(R.id.tvActiveJobTitle)
        tvActiveJobStatus = view.findViewById(R.id.tvActiveJobStatus)
        cardActiveRequest = view.findViewById(R.id.cardActiveRequest)

        val layoutHeader = view.findViewById<View>(R.id.layoutHeader)
        ViewCompat.setOnApplyWindowInsetsListener(layoutHeader) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            val topInset = systemBars.top
            val basePadding = (resources.displayMetrics.density * 36).toInt()
            v.updatePadding(top = basePadding + topInset)
            insets
        }

        setupServiceClickListeners(view)
        
        view.findViewById<View>(R.id.btnNotifications).setOnClickListener {
            NotificationHelper.showNotificationDialog(requireContext())
        }

        loadUserInfo()
        listenForActiveJob()

        return view
    }

    private fun setupServiceClickListeners(view: View) {
        val services = mapOf(
            R.id.servicePlumbing to "Plumbing",
            R.id.serviceCarpentry to "Carpentry",
            R.id.serviceElectrical to "Electrical",
            R.id.serviceMasonry to "Masonry",
            R.id.serviceWelding to "Welding",
            R.id.servicePainting to "Painting",
            R.id.serviceLandscaping to "Landscaping",
            R.id.serviceOthers to "Others"
        )

        services.forEach { (id, name) ->
            view.findViewById<View>(id).setOnClickListener {
                checkActiveJobAndNavigate(name)
            }
        }
    }

    private fun loadUserInfo() {
        val uid = auth.currentUser?.uid ?: return
        firestore.collection("users").document(uid).get().addOnSuccessListener { doc ->
            val user = doc.toObject(User::class.java)
            val firstName = user?.name?.split(" ")?.firstOrNull() ?: "User"
            tvUserName.text = "$firstName!"
        }
    }

    private fun listenForActiveJob() {
        val uid = auth.currentUser?.uid ?: return
        activeJobListener = firestore.collection("jobs")
            .whereEqualTo("clientId", uid)
            .addSnapshotListener { snapshots, e ->
                if (e != null) {
                    android.util.Log.e("ClientHomeFragment", "Listen failed", e)
                    return@addSnapshotListener
                }
                
                val activeStatuses = listOf("AVAILABLE", "IN_PROGRESS", "HEADING_TO_CLIENT", "ARRIVED", "PENDING_VERIFICATION", "REJECTED_BY_CLIENT")
                val job = snapshots?.documents
                    ?.mapNotNull { 
                        try {
                            it.toObject(Job::class.java) 
                        } catch (e: Exception) {
                            android.util.Log.e("ClientHomeFragment", "Error parsing job", e)
                            null
                        }
                    }
                    ?.filter { it.status in activeStatuses }
                    ?.maxByOrNull { it.createdAt }

                if (job != null) {
                    tvActiveJobTitle.text = job.jobTitle
                    tvActiveJobStatus.text = when (job.status) {
                        "AVAILABLE" -> "Searching for worker..."
                        "IN_PROGRESS" -> "Worker accepted your request"
                        "HEADING_TO_CLIENT" -> "Worker is on the way"
                        "ARRIVED" -> "Worker has arrived"
                        "PENDING_VERIFICATION" -> "Waiting for your confirmation"
                        "REJECTED_BY_CLIENT" -> "Waiting for worker to fix issues"
                        else -> job.status
                    }
                    cardActiveRequest.setOnClickListener {
                        (activity as? ClientDashboardActivity)?.switchTab(R.id.nav_requests)
                    }
                } else {
                    tvActiveJobTitle.text = "No active requests yet"
                    tvActiveJobStatus.text = "Tap to request a service above."
                    cardActiveRequest.setOnClickListener(null)
                }
            }
    }

    private fun checkActiveJobAndNavigate(serviceType: String) {
        val uid = auth.currentUser?.uid ?: return
        val currentContext = context ?: return

        firestore.collection("jobs")
            .whereEqualTo("clientId", uid)
            .get(com.google.firebase.firestore.Source.SERVER)
            .addOnSuccessListener { snapshots ->
                if (!isAdded) return@addOnSuccessListener
                
                val activeStatuses = listOf("AVAILABLE", "IN_PROGRESS", "HEADING_TO_CLIENT", "ARRIVED", "PENDING_VERIFICATION", "REJECTED_BY_CLIENT")
                val hasActiveJob = snapshots.documents.any { 
                    val status = it.getString("status") ?: ""
                    status in activeStatuses 
                }

                if (hasActiveJob) {
                    Toast.makeText(currentContext, "Finish your active request first", Toast.LENGTH_LONG).show()
                } else {
                    val intent = Intent(currentContext, CreateJobActivity::class.java)
                    intent.putExtra("SELECTED_SERVICE", serviceType)
                    startActivity(intent)
                }
            }
            .addOnFailureListener {
                if (!isAdded) return@addOnFailureListener
                Toast.makeText(currentContext, "Error checking status", Toast.LENGTH_SHORT).show()
            }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        activeJobListener?.remove()
    }
}