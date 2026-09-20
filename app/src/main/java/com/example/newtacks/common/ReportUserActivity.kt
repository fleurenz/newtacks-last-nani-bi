package com.example.newtacks.common

import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowCompat
import com.cloudinary.android.MediaManager
import com.cloudinary.android.callback.ErrorInfo
import com.cloudinary.android.callback.UploadCallback
import com.example.newtacks.R
import com.example.newtacks.models.Report
import com.example.newtacks.models.User
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

class ReportUserActivity : AppCompatActivity() {

    private val firestore = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()
    
    private var reporteeId: String? = null
    private var reporteeName: String? = null
    private var reporterName: String? = null
    private var evidenceUri: Uri? = null

    private lateinit var ivEvidence: ImageView
    private lateinit var etDescription: EditText
    private lateinit var rgReasons: RadioGroup
    private lateinit var loadingOverlay: View
    private lateinit var tvReportContext: TextView
    private lateinit var tvReporterInfo: TextView

    private val pickImage = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) {
            evidenceUri = uri
            ivEvidence.setImageURI(uri)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        setContentView(R.layout.activity_report_user)

        reporteeId = intent.getStringExtra("REPORTEE_ID")
        reporteeName = intent.getStringExtra("REPORTEE_NAME")
        
        if (reporteeId == null) {
            Toast.makeText(this, "No user specified to report", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        val toolbar = findViewById<Toolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayShowTitleEnabled(false)
        toolbar.setNavigationOnClickListener { finish() }

        // Robust Inset Handling
        val statusBarSpacer = findViewById<View>(R.id.statusBarSpacer)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(android.R.id.content)) { _, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            statusBarSpacer.layoutParams.height = systemBars.top
            statusBarSpacer.requestLayout()
            insets
        }

        determineTheme()

        ivEvidence = findViewById(R.id.ivReportEvidence)
        etDescription = findViewById(R.id.etReportDescription)
        rgReasons = findViewById(R.id.rgReportReasons)
        loadingOverlay = findViewById(R.id.loadingOverlay)
        tvReportContext = findViewById(R.id.tvReportContext)
        tvReporterInfo = findViewById(R.id.tvReporterInfo)

        tvReportContext.text = "Reporting: ${reporteeName ?: "User"}"
        
        fetchReporterInfo()

        findViewById<View>(R.id.btnUploadReportEvidence).setOnClickListener {
            pickImage.launch("image/*")
        }

        findViewById<Button>(R.id.btnSubmitUserReport).setOnClickListener {
            submitReport()
        }
    }

    private fun determineTheme() {
        val uid = auth.currentUser?.uid ?: return
        firestore.collection("users").document(uid).get().addOnSuccessListener { doc ->
            val user = doc.toObject(User::class.java)
            val themeColor = when (user?.role) {
                "WORKER"  -> Color.parseColor("#0F325E") // Worker Navy
                "COMPANY" -> Color.parseColor("#1C6EC6") // Company Blue
                else      -> Color.parseColor("#004A99") // Client Blue (Default)
            }
            applyTheme(themeColor)
        }
    }

    private fun applyTheme(color: Int) {
        window.statusBarColor = color
        findViewById<View>(R.id.reportUserRoot).setBackgroundColor(color)
        findViewById<View>(R.id.statusBarSpacer).setBackgroundColor(color)
        findViewById<Toolbar>(R.id.toolbar).setBackgroundColor(color)
    }

    private fun fetchReporterInfo() {
        val uid = auth.currentUser?.uid ?: return
        firestore.collection("users").document(uid).get().addOnSuccessListener { doc ->
            val user = doc.toObject(User::class.java)
            reporterName = user?.name ?: "Unknown"
            tvReporterInfo.text = "Reporter: $reporterName"
        }
    }

    private fun submitReport() {
        val selectedId = rgReasons.checkedRadioButtonId
        if (selectedId == -1) {
            Toast.makeText(this, "Please select a reason", Toast.LENGTH_SHORT).show()
            return
        }

        val reason = findViewById<RadioButton>(selectedId).text.toString()
        val description = etDescription.text.toString().trim()

        if (description.isEmpty()) {
            Toast.makeText(this, "Please provide more details", Toast.LENGTH_SHORT).show()
            return
        }

        loadingOverlay.visibility = View.VISIBLE

        if (evidenceUri != null) {
            uploadEvidence(evidenceUri!!, reason, description)
        } else {
            finalizeReport(reason, description, null)
        }
    }

    private fun uploadEvidence(uri: Uri, reason: String, description: String) {
        MediaManager.get().upload(uri)
            .option("folder", "user_reports_evidence")
            .callback(object : UploadCallback {
                override fun onStart(requestId: String?) {}
                override fun onProgress(requestId: String?, bytes: Long, totalBytes: Long) {}
                override fun onSuccess(requestId: String?, resultData: MutableMap<Any?, Any?>?) {
                    val url = resultData?.get("secure_url").toString()
                    finalizeReport(reason, description, url)
                }
                override fun onError(requestId: String?, error: ErrorInfo?) {
                    loadingOverlay.visibility = View.GONE
                    Toast.makeText(this@ReportUserActivity, "Upload failed", Toast.LENGTH_SHORT).show()
                }
                override fun onReschedule(requestId: String?, error: ErrorInfo?) {}
            }).dispatch()
    }

    private fun finalizeReport(reason: String, description: String, evidenceUrl: String?) {
        val reportId = firestore.collection("user_reports").document().id
        val report = Report(
            reportId = reportId,
            reporterId = auth.currentUser?.uid ?: "",
            reporterName = reporterName ?: "Unknown",
            reporteeId = reporteeId ?: "",
            reporteeName = reporteeName ?: "User",
            reason = reason,
            description = description,
            evidenceUrl = evidenceUrl
        )

        firestore.collection("user_reports").document(reportId).set(report)
            .addOnSuccessListener {
                loadingOverlay.visibility = View.GONE
                Toast.makeText(this, "Report submitted successfully", Toast.LENGTH_SHORT).show()
                finish()
            }
            .addOnFailureListener {
                loadingOverlay.visibility = View.GONE
                Toast.makeText(this, "Failed to submit report", Toast.LENGTH_SHORT).show()
            }
    }
}