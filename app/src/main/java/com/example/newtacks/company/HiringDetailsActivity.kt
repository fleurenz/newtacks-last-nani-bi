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
    
    private lateinit var btnApply: Button

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
        setupToolbar()
        setupTabs()
        displayDetails()
        loadCompanyExtraInfo()
        
        if (auth.currentUser?.uid == hiringPost?.companyId) {
            setupApplicantsList()
            listenForApplications()
            listenForPostUpdates()

            // Handle automatic tab focus if requested
            val focusTab = intent.getIntExtra("FOCUS_TAB", -1)
            if (focusTab != -1) {
                selectTab(focusTab)
            }
        }
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
        
        btnApply = findViewById(R.id.btnApply)
    }

    private fun setupToolbar() {
        val toolbar = findViewById<androidx.appcompat.widget.Toolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayShowTitleEnabled(false)
        toolbar.setNavigationOnClickListener { finish() }
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
                tabJobDetails.setBackgroundResource(R.drawable.bg_tab_left_selected)
                tabJobDetails.setTextColor("#1E293B".toColorInt())
                layoutJobDetailsContent.visibility = View.VISIBLE
                ivMainJobImage.visibility = View.VISIBLE
                layoutCarouselControls.visibility = if ((hiringPost?.images?.size ?: 0) > 1) View.VISIBLE else View.GONE
            }
            1 -> {
                tvToolbarTitle.text = getString(R.string.about_label)
                tabAboutCompany.setBackgroundColor("#D1E2FF".toColorInt())
                tabAboutCompany.setTextColor("#1E293B".toColorInt())
                layoutAboutCompanyContent.visibility = View.VISIBLE
                ivCompanyProfileCircle.visibility = View.VISIBLE
            }
            2 -> {
                tvToolbarTitle.text = "Applicants"
                tabApplicants.setBackgroundResource(R.drawable.bg_tab_right_selected)
                tabApplicants.setTextColor("#1E293B".toColorInt())
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
            .whereIn("uid", uids.take(10))
            .get()
            .addOnSuccessListener { snapshots ->
                applicantList.clear()
                for (doc in snapshots) {
                    val user = doc.toObject(User::class.java)
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
            }
        }
    }

    private fun showWorkerDetailsDialog(worker: User) {
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_worker_details_preview, null)
        val ivProfile = dialogView.findViewById<ImageView>(R.id.ivWorkerProfile)
        val tvName = dialogView.findViewById<TextView>(R.id.tvWorkerName)
        val tvRating = dialogView.findViewById<TextView>(R.id.tvWorkerRating)
        val tvStatus = dialogView.findViewById<TextView>(R.id.tvWorkerBadge)
        
        tvName.text = worker.name
        tvRating.text = "⭐ %.1f (%d reviews)".format(worker.rating, worker.totalRatings)

        val app = applicationMap[worker.uid]
        tvStatus.visibility = View.VISIBLE
        tvStatus.text = app?.status ?: "APPLIED"

        ivProfile.load(worker.profileImage) {
            crossfade(true)
            placeholder(R.drawable.ic_user_placeholder)
            transformations(CircleCropTransformation())
        }

        val builder = android.app.AlertDialog.Builder(this)
            .setView(dialogView)
            .setNeutralButton("Reject", { _, _ -> rejectApplicant(worker) })
            .setNegativeButton("Close", null)

        when (app?.status) {
            "APPLIED" -> {
                builder.setPositiveButton("Schedule Interview", { _, _ -> showScheduleInterviewDialog(worker) })
            }
            "INTERVIEW_SCHEDULED" -> {
                if (app.workerResponse == "ACCEPTED") {
                    builder.setPositiveButton("Hire", { _, _ -> confirmHiring(worker) })
                } else if (app.workerResponse == "RESCHEDULE") {
                    builder.setPositiveButton("Reschedule", { _, _ -> showScheduleInterviewDialog(worker) })
                }
            }
        }
        
        builder.show()
    }

    private fun showScheduleInterviewDialog(worker: User) {
        val cal = Calendar.getInstance()
        DatePickerDialog(this, { _, y, m, d ->
            val interviewCal = Calendar.getInstance()
            interviewCal.set(y, m, d, 10, 0) // Default 10 AM
            scheduleInterview(worker, interviewCal.timeInMillis)
        }, cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH)).show()
    }

    private fun scheduleInterview(worker: User, timestamp: Long) {
        val app = applicationMap[worker.uid] ?: return
        val post = hiringPost ?: return
        
        db.collection("applications").document(app.applicationId)
            .update(mapOf(
                "status" to "INTERVIEW_SCHEDULED",
                "interviewDate" to timestamp,
                "interviewLocation" to post.companyAddress,
                "workerResponse" to null // Reset response for new date
            ))
            .addOnSuccessListener {
                com.example.newtacks.utils.NotificationHelper.sendNotification(
                    worker.uid,
                    "Interview Scheduled",
                    "You have been invited for an interview for ${post.jobTitle} on ${java.text.SimpleDateFormat("MMM dd", Locale.getDefault()).format(timestamp)}.",
                    "HIRING"
                )
                Toast.makeText(this, "Interview scheduled", Toast.LENGTH_SHORT).show()
            }
    }

    private fun rejectApplicant(worker: User) {
        val app = applicationMap[worker.uid] ?: return
        db.collection("applications").document(app.applicationId)
            .update("status", "REJECTED")
            .addOnSuccessListener {
                Toast.makeText(this, "Applicant rejected", Toast.LENGTH_SHORT).show()
            }
    }

    private fun confirmHiring(worker: User) {
        val post = hiringPost ?: return
        val currentAccepted = post.acceptedWorkers.size
        
        if (currentAccepted >= post.vacancies) {
            Toast.makeText(this, "Threshold reached.", Toast.LENGTH_LONG).show()
            return
        }

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
            Toast.makeText(this, "Worker hired successfully!", Toast.LENGTH_SHORT).show()
            com.example.newtacks.utils.NotificationHelper.sendNotification(
                worker.uid,
                "Status: Hired!",
                "Congratulations! You have been officially hired for ${post.jobTitle}.",
                "HIRING"
            )
        }
    }

    private fun setupApplyButton(post: HiringPost) {
        val uid = auth.currentUser?.uid
        
        if (uid == post.companyId) {
            btnApply.text = "View Applicants"
            btnApply.setOnClickListener { selectTab(2) }
            return
        }

        val hasApplied = uid != null && applicationMap.containsKey(uid)
        if (hasApplied) {
            btnApply.text = "Already Applied"
            btnApply.isEnabled = false
            btnApply.alpha = 0.6f
        } else {
            btnApply.setOnClickListener { applyForHiring(post) }
        }
    }

    private fun applyForHiring(post: HiringPost) {
        val uid = auth.currentUser?.uid ?: return
        btnApply.isEnabled = false
        
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
                com.example.newtacks.utils.NotificationHelper.sendNotification(
                    post.companyId,
                    "New Job Applicant",
                    "Someone has applied for your ${post.jobTitle} position.",
                    "APPLICANTS"
                )
                Toast.makeText(this, "Application sent!", Toast.LENGTH_SHORT).show()
                finish()
            }
    }
}
