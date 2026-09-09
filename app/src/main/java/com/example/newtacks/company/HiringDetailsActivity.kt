package com.example.newtacks.company

import android.app.DatePickerDialog
import android.content.res.ColorStateList
import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.core.graphics.toColorInt
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.graphics.ColorUtils
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import coil.load
import coil.transform.CircleCropTransformation
import com.example.newtacks.R
import com.example.newtacks.models.Application
import com.example.newtacks.models.HiringPost
import com.example.newtacks.models.User
import com.example.newtacks.utils.ImageUtils
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import com.google.android.material.imageview.ShapeableImageView
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FieldValue
import com.google.gson.Gson
import java.util.Locale
import java.util.Calendar

class HiringDetailsActivity : AppCompatActivity() {

    private lateinit var db: FirebaseFirestore
    private lateinit var auth: FirebaseAuth
    private var hiringPost: HiringPost? = null

    // Views
    private lateinit var tvToolbarTitle: TextView
    private lateinit var ivMainJobImage: ImageView
    private lateinit var ivCompanyProfileCircle: ShapeableImageView
    private lateinit var layoutCarouselControls: View
    private lateinit var btnPrevImage: ImageView
    private lateinit var btnNextImage: ImageView
    
    private lateinit var tvCompanyName: TextView
    private lateinit var tvLocation: TextView
    private lateinit var tvBadgeType: TextView
    private lateinit var tvBadgeRate: TextView
    
    private lateinit var tabJobDetails: TextView
    private lateinit var tabAboutCompany: TextView
    private lateinit var tabApplicants: TextView
    
    private lateinit var layoutJobDetailsContent: LinearLayout
    private lateinit var layoutAboutCompanyContent: LinearLayout
    private lateinit var layoutApplicantsContent: LinearLayout
    
    private lateinit var tvDescription: TextView
    private lateinit var tvResponsibilities: TextView
    private lateinit var chipGroupServices: ChipGroup
    
    private lateinit var tvAboutUs: TextView
    private lateinit var tvCompanyEmail: TextView
    private lateinit var tvCompanyPhone: TextView
    private lateinit var tvCompanyWebsite: TextView

    private lateinit var tvApplicantStats: TextView
    private lateinit var rvApplicants: RecyclerView
    private val applicantList = mutableListOf<User>()
    private val applicationMap = mutableMapOf<String, Application>()
    private lateinit var applicantAdapter: ApplicantAdapter
    
    private lateinit var loadingOverlay: View
    private lateinit var tvLoadingMessage: TextView
    private lateinit var btnApply: Button
    private lateinit var btnMoreOptions: ImageView
    
    private var myApplication: Application? = null
    private var themeColor: Int = "#0F325E".toColorInt() // Default to Worker

    private var currentImageIndex = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_hiring_details)

        db = FirebaseFirestore.getInstance()
        auth = FirebaseAuth.getInstance()

        val postJson = intent.getStringExtra("HIRING_POST_JSON")
        if (postJson != null) {
            hiringPost = Gson().fromJson(postJson, HiringPost::class.java)
        }

        initializeViews()
        determineThemeColor()

        // Robust Inset Handling: Use spacer for status bar
        val statusBarSpacer = findViewById<View>(R.id.statusBarSpacer)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(android.R.id.content)) { _, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            val params = statusBarSpacer.layoutParams
            params.height = systemBars.top
            statusBarSpacer.layoutParams = params
            insets
        }

        setupToolbar()
        setupTabs()
        displayDetails()
        loadCompanyExtraInfo()
        
        listenForPostUpdates() // Listen for post changes for everyone (vacancies, applicants, etc.)
        
        if (auth.currentUser?.uid == hiringPost?.companyId) {
            setupApplicantsList()
            listenForApplications()
            
            // Handle automatic tab focus if requested
            val focusTab = intent.getIntExtra("FOCUS_TAB", -1)
            if (focusTab != -1) {
                selectTab(focusTab)
            }
        } else {
            listenForMyApplication()
        }
    }

    private fun listenForMyApplication() {
        val uid = auth.currentUser?.uid ?: return
        val postId = hiringPost?.hiringId ?: return
        
        db.collection("applications")
            .whereEqualTo("hiringId", postId)
            .whereEqualTo("workerId", uid)
            .addSnapshotListener { snapshots, _ ->
                val app = snapshots?.documents?.firstOrNull()?.toObject(Application::class.java)
                myApplication = app
                hiringPost?.let { setupApplyButton(it) }
            }
    }

    private fun determineThemeColor() {
        val uid = auth.currentUser?.uid ?: return
        if (uid == hiringPost?.companyId) {
            themeColor = "#1C6EC6".toColorInt() // Company Blue
        } else {
            themeColor = "#0F325E".toColorInt() // Worker Navy Blue
        }
        applyTheme()
    }

    private fun applyTheme() {
        findViewById<View>(R.id.statusBarSpacer)?.setBackgroundColor(themeColor)
        findViewById<androidx.appcompat.widget.Toolbar>(R.id.toolbar)?.setBackgroundColor(themeColor)
        btnApply.backgroundTintList = ColorStateList.valueOf(themeColor)
        tvBadgeType.backgroundTintList = ColorStateList.valueOf(themeColor)
        tvBadgeRate.backgroundTintList = ColorStateList.valueOf(themeColor)
        
        // Initial tab state
        selectTab(0)
    }

    private fun initializeViews() {
        tvToolbarTitle = findViewById(R.id.tvToolbarTitle)
        ivMainJobImage = findViewById(R.id.ivMainJobImage)
        ivCompanyProfileCircle = findViewById(R.id.ivCompanyProfileCircle)
        layoutCarouselControls = findViewById(R.id.layoutCarouselControls)
        btnPrevImage = findViewById(R.id.btnPrevImage)
        btnNextImage = findViewById(R.id.btnNextImage)
        
        tvCompanyName = findViewById(R.id.tvCompanyName)
        tvLocation = findViewById(R.id.tvLocation)
        tvBadgeType = findViewById(R.id.tvBadgeType)
        tvBadgeRate = findViewById(R.id.tvBadgeRate)
        
        tabJobDetails = findViewById(R.id.tabJobDetails)
        tabAboutCompany = findViewById(R.id.tabAboutCompany)
        tabApplicants = findViewById(R.id.tabApplicants)
        
        layoutJobDetailsContent = findViewById(R.id.layoutJobDetailsContent)
        layoutAboutCompanyContent = findViewById(R.id.layoutAboutCompanyContent)
        layoutApplicantsContent = findViewById(R.id.layoutApplicantsContent)
        
        tvDescription = findViewById(R.id.tvDescription)
        tvResponsibilities = findViewById(R.id.tvResponsibilities)
        chipGroupServices = findViewById(R.id.chipGroupServices)
        
        tvAboutUs = findViewById(R.id.tvAboutUs)
        tvCompanyEmail = findViewById(R.id.tvCompanyEmail)
        tvCompanyPhone = findViewById(R.id.tvCompanyPhone)
        tvCompanyWebsite = findViewById(R.id.tvCompanyWebsite)

        tvApplicantStats = findViewById(R.id.tvApplicantStats)
        rvApplicants = findViewById(R.id.rvApplicants)
        
        loadingOverlay = findViewById(R.id.loadingOverlay)
        tvLoadingMessage = findViewById(R.id.tvLoadingMessage)
        
        btnApply = findViewById(R.id.btnApply)
        btnMoreOptions = findViewById(R.id.btnMoreOptions)
    }

    private fun setupToolbar() {
        val toolbar = findViewById<androidx.appcompat.widget.Toolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayShowTitleEnabled(false)
        toolbar.setNavigationOnClickListener { finish() }

        btnMoreOptions.setOnClickListener { showToolbarMenu(it) }
    }

    private fun showToolbarMenu(view: View) {
        val uid = auth.currentUser?.uid ?: return
        val post = hiringPost ?: return

        val popup = androidx.appcompat.widget.PopupMenu(this, view)
        
        // Options for the company owner
        if (uid == post.companyId) {
            if (post.status == "OPEN") {
                popup.menu.add("Close Job Posting")
            } else if (post.status == "CLOSED") {
                popup.menu.add("Reopen Job Posting")
            }
        } else {
            // Options for workers
            popup.menu.add("Report Post")
        }

        popup.setOnMenuItemClickListener { item ->
            when (item.title) {
                "Close Job Posting" -> confirmClosePosting()
                "Reopen Job Posting" -> updatePostStatus("OPEN")
                "Report Post" -> Toast.makeText(this, "Post reported", Toast.LENGTH_SHORT).show()
            }
            true
        }
        popup.show()
    }

    private fun confirmClosePosting() {
        android.app.AlertDialog.Builder(this)
            .setTitle("Close Posting")
            .setMessage("Are you sure you want to close this hiring post prematurely? No more applications will be accepted.")
            .setPositiveButton("Close Now") { _, _ -> updatePostStatus("CLOSED") }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun updatePostStatus(newStatus: String) {
        val postId = hiringPost?.hiringId ?: return
        loadingOverlay.visibility = View.VISIBLE
        tvLoadingMessage.text = "Updating status..."

        db.collection("hiring").document(postId)
            .update("status", newStatus)
            .addOnSuccessListener {
                loadingOverlay.visibility = View.GONE
                Toast.makeText(this, "Post marked as $newStatus", Toast.LENGTH_SHORT).show()
                // The listener (listenForPostUpdates) will handle the UI update
            }
            .addOnFailureListener {
                loadingOverlay.visibility = View.GONE
                Toast.makeText(this, "Update failed", Toast.LENGTH_SHORT).show()
            }
    }

    private fun setupTabs() {
        tabJobDetails.setOnClickListener { selectTab(0) }
        tabAboutCompany.setOnClickListener { selectTab(1) }
        tabApplicants.setOnClickListener { selectTab(2) }

        if (auth.currentUser?.uid == hiringPost?.companyId) {
            tabApplicants.visibility = View.VISIBLE
        }
    }

    private fun selectTab(index: Int) {
        // Reset all
        tabJobDetails.background = null
        tabAboutCompany.background = null
        tabApplicants.background = null
        tabJobDetails.setTextColor("#64748B".toColorInt())
        tabAboutCompany.setTextColor("#64748B".toColorInt())
        tabApplicants.setTextColor("#64748B".toColorInt())
        
        layoutJobDetailsContent.visibility = View.GONE
        layoutAboutCompanyContent.visibility = View.GONE
        layoutApplicantsContent.visibility = View.GONE
        ivMainJobImage.visibility = View.GONE
        ivCompanyProfileCircle.visibility = View.GONE
        layoutCarouselControls.visibility = View.GONE

        when (index) {
            0 -> {
                tvToolbarTitle.text = getString(R.string.job_description_label)
                tabJobDetails.setBackgroundResource(R.drawable.bg_tab_selected)
                tabJobDetails.backgroundTintList = ColorStateList.valueOf(themeColor.withAlpha(40))
                tabJobDetails.setTextColor(themeColor)
                layoutJobDetailsContent.visibility = View.VISIBLE
                ivMainJobImage.visibility = View.VISIBLE
                layoutCarouselControls.visibility = if ((hiringPost?.images?.size ?: 0) > 1) View.VISIBLE else View.GONE
            }
            1 -> {
                tvToolbarTitle.text = getString(R.string.about_label)
                tabAboutCompany.setBackgroundResource(R.drawable.bg_tab_selected)
                tabAboutCompany.backgroundTintList = ColorStateList.valueOf(themeColor.withAlpha(40))
                tabAboutCompany.setTextColor(themeColor)
                layoutAboutCompanyContent.visibility = View.VISIBLE
                ivCompanyProfileCircle.visibility = View.VISIBLE
            }
            2 -> {
                tvToolbarTitle.text = "Applicants"
                tabApplicants.setBackgroundResource(R.drawable.bg_tab_selected)
                tabApplicants.backgroundTintList = ColorStateList.valueOf(themeColor.withAlpha(40))
                tabApplicants.setTextColor(themeColor)
                layoutApplicantsContent.visibility = View.VISIBLE
                ivCompanyProfileCircle.visibility = View.VISIBLE
            }
        }
    }

    private fun displayDetails() {
        val post = hiringPost ?: return

        tvCompanyName.text = post.companyName
        tvLocation.text = post.companyAddress
        tvBadgeRate.text = getString(R.string.rate_format, post.dailyRate)
        tvBadgeType.text = post.employmentType.replace("_", " ").lowercase()
            .replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString() }

        tvDescription.text = post.description.ifEmpty { "No description provided." }
        tvResponsibilities.text = post.responsibilities.ifEmpty { "No responsibilities listed." }

        // Services Tags
        chipGroupServices.removeAllViews()
        post.serviceCategories.forEach { service ->
            val chip = Chip(this)
            chip.text = service
            chip.chipBackgroundColor = ColorStateList.valueOf("#F1F5F9".toColorInt())
            chip.setTextColor("#475569".toColorInt())
            chip.chipStrokeWidth = 0f
            chip.isClickable = false
            chipGroupServices.addView(chip)
        }

        // Job Image Carousel
        updateJobImage()
        
        btnPrevImage.setOnClickListener {
            if (post.images.isNotEmpty()) {
                currentImageIndex = (currentImageIndex - 1 + post.images.size) % post.images.size
                updateJobImage()
            }
        }
        btnNextImage.setOnClickListener {
            if (post.images.isNotEmpty()) {
                currentImageIndex = (currentImageIndex + 1) % post.images.size
                updateJobImage()
            }
        }
        
        layoutCarouselControls.visibility = if (post.images.size > 1) View.VISIBLE else View.GONE

        setupApplyButton(post)
    }

    private fun updateJobImage() {
        val post = hiringPost ?: return
        if (post.images.isNotEmpty()) {
            val url = post.images[currentImageIndex]
            ivMainJobImage.load(url) {
                placeholder(R.drawable.bg_image_placeholder)
                error(R.drawable.bg_image_placeholder)
            }
            ivMainJobImage.setOnClickListener {
                ImageUtils.showFullscreenImage(this, url)
            }
        }
    }

    private fun loadCompanyExtraInfo() {
        val companyId = hiringPost?.companyId ?: return
        db.collection("users").document(companyId).get().addOnSuccessListener { doc ->
            val company = doc.toObject(User::class.java) ?: return@addOnSuccessListener
            
            tvAboutUs.text = company.aboutUs?.ifEmpty { "No company information available." } ?: "No company information available."
            tvCompanyEmail.text = company.email
            tvCompanyPhone.text = company.phone
            tvCompanyWebsite.text = getString(R.string.website_placeholder, company.companyName?.replace(" ", "")?.lowercase() ?: "")

            // Company Profile Image
            if (company.profileImage.isNotEmpty()) {
                ivCompanyProfileCircle.load(company.profileImage) {
                    placeholder(R.drawable.ic_user_placeholder)
                    transformations(CircleCropTransformation())
                }
            }
        }
    }

    private fun setupApplicantsList() {
        applicantAdapter = ApplicantAdapter(applicantList) { user ->
            showWorkerDetailsDialog(user)
        }
        rvApplicants.layoutManager = LinearLayoutManager(this)
        rvApplicants.adapter = applicantAdapter
    }

    private fun listenForApplications() {
        val postId = hiringPost?.hiringId ?: return
        db.collection("applications")
            .whereEqualTo("hiringId", postId)
            .addSnapshotListener { snapshots, _ ->
                if (snapshots == null) return@addSnapshotListener
                
                val workerIds = mutableListOf<String>()
                applicationMap.clear()
                
                for (doc in snapshots.documents) {
                    val app = doc.toObject(Application::class.java) ?: continue
                    applicationMap[app.workerId] = app
                    workerIds.add(app.workerId)
                }
                
                if (workerIds.isNotEmpty()) {
                    fetchApplicantProfiles(workerIds)
                } else {
                    applicantList.clear()
                    applicantAdapter.notifyDataSetChanged()
                }
            }
    }

    private fun fetchApplicantProfiles(uids: List<String>) {
        db.collection("users")
            .whereIn(com.google.firebase.firestore.FieldPath.documentId(), uids.take(10))
            .get()
            .addOnSuccessListener { snapshots ->
                applicantList.clear()
                for (doc in snapshots) {
                    val user = doc.toObject(User::class.java)?.copy(uid = doc.id)
                    if (user != null) applicantList.add(user)
                }
                applicantAdapter.notifyDataSetChanged()
            }
    }

    private fun listenForPostUpdates() {
        val postId = hiringPost?.hiringId ?: return
        db.collection("hiring").document(postId).addSnapshotListener { snapshot, _ ->
            val updatedPost = snapshot?.toObject(HiringPost::class.java)
            if (updatedPost != null) {
                hiringPost = updatedPost
                tvApplicantStats.text = "${updatedPost.acceptedWorkers.size} of ${updatedPost.vacancies} positions filled"
                setupApplyButton(updatedPost)
            }
        }
    }

    private fun showWorkerDetailsDialog(worker: User) {
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_worker_details_preview, null)
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

        val app = applicationMap[worker.uid]
        
        // Setup Status Badge (NC Level)
        if (worker.verificationStatus > 0) {
            tvBadge.visibility = View.VISIBLE
            tvBadge.text = "NC${worker.verificationStatus}"
            tvBadge.setBackgroundResource(R.drawable.bg_badge_green1)
        } else {
            tvBadge.visibility = View.GONE
        }

        ivProfile.load(worker.profileImage) {
            crossfade(true)
            placeholder(R.drawable.ic_user_placeholder)
            transformations(CircleCropTransformation())
        }

        // Detailed Status String
        val statusText = when (app?.status) {
            "APPLIED" -> "Approved"
            "INTERVIEW_SCHEDULED" -> {
                val sdf = java.text.SimpleDateFormat("MMM dd, yyyy 'at' h:mm a", Locale.getDefault())
                val dateStr = if (app.interviewDate != null) sdf.format(java.util.Date(app.interviewDate)) else "TBD"
                val response = when(app.workerResponse) {
                    "ACCEPTED" -> " (Confirmed)"
                    "REJECTED" -> " (Rejected)"
                    else -> " (Pending Confirmation)"
                }
                "Interview Scheduled at: $dateStr$response"
            }
            "HIRED" -> "Hired"
            "CANCELLED" -> "Cancelled"
            "REJECTED" -> "Rejected"
            else -> "Awaiting Review"
        }
        tvDetailedStatus.text = statusText

        // Process Buttons Logic
        btnPrimary.visibility = View.GONE
        btnSecondary.visibility = View.GONE

        val dialog = android.app.AlertDialog.Builder(this)
            .setView(dialogView)
            .create()

        dialogView.findViewById<View>(R.id.btnCloseDialog).setOnClickListener {
            dialog.dismiss()
        }

        when (app?.status) {
            "APPLIED" -> {
                btnPrimary.visibility = View.VISIBLE
                btnPrimary.text = "Schedule Interview"
                btnPrimary.setOnClickListener { dialog.dismiss(); showScheduleInterviewDialog(worker) }
                
                btnSecondary.visibility = View.VISIBLE
                btnSecondary.text = "Reject Applicant"
                btnSecondary.setOnClickListener { 
                    android.app.AlertDialog.Builder(this)
                        .setTitle("Reject Applicant")
                        .setMessage("Are you sure you want to reject this applicant?")
                        .setPositiveButton("Reject") { _, _ -> dialog.dismiss(); rejectApplicant(worker) }
                        .setNegativeButton("Cancel", null)
                        .show()
                }
            }
            "INTERVIEW_SCHEDULED" -> {
                if (app.workerResponse == "ACCEPTED") {
                    btnPrimary.visibility = View.VISIBLE
                    btnPrimary.text = "Hire Worker"
                    btnPrimary.setOnClickListener { dialog.dismiss(); confirmHiring(worker) }
                } else {
                    btnPrimary.visibility = View.VISIBLE
                    btnPrimary.text = "Reschedule Interview"
                    btnPrimary.setOnClickListener { dialog.dismiss(); showScheduleInterviewDialog(worker) }
                }
                
                btnSecondary.visibility = View.VISIBLE
                btnSecondary.text = "Reject Applicant"
                btnSecondary.setOnClickListener { 
                    android.app.AlertDialog.Builder(this)
                        .setTitle("Reject Applicant")
                        .setMessage("Are you sure you want to reject this applicant?")
                        .setPositiveButton("Reject") { _, _ -> dialog.dismiss(); rejectApplicant(worker) }
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
        worker.nc1CertificateUrl?.let { url -> if (url.isNotEmpty()) { btnNC1.visibility = View.VISIBLE; btnNC1.setOnClickListener { ImageUtils.showFullscreenImage(this, url) }; hasCert = true } }
        worker.nc2CertificateUrl?.let { url -> if (url.isNotEmpty()) { btnNC2.visibility = View.VISIBLE; btnNC2.setOnClickListener { ImageUtils.showFullscreenImage(this, url) }; hasCert = true } }
        worker.nc3CertificateUrl?.let { url -> if (url.isNotEmpty()) { btnNC3.visibility = View.VISIBLE; btnNC3.setOnClickListener { ImageUtils.showFullscreenImage(this, url) }; hasCert = true } }
        
        if (!hasCert) tvNoCert.visibility = View.VISIBLE

        val btnFullProfile = dialogView.findViewById<Button>(R.id.btnViewFullProfile)
        btnFullProfile.setOnClickListener {
            val intent = android.content.Intent(this, com.example.newtacks.worker.WorkerProfileActivity::class.java)
            intent.putExtra("WORKER_ID", worker.uid)
            startActivity(intent)
        }

        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
        dialog.window?.setLayout(
            (resources.displayMetrics.widthPixels * 0.95).toInt(),
            (resources.displayMetrics.heightPixels * 0.85).toInt()
        )
        dialog.show()
    }

    private fun showScheduleInterviewDialog(worker: User) {
        val cal = Calendar.getInstance()
        val dialog = DatePickerDialog(this, { _, y, m, d ->
            val interviewCal = Calendar.getInstance()
            interviewCal.set(y, m, d, 10, 0) // Default 10 AM
            scheduleInterview(worker, interviewCal.timeInMillis)
        }, cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH))
        
        // Prevent past dates
        dialog.datePicker.minDate = System.currentTimeMillis() - 1000
        dialog.show()
    }

    private fun scheduleInterview(worker: User, timestamp: Long) {
        val app = applicationMap[worker.uid] ?: return
        val post = hiringPost ?: return
        
        loadingOverlay.visibility = View.VISIBLE
        tvLoadingMessage.text = "Scheduling interview..."

        db.collection("applications").document(app.applicationId)
            .update(mapOf(
                "status" to "INTERVIEW_SCHEDULED",
                "interviewDate" to timestamp,
                "interviewLocation" to post.companyAddress,
                "workerResponse" to null // Reset response for new date
            ))
            .addOnSuccessListener {
                loadingOverlay.visibility = View.GONE
                com.example.newtacks.utils.NotificationHelper.sendNotification(
                    worker.uid,
                    "Interview Scheduled",
                    "You have been invited for an interview for ${post.jobTitle} on ${java.text.SimpleDateFormat("MMM dd", Locale.getDefault()).format(timestamp)}.",
                    "HIRING"
                )
                Toast.makeText(this, "Interview scheduled", Toast.LENGTH_SHORT).show()
            }
            .addOnFailureListener {
                loadingOverlay.visibility = View.GONE
                Toast.makeText(this, "Failed to schedule", Toast.LENGTH_SHORT).show()
            }
    }

    private fun rejectApplicant(worker: User) {
        val app = applicationMap[worker.uid] ?: return
        
        // Prevent infinite rejection if already rejected or in final status
        if (app.status == "REJECTED" || app.status == "CANCELLED" || app.status == "HIRED") {
            return
        }

        loadingOverlay.visibility = View.VISIBLE
        tvLoadingMessage.text = "Rejecting applicant..."

        db.collection("applications").document(app.applicationId)
            .update("status", "REJECTED")
            .addOnSuccessListener {
                loadingOverlay.visibility = View.GONE
                Toast.makeText(this, "Applicant rejected", Toast.LENGTH_SHORT).show()
                
                // Notify the worker
                com.example.newtacks.utils.NotificationHelper.sendNotification(
                    worker.uid,
                    "Application Update",
                    "The company has updated your application status for ${hiringPost?.jobTitle}.",
                    "HIRING"
                )
            }
            .addOnFailureListener {
                loadingOverlay.visibility = View.GONE
                Toast.makeText(this, "Action failed", Toast.LENGTH_SHORT).show()
            }
    }

    private fun confirmHiring(worker: User) {
        val post = hiringPost ?: return
        val currentAccepted = post.acceptedWorkers.size
        
        if (currentAccepted >= post.vacancies) {
            Toast.makeText(this, "Threshold reached.", Toast.LENGTH_LONG).show()
            return
        }

        loadingOverlay.visibility = View.VISIBLE
        tvLoadingMessage.text = "Hiring worker..."

        db.runTransaction { transaction ->
            val ref = db.collection("hiring").document(post.hiringId)
            val appRef = db.collection("applications").document(applicationMap[worker.uid]!!.applicationId)
            
            val snapshot = transaction.get(ref)
            val updatedAccepted = snapshot.toObject(HiringPost::class.java)?.acceptedWorkers ?: emptyList()
            
            if (updatedAccepted.size >= post.vacancies) {
                throw Exception("Vacancy full")
            }

            val newList = updatedAccepted.toMutableList()
            newList.add(worker.uid)
            
            transaction.update(ref, mapOf(
                "acceptedWorkers" to newList,
                "status" to if (newList.size >= post.vacancies) "CLOSED" else "OPEN"
            ))
            transaction.update(appRef, mapOf("status" to "HIRED"))
            
        }.addOnSuccessListener {
            loadingOverlay.visibility = View.GONE
            Toast.makeText(this, "Worker hired successfully!", Toast.LENGTH_SHORT).show()
            com.example.newtacks.utils.NotificationHelper.sendNotification(
                worker.uid,
                "Status: Hired!",
                "Congratulations! You have been officially hired for ${post.jobTitle}.",
                "HIRING"
            )
        }.addOnFailureListener {
            loadingOverlay.visibility = View.GONE
            Toast.makeText(this, "Hiring failed: ${it.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun setupApplyButton(post: HiringPost) {
        val uid = auth.currentUser?.uid
        
        if (uid == post.companyId) {
            btnApply.text = "View Applicants"
            btnApply.setOnClickListener { selectTab(2) }
            return
        }

        val app = myApplication
        if (app != null) {
            when (app.status) {
                "INTERVIEW_SCHEDULED" -> {
                    if (app.workerResponse == null) {
                        btnApply.text = "Confirm Interview Schedule"
                        btnApply.isEnabled = true
                        btnApply.alpha = 1.0f
                        btnApply.setOnClickListener { showInterviewConfirmationDialog(app) }
                    } else if (app.workerResponse == "ACCEPTED") {
                        btnApply.text = "Interview Confirmed"
                        btnApply.isEnabled = false
                        btnApply.alpha = 0.6f
                    }
                }
                "HIRED" -> {
                    btnApply.text = "You are Hired!"
                    btnApply.isEnabled = false
                    btnApply.alpha = 0.6f
                }
                "REJECTED", "CANCELLED" -> {
                    btnApply.text = "Application Closed"
                    btnApply.isEnabled = false
                    btnApply.alpha = 0.6f
                }
                else -> {
                    btnApply.text = "Applied"
                    btnApply.isEnabled = false
                    btnApply.alpha = 0.6f
                }
            }
            return
        }

        val hasApplied = uid != null && post.applicants.contains(uid)
        if (hasApplied) {
            btnApply.text = "Applied"
            btnApply.isEnabled = false
            btnApply.alpha = 0.6f
        } else if (post.status == "CLOSED" || post.status == "EXPIRED") {
            btnApply.text = "Hiring Closed"
            btnApply.isEnabled = false
            btnApply.alpha = 0.6f
        } else {
            btnApply.text = "Apply Now"
            btnApply.isEnabled = true
            btnApply.alpha = 1.0f
            btnApply.setOnClickListener { applyForHiring(post) }
        }
    }

    private fun showInterviewConfirmationDialog(app: Application) {
        val dialog = android.app.Dialog(this)
        dialog.setContentView(R.layout.dialog_role_select)
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
        dialog.window?.setLayout(
            (resources.displayMetrics.widthPixels * 0.9).toInt(),
            android.view.ViewGroup.LayoutParams.WRAP_CONTENT
        )

        val title = dialog.findViewById<TextView>(R.id.dialogTitle)
        val message = dialog.findViewById<TextView>(R.id.dialogMessage)
        val btnAccept = dialog.findViewById<com.google.android.material.button.MaterialButton>(R.id.dialogBtnPositive)
        val btnReject = dialog.findViewById<com.google.android.material.button.MaterialButton>(R.id.dialogBtnNegative)
        val icon = dialog.findViewById<ImageView>(R.id.dialogIcon)

        icon.setImageResource(R.drawable.ic_calendar)
        title.text = "Confirm Interview"
        
        val sdf = java.text.SimpleDateFormat("MMMM d, h:mm a", Locale.getDefault())
        val dateStr = if (app.interviewDate != null) sdf.format(java.util.Date(app.interviewDate)) else "TBD"
        
        message.text = "The company has scheduled an interview on:\n\n$dateStr\n\nWould you like to accept this schedule?"

        btnAccept.text = "Accept"
        btnAccept.setOnClickListener {
            updateWorkerInterviewResponse(app, "ACCEPTED")
            dialog.dismiss()
        }

        btnReject.text = "Reject"
        btnReject.setOnClickListener {
            android.app.AlertDialog.Builder(this)
                .setTitle("Reject Schedule")
                .setMessage("Are you sure you want to reject this interview? This will also cancel your application.")
                .setPositiveButton("Yes, Reject") { _, _ ->
                    updateWorkerInterviewResponse(app, "REJECTED")
                    dialog.dismiss()
                }
                .setNegativeButton("No", null)
                .show()
        }

        dialog.show()
    }

    private fun updateWorkerInterviewResponse(app: Application, response: String) {
        loadingOverlay.visibility = View.VISIBLE
        tvLoadingMessage.text = "Updating response..."
        
        val updates = mutableMapOf<String, Any>(
            "workerResponse" to response
        )
        
        if (response == "REJECTED") {
            updates["status"] = "CANCELLED"
        }

        db.collection("applications").document(app.applicationId)
            .update(updates)
            .addOnSuccessListener {
                loadingOverlay.visibility = View.GONE
                val toastMsg = if (response == "ACCEPTED") "Interview confirmed!" else "Application cancelled."
                Toast.makeText(this, toastMsg, Toast.LENGTH_SHORT).show()
                
                // Notify Company
                val post = hiringPost
                if (post != null) {
                    val notifTitle = if (response == "ACCEPTED") "Interview Accepted" else "Interview Rejected"
                    val notifMsg = if (response == "ACCEPTED") "A worker has accepted the interview for ${post.jobTitle}." 
                                  else "A worker has rejected the interview for ${post.jobTitle}."
                    
                    com.example.newtacks.utils.NotificationHelper.sendNotification(
                        post.companyId,
                        notifTitle,
                        notifMsg,
                        "APPLICANTS"
                    )
                }
            }
            .addOnFailureListener {
                loadingOverlay.visibility = View.GONE
                Toast.makeText(this, "Failed to update response", Toast.LENGTH_SHORT).show()
            }
    }

    private fun applyForHiring(post: HiringPost) {
        val uid = auth.currentUser?.uid ?: return
        btnApply.isEnabled = false
        
        loadingOverlay.visibility = View.VISIBLE
        tvLoadingMessage.text = "Sending application..."

        val appId = db.collection("applications").document().id
        val app = Application(
            applicationId = appId,
            hiringId = post.hiringId,
            companyId = post.companyId,
            workerId = uid,
            jobTitle = post.jobTitle,
            status = "APPLIED"
        )

        db.collection("applications").document(appId).set(app)
            .addOnSuccessListener {
                // Update the hiring post's applicants list in Firestore
                db.collection("hiring").document(post.hiringId)
                    .update("applicants", FieldValue.arrayUnion(uid))

                loadingOverlay.visibility = View.GONE
                com.example.newtacks.utils.NotificationHelper.sendNotification(
                    post.companyId,
                    "New Job Applicant",
                    "Someone has applied for your ${post.jobTitle} position.",
                    "APPLICANTS"
                )
                Toast.makeText(this, "Application sent!", Toast.LENGTH_SHORT).show()
                finish()
            }
            .addOnFailureListener {
                loadingOverlay.visibility = View.GONE
                btnApply.isEnabled = true
                Toast.makeText(this, "Application failed", Toast.LENGTH_SHORT).show()
            }
    }

    private fun Int.withAlpha(alpha: Int): Int {
        return ColorUtils.setAlphaComponent(this, alpha)
    }
}
