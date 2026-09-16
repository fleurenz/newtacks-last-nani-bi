package com.example.newtacks.worker

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.cloudinary.android.MediaManager
import com.cloudinary.android.callback.ErrorInfo
import com.cloudinary.android.callback.UploadCallback
import com.example.newtacks.R
import com.example.newtacks.models.User
import com.example.newtacks.models.VerificationRequest
import com.example.newtacks.models.WorkerCertificate
import com.example.newtacks.utils.ImageUtils
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration

class WorkerVerificationActivity : AppCompatActivity() {

    private val firestore = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()
    
    private lateinit var tvTier: TextView
    private lateinit var tvTierDesc: TextView
    private lateinit var progressBar: ProgressBar
    private lateinit var tvProgressLabel: TextView
    private lateinit var rvSkills: RecyclerView
    private lateinit var rvOtherCerts: RecyclerView
    private lateinit var btnAddCert: View
    private lateinit var loadingOverlay: View
    
    private var currentUser: User? = null
    private var pendingSkill: String? = null
    
    private var selectedFileUri: Uri? = null
    private var tvFileNameLabel: TextView? = null

    private var requestsListener: ListenerRegistration? = null
    private val pendingRequests = mutableListOf<VerificationRequest>()

    private val pickSkillCert = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let { uploadSkillCertificate(it) }
    }

    private val pickGenericFile = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            selectedFileUri = uri
            tvFileNameLabel?.text = "File selected"
        }
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
        rvOtherCerts = findViewById(R.id.rvOtherCertificates)
        btnAddCert = findViewById(R.id.btnAddCertificate)
        loadingOverlay = findViewById(R.id.loadingOverlay)

        rvSkills.layoutManager = LinearLayoutManager(this)
        rvOtherCerts.layoutManager = LinearLayoutManager(this)
        
        btnAddCert.setOnClickListener { showAddCertificateDialog() }
        
        loadUserData()
        listenForRequests()
    }

    private fun loadUserData() {
        val uid = auth.currentUser?.uid ?: return
        firestore.collection("users").document(uid).get().addOnSuccessListener { doc ->
            currentUser = doc.toObject(User::class.java)
            updateUI()
        }
    }

    private fun listenForRequests() {
        val uid = auth.currentUser?.uid ?: return
        requestsListener = firestore.collection("verification_requests")
            .whereEqualTo("workerId", uid)
            .addSnapshotListener { snapshots, _ ->
                pendingRequests.clear()
                snapshots?.toObjects(VerificationRequest::class.java)?.let {
                    pendingRequests.addAll(it)
                }
                updateUI()
            }
    }

    private fun updateUI() {
        val user = currentUser ?: return
        val skills = user.serviceCategories ?: emptyList()
        val verifiedCount = user.verifiedSkills.size
        val totalCount = skills.size

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
        
        if (user.verificationStatus != tier) {
            firestore.collection("users").document(user.uid).update("verificationStatus", tier)
        }

        rvSkills.adapter = VerificationAdapter(skills, user.verifiedSkills, pendingRequests) { skill ->
            pendingSkill = skill
            pickSkillCert.launch("image/*")
        }
        
        rvOtherCerts.adapter = OtherCertsAdapter(user.otherCertificates, pendingRequests)
    }

    private fun showAddCertificateDialog() {
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_add_certificate, null)
        val spinner = dialogView.findViewById<Spinner>(R.id.spinnerCertType)
        val etName = dialogView.findViewById<EditText>(R.id.etCertName)
        val btnPick = dialogView.findViewById<Button>(R.id.btnPickFile)
        tvFileNameLabel = dialogView.findViewById(R.id.tvFileName)
        val btnUpload = dialogView.findViewById<Button>(R.id.btnUploadCert)
        
        val types = arrayOf("NC1", "NC2", "NC3", "Seminar/Workshop", "Experience Proof", "Other")
        spinner.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, types)
        
        val dialog = AlertDialog.Builder(this).setView(dialogView).create()
        
        btnPick.setOnClickListener {
            pickGenericFile.launch(arrayOf("application/pdf", "application/vnd.openxmlformats-officedocument.wordprocessingml.document", "image/*"))
        }
        
        btnUpload.setOnClickListener {
            val name = etName.text.toString().trim()
            val type = spinner.selectedItem.toString()
            val uri = selectedFileUri
            
            if (name.isEmpty() || uri == null) {
                Toast.makeText(this, "Please fill all fields and select a file", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            
            dialog.dismiss()
            uploadGenericCertificate(uri, name, type)
        }
        
        dialog.show()
    }

    private fun uploadSkillCertificate(uri: Uri) {
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
                    saveSkillRequest(skill, url)
                }
                override fun onError(requestId: String?, error: ErrorInfo?) {
                    loadingOverlay.visibility = View.GONE
                    Toast.makeText(this@WorkerVerificationActivity, "Upload failed", Toast.LENGTH_SHORT).show()
                }
                override fun onReschedule(requestId: String?, error: ErrorInfo?) {}
            }).dispatch()
    }

    private fun saveSkillRequest(skill: String, url: String) {
        val uid = auth.currentUser?.uid ?: return
        val requestId = firestore.collection("verification_requests").document().id
        val request = VerificationRequest(
            requestId = requestId,
            workerId = uid,
            workerName = currentUser?.name ?: "Worker",
            type = "SKILL",
            skillName = skill,
            fileUrl = url,
            status = "PENDING"
        )

        firestore.collection("verification_requests").document(requestId).set(request)
            .addOnSuccessListener {
                loadingOverlay.visibility = View.GONE
                Toast.makeText(this, "$skill verification submitted for review!", Toast.LENGTH_SHORT).show()
            }
    }

    private fun uploadGenericCertificate(uri: Uri, name: String, type: String) {
        loadingOverlay.visibility = View.VISIBLE
        Toast.makeText(this, "Uploading $name...", Toast.LENGTH_SHORT).show()

        MediaManager.get().upload(uri)
            .option("folder", "worker_other_certificates")
            .callback(object : UploadCallback {
                override fun onStart(requestId: String?) {}
                override fun onProgress(requestId: String?, bytes: Long, totalBytes: Long) {}
                override fun onSuccess(requestId: String?, resultData: MutableMap<Any?, Any?>?) {
                    val url = resultData?.get("secure_url").toString()
                    saveGenericRequest(name, type, url)
                }
                override fun onError(requestId: String?, error: ErrorInfo?) {
                    loadingOverlay.visibility = View.GONE
                    Toast.makeText(this@WorkerVerificationActivity, "Upload failed", Toast.LENGTH_SHORT).show()
                }
                override fun onReschedule(requestId: String?, error: ErrorInfo?) {}
            }).dispatch()
    }

    private fun saveGenericRequest(name: String, type: String, url: String) {
        val uid = auth.currentUser?.uid ?: return
        val requestId = firestore.collection("verification_requests").document().id
        val request = VerificationRequest(
            requestId = requestId,
            workerId = uid,
            workerName = currentUser?.name ?: "Worker",
            type = "GENERAL_CERT",
            certName = name,
            certType = type,
            fileUrl = url,
            status = "PENDING"
        )

        firestore.collection("verification_requests").document(requestId).set(request)
            .addOnSuccessListener {
                loadingOverlay.visibility = View.GONE
                Toast.makeText(this, "Certificate submitted for review!", Toast.LENGTH_SHORT).show()
            }
    }

    inner class VerificationAdapter(
        private val skills: List<String>,
        private val verifiedMap: Map<String, String>,
        private val requests: List<VerificationRequest>,
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
            val pending = requests.find { it.type == "SKILL" && it.skillName == skill && it.status == "PENDING" }
            
            holder.tvName.text = skill
            
            if (certUrl != null) {
                holder.tvStatus.text = "Verified"
                holder.tvStatus.setTextColor(android.graphics.Color.parseColor("#16A34A"))
                holder.btnAction.text = "View"
                holder.btnAction.setIconResource(R.drawable.ic_check_circle)
                holder.btnAction.setOnClickListener { openFile(certUrl) }
            } else if (pending != null) {
                holder.tvStatus.text = "Pending Review"
                holder.tvStatus.setTextColor(android.graphics.Color.parseColor("#F59E0B"))
                holder.btnAction.text = "Waiting"
                holder.btnAction.isEnabled = false
                holder.btnAction.setIconResource(R.drawable.ic_inbox)
            } else {
                holder.tvStatus.text = "Not Verified"
                holder.tvStatus.setTextColor(android.graphics.Color.parseColor("#94A3B8"))
                holder.btnAction.text = "Upload"
                holder.btnAction.setEnabled(true)
                holder.btnAction.setIconResource(R.drawable.ic_plus)
                holder.btnAction.setOnClickListener { onUpload(skill) }
            }
        }

        override fun getItemCount() = skills.size
    }

    inner class OtherCertsAdapter(
        private val certs: List<WorkerCertificate>,
        private val requests: List<VerificationRequest>
    ) : RecyclerView.Adapter<OtherCertsAdapter.ViewHolder>() {
        
        private val combinedList: List<Any> get() {
            val list = mutableListOf<Any>()
            list.addAll(certs)
            list.addAll(requests.filter { it.type == "GENERAL_CERT" && it.status == "PENDING" })
            return list
        }

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
            val item = combinedList[position]
            if (item is WorkerCertificate) {
                holder.tvName.text = item.name
                holder.tvStatus.text = item.type
                holder.tvStatus.setTextColor(android.graphics.Color.parseColor("#16A34A"))
                holder.btnAction.text = "View"
                holder.btnAction.setIconResource(R.drawable.ic_check_circle)
                holder.btnAction.setOnClickListener { openFile(item.url) }
            } else if (item is VerificationRequest) {
                holder.tvName.text = item.certName
                holder.tvStatus.text = "Pending Review"
                holder.tvStatus.setTextColor(android.graphics.Color.parseColor("#F59E0B"))
                holder.btnAction.text = "Waiting"
                holder.btnAction.isEnabled = false
                holder.btnAction.setIconResource(R.drawable.ic_inbox)
            }
        }
        override fun getItemCount() = combinedList.size
    }

    private fun openFile(url: String) {
        val isImage = url.contains(".jpg", true) || url.contains(".png", true) || url.contains(".jpeg", true)
        if (isImage) {
            ImageUtils.showFullscreenImage(this, url)
        } else {
            val viewerUrl = "https://docs.google.com/viewer?url=$url"
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(viewerUrl))
            startActivity(intent)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        requestsListener?.remove()
    }
}