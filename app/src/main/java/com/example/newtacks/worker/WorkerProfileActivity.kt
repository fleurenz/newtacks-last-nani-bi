package com.example.newtacks.worker

import android.os.Bundle
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import coil.load
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import coil.transform.CircleCropTransformation
import com.example.newtacks.R
import com.example.newtacks.models.Review
import com.example.newtacks.models.User
import com.example.newtacks.utils.ImageUtils
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import com.google.firebase.firestore.FirebaseFirestore
import java.util.Locale

class WorkerProfileActivity : AppCompatActivity() {

    private val firestore = FirebaseFirestore.getInstance()
    
    private lateinit var ivProfile: ImageView
    private lateinit var tvName: TextView
    private lateinit var ivVerified: ImageView
    private lateinit var tvAddress: TextView
    private lateinit var tvReviewCount: TextView
    private lateinit var tvRatingAvg: TextView
    private lateinit var tvExperience: TextView
    private lateinit var tvAbout: TextView
    private lateinit var tvContact: TextView
    private lateinit var chipGroupSkills: ChipGroup
    private lateinit var tvReviewsHeader: TextView
    private lateinit var rvReviews: RecyclerView
    private lateinit var btnShowAllReviews: Button

    private var workerId: String? = null
    private var allReviewsList: List<Review> = emptyList()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_worker_profile)

        workerId = intent.getStringExtra("WORKER_ID")
        if (workerId == null) {
            finish()
            return
        }

        val toolbar = findViewById<Toolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)
        toolbar.setNavigationOnClickListener { finish() }

        findViewById<ImageButton>(R.id.btnReport).setOnClickListener {
            showReportDialog()
        }

        // Robust Inset Handling: Use spacer for status bar
        val statusBarSpacer = findViewById<View>(R.id.statusBarSpacer)
        val rootLayout = findViewById<View>(R.id.workerPreviewProfile)
        ViewCompat.setOnApplyWindowInsetsListener(rootLayout) { _, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            val params = statusBarSpacer.layoutParams
            params.height = systemBars.top
            statusBarSpacer.layoutParams = params
            insets
        }

        ivProfile = findViewById(R.id.ivWorkerProfile)
        tvName = findViewById(R.id.tvWorkerName)
        ivVerified = findViewById(R.id.ivVerifiedBadge)
        tvAddress = findViewById(R.id.tvWorkerAddress)
        tvReviewCount = findViewById(R.id.tvReviewCount)
        tvRatingAvg = findViewById(R.id.tvRatingAvg)
        tvExperience = findViewById(R.id.tvExperienceYears)
        tvAbout = findViewById(R.id.tvAboutMe)
        tvContact = findViewById(R.id.tvContactPhone)
        chipGroupSkills = findViewById(R.id.chipGroupSkills)
        tvReviewsHeader = findViewById(R.id.tvReviewsHeader)
        rvReviews = findViewById(R.id.rvReviews)
        btnShowAllReviews = findViewById(R.id.btnShowAllReviews)

        rvReviews.layoutManager = LinearLayoutManager(this)

        btnShowAllReviews.setOnClickListener {
            showAllReviewsDialog()
        }

        fetchWorkerData()
        fetchReviews()
    }

    private fun fetchWorkerData() {
        workerId?.let { id ->
            firestore.collection("users").document(id).get()
                .addOnSuccessListener { doc ->
                    val worker = doc.toObject(User::class.java) ?: return@addOnSuccessListener
                    
                    tvName.text = worker.name
                    tvAddress.text = worker.address
                    tvContact.text = worker.phone
                    tvAbout.text = if (!worker.aboutUs.isNullOrBlank()) worker.aboutUs else "No description provided."
                    
                    ivVerified.visibility = if (worker.verificationStatus > 0) View.VISIBLE else View.GONE
                    
                    val ratingAvg = doc.getDouble("ratingAverage") ?: worker.rating
                    val ratingCnt = doc.getLong("ratingCount") ?: worker.totalRatings.toLong()
                    
                    tvRatingAvg.text = String.format(Locale.getDefault(), "%.1f", ratingAvg)
                    tvReviewCount.text = ratingCnt.toString()
                    tvExperience.text = "${worker.serviceExperience ?: 0} years"

                    ivProfile.load(worker.profileImage) {
                        crossfade(true)
                        placeholder(R.drawable.ic_user_placeholder)
                        transformations(CircleCropTransformation())
                    }
                    ivProfile.setOnClickListener {
                        ImageUtils.showFullscreenImage(this, worker.profileImage)
                    }

                    // Skills
                    chipGroupSkills.removeAllViews()
                    worker.serviceCategories?.forEach { skill ->
                        val chip = Chip(this)
                        chip.text = skill
                        chip.isCheckable = false
                        chip.isClickable = false
                        chip.setChipBackgroundColorResource(R.color.white)
                        chip.setChipStrokeColorResource(R.color.stroke_color)
                        chip.setChipStrokeWidthResource(R.dimen.chip_stroke_width)
                        chip.setTextColor(resources.getColor(R.color.text_primary, theme))
                        
                        // Add orange dot icon
                        chip.chipIcon = androidx.core.content.ContextCompat.getDrawable(this, R.drawable.bg_circle)
                        chip.chipIconSize = 8 * resources.displayMetrics.density
                        chip.chipIconTint = android.content.res.ColorStateList.valueOf(android.graphics.Color.parseColor("#F59E0B"))
                        chip.isChipIconVisible = true
                        
                        chipGroupSkills.addView(chip)
                    }
                }
        }
    }

    private fun fetchReviews() {
        workerId?.let { id ->
            // First get the total count for the header
            firestore.collection("users").document(id).get().addOnSuccessListener { userDoc ->
                val totalReviews = userDoc.getLong("ratingCount") ?: 0
                tvReviewsHeader.text = "Recent Reviews"
                
                if (totalReviews > 5) {
                    btnShowAllReviews.visibility = View.VISIBLE
                } else {
                    btnShowAllReviews.visibility = View.GONE
                }
            }

            firestore.collection("reviews")
                .whereEqualTo("workerId", id)
                .get()
                .addOnSuccessListener { snapshots ->
                    allReviewsList = snapshots.toObjects(Review::class.java)
                        .sortedByDescending { it.createdAt } // Sort locally to avoid index requirement
                    
                    android.util.Log.d("WorkerProfile", "Fetched ${allReviewsList.size} reviews")
                    
                    val recentReviews = allReviewsList.take(5)
                    rvReviews.adapter = WorkerReviewAdapter(recentReviews)
                    
                    if (allReviewsList.isEmpty()) {
                        tvReviewsHeader.text = "No Reviews Yet"
                    } else {
                        tvReviewsHeader.text = "Recent Reviews"
                    }
                }
                .addOnFailureListener { e ->
                    android.util.Log.e("WorkerProfile", "Error fetching reviews: ${e.message}")
                    Toast.makeText(this, "Failed to load reviews. Check logs for index link.", Toast.LENGTH_LONG).show()
                }
        }
    }

    private fun showAllReviewsDialog() {
        val dialog = android.app.Dialog(this, android.R.style.Theme_Material_Light_NoActionBar_Fullscreen)
        dialog.setContentView(R.layout.dialog_all_reviews)
        
        val tabLayout = dialog.findViewById<com.google.android.material.tabs.TabLayout>(R.id.tabLayoutRating)
        val rvAll = dialog.findViewById<RecyclerView>(R.id.rvAllReviews)
        val tvNoReviews = dialog.findViewById<TextView>(R.id.tvNoReviews)
        val btnClose = dialog.findViewById<Button>(R.id.btnClose)

        rvAll.layoutManager = LinearLayoutManager(this)
        
        // Add tabs
        val ratings = listOf("All", "5 Stars", "4 Stars", "3 Stars", "2 Stars", "1 Star")
        ratings.forEach { text ->
            tabLayout.addTab(tabLayout.newTab().setText(text))
        }

        fun updateDialogList(rating: Int?) {
            val filtered = if (rating == null) {
                allReviewsList
            } else {
                allReviewsList.filter { it.rating.toInt() == rating }
            }

            if (filtered.isEmpty()) {
                rvAll.visibility = View.GONE
                tvNoReviews.visibility = View.VISIBLE
            } else {
                rvAll.visibility = View.VISIBLE
                tvNoReviews.visibility = View.GONE
                rvAll.adapter = WorkerReviewAdapter(filtered)
            }
        }

        tabLayout.addOnTabSelectedListener(object : com.google.android.material.tabs.TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: com.google.android.material.tabs.TabLayout.Tab?) {
                val rating = when (tab?.position) {
                    1 -> 5
                    2 -> 4
                    3 -> 3
                    4 -> 2
                    5 -> 1
                    else -> null
                }
                updateDialogList(rating)
            }
            override fun onTabUnselected(tab: com.google.android.material.tabs.TabLayout.Tab?) {}
            override fun onTabReselected(tab: com.google.android.material.tabs.TabLayout.Tab?) {}
        })

        // Initial load (All)
        updateDialogList(null)

        btnClose.setOnClickListener { dialog.dismiss() }
        dialog.show()
    }

    private fun showReportDialog() {
        val options = arrayOf("Inappropriate behavior", "Scam / Fraud", "Poor service quality", "Other")
        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("Report Account")
            .setItems(options) { _, which ->
                val reason = options[which]
                submitReport(reason)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun submitReport(reason: String) {
        val report = mapOf(
            "reporterId" to (com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.uid ?: ""),
            "reportedWorkerId" to (workerId ?: ""),
            "reason" to reason,
            "timestamp" to System.currentTimeMillis()
        )
        
        firestore.collection("reports").add(report)
            .addOnSuccessListener {
                Toast.makeText(this, "Report submitted successfully.", Toast.LENGTH_SHORT).show()
            }
            .addOnFailureListener {
                Toast.makeText(this, "Failed to submit report.", Toast.LENGTH_SHORT).show()
            }
    }
}