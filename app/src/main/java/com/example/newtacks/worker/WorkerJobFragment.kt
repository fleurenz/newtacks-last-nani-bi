package com.example.newtacks.worker

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.view.*
import android.widget.*
import androidx.appcompat.widget.PopupMenu
import androidx.appcompat.app.AlertDialog
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.fragment.app.Fragment
import coil.load
import coil.transform.CircleCropTransformation
import com.example.newtacks.R
import com.example.newtacks.models.Job
import com.example.newtacks.models.User
import com.example.newtacks.utils.DistanceUtils
import com.example.newtacks.utils.ImageUtils
import com.example.newtacks.utils.NotificationHelper
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.android.material.button.MaterialButton
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.*
import java.text.SimpleDateFormat
import java.util.Locale

class WorkerJobFragment : Fragment() {

    private val firestore = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()
    private var listener: ListenerRegistration? = null
    private lateinit var fusedLocationClient: FusedLocationProviderClient

    // Main UI
    private lateinit var layoutContent: View
    private lateinit var layoutEmptyState: View
    private lateinit var layoutHeader: View
    private lateinit var loadingOverlay: View

    // Header / Title Area
    private lateinit var tvJobTitle: TextView
    private lateinit var tvJobDateTop: TextView
    private lateinit var btnMoreOptions: ImageButton

    // Client Info
    private lateinit var ivClientProfile: ImageView
    private lateinit var tvClientName: TextView
    private lateinit var tvClientDistance: TextView
    private lateinit var btnViewClientProfile: View
    private lateinit var btnMessageClient: View

    // Progress Bar
    private lateinit var progressTrackActive: View
    private lateinit var stepCircles: List<ImageView>
    private lateinit var tvProgressStatus: TextView

    // Job Details Card
    private lateinit var tvServiceType: TextView
    private lateinit var tvAddress: TextView
    private lateinit var tvTime: TextView
    private lateinit var tvDate: TextView
    private lateinit var tvRate: TextView
    private lateinit var tvDescription: TextView
    private lateinit var btnViewImages: View

    // Bottom Sticky Buttons
    private lateinit var layoutBottomButtons: View
    private lateinit var btnNavigateMap: Button
    private lateinit var btnMainAction: Button
    private lateinit var tvMessageBadge: TextView

    private var currentJob: Job? = null
    private var currentJobId: String? = null
    private var messageListener: ListenerRegistration? = null
    private var activeRejectionDialog: AlertDialog? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val view = inflater.inflate(R.layout.fragment_worker_job, container, false)
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(requireActivity())

        initializeViews(view)
        setupStatusBar(view)
        setupListeners(view)

        listenForActiveJob()

        return view
    }

    private fun initializeViews(view: View) {
        layoutContent = view.findViewById(R.id.layoutContent)
        layoutEmptyState = view.findViewById(R.id.layoutEmptyState)
        layoutHeader = view.findViewById(R.id.layoutHeader)
        loadingOverlay = view.findViewById(R.id.loadingOverlay)

        tvJobTitle = view.findViewById(R.id.tvJobTitle)
        tvJobDateTop = view.findViewById(R.id.tvJobDateTop)
        btnMoreOptions = view.findViewById(R.id.btnMoreOptions)

        ivClientProfile = view.findViewById(R.id.ivClientProfile)
        tvClientName = view.findViewById(R.id.tvClientDetailName)
        tvClientDistance = view.findViewById(R.id.tvClientDistance)
        btnViewClientProfile = view.findViewById(R.id.btnViewClientProfile)
        btnMessageClient = view.findViewById(R.id.btnMessageClient)
        tvMessageBadge = view.findViewById(R.id.tvMessageBadgeWorker)

        // Progress Bar
        progressTrackActive = view.findViewById(R.id.progressTrackActive)
        tvProgressStatus = view.findViewById(R.id.tvProgressStatus)
        stepCircles = listOf(
            view.findViewById(R.id.step1),
            view.findViewById(R.id.step2),
            view.findViewById(R.id.step3),
            view.findViewById(R.id.step4)
        )

        tvServiceType = view.findViewById(R.id.tvServiceType)
        tvAddress = view.findViewById(R.id.tvAddress)
        tvTime = view.findViewById(R.id.tvTime)
        tvDate = view.findViewById(R.id.tvDate)
        tvRate = view.findViewById(R.id.tvRate)
        tvDescription = view.findViewById(R.id.tvDescription)
        btnViewImages = view.findViewById(R.id.btnViewImages)

        layoutBottomButtons = view.findViewById(R.id.layoutBottomButtons)
        btnNavigateMap = view.findViewById(R.id.btnNavigateToMap)
        btnMainAction = view.findViewById(R.id.btnMainAction)
    }

    private fun setupStatusBar(view: View) {
        val spacer = view.findViewById<View>(R.id.statusBarSpacer)
        ViewCompat.setOnApplyWindowInsetsListener(view) { _, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            spacer.layoutParams.height = systemBars.top
            spacer.requestLayout()
            insets
        }
    }

    private fun setupListeners(view: View) {
        btnMoreOptions.setOnClickListener { showPopupMenu(it) }
        btnMessageClient.setOnClickListener { openChat() }
        btnViewClientProfile.setOnClickListener {
            currentJob?.clientId?.let { showClientDetailsDialog(it) }
        }
        btnViewImages.setOnClickListener {
            currentJob?.jobImages?.firstOrNull()?.let { url ->
                ImageUtils.showFullscreenImage(requireContext(), url)
            } ?: Toast.makeText(requireContext(), "No images available", Toast.LENGTH_SHORT).show()
        }
        
        btnNavigateMap.setOnClickListener {
            currentJob?.let { job ->
                (activity as? com.example.newtacks.WorkerDashboardActivity)?.focusMapOnLocation(job.latitude, job.longitude)
            }
        }

        view.findViewById<View>(R.id.btnNotifications).setOnClickListener {
            NotificationHelper.showNotificationDialog(requireContext())
        }
    }

    private fun listenForActiveJob() {
        val workerId = auth.currentUser?.uid ?: return
        listener = firestore.collection("jobs")
            .whereEqualTo("workerId", workerId)
            .addSnapshotListener { snapshots, _ ->
                val activeStatuses = listOf("IN_PROGRESS", "HEADING_TO_CLIENT", "ARRIVED", "PENDING_VERIFICATION", "REJECTED_BY_CLIENT")
                val jobDoc = snapshots?.documents?.firstOrNull { 
                    val status = it.getString("status") ?: ""
                    status in activeStatuses 
                }

                if (jobDoc == null) {
                    showEmptyState()
                } else {
                    val job = jobDoc.toObject(Job::class.java)?.copy(jobId = jobDoc.id)
                    if (job != null) showActiveJob(job) else showEmptyState()
                }
            }
    }

    private fun showActiveJob(job: Job) {
        val oldId = currentJobId
        currentJob = job
        currentJobId = job.jobId
        
        if (oldId != currentJobId) {
            listenForUnreadMessages(job.jobId)
        }
        
        layoutContent.visibility = View.VISIBLE
        layoutEmptyState.visibility = View.GONE
        layoutBottomButtons.visibility = View.VISIBLE

        tvJobTitle.text = job.jobTitle
        
        val displayDate = formatJobDate(job.scheduledDate)
        tvJobDateTop.text = displayDate
        
        tvClientName.text = job.clientName
        updateDistanceUI(job.latitude, job.longitude)

        tvServiceType.text = job.serviceCategory
        tvAddress.text = job.clientAddress
        tvTime.text = job.scheduledTime
        tvDate.text = displayDate
        tvRate.text = "₱${job.offeredAmount.toInt()}/day"
        tvDescription.text = job.description

        loadClientProfile(job.clientId)
        updateStatusFlow(job.status)
    }

    private fun loadClientProfile(clientId: String) {
        firestore.collection("users").document(clientId).get().addOnSuccessListener { doc ->
            val url = doc.getString("profileImage") ?: ""
            ivClientProfile.load(url.ifEmpty { null }) {
                placeholder(R.drawable.ic_person_placeholder)
                transformations(CircleCropTransformation())
            }
        }
    }

    private fun listenForUnreadMessages(jobId: String) {
        val uid = auth.currentUser?.uid ?: return
        messageListener?.remove()
        
        messageListener = firestore.collection("chats")
            .whereEqualTo("jobId", jobId)
            .whereEqualTo("receiverId", uid)
            .whereEqualTo("read", false)
            .addSnapshotListener { snapshots, _ ->
                val count = snapshots?.size() ?: 0
                if (count > 0) {
                    tvMessageBadge.visibility = View.VISIBLE
                    tvMessageBadge.text = count.toString()
                } else {
                    tvMessageBadge.visibility = View.GONE
                }
            }
    }

    private fun updateStatusFlow(status: String) {
        // Reset Progress Bar
        stepCircles.forEach { 
            it.setBackgroundResource(R.drawable.bg_step_circle_inactive)
            it.imageTintList = android.content.res.ColorStateList.valueOf(Color.parseColor("#94A3B8"))
        }

        when (status) {
            "IN_PROGRESS" -> {
                highlightStep(0, "Accepted")
                btnMainAction.text = "Start heading there"
                btnMainAction.backgroundTintList = android.content.res.ColorStateList.valueOf(Color.parseColor("#0F325E"))
                btnMainAction.setOnClickListener { updateJobStatus("HEADING_TO_CLIENT") }
            }
            "HEADING_TO_CLIENT" -> {
                highlightStep(1, "Heading to Client")
                btnMainAction.text = "I Have Arrived"
                btnMainAction.backgroundTintList = android.content.res.ColorStateList.valueOf(Color.parseColor("#16A34A"))
                btnMainAction.setOnClickListener { updateJobStatus("ARRIVED") }
            }
            "ARRIVED" -> {
                highlightStep(2, "Working...")
                btnMainAction.text = "Finish Work"
                btnMainAction.backgroundTintList = android.content.res.ColorStateList.valueOf(Color.parseColor("#0F325E"))
                btnMainAction.setOnClickListener { requestDone() }
            }
            "PENDING_VERIFICATION" -> {
                highlightStep(3, "Waiting for Confirmation")
                btnMainAction.text = "Waiting for Verification"
                btnMainAction.isEnabled = false
                btnMainAction.alpha = 0.6f
            }
            "REJECTED_BY_CLIENT" -> {
                highlightStep(2, "Completion Refused")
                btnMainAction.text = "Resubmit for Review"
                btnMainAction.isEnabled = true
                btnMainAction.alpha = 1.0f
                btnMainAction.setOnClickListener { requestDone() }
                showRejectionDialog(currentJob!!)
            }
        }
    }

    private fun highlightStep(index: Int, statusText: String) {
        tvProgressStatus.text = statusText
        
        for (i in 0..index) {
            stepCircles[i].setBackgroundResource(R.drawable.bg_step_circle_active)
            stepCircles[i].imageTintList = android.content.res.ColorStateList.valueOf(Color.WHITE)
        }

        view?.post {
            if (!isAdded) return@post
            val totalWidth = stepCircles.last().left - stepCircles[0].left
            val progressWidth = if (index > 0) {
                (totalWidth / (stepCircles.size - 1)) * index
            } else 0
            
            val params = progressTrackActive.layoutParams
            params.width = progressWidth
            progressTrackActive.layoutParams = params
            
            // Translate status text
            val latestStep = stepCircles[index]
            val container = latestStep.parent as View
            val centerX = container.x + (container.width / 2)
            val parent = tvProgressStatus.parent as View
            val maxTranslation = parent.width - tvProgressStatus.width
            val targetX = centerX - (tvProgressStatus.width / 2)
            tvProgressStatus.translationX = targetX.coerceIn(0f, maxTranslation.toFloat())
        }
    }

    private fun updateJobStatus(newStatus: String) {
        val jobId = currentJobId ?: return
        loadingOverlay.visibility = View.VISIBLE
        firestore.collection("jobs").document(jobId).update("status", newStatus)
            .addOnSuccessListener {
                loadingOverlay.visibility = View.GONE
                val title = when (newStatus) {
                    "HEADING_TO_CLIENT" -> "Heading to You"
                    "ARRIVED" -> "Arrived"
                    else -> "Job Update"
                }
                val msg = when (newStatus) {
                    "HEADING_TO_CLIENT" -> "Your worker is now on their way."
                    "ARRIVED" -> "Your worker has arrived at your address."
                    else -> "Status changed to $newStatus"
                }
                currentJob?.let { NotificationHelper.sendNotification(it.clientId, title, msg, "REQUESTS") }
            }
            .addOnFailureListener { loadingOverlay.visibility = View.GONE }
    }

    private fun requestDone() {
        val jobId = currentJobId ?: return
        loadingOverlay.visibility = View.VISIBLE
        firestore.collection("jobs").document(jobId)
            .update(mapOf("status" to "PENDING_VERIFICATION", "completedAt" to System.currentTimeMillis()))
            .addOnSuccessListener {
                loadingOverlay.visibility = View.GONE
                currentJob?.let { NotificationHelper.sendNotification(it.clientId, "Job Ready", "Please verify completion.", "REQUESTS") }
            }
            .addOnFailureListener { loadingOverlay.visibility = View.GONE }
    }

    private fun updateDistanceUI(lat: Double, lng: Double) {
        if (!isAdded) return
        if (androidx.core.content.ContextCompat.checkSelfPermission(requireContext(), android.Manifest.permission.ACCESS_FINE_LOCATION) 
            != android.content.pm.PackageManager.PERMISSION_GRANTED) return

        fusedLocationClient.lastLocation.addOnSuccessListener { loc ->
            if (loc != null) {
                val results = FloatArray(1)
                android.location.Location.distanceBetween(loc.latitude, loc.longitude, lat, lng, results)
                tvClientDistance.text = DistanceUtils.formatDistance(results[0]) + " away"
            }
        }
    }

    private fun showEmptyState() {
        currentJob = null
        layoutContent.visibility = View.GONE
        layoutEmptyState.visibility = View.VISIBLE
        layoutBottomButtons.visibility = View.GONE
    }

    private fun showPopupMenu(view: View) {
        val popup = PopupMenu(view.context, view)
        popup.menu.add("Cancel Job")
        popup.menu.add("Report")
        popup.setOnMenuItemClickListener { item ->
            if (item.title == "Cancel Job") showCancelDialog()
            true
        }
        popup.show()
    }

    private fun openChat() {
        val job = currentJob ?: return
        val intent = Intent(requireContext(), com.example.newtacks.chatbot.presentation.ui.TransactionChatActivity::class.java)
        intent.putExtra("JOB_ID", job.jobId)
        intent.putExtra("WORKER_ID", job.workerId)
        intent.putExtra("OTHER_USER_ID", job.clientId)
        intent.putExtra("JOB_TITLE", job.jobTitle)
        startActivity(intent)
    }

    private fun showCancelDialog() {
        val dialog = android.app.Dialog(requireContext())
        dialog.setContentView(R.layout.dialog_role_select)
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
        dialog.window?.setLayout((resources.displayMetrics.widthPixels * 0.88).toInt(), ViewGroup.LayoutParams.WRAP_CONTENT)

        dialog.findViewById<ImageView>(R.id.dialogIcon).setImageResource(R.drawable.ic_close)
        dialog.findViewById<TextView>(R.id.dialogTitle).text = "Cancel Job?"
        dialog.findViewById<TextView>(R.id.dialogMessage).text = "This job will be returned to the feed."

        dialog.findViewById<MaterialButton>(R.id.dialogBtnPositive).apply {
            text = "Yes, Cancel"
            setOnClickListener {
                dialog.dismiss()
                cancelJob()
            }
        }
        dialog.findViewById<MaterialButton>(R.id.dialogBtnNegative).apply {
            text = "Keep Working"
            setOnClickListener { dialog.dismiss() }
        }
        dialog.show()
    }

    private fun cancelJob() {
        val jobId = currentJobId ?: return
        loadingOverlay.visibility = View.VISIBLE
        val update = mapOf("status" to "AVAILABLE", "workerId" to FieldValue.delete(), "workerName" to FieldValue.delete(), "acceptedAt" to FieldValue.delete())
        firestore.collection("jobs").document(jobId).update(update).addOnSuccessListener {
            loadingOverlay.visibility = View.GONE
            showEmptyState()
        }.addOnFailureListener { loadingOverlay.visibility = View.GONE }
    }

    private fun showRejectionDialog(job: Job) {
        if (activeRejectionDialog?.isShowing == true) return
        val details = job.rejectionDetails as? Map<String, Any> ?: return
        val dialogView = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_job_rejection, null)
        dialogView.findViewById<TextView>(R.id.tvRejectionReason).text = details["reason"] as? String ?: "Unknown"
        dialogView.findViewById<TextView>(R.id.tvRejectionDescription).text = details["description"] as? String ?: "No details"
        
        activeRejectionDialog = AlertDialog.Builder(requireContext()).setView(dialogView).setCancelable(false).create()
        activeRejectionDialog?.window?.setBackgroundDrawableResource(android.R.color.transparent)
        dialogView.findViewById<Button>(R.id.btnAcknowledge).setOnClickListener { activeRejectionDialog?.dismiss() }
        activeRejectionDialog?.show()
    }

    private fun showClientDetailsDialog(clientId: String) {
        firestore.collection("users").document(clientId).get().addOnSuccessListener { doc ->
            val client = doc.toObject(User::class.java) ?: return@addOnSuccessListener
            val dialogView = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_client_details_preview, null)
            dialogView.findViewById<TextView>(R.id.tvClientName).text = client.name
            dialogView.findViewById<TextView>(R.id.tvClientPhone).text = client.phone
            dialogView.findViewById<TextView>(R.id.tvClientAddress).text = client.address
            val iv = dialogView.findViewById<ImageView>(R.id.ivClientProfile)
            iv.load(client.profileImage) { placeholder(R.drawable.ic_user_placeholder); transformations(CircleCropTransformation()) }
            AlertDialog.Builder(requireContext(), R.style.CustomAlertDialog).setView(dialogView).setPositiveButton("Close", null).show()
        }
    }

    /**
     * Formats job date from "M/d/yyyy" or "MM/dd/yy" to "MMMM d, yyyy"
     */
    private fun formatJobDate(dateStr: String): String {
        if (dateStr.isEmpty()) return ""
        
        val inputFormats = listOf(
            SimpleDateFormat("M/d/yyyy", Locale.getDefault()),
            SimpleDateFormat("MM/dd/yy", Locale.getDefault()),
            SimpleDateFormat("dd/MM/yy", Locale.getDefault())
        )
        
        val outputFormat = SimpleDateFormat("MMMM d, yyyy", Locale.getDefault())
        
        for (format in inputFormats) {
            try {
                val date = format.parse(dateStr)
                if (date != null) return outputFormat.format(date)
            } catch (e: Exception) {
                // Try next format
            }
        }
        
        return dateStr // Fallback to original if parsing fails
    }

    override fun onDestroyView() {
        super.onDestroyView()
        listener?.remove()
        messageListener?.remove()
    }
}
