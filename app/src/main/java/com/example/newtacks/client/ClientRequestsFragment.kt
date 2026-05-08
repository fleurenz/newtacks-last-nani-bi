package com.example.newtacks.client

import android.app.Dialog
import android.os.Bundle
import android.view.*
import android.view.animation.AlphaAnimation
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.fragment.app.Fragment
import coil.load
import coil.transform.CircleCropTransformation
import com.example.newtacks.R
import com.example.newtacks.models.Job
import com.example.newtacks.models.Receipt
import com.example.newtacks.models.Review
import com.example.newtacks.models.User
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.*
import java.util.Locale

class ClientRequestsFragment : Fragment() {

    private val firestore = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()

    private var listener: ListenerRegistration? = null

    private lateinit var tvTitle: TextView
    private lateinit var tvDetails: TextView
    private lateinit var btnConfirm: Button
    private lateinit var btnCancelJob: Button
    private lateinit var btnReject: Button
    private lateinit var progressText: TextView
    private lateinit var progressBar: ProgressBar
    private lateinit var layoutContent: LinearLayout
    private lateinit var layoutEmptyState: LinearLayout
    private lateinit var layoutProgressLabels: LinearLayout
    private lateinit var layoutBottomButtons: LinearLayout
    private lateinit var layoutHeader: LinearLayout
    private lateinit var cardJobDetails: View
    private lateinit var cardWorkerInfo: View
    private lateinit var tvWorkerDetailName: TextView
    private lateinit var tvWorkerDetailPhone: TextView
    private lateinit var tvWorkerDetailRating: TextView
    private lateinit var tvWorkerDetailBadge: TextView

    private var currentJob: Job? = null
    private var currentJobId: String? = null
    private var lastCancelTime: Long = 0

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val view = inflater.inflate(R.layout.fragment_client_requests, container, false)

        tvTitle              = view.findViewById(R.id.tvRequestTitle)
        tvDetails            = view.findViewById(R.id.tvRequestDetails)
        btnConfirm           = view.findViewById(R.id.btnConfirm)
        btnCancelJob          = view.findViewById(R.id.btnCancelJob)
        btnReject            = view.findViewById(R.id.btnReject)
        progressText         = view.findViewById(R.id.tvProgress)
        progressBar          = view.findViewById(R.id.progressBar)
        layoutContent        = view.findViewById(R.id.layoutContent)
        layoutEmptyState     = view.findViewById(R.id.layoutEmptyState)
        layoutProgressLabels = view.findViewById(R.id.layoutProgressLabels)
        layoutBottomButtons  = view.findViewById(R.id.layoutBottomButtons)
        layoutHeader         = view.findViewById(R.id.layoutHeader)

        cardJobDetails       = view.findViewById(R.id.cardJobDetails)
        cardWorkerInfo       = view.findViewById(R.id.cardWorkerInfo)
        tvWorkerDetailName   = view.findViewById(R.id.tvWorkerDetailName)
        tvWorkerDetailPhone  = view.findViewById(R.id.tvWorkerDetailPhone)
        tvWorkerDetailRating = view.findViewById(R.id.tvWorkerDetailRating)
        tvWorkerDetailBadge  = view.findViewById(R.id.tvWorkerDetailBadge)

        // --------------------------------------------------
        // ✅ WINDOW INSETS — push header down below status bar
        // --------------------------------------------------
        ViewCompat.setOnApplyWindowInsetsListener(view) { _, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())

            // Add status bar height on top of existing paddingTop (36dp)
            layoutHeader.setPadding(
                layoutHeader.paddingLeft,
                systemBars.top + resources.getDimensionPixelSize(R.dimen.header_padding_top),
                layoutHeader.paddingRight,
                layoutHeader.paddingBottom
            )

            insets
        }

        listenForActiveJob()
        btnConfirm.setOnClickListener { confirmJob() }
        btnCancelJob.setOnClickListener { showCancelConfirmationDialog() }
        btnReject.setOnClickListener { rejectJob() }

        cardJobDetails.setOnClickListener {
            currentJob?.let { showJobDetailsDialog(it) }
        }

        cardWorkerInfo.setOnClickListener {
            currentJob?.workerId?.let { showWorkerDetailsDialog(it) }
        }

        return view
    }

    // --------------------------------------------------
    // 🔥 REALTIME ACTIVE JOB LISTENER
    // --------------------------------------------------
    private fun listenForActiveJob() {
        val clientId = auth.currentUser?.uid ?: return
        listener = firestore.collection("jobs")
            .whereEqualTo("clientId", clientId)
            .whereIn("status", listOf("AVAILABLE", "IN_PROGRESS", "PENDING_VERIFICATION"))
            .limit(1)
            .addSnapshotListener { snapshots, error ->
                if (error != null) return@addSnapshotListener
                val job = snapshots?.documents?.firstOrNull()?.toObject(Job::class.java)
                if (job == null) {
                    showEmptyState()
                } else {
                    showActiveJob(job)
                    animateUpdate()
                }
            }
    }

    // --------------------------------------------------
    // 🔥 UI STATE: ACTIVE JOB
    // --------------------------------------------------
    private fun showActiveJob(job: Job) {
        currentJob = job
        layoutContent.visibility        = View.VISIBLE
        layoutEmptyState.visibility     = View.GONE
        progressText.visibility         = View.VISIBLE
        progressBar.visibility          = View.VISIBLE
        layoutProgressLabels.visibility = View.VISIBLE
        layoutBottomButtons.visibility  =
            if (job.status == "PENDING_VERIFICATION" || job.status == "AVAILABLE") View.VISIBLE else View.GONE

        currentJobId   = job.jobId
        tvTitle.text   = job.jobTitle
        tvDetails.text = """
            Service: ${job.serviceCategory}
            Worker: ${job.workerName ?: "Waiting for worker..."}
            Price: ₱${job.offeredAmount}
        """.trimIndent()

        when (job.status) {
            "AVAILABLE" -> {
                progressText.text = "Waiting for worker..."
                progressText.setTextColor(android.graphics.Color.parseColor("#FFFFFF"))
                progressText.setBackgroundResource(R.drawable.bg_badge_blue)
                btnCancelJob.visibility = View.VISIBLE
                btnConfirm.visibility = View.GONE
                btnReject.visibility = View.GONE
            }
            "IN_PROGRESS" -> {
                progressText.text = "Worker is working"
                progressText.setTextColor(android.graphics.Color.parseColor("#D97706"))
                progressText.setBackgroundResource(R.drawable.bg_badge_yellow)
                btnCancelJob.visibility = View.GONE
                btnConfirm.visibility = View.GONE
                btnReject.visibility = View.GONE
            }
            "PENDING_VERIFICATION" -> {
                progressText.text = "Ready for confirmation"
                progressText.setTextColor(android.graphics.Color.parseColor("#16A34A"))
                progressText.setBackgroundResource(R.drawable.bg_badge_green)
                btnCancelJob.visibility = View.GONE
                btnConfirm.visibility = View.VISIBLE
                btnReject.visibility = View.VISIBLE
            }
            else -> {
                progressText.text = "Active"
                progressText.setTextColor(android.graphics.Color.parseColor("#FFFFFF"))
                progressText.setBackgroundResource(R.drawable.bg_badge_blue)
                btnCancelJob.visibility = View.GONE
                btnConfirm.visibility = View.GONE
                btnReject.visibility = View.GONE
            }
        }

        progressBar.progress = when (job.status) {
            "AVAILABLE"            -> 25
            "IN_PROGRESS"          -> 60
            "PENDING_VERIFICATION" -> 100
            else                   -> 0
        }

        if (job.workerId != null) {
            fetchWorkerDetails(job.workerId)
        } else {
            cardWorkerInfo.visibility = View.GONE
        }
    }

    private fun fetchWorkerDetails(workerId: String) {
        firestore.collection("users").document(workerId).get()
            .addOnSuccessListener { doc ->
                val name = doc.getString("name") ?: "Worker"
                val phone = doc.getString("phone") ?: "N/A"
                val rating = doc.getDouble("ratingAverage") ?: 0.0
                val count = doc.getLong("ratingCount") ?: 0
                val status = doc.getLong("verificationStatus")?.toInt() ?: 0

                tvWorkerDetailName.text = name
                tvWorkerDetailPhone.text = "Phone: $phone"
                tvWorkerDetailRating.text = String.format(Locale.getDefault(), "%.1f (%d reviews)", rating, count)
                
                if (status > 0) {
                    tvWorkerDetailBadge.visibility = View.VISIBLE
                    tvWorkerDetailBadge.text = "NC$status"
                } else {
                    tvWorkerDetailBadge.visibility = View.GONE
                }
                
                cardWorkerInfo.visibility = View.VISIBLE
            }
    }

    // --------------------------------------------------
    // 🔥 EMPTY STATE
    // --------------------------------------------------
    private fun showEmptyState() {
        currentJob                      = null
        currentJobId                    = null
        layoutContent.visibility        = View.GONE
        layoutEmptyState.visibility     = View.VISIBLE
        layoutBottomButtons.visibility  = View.GONE
        cardWorkerInfo.visibility       = View.GONE
        progressText.visibility         = View.GONE
        progressBar.visibility          = View.GONE
        layoutProgressLabels.visibility = View.GONE
        tvTitle.text                    = ""
        btnConfirm.visibility           = View.GONE
        btnCancelJob.visibility         = View.GONE
        btnReject.visibility            = View.GONE
    }

    // --------------------------------------------------
    // 🔥 DIALOGS: JOB & WORKER DETAILS
    // --------------------------------------------------

    private fun showJobDetailsDialog(job: Job) {
        val dialogView = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_job_details_preview, null)
        val tvTitle = dialogView.findViewById<TextView>(R.id.tvDialogJobTitle)
        val tvCategory = dialogView.findViewById<TextView>(R.id.tvDialogCategory)
        val tvSchedule = dialogView.findViewById<TextView>(R.id.tvDialogSchedule)
        val tvPrice = dialogView.findViewById<TextView>(R.id.tvDialogPrice)
        val tvDescription = dialogView.findViewById<TextView>(R.id.tvDialogDescription)
        val layoutImages = dialogView.findViewById<LinearLayout>(R.id.layoutDialogImages)

        tvTitle.text = job.jobTitle
        tvCategory.text = job.serviceCategory
        tvSchedule.text = "${job.scheduledDate} at ${job.scheduledTime}"
        tvPrice.text = "₱${job.offeredAmount}"
        tvDescription.text = job.description

        if (job.jobImages.isEmpty()) {
            dialogView.findViewById<View>(R.id.tvNoImages).visibility = View.VISIBLE
        } else {
            job.jobImages.forEach { url ->
                val imageView = ImageView(requireContext())
                val params = LinearLayout.LayoutParams(
                    resources.getDimensionPixelSize(R.dimen.preview_image_size),
                    resources.getDimensionPixelSize(R.dimen.preview_image_size)
                )
                params.setMargins(0, 0, 12, 0)
                imageView.layoutParams = params
                imageView.scaleType = ImageView.ScaleType.CENTER_CROP
                imageView.load(url) {
                    crossfade(true)
                    placeholder(R.drawable.bg_image_placeholder)
                }
                layoutImages.addView(imageView)
            }
        }

        AlertDialog.Builder(requireContext(), R.style.CustomAlertDialog)
            .setView(dialogView)
            .setPositiveButton("Close", null)
            .show()
    }

    private fun showWorkerDetailsDialog(workerId: String) {
        firestore.collection("users").document(workerId).get()
            .addOnSuccessListener { doc ->
                val worker = doc.toObject(User::class.java) ?: return@addOnSuccessListener
                
                val dialogView = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_worker_details_preview, null)
                val ivProfile = dialogView.findViewById<ImageView>(R.id.ivWorkerProfile)
                val tvName = dialogView.findViewById<TextView>(R.id.tvWorkerName)
                val tvBadge = dialogView.findViewById<TextView>(R.id.tvWorkerBadge)
                val tvPhone = dialogView.findViewById<TextView>(R.id.tvWorkerPhone)
                val tvRating = dialogView.findViewById<TextView>(R.id.tvWorkerRating)
                val tvExperience = dialogView.findViewById<TextView>(R.id.tvWorkerExperience)
                val tvCategories = dialogView.findViewById<TextView>(R.id.tvWorkerCategories)

                tvName.text = worker.name
                tvPhone.text = worker.phone
                
                if (worker.verificationStatus > 0) {
                    tvBadge.visibility = View.VISIBLE
                    tvBadge.text = "NC${worker.verificationStatus}"
                } else {
                    tvBadge.visibility = View.GONE
                }
                
                // Using names from Firestore transaction logic if they differ from model
                val ratingAvg = doc.getDouble("ratingAverage") ?: worker.rating
                val ratingCnt = doc.getLong("ratingCount") ?: worker.totalRatings.toLong()
                
                tvRating.text = String.format(Locale.getDefault(), "%.1f (%d reviews)", ratingAvg, ratingCnt)
                tvExperience.text = "${worker.serviceExperience ?: 0} years experience"
                tvCategories.text = worker.serviceCategories?.joinToString(", ") ?: "N/A"

                ivProfile.load(worker.profileImage) {
                    crossfade(true)
                    placeholder(R.drawable.ic_user_placeholder)
                    transformations(CircleCropTransformation())
                }

                AlertDialog.Builder(requireContext(), R.style.CustomAlertDialog)
                    .setView(dialogView)
                    .setPositiveButton("Close", null)
                    .show()
            }
    }

    // --------------------------------------------------
    // 🔥 ANIMATION
    // --------------------------------------------------
    private fun animateUpdate() {
        val anim = AlphaAnimation(0.4f, 1.0f)
        anim.duration = 300
        view?.startAnimation(anim)
    }

    // --------------------------------------------------
    // 🔥 CANCEL JOB
    // --------------------------------------------------
    private fun showCancelConfirmationDialog() {
        val dialog = Dialog(requireContext())
        dialog.setContentView(R.layout.dialog_role_select)
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
        dialog.window?.setLayout(
            (resources.displayMetrics.widthPixels * 0.88).toInt(),
            android.view.ViewGroup.LayoutParams.WRAP_CONTENT
        )

        val icon = dialog.findViewById<ImageView>(R.id.dialogIcon)
        val title = dialog.findViewById<TextView>(R.id.dialogTitle)
        val message = dialog.findViewById<TextView>(R.id.dialogMessage)
        val btnPositive = dialog.findViewById<com.google.android.material.button.MaterialButton>(R.id.dialogBtnPositive)
        val btnNegative = dialog.findViewById<com.google.android.material.button.MaterialButton>(R.id.dialogBtnNegative)

        icon.setImageResource(R.drawable.ic_close)
        title.text = "Cancel Job Request"
        message.text = "Are you sure you want to cancel this request? This action cannot be undone."
        btnPositive.text = "Yes, Cancel"
        btnNegative.text = "No"

        btnPositive.setOnClickListener {
            dialog.dismiss()
            cancelJob()
        }
        btnNegative.setOnClickListener {
            dialog.dismiss()
        }

        dialog.show()
    }

    private fun cancelJob() {
        val jobId = currentJobId ?: return
        firestore.collection("jobs")
            .document(jobId)
            .delete()
            .addOnSuccessListener {
                Toast.makeText(requireContext(), "Job request cancelled and deleted", Toast.LENGTH_SHORT).show()
                showEmptyState()
            }
            .addOnFailureListener { e ->
                Toast.makeText(requireContext(), "Error: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }

    // --------------------------------------------------
    // 🔥 CONFIRM JOB
    // --------------------------------------------------
    private fun confirmJob() {
        val jobId = currentJobId ?: return
        firestore.collection("jobs")
            .document(jobId)
            .update(
                mapOf(
                    "status"      to "COMPLETED",
                    "completedAt" to System.currentTimeMillis()
                )
            )
            .addOnSuccessListener {
                Toast.makeText(requireContext(), "Job Completed", Toast.LENGTH_SHORT).show()
                fetchJobAndGenerateReceipt(jobId)
            }
    }

    // --------------------------------------------------
    // 🔥 FETCH JOB
    // --------------------------------------------------
    private fun fetchJobAndGenerateReceipt(jobId: String) {
        firestore.collection("jobs")
            .document(jobId)
            .get()
            .addOnSuccessListener { doc ->
                val job = doc.toObject(Job::class.java)
                if (job != null) generateReceipt(job)
            }
    }

    // --------------------------------------------------
    // 🔥 RECEIPT
    // --------------------------------------------------
    private fun generateReceipt(job: Job) {
        val workerId  = job.workerId ?: return
        val receiptId = firestore.collection("receipts").document().id
        val receipt   = Receipt(
            receiptId       = receiptId,
            jobId           = job.jobId ?: "",
            clientId        = job.clientId,
            workerId        = workerId,
            clientName      = job.clientName,
            workerName      = job.workerName ?: "",
            jobTitle        = job.jobTitle,
            serviceCategory = job.serviceCategory,
            amount          = job.offeredAmount,
            createdAt       = job.createdAt,
            completedAt     = job.completedAt ?: System.currentTimeMillis()
        )
        firestore.collection("receipts")
            .document(receiptId)
            .set(receipt)
            .addOnSuccessListener {
                sendVerificationNotification(job.clientId)
                showReviewDialog(job)
            }
    }

    // --------------------------------------------------
    // 🔥 NOTIFICATION
    // --------------------------------------------------
    private fun sendVerificationNotification(clientId: String) {
        firestore.collection("notifications")
            .add(
                mapOf(
                    "to"      to clientId,
                    "title"   to "Job Ready for Verification",
                    "message" to "Your worker has completed the job."
                )
            )
    }

    // --------------------------------------------------
    // 🔥 REJECT / COOLDOWN
    // --------------------------------------------------
    private fun rejectJob() {
        val now = System.currentTimeMillis()
        if (now - lastCancelTime < 10_000) {
            Toast.makeText(requireContext(), "Please wait before cancelling again", Toast.LENGTH_SHORT).show()
            return
        }
        lastCancelTime = now
        val jobId = currentJobId ?: return
        firestore.collection("jobs")
            .document(jobId)
            .update("status", "IN_PROGRESS")
            .addOnSuccessListener {
                Toast.makeText(requireContext(), "Returned to worker", Toast.LENGTH_SHORT).show()
            }
    }

    // --------------------------------------------------
    // 🔥 REVIEW DIALOG
    // --------------------------------------------------
    private fun showReviewDialog(job: Job) {
        val dialogView = layoutInflater.inflate(R.layout.dialog_review, null)
        val ratingBar  = dialogView.findViewById<RatingBar>(R.id.ratingBar)
        val etComment  = dialogView.findViewById<EditText>(R.id.etComment)
        val cbAnonymous = dialogView.findViewById<CheckBox>(R.id.cbAnonymous)

        AlertDialog.Builder(requireContext(), R.style.CustomAlertDialog)
            .setView(dialogView)
            .setPositiveButton("Submit") { _, _ ->
                val review = Review(
                    reviewId = firestore.collection("reviews").document().id,
                    jobId    = job.jobId ?: "",
                    clientId = job.clientId,
                    clientName = job.clientName,
                    workerId = job.workerId ?: "",
                    rating   = ratingBar.rating,
                    comment  = etComment.text.toString(),
                    isAnonymous = cbAnonymous.isChecked
                )
                saveReview(review)
            }
            .setNegativeButton("Skip", null)
            .show()
    }

    // --------------------------------------------------
    // 🔥 SAVE REVIEW
    // --------------------------------------------------
    private fun saveReview(review: Review) {
        firestore.collection("reviews")
            .document(review.reviewId)
            .set(review)
            .addOnSuccessListener { updateWorkerRating(review) }
    }

    // --------------------------------------------------
    // 🔥 UPDATE WORKER RATING
    // --------------------------------------------------
    private fun updateWorkerRating(review: Review) {
        val workerRef = firestore.collection("users").document(review.workerId)
        firestore.runTransaction { transaction ->
            val snapshot   = transaction.get(workerRef)
            val currentAvg = snapshot.getDouble("ratingAverage") ?: 0.0
            val count      = snapshot.getLong("ratingCount") ?: 0
            val newCount   = count + 1
            val newAvg     = ((currentAvg * count) + review.rating) / newCount
            transaction.update(
                workerRef,
                mapOf(
                    "ratingAverage" to newAvg,
                    "ratingCount"   to newCount
                )
            )
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        listener?.remove()
    }
}