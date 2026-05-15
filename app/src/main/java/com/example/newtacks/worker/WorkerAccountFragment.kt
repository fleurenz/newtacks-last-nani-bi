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
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.fragment.app.Fragment
import coil.load
import coil.transform.CircleCropTransformation
import com.cloudinary.android.MediaManager
import com.cloudinary.android.callback.ErrorInfo
import com.cloudinary.android.callback.UploadCallback
import com.example.newtacks.R
import com.example.newtacks.authentication.OnboardingActivity
import com.example.newtacks.chatbot.data.remote.RetrofitClient
import com.example.newtacks.chatbot.data.repository.ChatRepository
import com.example.newtacks.models.Review
import com.example.newtacks.models.User
import com.example.newtacks.worker.account.WorkerReviewsActivity
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

class WorkerAccountFragment : Fragment() {

    private val firestore = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()
    private lateinit var tvWorkerName: TextView
    private lateinit var tvWorkerRating: TextView
    private lateinit var tvAcceptedJobs: TextView
    private lateinit var tvCompletedJobs: TextView
    private lateinit var ivWorkerProfile: ImageView
    private lateinit var layoutHeader: LinearLayout
    private lateinit var tvVerificationBadge: TextView
    private lateinit var tvVerificationLevel: TextView
    private lateinit var btnUploadNC1: Button
    private lateinit var btnUploadNC2: Button
    private lateinit var btnUploadNC3: Button
    private var isShowingAllReviews = false
    private var currentRatingFilter: Int? = null
    private var pendingNCLevel: Int = 0
    private lateinit var menuLogout: LinearLayout
    private lateinit var menuCertificates: LinearLayout
    private lateinit var layoutNCButtons: LinearLayout
    private lateinit var menuReviews: LinearLayout

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

        tvWorkerName = view.findViewById(R.id.tvWorkerName)
        tvWorkerRating = view.findViewById(R.id.tvWorkerRating)
        tvAcceptedJobs = view.findViewById(R.id.tvAcceptedJobs)
        tvCompletedJobs = view.findViewById(R.id.tvCompletedJobs)
        ivWorkerProfile = view.findViewById(R.id.ivWorkerProfile)
        menuLogout = view.findViewById(R.id.menuLogout)
        menuCertificates = view.findViewById(R.id.menuCertificates)
        layoutNCButtons = view.findViewById(R.id.layoutNCButtons)
        menuReviews = view.findViewById(R.id.menuReviews)
        layoutHeader = view.findViewById(R.id.layoutHeader)

        tvVerificationBadge = view.findViewById(R.id.tvVerificationBadge)
        tvVerificationLevel = view.findViewById(R.id.tvVerificationLevel)
        btnUploadNC1 = view.findViewById(R.id.btnUploadNC1)
        btnUploadNC2 = view.findViewById(R.id.btnUploadNC2)
        btnUploadNC3 = view.findViewById(R.id.btnUploadNC3)

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
        setupLogout()
        setupCertificatesMenu()
        setupReviewsMenu()

        return view
    }

    // ✅ Fires every time this fragment is shown via show() in add/hide/show pattern
    override fun onHiddenChanged(hidden: Boolean) {
        super.onHiddenChanged(hidden)
        if (!hidden) {
            loadProfile()
            loadStats()
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
                val name = user.name
                val avg = doc.getDouble("ratingAverage") ?: user.rating
                val count = doc.getLong("ratingCount") ?: user.totalRatings.toLong()

                tvWorkerName.text = name
                tvWorkerRating.text = "%.1f (%d reviews)".format(avg, count)

                if (user.profileImage.isNotEmpty()) {
                    ivWorkerProfile.load(user.profileImage) {
                        crossfade(true)
                        placeholder(R.drawable.ic_person_placeholder)
                        error(R.drawable.ic_person_placeholder)
                        transformations(CircleCropTransformation())
                    }
                }

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
        Toast.makeText(requireContext(), "Uploading NC$level certificate...", Toast.LENGTH_SHORT)
            .show()

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
                    Toast.makeText(
                        requireContext(),
                        "Upload failed: ${error?.description}",
                        Toast.LENGTH_SHORT
                    ).show()
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
                Toast.makeText(
                    requireContext(),
                    "NC$level Certificate Uploaded!",
                    Toast.LENGTH_SHORT
                ).show()
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
    // LOGOUT
    // --------------------------------------------------
    private fun setupReviewsMenu() {
        menuReviews.setOnClickListener {
            startActivity(Intent(requireContext(), WorkerReviewsActivity::class.java))
        }
    }

    private fun setupLogout() {
        menuLogout.setOnClickListener {
            showLogoutConfirmDialog()
        }
    }

    private fun setupCertificatesMenu() {
        menuCertificates.setOnClickListener {
            layoutNCButtons.visibility =
                if (layoutNCButtons.visibility == View.GONE) View.VISIBLE else View.GONE
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
        dialog.findViewById<TextView>(R.id.dialogTitle).text = "Logout"
        dialog.findViewById<TextView>(R.id.dialogMessage).text = "Are you sure you want to log out?"

        dialog.findViewById<com.google.android.material.button.MaterialButton>(R.id.dialogBtnPositive)
            .setOnClickListener {
                dialog.dismiss()
                // Clear Tey's memory on logout
                ChatRepository.getInstance(RetrofitClient.chatApiService).clearSession()
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