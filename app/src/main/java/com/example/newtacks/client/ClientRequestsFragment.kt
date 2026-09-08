package com.example.newtacks.client

import android.app.Dialog
import android.os.Bundle
import android.view.*
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import androidx.fragment.app.Fragment
import coil.load
import coil.transform.CircleCropTransformation
import com.example.newtacks.R
import com.example.newtacks.models.Job
import com.example.newtacks.models.Receipt
import com.example.newtacks.models.Review
import com.example.newtacks.models.User
import com.example.newtacks.utils.DistanceUtils
import com.example.newtacks.utils.ImageUtils
import com.example.newtacks.utils.RouteApiService
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.*
import java.util.Locale

class ClientRequestsFragment : Fragment() {

    private val firestore = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private val routeService: RouteApiService by lazy {
        retrofit2.Retrofit.Builder()
            .baseUrl("https://router.project-osrm.org/")
            .addConverterFactory(retrofit2.converter.gson.GsonConverterFactory.create())
            .build()
            .create(RouteApiService::class.java)
    }

    private var listener: ListenerRegistration? = null
    private var workerLocationListener: ListenerRegistration? = null

    private lateinit var tvHeaderTitle: TextView
    private lateinit var layoutDistanceHeader: LinearLayout
    private lateinit var tvDistanceBigValue: TextView
    private lateinit var tvDistanceBigUnit: TextView

    private lateinit var tvTitle: TextView
    private lateinit var tvProgressStatus: TextView
    private lateinit var layoutStandardProgress: LinearLayout
    private lateinit var progressTrackActive: View
    private lateinit var step1: ImageView
    private lateinit var step2: ImageView
    private lateinit var step3: ImageView
    private lateinit var step4: ImageView
    private lateinit var step5: ImageView
    
    private lateinit var layoutSimpleTimeline: LinearLayout
    private lateinit var ivWorkerPointer: ImageView
    
    private lateinit var layoutWorker: LinearLayout
    private lateinit var ivWorkerProfile: ImageView
    private lateinit var tvWorkerName: TextView
    private lateinit var tvWorkerDistance: TextView
    private lateinit var btnViewProfile: Button
    private lateinit var btnMessageWorker: Button
    
    private lateinit var tvDetailService: TextView
    private lateinit var tvDetailAddress: TextView
    private lateinit var tvDetailTime: TextView
    private lateinit var tvDetailDate: TextView
    private lateinit var btnViewImages: TextView
    private lateinit var tvDetailRate: TextView
    private lateinit var tvDetailDescription: TextView

    private lateinit var btnConfirm: Button
    private lateinit var btnReject: Button
    private lateinit var btnMoreOptions: ImageButton
    
    private lateinit var layoutContent: LinearLayout
    private lateinit var layoutEmptyState: LinearLayout
    private lateinit var layoutBottomButtons: LinearLayout
    private lateinit var layoutHeader: View
    private lateinit var loadingOverlay: View
    private lateinit var tvLoadingMessage: TextView

    private var currentJob: Job? = null
    private var currentJobId: String? = null
    private var lastCancelTime: Long = 0

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val view = inflater.inflate(R.layout.fragment_client_requests, container, false)
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(requireActivity())

        // General UI
        tvHeaderTitle       = view.findViewById(R.id.tvHeaderTitle)
        layoutDistanceHeader = view.findViewById(R.id.layoutDistanceHeader)
        tvDistanceBigValue   = view.findViewById(R.id.tvDistanceBigValue)
        tvDistanceBigUnit    = view.findViewById(R.id.tvDistanceBigUnit)

        tvTitle           = view.findViewById(R.id.tvRequestTitle)
        tvProgressStatus = view.findViewById(R.id.tvProgressStatus)
        layoutStandardProgress = view.findViewById(R.id.layoutStandardProgress)
        progressTrackActive = view.findViewById(R.id.progressTrackActive)
        step1 = view.findViewById(R.id.step1)
        step2 = view.findViewById(R.id.step2)
        step3 = view.findViewById(R.id.step3)
        step4 = view.findViewById(R.id.step4)
        step5 = view.findViewById(R.id.step5)

        layoutSimpleTimeline = view.findViewById(R.id.layoutSimpleTimeline)
        ivWorkerPointer      = view.findViewById(R.id.ivWorkerPointer)

        layoutWorker     = view.findViewById(R.id.layoutWorker)
        ivWorkerProfile  = view.findViewById(R.id.ivWorkerProfile)
        tvWorkerName     = view.findViewById(R.id.tvWorkerName)
        tvWorkerDistance = view.findViewById(R.id.tvWorkerDistance)
        btnViewProfile   = view.findViewById(R.id.btnViewProfile)
        btnMessageWorker = view.findViewById(R.id.btnMessageWorkerSmall)

        tvDetailService     = view.findViewById(R.id.tvDetailService)
        tvDetailAddress     = view.findViewById(R.id.tvDetailAddress)
        tvDetailTime        = view.findViewById(R.id.tvDetailTime)
        tvDetailDate        = view.findViewById(R.id.tvDetailDate)
        btnViewImages       = view.findViewById(R.id.btnViewImages)
        tvDetailRate        = view.findViewById(R.id.tvDetailRate)
        tvDetailDescription = view.findViewById(R.id.tvDetailDescription)

        btnConfirm           = view.findViewById(R.id.btnConfirm)
        btnReject            = view.findViewById(R.id.btnReject)
        btnMoreOptions       = view.findViewById(R.id.btnMoreOptions)
        
        layoutContent        = view.findViewById(R.id.layoutContent)
        layoutEmptyState     = view.findViewById(R.id.layoutEmptyState)
        layoutBottomButtons  = view.findViewById(R.id.layoutBottomButtons)
        layoutHeader         = view.findViewById(R.id.layoutHeader)

        loadingOverlay       = view.findViewById(R.id.loadingOverlay)
        tvLoadingMessage     = view.findViewById(R.id.tvLoadingMessage)

        // Window Insets
        ViewCompat.setOnApplyWindowInsetsListener(layoutHeader) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.updatePadding(top = systemBars.top + (resources.displayMetrics.density * 8).toInt())
            insets
        }

        listenForActiveJob()
        
        btnConfirm.setOnClickListener { confirmJob() }
        btnReject.setOnClickListener { rejectJob() }
        btnMoreOptions.setOnClickListener { showPopupMenu(it) }
        btnMessageWorker.setOnClickListener { openChat() }
        btnViewProfile.setOnClickListener { 
            currentJob?.workerId?.let { workerId ->
                val intent = android.content.Intent(requireContext(), com.example.newtacks.worker.WorkerProfileActivity::class.java)
                intent.putExtra("WORKER_ID", workerId)
                startActivity(intent)
            }
        }
        btnViewImages.setOnClickListener {
            currentJob?.let { job ->
                if (job.jobImages.isNotEmpty()) {
                    ImageUtils.showFullscreenImage(requireContext(), job.jobImages[0])
                } else {
                    Toast.makeText(requireContext(), "No images provided", Toast.LENGTH_SHORT).show()
                }
            }
        }

        return view
    }

    private fun listenForActiveJob() {
        val clientId = auth.currentUser?.uid ?: return
        listener = firestore.collection("jobs")
            .whereEqualTo("clientId", clientId)
            .addSnapshotListener { snapshots, error ->
                if (error != null) return@addSnapshotListener
                val activeStatuses = listOf("AVAILABLE", "IN_PROGRESS", "HEADING_TO_CLIENT", "ARRIVED", "PENDING_VERIFICATION", "REJECTED_BY_CLIENT")
                val job = snapshots?.documents
                    ?.mapNotNull { 
                        try { it.toObject(Job::class.java) } catch (e: Exception) { null }
                    }
                    ?.filter { it.status in activeStatuses }
                    ?.maxByOrNull { it.createdAt }

                if (job == null) showEmptyState() else showActiveJob(job)
            }
    }

    private fun showActiveJob(job: Job) {
        currentJob = job
        currentJobId = job.jobId
        
        if (job.status != "HEADING_TO_CLIENT") {
            workerLocationListener?.remove()
            workerLocationListener = null
        }

        layoutContent.visibility = View.VISIBLE
        layoutEmptyState.visibility = View.GONE
        
        tvTitle.text = job.jobTitle
        
        tvDetailService.text = job.serviceCategory
        tvDetailAddress.text = job.clientAddress
        tvDetailTime.text = job.scheduledTime
        tvDetailDate.text = job.scheduledDate
        tvDetailRate.text = "₱${job.offeredAmount}"
        tvDetailDescription.text = job.description

        updateProgressUI(job.status)

        // Toggle Header based on status
        if (job.status == "HEADING_TO_CLIENT") {
            tvHeaderTitle.visibility = View.GONE
            layoutDistanceHeader.visibility = View.VISIBLE
        } else {
            tvHeaderTitle.visibility = View.VISIBLE
            layoutDistanceHeader.visibility = View.GONE
        }

        if (job.workerId != null) {
            layoutWorker.visibility = View.VISIBLE
            fetchWorkerDetails(job.workerId)
        } else {
            layoutWorker.visibility = View.GONE
        }

        layoutBottomButtons.visibility = if (job.status == "PENDING_VERIFICATION") View.VISIBLE else View.GONE
        
        if (job.status == "HEADING_TO_CLIENT") {
            startTrackingWorkerDistance(job)
        }
    }

    private fun updateProgressUI(status: String) {
        if (status == "HEADING_TO_CLIENT") {
            layoutStandardProgress.visibility = View.GONE
            layoutSimpleTimeline.visibility = View.VISIBLE
            return
        }

        layoutStandardProgress.visibility = View.VISIBLE
        layoutSimpleTimeline.visibility = View.GONE

        val steps = listOf(step1, step2, step3, step4, step5)
        steps.forEach { it.setBackgroundResource(R.drawable.bg_step_circle_inactive) }

        var activeStepsCount = 1
        when (status) {
            "AVAILABLE" -> { activeStepsCount = 1; tvProgressStatus.text = "Searching..." }
            "IN_PROGRESS" -> { activeStepsCount = 2; tvProgressStatus.text = "Waiting..." }
            "ARRIVED" -> { activeStepsCount = 4; tvProgressStatus.text = "Worker is working" }
            "PENDING_VERIFICATION" -> { activeStepsCount = 5; tvProgressStatus.text = "Confirm & Review" }
            "REJECTED_BY_CLIENT" -> { activeStepsCount = 4; tvProgressStatus.text = "Waiting for worker to respond" }
        }

        for (i in 0 until activeStepsCount) {
            steps[i].setBackgroundResource(R.drawable.bg_step_circle_active)
        }

        view?.post {
            val totalWidth = step5.left - step1.left
            val progressWidth = if (activeStepsCount > 1) {
                (totalWidth / (steps.size - 1)) * (activeStepsCount - 1)
            } else 0
            
            val params = progressTrackActive.layoutParams
            params.width = progressWidth
            progressTrackActive.layoutParams = params
            
            val latestStep = steps[activeStepsCount - 1]
            tvProgressStatus.translationX = latestStep.x + (latestStep.width / 2) - (tvProgressStatus.width / 2)
        }
    }

    private fun startTrackingWorkerDistance(job: Job) {
        val workerId = job.workerId ?: return
        if (workerLocationListener != null) return

        workerLocationListener = firestore.collection("users").document(workerId)
            .addSnapshotListener { snapshot, _ ->
                if (snapshot != null && snapshot.exists() && isAdded) {
                    val wLat = snapshot.getDouble("latitude")
                    val wLng = snapshot.getDouble("longitude")
                    
                    if (wLat != null && wLng != null) {
                        fetchRoadDistance(wLat, wLng, job.latitude, job.longitude)
                    }
                }
            }
    }

    private fun fetchRoadDistance(wLat: Double, wLng: Double, jobLat: Double, jobLng: Double) {
        val coords = "$wLng,$wLat;$jobLng,$jobLat"
        routeService.getRoute(coords).enqueue(object : retrofit2.Callback<com.example.newtacks.utils.OsrmResponse> {
            override fun onResponse(call: retrofit2.Call<com.example.newtacks.utils.OsrmResponse>, response: retrofit2.Response<com.example.newtacks.utils.OsrmResponse>) {
                if (response.isSuccessful && isAdded) {
                    val route = response.body()?.routes?.firstOrNull() ?: return
                    val distanceMeters = route.distance.toFloat()
                    val distanceStr = DistanceUtils.formatDistance(distanceMeters)
                    
                    tvWorkerDistance.text = "$distanceStr away"

                    // Update big distance header
                    if (distanceMeters < 1000) {
                        tvDistanceBigValue.text = distanceMeters.toInt().toString()
                        tvDistanceBigUnit.text = "m"
                    } else {
                        tvDistanceBigValue.text = String.format(Locale.getDefault(), "%.1f", distanceMeters / 1000)
                        tvDistanceBigUnit.text = "km"
                    }
                }
            }

            override fun onFailure(call: retrofit2.Call<com.example.newtacks.utils.OsrmResponse>, t: Throwable) {
                // Fallback to straight-line if OSRM fails
                val results = FloatArray(1)
                android.location.Location.distanceBetween(wLat, wLng, jobLat, jobLng, results)
                val distanceStr = DistanceUtils.formatDistance(results[0])
                if (isAdded) tvWorkerDistance.text = "$distanceStr away"
            }
        })
    }

    private fun fetchWorkerDetails(workerId: String) {
        firestore.collection("users").document(workerId).get()
            .addOnSuccessListener { doc ->
                if (!isAdded) return@addOnSuccessListener
                val worker = doc.toObject(User::class.java) ?: return@addOnSuccessListener
                tvWorkerName.text = worker.name
                ivWorkerProfile.load(worker.profileImage) {
                    crossfade(true)
                    placeholder(R.drawable.ic_user_placeholder)
                    transformations(CircleCropTransformation())
                }
            }
    }

    private fun showPopupMenu(view: View) {
        val popup = androidx.appcompat.widget.PopupMenu(view.context, view)
        if (currentJob?.status == "AVAILABLE") popup.menu.add("Cancel Request")
        popup.menu.add("Report")
        popup.setOnMenuItemClickListener { item ->
            when (item.title) {
                "Cancel Request" -> showCancelConfirmationDialog()
                "Report" -> Toast.makeText(requireContext(), "Report submitted", Toast.LENGTH_SHORT).show()
            }
            true
        }
        popup.show()
    }

    private fun showCancelConfirmationDialog() {
        val dialog = Dialog(requireContext())
        dialog.setContentView(R.layout.dialog_role_select)
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
        dialog.window?.setLayout((resources.displayMetrics.widthPixels * 0.88).toInt(), ViewGroup.LayoutParams.WRAP_CONTENT)

        dialog.findViewById<ImageView>(R.id.dialogIcon).setImageResource(R.drawable.ic_close)
        dialog.findViewById<TextView>(R.id.dialogTitle).text = "Cancel Request?"
        dialog.findViewById<TextView>(R.id.dialogMessage).text = "Are you sure you want to cancel this request?"
        dialog.findViewById<Button>(R.id.dialogBtnPositive).apply {
            text = "Yes, Cancel"
            setOnClickListener { dialog.dismiss(); cancelJob() }
        }
        dialog.findViewById<Button>(R.id.dialogBtnNegative).apply {
            text = "No"
            setOnClickListener { dialog.dismiss() }
        }
        dialog.show()
    }

    private fun cancelJob() {
        val jobId = currentJobId ?: return
        loadingOverlay.visibility = View.VISIBLE
        tvLoadingMessage.text = "Cancelling request..."
        firestore.collection("jobs").document(jobId).delete()
            .addOnSuccessListener {
                loadingOverlay.visibility = View.GONE
                Toast.makeText(requireContext(), "Cancelled successfully", Toast.LENGTH_SHORT).show()
                showEmptyState()
            }
            .addOnFailureListener { loadingOverlay.visibility = View.GONE }
    }

    private fun confirmJob() {
        val jobId = currentJobId ?: return
        loadingOverlay.visibility = View.VISIBLE
        tvLoadingMessage.text = "Confirming job..."
        firestore.collection("jobs").document(jobId).update(mapOf("status" to "COMPLETED", "completedAt" to System.currentTimeMillis()))
            .addOnSuccessListener {
                loadingOverlay.visibility = View.GONE
                fetchJobAndGenerateReceipt(jobId)
            }
            .addOnFailureListener { loadingOverlay.visibility = View.GONE }
    }

    private fun fetchJobAndGenerateReceipt(jobId: String) {
        firestore.collection("jobs").document(jobId).get()
            .addOnSuccessListener { doc ->
                val job = doc.toObject(Job::class.java)
                if (job != null) generateReceipt(job)
            }
    }

    private fun generateReceipt(job: Job) {
        val workerId = job.workerId ?: return
        val receiptId = firestore.collection("receipts").document().id
        val refNum = (10000000..99999999).random().toString()
        val receipt = Receipt(
            receiptId = receiptId,
            jobId = job.jobId,
            clientId = job.clientId,
            workerId = workerId,
            clientName = job.clientName,
            workerName = job.workerName ?: "",
            jobTitle = job.jobTitle,
            serviceCategory = job.serviceCategory,
            amount = job.offeredAmount,
            referenceNumber = refNum,
            createdAt = job.createdAt,
            completedAt = job.completedAt ?: System.currentTimeMillis()
        )
        firestore.collection("receipts").document(receiptId).set(receipt)
            .addOnSuccessListener {
                com.example.newtacks.utils.NotificationHelper.sendNotification(workerId, "Job Confirmed", "Client has confirmed work.", "JOB")
                showReviewDialog(job)
            }
    }

    private fun rejectJob() {
        currentJobId?.let { id ->
            val intent = android.content.Intent(requireContext(), RefuseCompletionActivity::class.java)
            intent.putExtra("JOB_ID", id)
            startActivity(intent)
        }
    }

    private fun showReviewDialog(job: Job) {
        val dialogView = layoutInflater.inflate(R.layout.dialog_review, null)
        val ratingBar = dialogView.findViewById<RatingBar>(R.id.ratingBar)
        val etComment = dialogView.findViewById<EditText>(R.id.etComment)
        val cbAnonymous = dialogView.findViewById<CheckBox>(R.id.cbAnonymous)

        AlertDialog.Builder(requireContext(), R.style.CustomAlertDialog)
            .setView(dialogView)
            .setPositiveButton("Submit") { _, _ ->
                val review = Review(reviewId = firestore.collection("reviews").document().id, jobId = job.jobId, clientId = job.clientId, clientName = job.clientName, workerId = job.workerId ?: "", rating = ratingBar.rating, comment = etComment.text.toString(), isAnonymous = cbAnonymous.isChecked)
                saveReview(review)
            }.setNegativeButton("Skip", null).show()
    }

    private fun saveReview(review: Review) {
        firestore.collection("reviews").document(review.reviewId).set(review).addOnSuccessListener { updateWorkerRating(review) }
    }

    private fun updateWorkerRating(review: Review) {
        val workerRef = firestore.collection("users").document(review.workerId)
        firestore.runTransaction { transaction ->
            val snapshot = transaction.get(workerRef)
            val currentAvg = snapshot.getDouble("ratingAverage") ?: 0.0
            val count = snapshot.getLong("ratingCount") ?: 0
            val newCount = count + 1
            val newAvg = ((currentAvg * count) + review.rating) / newCount
            transaction.update(workerRef, mapOf("ratingAverage" to newAvg, "ratingCount" to newCount))
        }
    }

    private fun openChat() {
        val job = currentJob ?: return
        val workerId = job.workerId ?: return
        val intent = android.content.Intent(requireContext(), com.example.newtacks.chatbot.presentation.ui.TransactionChatActivity::class.java)
        intent.putExtra("JOB_ID", job.jobId); intent.putExtra("WORKER_ID", workerId); intent.putExtra("OTHER_USER_ID", workerId); intent.putExtra("JOB_TITLE", job.jobTitle)
        startActivity(intent)
    }

    private fun showEmptyState() {
        currentJob = null; currentJobId = null
        layoutContent.visibility = View.GONE; layoutEmptyState.visibility = View.VISIBLE; layoutBottomButtons.visibility = View.GONE
    }

    override fun onDestroyView() {
        super.onDestroyView()
        listener?.remove(); workerLocationListener?.remove()
    }
}