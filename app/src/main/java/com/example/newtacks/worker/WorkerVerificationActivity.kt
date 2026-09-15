package com.example.newtacks.worker

import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.cloudinary.android.MediaManager
import com.cloudinary.android.callback.ErrorInfo
import com.cloudinary.android.callback.UploadCallback
import com.example.newtacks.R
import com.example.newtacks.models.User
import com.example.newtacks.utils.ImageUtils
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

class WorkerVerificationActivity : AppCompatActivity() {

    private val firestore = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()
    
    private lateinit var tvTier: TextView
    private lateinit var tvTierDesc: TextView
    private lateinit var progressBar: ProgressBar
    private lateinit var tvProgressLabel: TextView
    private lateinit var rvSkills: RecyclerView
    private lateinit var loadingOverlay: View
    
    private var currentUser: User? = null
    private var pendingSkill: String? = null

    private val pickCertificate = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let { uploadCertificate(it) }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_worker_verification)

        val toolbar = findViewById<Toolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)
        toolbar.setNavigationOnClickListener { finish() }

        tvTier = findViewById(R.id.tvCurrentTier)
        tvTierDesc = findViewById(R.id.tvTierDescription)
        progressBar = findViewById(R.id.tierProgressBar)
        tvProgressLabel = findViewById(R.id.tvProgressLabel)
        rvSkills = findViewById(R.id.rvSkillVerification)
        loadingOverlay = findViewById(R.id.loadingOverlay)

        rvSkills.layoutManager = LinearLayoutManager(this)
        
        loadUserData()
    }

    private fun loadUserData() {
        val uid = auth.currentUser?.uid ?: return
        firestore.collection("users").document(uid).get().addOnSuccessListener { doc ->
            currentUser = doc.toObject(User::class.java)
            updateUI()
        }
    }

    private fun updateUI() {
        val user = currentUser ?: return
        val skills = user.serviceCategories ?: emptyList()
        val verifiedCount = user.verifiedSkills.size
        val totalCount = skills.size

        // Calculate Tier
        // Tier 0: Resume uploaded (already handled by block logic, but shown here)
        // Tier 1: 50% verified
        // Tier 2: 100% verified
        val ratio = if (totalCount > 0) verifiedCount.toFloat() / totalCount else 0f
        val tier = when {
            ratio >= 1f -> 2
            ratio >= 0.5f -> 1
            else -> 0
        }

        tvTier.text = "Tier $tier: ${if (tier == 2) "Verified Pro" else if (tier == 1) "Trusted" else "Basic"}"
        tvProgressLabel.text = "$verifiedCount/$totalCount Skills Verified"
        progressBar.max = totalCount
        progressBar.progress = verifiedCount
        
        // Update Activity-wide tier in DB if it changed
        if (user.verificationStatus != tier) {
            firestore.collection("users").document(user.uid).update("verificationStatus", tier)
        }

        rvSkills.adapter = VerificationAdapter(skills, user.verifiedSkills) { skill ->
            pendingSkill = skill
            pickCertificate.launch("image/*")
        }
    }

    private fun uploadCertificate(uri: Uri) {
        val skill = pendingSkill ?: return
        loadingOverlay.visibility = View.VISIBLE
        Toast.makeText(this, "Uploading $skill certificate...", Toast.LENGTH_SHORT).show()

        MediaManager.get().upload(uri)
            .option("folder", "worker_skill_certificates")
            .callback(object : UploadCallback {
                override fun onStart(requestId: String?) {}
                override fun onProgress(requestId: String?, bytes: Long, totalBytes: Long) {}
                override fun onSuccess(requestId: String?, resultData: MutableMap<Any?, Any?>?) {
                    val url = resultData?.get("secure_url").toString()
                    saveCertificate(skill, url)
                }
                override fun onError(requestId: String?, error: ErrorInfo?) {
                    loadingOverlay.visibility = View.GONE
                    Toast.makeText(this@WorkerVerificationActivity, "Upload failed", Toast.LENGTH_SHORT).show()
                }
                override fun onReschedule(requestId: String?, error: ErrorInfo?) {}
            }).dispatch()
    }

    private fun saveCertificate(skill: String, url: String) {
        val uid = auth.currentUser?.uid ?: return
        val updatedVerifiedSkills = currentUser?.verifiedSkills?.toMutableMap() ?: mutableMapOf()
        updatedVerifiedSkills[skill] = url

        firestore.collection("users").document(uid).update("verifiedSkills", updatedVerifiedSkills)
            .addOnSuccessListener {
                loadingOverlay.visibility = View.GONE
                Toast.makeText(this, "$skill verified!", Toast.LENGTH_SHORT).show()
                loadUserData()
            }
    }

    inner class VerificationAdapter(
        private val skills: List<String>,
        private val verifiedMap: Map<String, String>,
        private val onUpload: (String) -> Unit
    ) : RecyclerView.Adapter<VerificationAdapter.ViewHolder>() {

        inner class ViewHolder(v: View) : RecyclerView.ViewHolder(v) {
            val tvName: TextView = v.findViewById(R.id.tvSkillName)
            val tvStatus: TextView = v.findViewById(R.id.tvVerificationStatus)
            val btnAction: com.google.android.material.button.MaterialButton = v.findViewById(R.id.btnAction)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val v = LayoutInflater.from(parent.context).inflate(R.layout.item_skill_verification, parent, false)
            return ViewHolder(v)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val skill = skills[position]
            val certUrl = verifiedMap[skill]
            
            holder.tvName.text = skill
            
            if (certUrl != null) {
                holder.tvStatus.text = "Verified"
                holder.tvStatus.setTextColor(android.graphics.Color.parseColor("#16A34A"))
                holder.btnAction.text = "View"
                holder.btnAction.setIconResource(R.drawable.ic_check_circle)
                holder.btnAction.setOnClickListener {
                    ImageUtils.showFullscreenImage(this@WorkerVerificationActivity, certUrl)
                }
            } else {
                holder.tvStatus.text = "Not Verified"
                holder.tvStatus.setTextColor(android.graphics.Color.parseColor("#94A3B8"))
                holder.btnAction.text = "Upload"
                holder.btnAction.setIconResource(R.drawable.ic_plus)
                holder.btnAction.setOnClickListener { onUpload(skill) }
            }
        }

        override fun getItemCount() = skills.size
    }
}