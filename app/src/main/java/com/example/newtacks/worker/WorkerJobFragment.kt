package com.example.newtacks.worker

import android.os.Bundle
import android.view.*
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.fragment.app.Fragment
import coil.load
import coil.transform.CircleCropTransformation
import com.example.newtacks.R
import com.example.newtacks.models.Job
import com.example.newtacks.models.User
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.*
import java.util.Locale

class WorkerJobFragment : Fragment() {

    private val firestore = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()
    private var listener: ListenerRegistration? = null
    private lateinit var fusedLocationClient: FusedLocationProviderClient

    private lateinit var tvTitle: TextView
    private lateinit var tvDetails: TextView
    private lateinit var tvStatus: TextView
    private lateinit var btnDone: Button
    private lateinit var btnStartHeading: Button
    private lateinit var btnArrived: Button
    private lateinit var btnMessageClient: Button
    private lateinit var btnCancelJob: Button
    private lateinit var layoutContent: LinearLayout
    private lateinit var layoutEmptyState: LinearLayout
    private lateinit var layoutBottomButtons: LinearLayout
    private lateinit var layoutHeader: LinearLayout
    private lateinit var cardJobDetails: View
    private lateinit var cardClientInfo: View
    private lateinit var tvClientDetailName: TextView
    private lateinit var tvClientDetailPhone: TextView

    private var currentJob: Job? = null
    private var currentJobId: String? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val view = inflater.inflate(R.layout.fragment_worker_job, container, false)
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(requireActivity())

        tvTitle             = view.findViewById(R.id.tvJobTitle)
        tvDetails           = view.findViewById(R.id.tvJobDetails)
        tvStatus            = view.findViewById(R.id.tvJobStatus)
        btnDone             = view.findViewById(R.id.btnRequestDone)
        btnStartHeading     = view.findViewById(R.id.btnStartHeading)
        btnArrived          = view.findViewById(R.id.btnArrived)
        btnMessageClient    = view.findViewById(R.id.btnMessageClient)
        btnCancelJob        = view.findViewById(R.id.btnCancelActiveJob)
        layoutContent       = view.findViewById(R.id.layoutContent)
        layoutEmptyState    = view.findViewById(R.id.layoutEmptyState)
        layoutBottomButtons = view.findViewById(R.id.layoutBottomButtons)
        layoutHeader        = view.findViewById(R.id.layoutHeader)

        cardJobDetails      = view.findViewById(R.id.cardJobDetails)
        cardClientInfo      = view.findViewById(R.id.cardClientInfo)
        tvClientDetailName  = view.findViewById(R.id.tvClientDetailName)
        tvClientDetailPhone = view.findViewById(R.id.tvClientDetailPhone)

        // --------------------------------------------------
        // ✅ WINDOW INSETS
        // --------------------------------------------------
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

        listenForActiveJob()
        btnDone.setOnClickListener { requestDone() }
        btnStartHeading.setOnClickListener { updateJobStatus("HEADING_TO_CLIENT") }
        btnArrived.setOnClickListener { updateJobStatus("ARRIVED") }
        btnMessageClient.setOnClickListener { openChat() }
        btnCancelJob.setOnClickListener { showCancelDialog() }

        cardJobDetails.setOnClickListener {
            currentJob?.let { showJobDetailsDialog(it) }
        }

        cardClientInfo.setOnClickListener {
            currentJob?.clientId?.let { showClientDetailsDialog(it) }
        }

        return view
    }

    // --------------------------------------------------
    // 🔥 ACTIVE JOB LISTENER
    // --------------------------------------------------
    private fun listenForActiveJob() {
        val workerId = auth.currentUser?.uid ?: return
        
        // Query only by workerId to avoid composite index requirements
        listener = firestore.collection("jobs")
            .whereEqualTo("workerId", workerId)
            .addSnapshotListener { snapshots, error ->
                if (error != null) {
                    android.util.Log.e("Firestore", "Error: ${error.message}")
                    return@addSnapshotListener
                }

                // Filter locally for the active handshake statuses
                val activeStatuses = listOf("IN_PROGRESS", "HEADING_TO_CLIENT", "ARRIVED", "PENDING_VERIFICATION")
                val job = snapshots?.documents
                    ?.mapNotNull { it.toObject(Job::class.java) }
                    ?.firstOrNull { it.status in activeStatuses }

                if (job == null) showEmptyState() else showActiveJob(job)
            }
    }

    // --------------------------------------------------
    // UI STATE: ACTIVE JOB
    // --------------------------------------------------
    private fun showActiveJob(job: Job) {
        currentJob                  = job
        currentJobId                = job.jobId
        layoutContent.visibility    = View.VISIBLE
        layoutEmptyState.visibility = View.GONE
        layoutBottomButtons.visibility = View.VISIBLE

        tvTitle.text = job.jobTitle
        tvDetails.text = "Service: ${job.serviceCategory}\n₱${job.offeredAmount}"

        tvClientDetailName.text = job.clientName
        tvClientDetailPhone.text = "Tap to view contact"

        tvStatus.visibility = View.VISIBLE
        
        // Default visibility
        btnStartHeading.visibility = View.GONE
        btnArrived.visibility      = View.GONE
        btnDone.visibility         = View.GONE
        btnMessageClient.visibility = View.VISIBLE
        btnCancelJob.visibility    = View.VISIBLE // Can cancel anytime before verification

        when (job.status) {
            "IN_PROGRESS" -> {
                tvStatus.text = "Job Accepted"
                tvStatus.setTextColor(android.graphics.Color.parseColor("#D97706"))
                tvStatus.setBackgroundResource(R.drawable.bg_badge_yellow)
                btnStartHeading.visibility = View.VISIBLE
            }
            "HEADING_TO_CLIENT" -> {
                tvStatus.text = "Heading to Location..."
                tvStatus.setTextColor(android.graphics.Color.parseColor("#2563EB"))
                tvStatus.setBackgroundResource(R.drawable.bg_badge_blue)
                btnArrived.visibility = View.VISIBLE
                
                // Show live distance
                showLiveDistance(job.latitude, job.longitude)
            }
            "ARRIVED" -> {
                tvStatus.text = "Arrived at Location"
                tvStatus.setTextColor(android.graphics.Color.parseColor("#16A34A"))
                tvStatus.setBackgroundResource(R.drawable.bg_badge_green)
                btnDone.visibility = View.VISIBLE
            }
            "PENDING_VERIFICATION" -> {
                tvStatus.text = "Waiting for client confirmation"
                tvStatus.setTextColor(android.graphics.Color.parseColor("#16A34A"))
                tvStatus.setBackgroundResource(R.drawable.bg_badge_green)
                btnCancelJob.visibility = View.GONE
            }
            else -> {
                tvStatus.text = job.status
                tvStatus.setTextColor(android.graphics.Color.parseColor("#FFFFFF"))
                tvStatus.setBackgroundResource(R.drawable.bg_badge_blue)
            }
        }
    }

    private fun showLiveDistance(jobLat: Double, jobLng: Double) {
        if (androidx.core.content.ContextCompat.checkSelfPermission(requireContext(), android.Manifest.permission.ACCESS_FINE_LOCATION) 
            != android.content.pm.PackageManager.PERMISSION_GRANTED) return

        fusedLocationClient.lastLocation.addOnSuccessListener { location ->
            if (location != null) {
                val results = FloatArray(1)
                android.location.Location.distanceBetween(
                    location.latitude, location.longitude,
                    jobLat, jobLng,
                    results
                )
                val distanceKm = results[0] / 1000
                tvStatus.text = String.format(Locale.getDefault(), "Heading to Location... (%.1f km away)", distanceKm)
            }
        }
    }

    private fun openChat() {
        val job = currentJob ?: return
        val intent = android.content.Intent(requireContext(), com.example.newtacks.chatbot.presentation.ui.TransactionChatActivity::class.java)
        intent.putExtra("JOB_ID", job.jobId)
        intent.putExtra("WORKER_ID", job.workerId) // Pass the worker ID for session isolation
        intent.putExtra("OTHER_USER_ID", job.clientId)
        intent.putExtra("JOB_TITLE", job.jobTitle)
        startActivity(intent)
    }

    private fun updateJobStatus(newStatus: String) {
        val jobId = currentJobId ?: return
        firestore.collection("jobs").document(jobId)
            .update("status", newStatus)
            .addOnSuccessListener {
                Toast.makeText(requireContext(), "Status updated: $newStatus", Toast.LENGTH_SHORT).show()
            }
    }

    private fun showCancelDialog() {
        val dialog = android.app.Dialog(requireContext())
        dialog.setContentView(R.layout.dialog_role_select)
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
        dialog.window?.setLayout(
            (resources.displayMetrics.widthPixels * 0.88).toInt(),
            ViewGroup.LayoutParams.WRAP_CONTENT
        )

        dialog.findViewById<ImageView>(R.id.dialogIcon).setImageResource(R.drawable.ic_close)
        dialog.findViewById<TextView>(R.id.dialogTitle).text = "Cancel Job?"
        dialog.findViewById<TextView>(R.id.dialogMessage).text = "Are you sure you want to cancel? This job will be returned to the feed for others."

        dialog.findViewById<com.google.android.material.button.MaterialButton>(R.id.dialogBtnPositive).apply {
            text = "Yes, Cancel"
            setOnClickListener {
                dialog.dismiss()
                cancelJobByWorker()
            }
        }

        dialog.findViewById<com.google.android.material.button.MaterialButton>(R.id.dialogBtnNegative).apply {
            text = "Keep Working"
            setOnClickListener { dialog.dismiss() }
        }

        dialog.show()
    }

    private fun cancelJobByWorker() {
        val jobId = currentJobId ?: return
        
        // Set back to AVAILABLE and clear worker info
        val update = mapOf(
            "status" to "AVAILABLE",
            "workerId" to FieldValue.delete(),
            "workerName" to FieldValue.delete(),
            "acceptedAt" to FieldValue.delete()
        )

        firestore.collection("jobs").document(jobId)
            .update(update)
            .addOnSuccessListener {
                com.example.newtacks.utils.ChatUtils.deleteChatHistory(jobId) // Maintain privacy & save space
                Toast.makeText(requireContext(), "Job cancelled and returned to feed", Toast.LENGTH_SHORT).show()
                showEmptyState()
            }
            .addOnFailureListener {
                Toast.makeText(requireContext(), "Failed to cancel: ${it.message}", Toast.LENGTH_SHORT).show()
            }
    }

    // --------------------------------------------------
    // UI STATE: EMPTY
    // --------------------------------------------------
    private fun showEmptyState() {
        currentJob                     = null
        currentJobId                   = null
        layoutContent.visibility       = View.GONE
        layoutEmptyState.visibility    = View.VISIBLE
        layoutBottomButtons.visibility = View.GONE
        tvTitle.text                   = ""
        tvStatus.visibility            = View.GONE
        tvStatus.background            = null
        btnDone.visibility             = View.GONE
    }

    // --------------------------------------------------
    // 🔥 DIALOGS: JOB & CLIENT DETAILS
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

    private fun showClientDetailsDialog(clientId: String) {
        firestore.collection("users").document(clientId).get()
            .addOnSuccessListener { doc ->
                val client = doc.toObject(User::class.java) ?: return@addOnSuccessListener
                
                val dialogView = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_client_details_preview, null)
                val ivProfile = dialogView.findViewById<ImageView>(R.id.ivClientProfile)
                val tvName = dialogView.findViewById<TextView>(R.id.tvClientName)
                val tvRole = dialogView.findViewById<TextView>(R.id.tvClientRole)
                val tvPhone = dialogView.findViewById<TextView>(R.id.tvClientPhone)
                val tvAddress = dialogView.findViewById<TextView>(R.id.tvClientAddress)
                val layoutCompany = dialogView.findViewById<LinearLayout>(R.id.layoutCompanyInfo)
                val tvCompanyName = dialogView.findViewById<TextView>(R.id.tvCompanyName)

                tvName.text = client.name
                tvRole.text = client.role
                tvPhone.text = client.phone
                tvAddress.text = client.address

                if (client.role == "COMPANY") {
                    layoutCompany.visibility = View.VISIBLE
                    tvCompanyName.text = client.companyName
                }

                ivProfile.load(client.profileImage) {
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
    // ACTION: REQUEST DONE
    // --------------------------------------------------
    private fun requestDone() {
        val jobId = currentJobId ?: return
        firestore.collection("jobs")
            .document(jobId)
            .update(
                mapOf(
                    "status"      to "PENDING_VERIFICATION",
                    "completedAt" to System.currentTimeMillis()
                )
            )
            .addOnSuccessListener {
                Toast.makeText(
                    requireContext(),
                    "Marked as done. Waiting for client verification.",
                    Toast.LENGTH_SHORT
                ).show()
            }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        listener?.remove()
    }
}