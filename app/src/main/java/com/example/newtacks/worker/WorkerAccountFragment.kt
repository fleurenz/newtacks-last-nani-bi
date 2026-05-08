package com.example.newtacks.worker

import android.annotation.SuppressLint
import android.app.Dialog
import android.content.Intent
import android.os.Bundle
import android.view.*
import android.net.Uri
import android.widget.Button
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.fragment.app.Fragment
import com.cloudinary.android.MediaManager
import com.cloudinary.android.callback.ErrorInfo
import com.cloudinary.android.callback.UploadCallback
import com.example.newtacks.R
import com.example.newtacks.authentication.OnboardingActivity
import com.example.newtacks.models.Review
import com.example.newtacks.models.User
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

class WorkerAccountFragment : Fragment() {

    private val firestore = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()
    private lateinit var tvWorkerName: TextView
    private lateinit var tvWorkerRating: TextView
    private lateinit var tvAcceptedJobs: TextView
    private lateinit var tvCompletedJobs: TextView
    private lateinit var reviewContainer: LinearLayout
    private lateinit var tvSeeAllReviews: TextView
    private lateinit var filterAll: TextView
    private lateinit var filter5: TextView
    private lateinit var filter4: TextView
    private lateinit var filter3: TextView
    private lateinit var filter2: TextView
    private lateinit var filter1: TextView
    private lateinit var btnLogout: Button
    private lateinit var layoutHeader: LinearLayout

    private lateinit var tvVerificationBadge: TextView
    private lateinit var tvVerificationLevel: TextView
    private lateinit var btnUploadNC1: Button
    private lateinit var btnUploadNC2: Button
    private lateinit var btnUploadNC3: Button

    private var isShowingAllReviews = false
    private var currentRatingFilter: Int? = null

    private var pendingNCLevel: Int = 0

    private val pickCertificate =
        registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
            uri?.let { uploadCertificate(it, pendingNCLevel) }
        }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val view = inflater.inflate(R.layout.fragment_worker_account, container, false)

        tvWorkerName    = view.findViewById(R.id.tvWorkerName)
        tvWorkerRating  = view.findViewById(R.id.tvWorkerRating)
        tvAcceptedJobs  = view.findViewById(R.id.tvAcceptedJobs)
        tvCompletedJobs = view.findViewById(R.id.tvCompletedJobs)
        reviewContainer = view.findViewById(R.id.reviewContainer)
        tvSeeAllReviews = view.findViewById(R.id.tvSeeAllReviews)
        filterAll       = view.findViewById(R.id.filterAll)
        filter5         = view.findViewById(R.id.filter5)
        filter4         = view.findViewById(R.id.filter4)
        filter3         = view.findViewById(R.id.filter3)
        filter2         = view.findViewById(R.id.filter2)
        filter1         = view.findViewById(R.id.filter1)
        btnLogout       = view.findViewById(R.id.btnLogout)
        layoutHeader    = view.findViewById(R.id.layoutHeader)

        tvVerificationBadge = view.findViewById(R.id.tvVerificationBadge)
        tvVerificationLevel = view.findViewById(R.id.tvVerificationLevel)
        btnUploadNC1        = view.findViewById(R.id.btnUploadNC1)
        btnUploadNC2        = view.findViewById(R.id.btnUploadNC2)
        btnUploadNC3        = view.findViewById(R.id.btnUploadNC3)

        btnUploadNC1.setOnClickListener { 
            pendingNCLevel = 1
            pickCertificate.launch("image/*")
        }
        btnUploadNC2.setOnClickListener { 
            pendingNCLevel = 2
            pickCertificate.launch("image/*")
        }
        btnUploadNC3.setOnClickListener { 
            pendingNCLevel = 3
            pickCertificate.launch("image/*")
        }

        ViewCompat.setOnApplyWindowInsetsListener(view) { _, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            layoutHeader.setPadding(
                layoutHeader.paddingLeft,
                systemBars.top + resources.getDimensionPixelSize(R.dimen.header_padding_top),
                layoutHeader.paddingRight,
                layoutHeader.paddingBottom
            )
            insets
        }

        // ✅ First load when fragment is created
        loadProfile()
        loadStats()
        loadReviews()
        setupLogout()
        setupSeeAll()
        setupFilters()

        return view
    }

    private fun setupFilters() {
        val filters = listOf(filterAll, filter5, filter4, filter3, filter2, filter1)
        val ratings = listOf(null, 5, 4, 3, 2, 1)

        filters.forEachIndexed { index, textView ->
            textView.setOnClickListener {
                currentRatingFilter = ratings[index]
                isShowingAllReviews = false
                tvSeeAllReviews.text = "See all"

                // Update UI selection
                filters.forEach { 
                    it.setBackgroundResource(R.drawable.bg_badge_white)
                    it.setTextColor(android.graphics.Color.parseColor("#64748B"))
                    it.setTypeface(null, android.graphics.Typeface.NORMAL)
                }

                textView.setBackgroundResource(R.drawable.bg_badge_blue)
                textView.setTextColor(android.graphics.Color.parseColor("#FFFFFF"))
                textView.setTypeface(null, android.graphics.Typeface.BOLD)

                loadReviews()
            }
        }
    }

    private fun setupSeeAll() {
        tvSeeAllReviews.setOnClickListener {
            isShowingAllReviews = !isShowingAllReviews
            tvSeeAllReviews.text = if (isShowingAllReviews) "Show less" else "See all"
            loadReviews()
        }
    }

    // ✅ Fires every time this fragment is shown via show() in add/hide/show pattern
    override fun onHiddenChanged(hidden: Boolean) {
        super.onHiddenChanged(hidden)
        if (!hidden) {
            loadProfile()
            loadStats()
            loadReviews()
        }
    }

    // --------------------------------------------------
    // PROFILE
    // --------------------------------------------------
    private fun loadProfile() {
        val uid = auth.currentUser?.uid ?: return
        firestore.collection("users")
            .document(uid)
            .get()
            .addOnSuccessListener { doc ->
                val user = doc.toObject(User::class.java) ?: return@addOnSuccessListener
                val name  = user.name
                val avg   = doc.getDouble("ratingAverage") ?: user.rating
                val count = doc.getLong("ratingCount") ?: user.totalRatings.toLong()
                
                tvWorkerName.text   = name
                tvWorkerRating.text = "%.1f (%d reviews)".format(avg, count)

                updateVerificationUI(user.verificationStatus)
            }
    }

    private fun updateVerificationUI(status: Int) {
        tvVerificationBadge.visibility = if (status > 0) View.VISIBLE else View.GONE
        tvVerificationBadge.text = when (status) {
            1 -> "NC1"
            2 -> "NC2"
            3 -> "NC3"
            else -> ""
        }

        tvVerificationLevel.text = when (status) {
            1 -> "Status: NC1 Verified"
            2 -> "Status: NC2 Verified"
            3 -> "Status: NC3 Verified"
            else -> "Status: Unverified"
        }

        // Optional: disable buttons for levels already achieved or skipped
        btnUploadNC1.isEnabled = status < 1
        btnUploadNC2.isEnabled = status < 2
        btnUploadNC3.isEnabled = status < 3
    }

    private fun uploadCertificate(uri: Uri, level: Int) {
        val uid = auth.currentUser?.uid ?: return
        Toast.makeText(requireContext(), "Uploading NC$level certificate...", Toast.LENGTH_SHORT).show()
        
        MediaManager.get().upload(uri)
            .option("folder", "worker_certificates")
            .callback(object : UploadCallback {
                override fun onStart(requestId: String?) {}
                override fun onProgress(requestId: String?, bytes: Long, totalBytes: Long) {}
                override fun onSuccess(requestId: String?, resultData: MutableMap<Any?, Any?>?) {
                    val imageUrl = resultData?.get("secure_url").toString()
                    saveCertificateUrl(uid, imageUrl, level)
                }
                override fun onError(requestId: String?, error: ErrorInfo?) {
                    Toast.makeText(requireContext(), "Upload failed: ${error?.description}", Toast.LENGTH_SHORT).show()
                }
                override fun onReschedule(requestId: String?, error: ErrorInfo?) {}
            }).dispatch()
    }

    private fun saveCertificateUrl(uid: String, url: String, level: Int) {
        val fieldName = when (level) {
            1 -> "nc1CertificateUrl"
            2 -> "nc2CertificateUrl"
            3 -> "nc3CertificateUrl"
            else -> return
        }

        firestore.collection("users").document(uid).get().addOnSuccessListener { doc ->
            val currentStatus = doc.getLong("verificationStatus")?.toInt() ?: 0
            val newStatus = maxOf(currentStatus, level)

            firestore.collection("users").document(uid).update(
                mapOf(
                    fieldName to url,
                    "verificationStatus" to newStatus
                )
            ).addOnSuccessListener {
                Toast.makeText(requireContext(), "NC$level Certificate Uploaded!", Toast.LENGTH_SHORT).show()
                loadProfile()
            }
        }
    }

    // --------------------------------------------------
    // STATS
    // --------------------------------------------------
    private fun loadStats() {
        val uid = auth.currentUser?.uid ?: return
        firestore.collection("jobs")
            .whereEqualTo("workerId", uid)
            .get()
            .addOnSuccessListener {
                tvAcceptedJobs.text = "${it.size()}"
            }
        firestore.collection("jobs")
            .whereEqualTo("workerId", uid)
            .whereEqualTo("status", "COMPLETED")
            .get()
            .addOnSuccessListener {
                tvCompletedJobs.text = "${it.size()}"
            }
    }

    // --------------------------------------------------
    // REVIEWS
    // --------------------------------------------------
    @SuppressLint("MissingInflatedId")
    private fun loadReviews() {
        val uid = auth.currentUser?.uid ?: return
        
        var query = firestore.collection("reviews")
            .whereEqualTo("workerId", uid)
        
        currentRatingFilter?.let {
            query = query.whereEqualTo("rating", it.toDouble())
        }

        query.get().addOnSuccessListener { snapshot ->
                reviewContainer.removeAllViews()
                if (snapshot.isEmpty) {
                    val empty = TextView(requireContext())
                    empty.text      = if (currentRatingFilter == null) "No reviews yet." else "No $currentRatingFilter star reviews yet."
                    empty.textSize  = 13f
                    empty.setTextColor(android.graphics.Color.parseColor("#94A3B8"))
                    reviewContainer.addView(empty)
                    tvSeeAllReviews.visibility = View.GONE
                    return@addOnSuccessListener
                }

                val allReviews = snapshot.documents
                val displayReviews = if (isShowingAllReviews) allReviews else allReviews.take(5)

                if (allReviews.size > 5) {
                    tvSeeAllReviews.visibility = View.VISIBLE
                } else {
                    tvSeeAllReviews.visibility = View.GONE
                }

                for (doc in displayReviews) {
                    val review = doc.toObject(Review::class.java) ?: continue
                    val isAnonymous = review.isAnonymous
                    val clientName = if (isAnonymous) "Anonymous User" else review.clientName
                    val comment    = review.comment
                    val rating     = review.rating
                    val card = layoutInflater.inflate(
                        R.layout.item_review_card,
                        reviewContainer,
                        false
                    )
                    card.findViewById<TextView>(R.id.tvReviewRating).text =
                        "⭐ %.1f".format(rating)
                    card.findViewById<TextView>(R.id.tvReviewClient).text =
                        clientName
                    card.findViewById<TextView>(R.id.tvReviewComment).text =
                        comment
                    reviewContainer.addView(card)
                }
            }
    }

    // --------------------------------------------------
    // LOGOUT
    // --------------------------------------------------
    private fun setupLogout() {
        btnLogout.setOnClickListener {
            showLogoutConfirmDialog()
        }
    }

    private fun showLogoutConfirmDialog() {
        val dialog = Dialog(requireContext())
        dialog.setContentView(R.layout.dialog_role_select)
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
        dialog.window?.setLayout(
            (resources.displayMetrics.widthPixels * 0.88).toInt(),
            android.view.ViewGroup.LayoutParams.WRAP_CONTENT
        )

        dialog.findViewById<ImageView>(R.id.dialogIcon).setImageResource(R.drawable.ic_nav_account)
        dialog.findViewById<TextView>(R.id.dialogTitle).text   = "Logout"
        dialog.findViewById<TextView>(R.id.dialogMessage).text = "Are you sure you want to log out?"

        dialog.findViewById<com.google.android.material.button.MaterialButton>(R.id.dialogBtnPositive)
            .setOnClickListener {
                dialog.dismiss()
                auth.signOut()
                Toast.makeText(requireContext(), "Logged out", Toast.LENGTH_SHORT).show()
                val intent = Intent(requireContext(), OnboardingActivity::class.java)
                intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                startActivity(intent)
            }

        dialog.findViewById<com.google.android.material.button.MaterialButton>(R.id.dialogBtnNegative)
            .setOnClickListener { dialog.dismiss() }

        dialog.show()
    }
}