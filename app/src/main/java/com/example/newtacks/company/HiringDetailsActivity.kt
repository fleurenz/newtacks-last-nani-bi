package com.example.newtacks.company

import android.content.res.ColorStateList
import android.graphics.Color
import android.os.Bundle
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.core.graphics.toColorInt
import coil.load
import com.example.newtacks.R
import com.example.newtacks.models.HiringPost
import com.example.newtacks.models.User
import com.example.newtacks.utils.ImageUtils
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import com.google.android.material.imageview.ShapeableImageView
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.gson.Gson
import java.util.Locale

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
    
    private lateinit var layoutJobDetailsContent: LinearLayout
    private lateinit var layoutAboutCompanyContent: LinearLayout
    
    private lateinit var tvDescription: TextView
    private lateinit var tvResponsibilities: TextView
    private lateinit var chipGroupServices: ChipGroup
    
    private lateinit var tvAboutUs: TextView
    private lateinit var tvCompanyEmail: TextView
    private lateinit var tvCompanyPhone: TextView
    private lateinit var tvCompanyWebsite: TextView
    
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
        
        layoutJobDetailsContent = findViewById(R.id.layoutJobDetailsContent)
        layoutAboutCompanyContent = findViewById(R.id.layoutAboutCompanyContent)
        
        tvDescription = findViewById(R.id.tvDescription)
        tvResponsibilities = findViewById(R.id.tvResponsibilities)
        chipGroupServices = findViewById(R.id.chipGroupServices)
        
        tvAboutUs = findViewById(R.id.tvAboutUs)
        tvCompanyEmail = findViewById(R.id.tvCompanyEmail)
        tvCompanyPhone = findViewById(R.id.tvCompanyPhone)
        tvCompanyWebsite = findViewById(R.id.tvCompanyWebsite)
        
        btnApply = findViewById(R.id.btnApply)
    }

    private fun setupToolbar() {
        val toolbar = findViewById<androidx.appcompat.widget.Toolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayShowTitleEnabled(false)
        toolbar.setNavigationOnClickListener { finish() }
    }

    private fun setupTabs() {
        tabJobDetails.setOnClickListener {
            selectTab(isJobDetails = true)
        }
        tabAboutCompany.setOnClickListener {
            selectTab(isJobDetails = false)
        }
    }

    private fun selectTab(isJobDetails: Boolean) {
        if (isJobDetails) {
            tvToolbarTitle.text = getString(R.string.job_description_label)
            tabJobDetails.setBackgroundResource(R.drawable.bg_tab_left_selected)
            tabJobDetails.setTextColor("#1E293B".toColorInt())
            tabAboutCompany.background = null
            tabAboutCompany.setTextColor("#64748B".toColorInt())
            
            layoutJobDetailsContent.visibility = View.VISIBLE
            layoutAboutCompanyContent.visibility = View.GONE
            
            ivMainJobImage.visibility = View.VISIBLE
            ivCompanyProfileCircle.visibility = View.GONE
            layoutCarouselControls.visibility = if ((hiringPost?.images?.size ?: 0) > 1) View.VISIBLE else View.GONE
        } else {
            tvToolbarTitle.text = getString(R.string.about_label)
            tabAboutCompany.setBackgroundResource(R.drawable.bg_tab_right_selected)
            tabAboutCompany.setTextColor("#1E293B".toColorInt())
            tabJobDetails.background = null
            tabJobDetails.setTextColor("#64748B".toColorInt())
            
            layoutJobDetailsContent.visibility = View.GONE
            layoutAboutCompanyContent.visibility = View.VISIBLE
            
            ivMainJobImage.visibility = View.GONE
            ivCompanyProfileCircle.visibility = View.VISIBLE
            layoutCarouselControls.visibility = View.GONE
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
                }
            }
        }
    }

    private fun setupApplyButton(post: HiringPost) {
        val uid = auth.currentUser?.uid
        
        if (uid == post.companyId) {
            btnApply.text = "You posted this"
            btnApply.isEnabled = false
            btnApply.alpha = 0.6f
            return
        }

        val hasApplied = uid != null && post.applicants.contains(uid)
        if (hasApplied) {
            btnApply.text = "Already Applied"
            btnApply.isEnabled = false
            btnApply.alpha = 0.6f
        } else {
            btnApply.setOnClickListener {
                applyForHiring(post)
            }
        }
    }

    private fun applyForHiring(post: HiringPost) {
        val uid = auth.currentUser?.uid ?: return
        btnApply.isEnabled = false
        
        db.collection("hiring").document(post.hiringId)
            .update("applicants", com.google.firebase.firestore.FieldValue.arrayUnion(uid))
            .addOnSuccessListener {
                Toast.makeText(this, "Application sent successfully!", Toast.LENGTH_SHORT).show()
                finish()
            }
            .addOnFailureListener {
                btnApply.isEnabled = true
                Toast.makeText(this, "Failed to apply", Toast.LENGTH_SHORT).show()
            }
    }
}
