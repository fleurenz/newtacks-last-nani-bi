package com.example.newtacks.worker

import android.annotation.SuppressLint
import android.app.Dialog
import android.content.Intent
import android.graphics.Color
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
import androidx.core.view.updatePadding
import androidx.fragment.app.Fragment
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
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
import com.google.android.material.appbar.AppBarLayout
import com.google.android.material.bottomsheet.BottomSheetDialog
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
    private lateinit var tvVerificationLevel: TextView
    
    private lateinit var menuLogout: View
    private lateinit var menuCertificates: View
    private lateinit var menuReviews: View
    private lateinit var swipeRefresh: SwipeRefreshLayout
    private lateinit var loadingOverlay: View
    private lateinit var menuEditProfile: View
    private lateinit var menuViewProfile: View
    private lateinit var menuPrivacy: View
    private lateinit var menuHelp: View
    
    private lateinit var layoutResume: View
    private lateinit var tvResumeStatus: TextView
    private lateinit var ivResumeIcon: ImageView
    private var currentResumeUrl: String? = null

    private val pickResume =
        registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            uri?.let { uploadResume(it) }
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
        menuReviews = view.findViewById(R.id.menuReviews)
        swipeRefresh = view.findViewById(R.id.swipeRefreshAccount)
        loadingOverlay = view.findViewById(R.id.loadingOverlay)
        menuEditProfile = view.findViewById(R.id.menuEditProfile)
        menuViewProfile = view.findViewById(R.id.menuViewProfile)
        menuPrivacy = view.findViewById(R.id.menuPrivacy)
        menuHelp = view.findViewById(R.id.menuHelp)
        
        layoutResume = view.findViewById(R.id.layoutResume)
        tvResumeStatus = view.findViewById(R.id.tvResumeStatus)
        ivResumeIcon = view.findViewById(R.id.ivResumeIcon)

        tvVerificationLevel = view.findViewById(R.id.tvVerificationLevel)

        // Robust Inset Handling: Use spacer for status bar
        val statusBarSpacer = view.findViewById<View>(R.id.statusBarSpacer)
        ViewCompat.setOnApplyWindowInsetsListener(view) { _, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            val params = statusBarSpacer.layoutParams
            params.height = systemBars.top
            statusBarSpacer.layoutParams = params
            insets
        }

        loadProfile()
        loadStats()
        setupLogout()
        setupCertificatesMenu()
        setupReviewsMenu()
        setupEditProfileMenu()
        setupViewProfileMenu()
        setupPrivacyMenu()
        setupHelpMenu()
        setupResumeAction()

        swipeRefresh.setOnRefreshListener {
            loadProfile()
            loadStats()
        }

        return view
    }

    override fun onHiddenChanged(hidden: Boolean) {
        super.onHiddenChanged(hidden)
        if (!hidden) {
            loadProfile()
            loadStats()
        }
    }

    private fun loadProfile() {
        val uid = auth.currentUser?.uid ?: return
        firestore.collection("users")
            .document(uid)
            .get()
            .addOnSuccessListener { doc ->
                val user = User.fromSnapshot(doc)?.copy(uid = doc.id) ?: return@addOnSuccessListener
                val name = user.name
                val avg = doc.getDouble("ratingAverage") ?: user.rating
                val count = doc.getLong("ratingCount") ?: user.totalRatings.toLong()

                tvWorkerName.text = name
                tvWorkerRating.text = "%.1f (%d reviews)".format(avg, count)
                
                currentResumeUrl = user.resumeUrl
                if (currentResumeUrl.isNullOrEmpty()) {
                    tvResumeStatus.text = "No resume submitted, submit now?"
                    tvResumeStatus.setTextColor(Color.parseColor("#004A99"))
                    ivResumeIcon.imageTintList = android.content.res.ColorStateList.valueOf(Color.parseColor("#64748B"))
                } else {
                    tvResumeStatus.text = "View My Resume"
                    tvResumeStatus.setTextColor(Color.parseColor("#16A34A"))
                    ivResumeIcon.imageTintList = android.content.res.ColorStateList.valueOf(Color.parseColor("#16A34A"))
                }

                if (user.profileImage.isNotEmpty()) {
                    ivWorkerProfile.load(user.profileImage) {
                        crossfade(true)
                        placeholder(R.drawable.ic_person_placeholder)
                        error(R.drawable.ic_person_placeholder)
                        transformations(CircleCropTransformation())
                    }
                }

                updateVerificationUI(user.verificationStatus)
                swipeRefresh.isRefreshing = false
            }
            .addOnFailureListener {
                swipeRefresh.isRefreshing = false
            }
    }

    private fun updateVerificationUI(status: Int) {
        tvVerificationLevel.text = when (status) {
            1 -> "Status: Tier 1 Verified"
            2 -> "Status: Tier 2 Verified"
            else -> "Status: Tier 0 Basic"
        }
    }

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

    private fun setupEditProfileMenu() {
        menuEditProfile.setOnClickListener {
            startActivity(Intent(requireContext(), WorkerEditProfileActivity::class.java))
        }
    }

    private fun setupPrivacyMenu() {
        menuPrivacy.setOnClickListener {
            startActivity(Intent(requireContext(), com.example.newtacks.utils.PrivacySecurityActivity::class.java))
        }
    }

    private fun setupViewProfileMenu() {
        menuViewProfile.setOnClickListener {
            val uid = auth.currentUser?.uid ?: return@setOnClickListener
            val intent = Intent(requireContext(), WorkerProfileActivity::class.java)
            intent.putExtra("WORKER_ID", uid)
            startActivity(intent)
        }
    }

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

    private fun setupHelpMenu() {
        menuHelp.setOnClickListener {
            startActivity(Intent(requireContext(), com.example.newtacks.utils.HelpSupportActivity::class.java))
        }
    }

    private fun setupCertificatesMenu() {
        menuCertificates.setOnClickListener {
            startActivity(Intent(requireContext(), WorkerVerificationActivity::class.java))
        }
    }

    private fun setupResumeAction() {
        layoutResume.setOnClickListener {
            if (currentResumeUrl.isNullOrEmpty()) {
                pickResume.launch(arrayOf("application/pdf", "application/vnd.openxmlformats-officedocument.wordprocessingml.document", "image/*"))
            } else {
                showResumeOptions()
            }
        }
    }

    private fun showResumeOptions() {
        val dialog = BottomSheetDialog(requireContext(), R.style.BottomSheetDialogTheme)
        val view = layoutInflater.inflate(R.layout.dialog_resume_options, null)
        dialog.setContentView(view)

        view.findViewById<View>(R.id.btnViewResume).setOnClickListener {
            dialog.dismiss()
            val url = currentResumeUrl ?: return@setOnClickListener
            val isImage = url.contains(".jpg", true) || url.contains(".png", true) || url.contains(".jpeg", true)
            
            if (isImage) {
                com.example.newtacks.utils.ImageUtils.showFullscreenImage(requireContext(), url)
            } else {
                val viewerUrl = "https://docs.google.com/viewer?url=$url"
                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(viewerUrl))
                startActivity(intent)
            }
        }

        view.findViewById<View>(R.id.btnUpdateResume).setOnClickListener {
            dialog.dismiss()
            pickResume.launch(arrayOf("application/pdf", "application/vnd.openxmlformats-officedocument.wordprocessingml.document", "image/*"))
        }

        dialog.show()
    }

    private fun uploadResume(uri: Uri) {
        val uid = auth.currentUser?.uid ?: return
        loadingOverlay.visibility = View.VISIBLE
        Toast.makeText(requireContext(), "Uploading resume...", Toast.LENGTH_SHORT).show()

        MediaManager.get().upload(uri)
            .option("folder", "worker_resumes")
            .callback(object : UploadCallback {
                override fun onStart(requestId: String?) {}
                override fun onProgress(requestId: String?, bytes: Long, totalBytes: Long) {}
                override fun onSuccess(requestId: String?, resultData: MutableMap<Any?, Any?>?) {
                    val url = resultData?.get("secure_url").toString()
                    firestore.collection("users").document(uid).update("resumeUrl", url)
                        .addOnSuccessListener {
                            loadingOverlay.visibility = View.GONE
                            Toast.makeText(requireContext(), "Resume uploaded successfully!", Toast.LENGTH_SHORT).show()
                            loadProfile()
                        }
                }
                override fun onError(requestId: String?, error: ErrorInfo?) {
                    loadingOverlay.visibility = View.GONE
                    Toast.makeText(requireContext(), "Upload failed", Toast.LENGTH_SHORT).show()
                }
                override fun onReschedule(requestId: String?, error: ErrorInfo?) {}
            }).dispatch()
    }

    private fun showLogoutConfirmDialog() {
        val dialog = Dialog(requireContext())
        dialog.setContentView(R.layout.dialog_role_select)
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
        dialog.window?.setLayout(
            (resources.displayMetrics.widthPixels * 0.88).toInt(),
            ViewGroup.LayoutParams.WRAP_CONTENT
        )

        dialog.findViewById<ImageView>(R.id.dialogIcon).setImageResource(R.drawable.ic_nav_account)
        dialog.findViewById<TextView>(R.id.dialogTitle).text = "Logout"
        dialog.findViewById<TextView>(R.id.dialogMessage).text = "Are you sure you want to log out?"

        dialog.findViewById<com.google.android.material.button.MaterialButton>(R.id.dialogBtnPositive)
            .setOnClickListener {
                dialog.dismiss()
                ChatRepository.getInstance(RetrofitClient.chatApiService).clearSession()
                requireContext().stopService(Intent(requireContext(), com.example.newtacks.utils.NotificationService::class.java))
                auth.signOut()
                val intent = Intent(requireContext(), OnboardingActivity::class.java)
                intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                startActivity(intent)
            }

        dialog.findViewById<com.google.android.material.button.MaterialButton>(R.id.dialogBtnNegative)
            .setOnClickListener { dialog.dismiss() }

        dialog.show()
    }
}