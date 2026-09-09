package com.example.newtacks.company

import android.app.DatePickerDialog
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import coil.load
import coil.transform.CircleCropTransformation
import com.example.newtacks.R
import com.example.newtacks.models.Application
import com.example.newtacks.models.HiringPost
import com.example.newtacks.models.User
import com.example.newtacks.utils.ImageUtils
import com.example.newtacks.utils.NotificationHelper
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.gson.Gson
import java.util.*

class CompanyApplicantsFragment : Fragment() {

    private val db = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()

    private lateinit var rvApplicants: RecyclerView
    private lateinit var swipeRefresh: SwipeRefreshLayout
    private lateinit var tabAll: TextView
    private lateinit var tabNew: TextView
    private lateinit var tabInterview: TextView
    private lateinit var tabHired: TextView
    
    private lateinit var layoutActiveFilter: View
    private lateinit var tvActiveFilterName: TextView
    private lateinit var btnClearFilter: View
    private lateinit var loadingOverlay: View

    private var appsListener: ListenerRegistration? = null
    private val allData = mutableListOf<Pair<User, Application>>()
    private val allHiringPosts = mutableListOf<HiringPost>()
    private lateinit var adapter: CompanyApplicantFullAdapter
    
    private var currentTab = "ALL"
    private var selectedJobFilterId: String? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val view = inflater.inflate(R.layout.fragment_company_applicants, container, false)
        
        rvApplicants = view.findViewById(R.id.rvApplicants)
        swipeRefresh = view.findViewById(R.id.swipeRefreshApplicants)
        tabAll = view.findViewById(R.id.tabAll)
        tabNew = view.findViewById(R.id.tabNew)
        tabInterview = view.findViewById(R.id.tabInterview)
        tabHired = view.findViewById(R.id.tabHired)
        
        layoutActiveFilter = view.findViewById(R.id.layoutActiveFilter)
        tvActiveFilterName = view.findViewById(R.id.tvActiveFilterName)
        btnClearFilter     = view.findViewById(R.id.btnClearFilter)
        loadingOverlay     = view.findViewById(R.id.loadingOverlay)

        // Robust Inset Handling: Use spacer for status bar
        val statusBarSpacer = view.findViewById<View>(R.id.statusBarSpacer)
        ViewCompat.setOnApplyWindowInsetsListener(view) { _, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            val params = statusBarSpacer.layoutParams
            params.height = systemBars.top
            statusBarSpacer.layoutParams = params
            insets
        }

        view.findViewById<View>(R.id.btnNotifications).setOnClickListener {
            NotificationHelper.showNotificationDialog(requireContext())
        }

        setupRecyclerView()
        setupTabs()

        view.findViewById<View>(R.id.btnFilter).setOnClickListener {
            showFilterDialog()
        }

        btnClearFilter.setOnClickListener {
            clearJobFilter()
        }

        listenForApplications()
        fetchHiringPosts()

        swipeRefresh.setOnRefreshListener {
            listenForApplications()
            fetchHiringPosts()
        }

        return view
    }

    private fun setupRecyclerView() {
        adapter = CompanyApplicantFullAdapter(emptyList(), { user, app ->
            // On Item Click: Open Worker Details Dialog directly instead of Job Details screen
            showWorkerDetailsDialog(user, app)
        }, { user ->
            // On Profile Label Click: Open Full Profile directly
            val intent = Intent(requireContext(), com.example.newtacks.worker.WorkerProfileActivity::class.java)
            intent.putExtra("WORKER_ID", user.uid)
            startActivity(intent)
        })
        rvApplicants.layoutManager = LinearLayoutManager(requireContext())
        rvApplicants.adapter = adapter
    }

    private fun showWorkerDetailsDialog(worker: User, app: Application) {
        val dialogView = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_worker_details_preview, null)
        val ivProfile = dialogView.findViewById<ImageView>(R.id.ivWorkerProfile)
        val tvName = dialogView.findViewById<TextView>(R.id.tvWorkerName)
        val tvRating = dialogView.findViewById<TextView>(R.id.tvWorkerRating)
        val tvBadge = dialogView.findViewById<TextView>(R.id.tvWorkerBadge)
        val tvPhone = dialogView.findViewById<TextView>(R.id.tvWorkerPhone)
        val tvExp = dialogView.findViewById<TextView>(R.id.tvWorkerExperience)
        val tvCat = dialogView.findViewById<TextView>(R.id.tvWorkerCategories)
        
        val tvDetailedStatus = dialogView.findViewById<TextView>(R.id.tvDetailedStatus)
        val btnPrimary = dialogView.findViewById<com.google.android.material.button.MaterialButton>(R.id.btnProcessPrimary)
        val btnSecondary = dialogView.findViewById<com.google.android.material.button.MaterialButton>(R.id.btnProcessSecondary)
        
        tvName.text = worker.name
        tvRating.text = "⭐ %.1f (%d reviews)".format(worker.rating, worker.totalRatings)
        tvPhone.text = worker.phone
        tvExp.text = "${worker.serviceExperience ?: 0} years of experience"
        tvCat.text = worker.serviceCategories?.joinToString(", ") ?: "General Services"

        if (worker.verificationStatus > 0) {
            tvBadge.visibility = View.VISIBLE
            tvBadge.text = "NC${worker.verificationStatus}"
            tvBadge.setBackgroundResource(R.drawable.bg_badge_green1)
        } else {
            tvBadge.visibility = View.GONE
        }

        ivProfile.load(worker.profileImage) {
            crossfade(true)
            placeholder(R.drawable.ic_person_placeholder)
            transformations(CircleCropTransformation())
        }

        // Setup Detailed Status Text
        val statusText = when (app.status) {
            "APPLIED" -> "Awaiting Review"
            "INTERVIEW_SCHEDULED" -> {
                val sdf = java.text.SimpleDateFormat("MMM dd, yyyy 'at' h:mm a", Locale.getDefault())
                val dateStr = if (app.interviewDate != null) sdf.format(Date(app.interviewDate)) else "TBD"
                val response = when(app.workerResponse) {
                    "ACCEPTED" -> " (Confirmed)"
                    "REJECTED" -> " (Rejected)"
                    else -> " (Pending Confirmation)"
                }
                "Interview: $dateStr$response"
            }
            "HIRED" -> "Officially Hired"
            "REJECTED" -> "Application Rejected"
            else -> app.status
        }
        tvDetailedStatus.text = statusText

        val dialog = AlertDialog.Builder(requireContext())
            .setView(dialogView)
            .create()

        dialogView.findViewById<View>(R.id.btnCloseDialog).setOnClickListener { dialog.dismiss() }

        // Process Buttons Logic
        btnPrimary.visibility = View.GONE
        btnSecondary.visibility = View.GONE

        when (app.status) {
            "APPLIED" -> {
                btnPrimary.visibility = View.VISIBLE
                btnPrimary.text = "Schedule Interview"
                btnPrimary.setOnClickListener { dialog.dismiss(); showScheduleInterviewDialog(worker, app) }
                
                btnSecondary.visibility = View.VISIBLE
                btnSecondary.text = "Reject Applicant"
                btnSecondary.setOnClickListener { 
                    AlertDialog.Builder(requireContext())
                        .setTitle("Reject Applicant")
                        .setMessage("Are you sure you want to reject this applicant?")
                        .setPositiveButton("Reject") { _, _ -> dialog.dismiss(); rejectApplicant(worker, app) }
                        .setNegativeButton("Cancel", null)
                        .show()
                }
            }
            "INTERVIEW_SCHEDULED" -> {
                if (app.workerResponse == "ACCEPTED") {
                    btnPrimary.visibility = View.VISIBLE
                    btnPrimary.text = "Hire Worker"
                    btnPrimary.setOnClickListener { dialog.dismiss(); confirmHiring(worker, app) }
                } else {
                    btnPrimary.visibility = View.VISIBLE
                    btnPrimary.text = "Reschedule Interview"
                    btnPrimary.setOnClickListener { dialog.dismiss(); showScheduleInterviewDialog(worker, app) }
                }
                
                btnSecondary.visibility = View.VISIBLE
                btnSecondary.text = "Reject Applicant"
                btnSecondary.setOnClickListener { 
                    AlertDialog.Builder(requireContext())
                        .setTitle("Reject Applicant")
                        .setMessage("Are you sure you want to reject this applicant?")
                        .setPositiveButton("Reject") { _, _ -> dialog.dismiss(); rejectApplicant(worker, app) }
                        .setNegativeButton("Cancel", null)
                        .show()
                }
            }
        }

        // Certificates buttons
        val btnNC1 = dialogView.findViewById<Button>(R.id.btnViewNC1)
        val btnNC2 = dialogView.findViewById<Button>(R.id.btnViewNC2)
        val btnNC3 = dialogView.findViewById<Button>(R.id.btnViewNC3)
        val tvNoCert = dialogView.findViewById<TextView>(R.id.tvNoCertificates)

        var hasCert = false
        worker.nc1CertificateUrl?.let { url -> if (url.isNotEmpty()) { btnNC1.visibility = View.VISIBLE; btnNC1.setOnClickListener { ImageUtils.showFullscreenImage(requireContext(), url) }; hasCert = true } }
        worker.nc2CertificateUrl?.let { url -> if (url.isNotEmpty()) { btnNC2.visibility = View.VISIBLE; btnNC2.setOnClickListener { ImageUtils.showFullscreenImage(requireContext(), url) }; hasCert = true } }
        worker.nc3CertificateUrl?.let { url -> if (url.isNotEmpty()) { btnNC3.visibility = View.VISIBLE; btnNC3.setOnClickListener { ImageUtils.showFullscreenImage(requireContext(), url) }; hasCert = true } }
        if (!hasCert) tvNoCert.visibility = View.VISIBLE

        dialogView.findViewById<Button>(R.id.btnViewFullProfile).setOnClickListener {
            val intent = Intent(requireContext(), com.example.newtacks.worker.WorkerProfileActivity::class.java)
            intent.putExtra("WORKER_ID", worker.uid)
            startActivity(intent)
        }

        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
        // Match standard width
        dialog.window?.setLayout(
            (resources.displayMetrics.widthPixels * 0.95).toInt(),
            ViewGroup.LayoutParams.WRAP_CONTENT
        )
        dialog.show()
    }

    private fun showScheduleInterviewDialog(worker: User, app: Application) {
        val cal = Calendar.getInstance()
        DatePickerDialog(requireContext(), { _, y, m, d ->
            val interviewCal = Calendar.getInstance()
            interviewCal.set(y, m, d, 10, 0)
            scheduleInterview(worker, app, interviewCal.timeInMillis)
        }, cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH)).show()
    }

    private fun scheduleInterview(worker: User, app: Application, timestamp: Long) {
        loadingOverlay.visibility = View.VISIBLE
        db.collection("applications").document(app.applicationId)
            .update(mapOf("status" to "INTERVIEW_SCHEDULED", "interviewDate" to timestamp, "workerResponse" to null))
            .addOnSuccessListener {
                loadingOverlay.visibility = View.GONE
                NotificationHelper.sendNotification(worker.uid, "Interview Scheduled", "You have an interview request for ${app.jobTitle}.", "HIRING")
                Toast.makeText(requireContext(), "Interview scheduled", Toast.LENGTH_SHORT).show()
            }
            .addOnFailureListener {
                loadingOverlay.visibility = View.GONE
                Toast.makeText(requireContext(), "Action failed", Toast.LENGTH_SHORT).show()
            }
    }

    private fun rejectApplicant(worker: User, app: Application) {
        loadingOverlay.visibility = View.VISIBLE
        db.collection("applications").document(app.applicationId).update("status", "REJECTED")
            .addOnSuccessListener {
                loadingOverlay.visibility = View.GONE
                Toast.makeText(requireContext(), "Applicant rejected", Toast.LENGTH_SHORT).show()
            }
            .addOnFailureListener {
                loadingOverlay.visibility = View.GONE
                Toast.makeText(requireContext(), "Action failed", Toast.LENGTH_SHORT).show()
            }
    }

    private fun confirmHiring(worker: User, app: Application) {
        loadingOverlay.visibility = View.VISIBLE
        db.runTransaction { transaction ->
            val ref = db.collection("hiring").document(app.hiringId)
            val post = transaction.get(ref).toObject(HiringPost::class.java) ?: throw Exception("Post not found")
            
            if (post.acceptedWorkers.size >= post.vacancies) {
                throw Exception("Vacancy full")
            }

            val newList = post.acceptedWorkers.toMutableList()
            newList.add(worker.uid)
            
            transaction.update(ref, mapOf(
                "acceptedWorkers" to newList,
                "status" to if (newList.size >= post.vacancies) "CLOSED" else "OPEN"
            ))
            transaction.update(db.collection("applications").document(app.applicationId), mapOf("status" to "HIRED"))
        }.addOnSuccessListener {
            loadingOverlay.visibility = View.GONE
            NotificationHelper.sendNotification(worker.uid, "Status: Hired!", "You are officially hired for ${app.jobTitle}!", "HIRING")
            Toast.makeText(requireContext(), "Worker hired successfully!", Toast.LENGTH_SHORT).show()
        }.addOnFailureListener {
            loadingOverlay.visibility = View.GONE
            Toast.makeText(requireContext(), "Hiring failed: ${it.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun setupTabs() {
        tabAll.setOnClickListener { switchTab("ALL") }
        tabNew.setOnClickListener { switchTab("NEW") }
        tabInterview.setOnClickListener { switchTab("INTERVIEW") }
        tabHired.setOnClickListener { switchTab("HIRED") }
    }

    fun selectTab(tab: String) {
        switchTab(tab)
    }

    private fun switchTab(tab: String) {
        currentTab = tab
        
        // Reset all
        val tabs = listOf(tabAll, tabNew, tabInterview, tabHired)
        tabs.forEach { 
            it.background = null
            it.setTextColor(Color.parseColor("#64748B"))
            it.paint.isFakeBoldText = false
        }

        when (tab) {
            "ALL" -> {
                tabAll.setBackgroundResource(R.drawable.bg_tab_selected)
                tabAll.setTextColor(Color.parseColor("#1E293B"))
                tabAll.paint.isFakeBoldText = true
            }
            "NEW" -> {
                tabNew.setBackgroundResource(R.drawable.bg_tab_selected)
                tabNew.setTextColor(Color.parseColor("#1E293B"))
                tabNew.paint.isFakeBoldText = true
            }
            "INTERVIEW" -> {
                tabInterview.setBackgroundResource(R.drawable.bg_tab_selected)
                tabInterview.setTextColor(Color.parseColor("#1E293B"))
                tabInterview.paint.isFakeBoldText = true
            }
            "HIRED" -> {
                tabHired.setBackgroundResource(R.drawable.bg_tab_selected)
                tabHired.setTextColor(Color.parseColor("#1E293B"))
                tabHired.paint.isFakeBoldText = true
            }
        }
        
        filterAndDisplay()
    }

    private fun listenForApplications() {
        val uid = auth.currentUser?.uid ?: return
        
        appsListener?.remove()
        appsListener = db.collection("applications")
            .whereEqualTo("companyId", uid)
            .addSnapshotListener { snapshots, error ->
                swipeRefresh.isRefreshing = false
                if (error != null || snapshots == null) return@addSnapshotListener
                
                val applications = snapshots.toObjects(Application::class.java)
                if (applications.isEmpty()) {
                    allData.clear()
                    filterAndDisplay()
                    return@addSnapshotListener
                }

                // Fetch user profiles for all applicants
                val workerIds = applications.map { it.workerId }.distinct()
                
                db.collection("users")
                    .whereIn(com.google.firebase.firestore.FieldPath.documentId(), workerIds.take(10))
                    .get()
                    .addOnSuccessListener { userSnapshots ->
                        val userMap = userSnapshots.documents.associate { 
                            it.id to it.toObject(User::class.java)!!.copy(uid = it.id)
                        }
                        
                        allData.clear()
                        applications.forEach { app ->
                            userMap[app.workerId]?.let { user ->
                                allData.add(user to app)
                            }
                        }
                        filterAndDisplay()
                    }
            }
    }

    private fun filterAndDisplay() {
        var filtered = when (currentTab) {
            "NEW"       -> allData.filter { it.second.status == "APPLIED" }
            "INTERVIEW" -> allData.filter { it.second.status == "INTERVIEW_SCHEDULED" }
            "HIRED"     -> allData.filter { it.second.status == "HIRED" }
            else        -> allData
        }
        
        // Apply Job Posting Filter
        selectedJobFilterId?.let { jobId ->
            filtered = filtered.filter { it.second.hiringId == jobId }
        }

        adapter.updateData(filtered)
    }

    private fun fetchHiringPosts() {
        val uid = auth.currentUser?.uid ?: return
        db.collection("hiring")
            .whereEqualTo("companyId", uid)
            .get()
            .addOnSuccessListener { snapshots ->
                allHiringPosts.clear()
                snapshots.documents.forEach { doc ->
                    doc.toObject(HiringPost::class.java)?.let { post ->
                        allHiringPosts.add(post.copy(hiringId = doc.id))
                    }
                }
            }
    }

    private fun showFilterDialog() {
        val dialog = BottomSheetDialog(requireContext(), R.style.BottomSheetDialogTheme)
        val dialogView = layoutInflater.inflate(R.layout.dialog_filter_job_postings, null, false)
        dialog.setContentView(dialogView)

        val rvFilter = dialogView.findViewById<RecyclerView>(R.id.rvFilterJobs)
        val btnApply = dialogView.findViewById<View>(R.id.btnApplyFilter)

        val options = mutableListOf<HiringPost?>()
        options.add(null)
        options.addAll(allHiringPosts)

        var tempSelectedId = selectedJobFilterId

        rvFilter.layoutManager = LinearLayoutManager(requireContext())
        rvFilter.adapter = object : RecyclerView.Adapter<RecyclerView.ViewHolder>() {
            override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
                val v = layoutInflater.inflate(R.layout.item_filter_job, parent, false)
                return object : RecyclerView.ViewHolder(v) {}
            }

            override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
                val post = options[position]
                val tv = holder.itemView.findViewById<TextView>(R.id.tvJobTitle)
                tv.text = post?.jobTitle ?: "All posts"
                val isSelected = if (post == null) tempSelectedId == null else post.hiringId == tempSelectedId
                if (isSelected) {
                    tv.setBackgroundResource(R.drawable.bg_tab_selected)
                    tv.setTextColor(Color.parseColor("#1C6EC6"))
                    tv.paint.isFakeBoldText = true
                } else {
                    tv.background = null
                    tv.setTextColor(Color.parseColor("#1E293B"))
                    tv.paint.isFakeBoldText = false
                }
                tv.setOnClickListener {
                    tempSelectedId = post?.hiringId
                    notifyDataSetChanged()
                }
            }
            override fun getItemCount() = options.size
        }

        btnApply.setOnClickListener {
            selectedJobFilterId = tempSelectedId
            updateFilterUI()
            filterAndDisplay()
            dialog.dismiss()
        }
        dialog.show()
    }

    private fun updateFilterUI() {
        if (selectedJobFilterId == null) {
            layoutActiveFilter.visibility = View.GONE
        } else {
            layoutActiveFilter.visibility = View.VISIBLE
            val job = allHiringPosts.find { it.hiringId == selectedJobFilterId }
            tvActiveFilterName.text = job?.jobTitle ?: "Filtered Post"
        }
    }

    private fun clearJobFilter() {
        selectedJobFilterId = null
        updateFilterUI()
        filterAndDisplay()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        appsListener?.remove()
    }
}
