package com.example.newtacks.client

import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import coil.load
import com.cloudinary.android.MediaManager
import com.cloudinary.android.callback.ErrorInfo
import com.cloudinary.android.callback.UploadCallback
import com.example.newtacks.R
import com.example.newtacks.models.Job
import com.google.firebase.firestore.FirebaseFirestore
import java.text.SimpleDateFormat
import java.util.*

class RefuseCompletionActivity : AppCompatActivity() {

    private val firestore = FirebaseFirestore.getInstance()
    private var jobId: String? = null
    private var currentJob: Job? = null
    private var evidenceUri: Uri? = null

    private lateinit var ivEvidence: ImageView
    private lateinit var etDescription: EditText
    private lateinit var rgReasons: RadioGroup
    private lateinit var loadingOverlay: View
    private lateinit var tvJobHeader: TextView
    private lateinit var tvMarkedDoneTime: TextView

    private val pickImage = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) {
            evidenceUri = uri
            ivEvidence.setImageURI(uri)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_refuse_completion)

        jobId = intent.getStringExtra("JOB_ID")
        if (jobId == null) {
            finish()
            return
        }

        val toolbar = findViewById<Toolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)
        toolbar.setNavigationOnClickListener { finish() }

        ivEvidence = findViewById(R.id.ivEvidence)
        etDescription = findViewById(R.id.etProblemDescription)
        rgReasons = findViewById(R.id.rgReasons)
        loadingOverlay = findViewById(R.id.loadingOverlay)
        tvJobHeader = findViewById(R.id.tvJobHeaderInfo)
        tvMarkedDoneTime = findViewById(R.id.tvMarkedDoneTime)

        findViewById<View>(R.id.btnUploadEvidence).setOnClickListener {
            pickImage.launch("image/*")
        }

        findViewById<Button>(R.id.btnSubmitReport).setOnClickListener {
            submitReport()
        }

        fetchJobData()
    }

    private fun fetchJobData() {
        jobId?.let { id ->
            firestore.collection("jobs").document(id).get().addOnSuccessListener { doc ->
                val job = doc.toObject(Job::class.java) ?: return@addOnSuccessListener
                currentJob = job
                tvJobHeader.text = "${job.jobTitle} • ${job.workerName ?: "Worker"}"
                
                job.completedAt?.let { time ->
                    val sdf = SimpleDateFormat("h:mm a", Locale.getDefault())
                    tvMarkedDoneTime.text = "Marked done at ${sdf.format(Date(time))}"
                }
            }
        }
    }

    private fun submitReport() {
        val selectedId = rgReasons.checkedRadioButtonId
        if (selectedId == -1) {
            Toast.makeText(this, "Please select a reason", Toast.LENGTH_SHORT).show()
            return
        }

        val reason = findViewById<RadioButton>(selectedId).text.toString()
        val description = etDescription.text.toString()

        if (description.isBlank()) {
            Toast.makeText(this, "Please describe the problem", Toast.LENGTH_SHORT).show()
            return
        }

        loadingOverlay.visibility = View.VISIBLE

        if (evidenceUri != null) {
            uploadEvidence(evidenceUri!!, reason, description)
        } else {
            updateJobStatus(reason, description, null)
        }
    }

    private fun uploadEvidence(uri: Uri, reason: String, description: String) {
        MediaManager.get().upload(uri)
            .option("folder", "job_rejection_evidence")
            .callback(object : UploadCallback {
                override fun onStart(requestId: String?) {}
                override fun onProgress(requestId: String?, bytes: Long, totalBytes: Long) {}
                override fun onSuccess(requestId: String?, resultData: MutableMap<Any?, Any?>?) {
                    val url = resultData?.get("secure_url").toString()
                    updateJobStatus(reason, description, url)
                }
                override fun onError(requestId: String?, error: ErrorInfo?) {
                    loadingOverlay.visibility = View.GONE
                    Toast.makeText(this@RefuseCompletionActivity, "Upload failed", Toast.LENGTH_SHORT).show()
                }
                override fun onReschedule(requestId: String?, error: ErrorInfo?) {}
            }).dispatch()
    }

    private fun updateJobStatus(reason: String, description: String, evidenceUrl: String?) {
        val updates = mapOf(
            "status" to "REJECTED_BY_CLIENT",
            "rejectionDetails" to mapOf(
                "reason" to reason,
                "description" to description,
                "evidenceUrl" to evidenceUrl,
                "timestamp" to System.currentTimeMillis()
            )
        )

        jobId?.let { id ->
            firestore.collection("jobs").document(id).update(updates)
                .addOnSuccessListener {
                    loadingOverlay.visibility = View.GONE
                    Toast.makeText(this, "Report submitted", Toast.LENGTH_SHORT).show()
                    finish()
                }
                .addOnFailureListener {
                    loadingOverlay.visibility = View.GONE
                    Toast.makeText(this, "Failed to submit", Toast.LENGTH_SHORT).show()
                }
        }
    }
}