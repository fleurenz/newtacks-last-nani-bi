package com.example.newtacks.company

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import coil.load
import coil.transform.CircleCropTransformation
import com.example.newtacks.R
import com.example.newtacks.models.HiringPost
import com.example.newtacks.utils.ImageUtils
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration

class CompanyHiringFragment : Fragment() {

    private val firestore = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()
    
    private lateinit var tvHiringTitle: TextView
    private lateinit var tvPostDetails: TextView
    private lateinit var tvApplicantCount: TextView
    private lateinit var cardPostDetails: View
    private lateinit var swipeRefresh: SwipeRefreshLayout
    private lateinit var layoutEmptyState: LinearLayout
    private lateinit var layoutApplicants: View
    private lateinit var rvApplicants: RecyclerView
    private lateinit var btnDeletePost: View
    
    private var hiringListener: ListenerRegistration? = null
    private val applicantList = mutableListOf<com.example.newtacks.models.User>()
    private lateinit var adapter: ApplicantAdapter

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val view = inflater.inflate(R.layout.fragment_company_hiring, container, false)
        
        tvHiringTitle   = view.findViewById(R.id.tvHiringTitle)
        tvPostDetails   = view.findViewById(R.id.tvPostDetails)
        tvApplicantCount = view.findViewById(R.id.tvApplicantCount)
        cardPostDetails  = view.findViewById(R.id.cardPostDetails)
        swipeRefresh     = view.findViewById(R.id.swipeRefreshCompanyHiring)
        layoutEmptyState = view.findViewById(R.id.layoutEmptyState)
        layoutApplicants = view.findViewById(R.id.layoutApplicants)
        rvApplicants     = view.findViewById(R.id.rvApplicants)
        btnDeletePost    = view.findViewById(R.id.btnDeletePost)
        
        adapter = ApplicantAdapter(applicantList) { user ->
            showWorkerDetailsDialog(user)
        }
        rvApplicants.layoutManager = LinearLayoutManager(requireContext())
        rvApplicants.adapter = adapter
        
        btnDeletePost.setOnClickListener { confirmCancelPost() }
        
        listenForActiveHiring()

        swipeRefresh.setOnRefreshListener {
            listenForActiveHiring()
        }
        
        return view
    }

    private fun listenForActiveHiring() {
        val uid = auth.currentUser?.uid ?: return
        
        hiringListener = firestore.collection("hiring")
            .whereEqualTo("companyId", uid)
            .whereEqualTo("status", "OPEN")
            .limit(1)
            .addSnapshotListener { snapshots, error ->
                swipeRefresh.isRefreshing = false
                if (error != null) return@addSnapshotListener
                
                val post = snapshots?.documents?.firstOrNull()?.toObject(HiringPost::class.java)
                val now = System.currentTimeMillis()
                
                if (post == null || (post.expiresAt != 0L && now > post.expiresAt)) {
                    showEmptyState()
                    // Auto-close in DB if expired
                    if (post != null && now > post.expiresAt) {
                        firestore.collection("hiring").document(post.hiringId).update("status", "EXPIRED")
                    }
                } else {
                    showActivePost(post)
                }
            }
    }

    private fun showEmptyState() {
        tvHiringTitle.text = "No active post"
        layoutEmptyState.visibility = View.VISIBLE
        cardPostDetails.visibility = View.GONE
        layoutApplicants.visibility = View.GONE
    }

    private fun showActivePost(post: HiringPost) {
        tvHiringTitle.text = post.jobTitle
        layoutEmptyState.visibility = View.GONE
        cardPostDetails.visibility = View.VISIBLE
        layoutApplicants.visibility = View.VISIBLE
        
        tvPostDetails.text = """
            Rate: ₱${post.dailyRate}/day
            Type: ${post.employmentType}
            Services: ${post.serviceCategories.joinToString(", ")}
            Location: ${post.companyAddress}
        """.trimIndent()
        
        fetchApplicants(post.applicants)
    }

    private fun fetchApplicants(uids: List<String>) {
        if (uids.isEmpty()) {
            applicantList.clear()
            adapter.notifyDataSetChanged()
            tvApplicantCount.text = "APPLICANTS (0)"
            return
        }
        
        // Firestore 'in' query supports up to 10 IDs. 
        // For larger lists, we'd need to chunk this.
        val targetUids = uids.take(10)
        
        firestore.collection("users")
            .whereIn("uid", targetUids)
            .get()
            .addOnSuccessListener { snapshots ->
                applicantList.clear()
                for (doc in snapshots) {
                    val user = doc.toObject(com.example.newtacks.models.User::class.java)
                    applicantList.add(user)
                }
                adapter.notifyDataSetChanged()
                tvApplicantCount.text = "APPLICANTS (${applicantList.size})"
            }
    }

    private fun confirmCancelPost() {
        android.app.AlertDialog.Builder(requireContext())
            .setTitle("Cancel Post")
            .setMessage("Are you sure you want to remove this hiring post?")
            .setPositiveButton("Yes") { _, _ -> deletePost() }
            .setNegativeButton("No", null)
            .show()
    }

    private fun deletePost() {
        val uid = auth.currentUser?.uid ?: return
        firestore.collection("hiring")
            .whereEqualTo("companyId", uid)
            .whereEqualTo("status", "OPEN")
            .get()
            .addOnSuccessListener { snapshots ->
                for (doc in snapshots) {
                    doc.reference.delete()
                }
                Toast.makeText(requireContext(), "Post cancelled", Toast.LENGTH_SHORT).show()
            }
    }

    private fun showWorkerDetailsDialog(worker: com.example.newtacks.models.User) {
        val dialogView = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_worker_details_preview, null)
        val ivProfile = dialogView.findViewById<ImageView>(R.id.ivWorkerProfile)
        val tvName = dialogView.findViewById<TextView>(R.id.tvWorkerName)
        val tvBadge = dialogView.findViewById<TextView>(R.id.tvWorkerBadge)
        val tvPhone = dialogView.findViewById<TextView>(R.id.tvWorkerPhone)
        val tvRating = dialogView.findViewById<TextView>(R.id.tvWorkerRating)
        val tvExperience = dialogView.findViewById<TextView>(R.id.tvWorkerExperience)
        val tvCategories = dialogView.findViewById<TextView>(R.id.tvWorkerCategories)
        
        val btnNC1 = dialogView.findViewById<com.google.android.material.button.MaterialButton>(R.id.btnViewNC1)
        val btnNC2 = dialogView.findViewById<com.google.android.material.button.MaterialButton>(R.id.btnViewNC2)
        val btnNC3 = dialogView.findViewById<com.google.android.material.button.MaterialButton>(R.id.btnViewNC3)
        val tvNoNC = dialogView.findViewById<TextView>(R.id.tvNoCertificates)
        
        tvName.text = worker.name
        tvPhone.text = worker.phone
        tvRating.text = "⭐ %.1f (%d reviews)".format(worker.rating, worker.totalRatings)
        tvExperience.text = "${worker.serviceExperience ?: 0} years of experience"
        tvCategories.text = worker.serviceCategories?.joinToString(", ") ?: "None"

        if (worker.verificationStatus > 0) {
            tvBadge.visibility = View.VISIBLE
            tvBadge.text = "NC${worker.verificationStatus}"
        }

        // Handle Certificates
        var hasAnyNC = false
        if (!worker.nc1CertificateUrl.isNullOrEmpty()) {
            btnNC1.visibility = View.VISIBLE
            btnNC1.setOnClickListener { openUrl(worker.nc1CertificateUrl) }
            hasAnyNC = true
        }
        if (!worker.nc2CertificateUrl.isNullOrEmpty()) {
            btnNC2.visibility = View.VISIBLE
            btnNC2.setOnClickListener { openUrl(worker.nc2CertificateUrl) }
            hasAnyNC = true
        }
        if (!worker.nc3CertificateUrl.isNullOrEmpty()) {
            btnNC3.visibility = View.VISIBLE
            btnNC3.setOnClickListener { openUrl(worker.nc3CertificateUrl) }
            hasAnyNC = true
        }
        
        if (!hasAnyNC) tvNoNC.visibility = View.VISIBLE

        ivProfile.load(worker.profileImage) {
            crossfade(true)
            placeholder(R.drawable.ic_user_placeholder)
            transformations(CircleCropTransformation())
        }
        ivProfile.setOnClickListener {
            ImageUtils.showFullscreenImage(requireContext(), worker.profileImage)
        }

        android.app.AlertDialog.Builder(requireContext())
            .setView(dialogView)
            .setPositiveButton("Hire", { _, _ ->
                confirmHiring(worker)
            })
            .setNegativeButton("Close", null)
            .show()
    }

    private fun openUrl(url: String) {
        try {
            val intent = android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(url))
            startActivity(intent)
        } catch (e: Exception) {
            Toast.makeText(requireContext(), "Cannot open link", Toast.LENGTH_SHORT).show()
        }
    }

    private fun confirmHiring(worker: com.example.newtacks.models.User) {
        // Logic to hire the worker (e.g., change status, notify worker, etc.)
        Toast.makeText(requireContext(), "Hiring ${worker.name}...", Toast.LENGTH_SHORT).show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        hiringListener?.remove()
    }
}