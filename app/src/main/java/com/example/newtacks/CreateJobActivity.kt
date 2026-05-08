package com.example.newtacks

import android.app.DatePickerDialog
import android.app.Dialog
import android.app.TimePickerDialog
import android.content.Intent
import android.os.Bundle
import android.view.MotionEvent
import android.view.View
import android.widget.*
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import com.example.newtacks.models.Job
import com.example.newtacks.models.User
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import android.net.Uri
import androidx.activity.result.contract.ActivityResultContracts
import com.cloudinary.android.MediaManager
import com.cloudinary.android.callback.ErrorInfo
import com.cloudinary.android.callback.UploadCallback
import java.util.Locale
import java.util.*

class CreateJobActivity : AppCompatActivity() {

    private lateinit var auth: FirebaseAuth
    private lateinit var firestore: FirebaseFirestore

    private lateinit var etJobTitle: EditText
    private lateinit var etClientName: EditText
    private lateinit var etClientAddress: EditText

    private lateinit var spinnerServiceType: Spinner
    private lateinit var btnSelectDate: Button
    private lateinit var btnSelectTime: Button
    private lateinit var etDuration: EditText
    private lateinit var etOfferAmount: EditText
    private lateinit var etDescription: EditText

    private lateinit var btnCancel: Button
    private lateinit var btnSubmit: Button

    private lateinit var btnAddPhoto: Button
    private lateinit var layoutImages: LinearLayout

    private var selectedDate = ""
    private var selectedTime = ""

    private var isUserEditingTitle = false

    private val selectedImages = mutableListOf<Uri>()

    private val pickImage =
        registerForActivityResult(ActivityResultContracts.GetMultipleContents()) { uris ->
            uris.forEach { uri ->
                if (selectedImages.size < 5) {
                    selectedImages.add(uri)
                    addImagePreview(uri)
                }
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_create_job)

        auth = FirebaseAuth.getInstance()
        firestore = FirebaseFirestore.getInstance()

        // ===== TOOLBAR =====
        val toolbar = findViewById<androidx.appcompat.widget.Toolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)
        toolbar.setNavigationOnClickListener { finish() }

        initializeViews()
        loadClientInformation()
        setupServiceSpinner()
        setupDatePicker()
        setupTimePicker()

        btnCancel.setOnClickListener { handleBackPress() }
        btnSubmit.setOnClickListener { submitJob() }

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                handleBackPress()
            }
        })
    }

    private fun handleBackPress() {
        if (isFormDirty()) {
            showDiscardDialog()
        } else {
            finish()
        }
    }

    private fun isFormDirty(): Boolean {
        return etJobTitle.text.isNotEmpty() ||
                etDescription.text.isNotEmpty() ||
                etOfferAmount.text.isNotEmpty() ||
                selectedImages.isNotEmpty() ||
                selectedDate.isNotEmpty() ||
                selectedTime.isNotEmpty()
    }

    private fun showDiscardDialog() {
        val dialog = Dialog(this)
        dialog.setContentView(R.layout.dialog_role_select)
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
        dialog.window?.setLayout(
            (resources.displayMetrics.widthPixels * 0.88).toInt(),
            android.view.ViewGroup.LayoutParams.WRAP_CONTENT
        )

        dialog.findViewById<ImageView>(R.id.dialogIcon).setImageResource(R.drawable.ic_close)
        dialog.findViewById<TextView>(R.id.dialogTitle).text = "Discard Changes?"
        dialog.findViewById<TextView>(R.id.dialogMessage).text = "Are you sure you want to discard this job post?"

        dialog.findViewById<com.google.android.material.button.MaterialButton>(R.id.dialogBtnPositive).apply {
            this.text = "Discard"
            this.setOnClickListener {
                dialog.dismiss()
                finish()
            }
        }

        dialog.findViewById<com.google.android.material.button.MaterialButton>(R.id.dialogBtnNegative).apply {
            this.text = "Keep Editing"
            this.setOnClickListener {
                dialog.dismiss()
            }
        }

        dialog.show()
    }



    // ---------------- INIT ----------------

    private fun initializeViews() {

        etJobTitle = findViewById(R.id.etJobTitle)
        etClientName = findViewById(R.id.etClientName)
        etClientAddress = findViewById(R.id.etClientAddress)

        spinnerServiceType = findViewById(R.id.spinnerServiceType)

        btnSelectDate = findViewById(R.id.btnSelectDate)
        btnSelectTime = findViewById(R.id.btnSelectTime)

        etDuration = findViewById(R.id.etDuration)
        etOfferAmount = findViewById(R.id.etOfferAmount)
        etDescription = findViewById(R.id.etDescription)

        btnCancel = findViewById(R.id.btnCancel)
        btnSubmit = findViewById(R.id.btnSubmit)

        btnAddPhoto = findViewById(R.id.btnAddPhoto)
        layoutImages = findViewById(R.id.layoutImages)

        btnAddPhoto.setOnClickListener {
            pickImage.launch("image/*")
        }

        // detect manual editing
        etJobTitle.setOnFocusChangeListener { _, hasFocus ->
            if (hasFocus) isUserEditingTitle = true
        }

        btnSubmit.setOnTouchListener { v, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    v.animate().translationY(6f).setDuration(80).start()
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    v.animate().translationY(0f).setDuration(80).start()
                }
            }
            false
        }
    }


    // ---------------- USER DATA ----------------

    private fun loadClientInformation() {

        val currentUser = auth.currentUser ?: return

        firestore.collection("users")
            .document(currentUser.uid)
            .get()
            .addOnSuccessListener { doc ->

                val user = doc.toObject(User::class.java)

                if (user != null) {
                    etClientName.setText(user.name)
                    etClientAddress.setText(user.address)

                    etClientName.isEnabled = false
                    etClientAddress.isEnabled = false
                }
            }
            .addOnFailureListener {
                Toast.makeText(this, "Failed to load user info", Toast.LENGTH_SHORT).show()
            }
    }

    // ---------------- SERVICE TYPE + TITLE ----------------

    private fun setupServiceSpinner() {

        val services = arrayOf(
            "Plumbing",
            "Electrical",
            "Carpentry"
        )

        val adapter = ArrayAdapter(
            this,
            android.R.layout.simple_spinner_dropdown_item,
            services
        )

        spinnerServiceType.adapter = adapter

        spinnerServiceType.onItemSelectedListener =
            object : AdapterView.OnItemSelectedListener {

                override fun onItemSelected(
                    parent: AdapterView<*>?,
                    view: View?,
                    position: Int,
                    id: Long
                ) {

                    val selectedService = services[position]
                    val generatedTitle = "$selectedService Request"

                    if (!isUserEditingTitle) {
                        etJobTitle.setText(generatedTitle)
                    }
                }

                override fun onNothingSelected(parent: AdapterView<*>?) {}
            }
    }

    // ---------------- DATE PICKER ----------------

    private fun setupDatePicker() {

        btnSelectDate.setOnClickListener {

            val calendar = Calendar.getInstance()

            DatePickerDialog(
                this,
                { _, year, month, day ->

                    selectedDate = "${month + 1}/$day/$year"
                    btnSelectDate.text = selectedDate

                },
                calendar.get(Calendar.YEAR),
                calendar.get(Calendar.MONTH),
                calendar.get(Calendar.DAY_OF_MONTH)
            ).show()
        }
    }

    // ---------------- TIME PICKER ----------------

    private fun setupTimePicker() {

        btnSelectTime.setOnClickListener {

            val calendar = Calendar.getInstance()

            TimePickerDialog(
                this,
                { _, hour, minute ->

                    selectedTime = String.format(Locale.getDefault(), "%02d:%02d", hour, minute)
                    btnSelectTime.text = selectedTime

                },
                calendar.get(Calendar.HOUR_OF_DAY),
                calendar.get(Calendar.MINUTE),
                true
            ).show()
        }
    }

    private fun addImagePreview(uri: Uri) {
        val imageView = ImageView(this)
        val params = LinearLayout.LayoutParams(250, 250)
        params.setMargins(0, 0, 16, 0)
        imageView.layoutParams = params
        imageView.scaleType = ImageView.ScaleType.CENTER_CROP
        imageView.setImageURI(uri)
        layoutImages.addView(imageView)
    }

    // ---------------- SUBMIT JOB ----------------

    private fun submitJob() {

        val currentUser = auth.currentUser

        if (currentUser == null) {
            Toast.makeText(this, "User not authenticated", Toast.LENGTH_SHORT).show()
            return
        }

        val jobTitle = etJobTitle.text.toString().trim()
        val clientName = etClientName.text.toString().trim()
        val clientAddress = etClientAddress.text.toString().trim()

        val serviceCategory = spinnerServiceType.selectedItem.toString()

        val durationInput = etDuration.text.toString().trim()
        val offerInput = etOfferAmount.text.toString().trim()

        val description = etDescription.text.toString().trim()

        // ---------------- VALIDATION ----------------

        if (
            jobTitle.isEmpty() ||
            clientName.isEmpty() ||
            clientAddress.isEmpty() ||
            selectedDate.isEmpty() ||
            selectedTime.isEmpty() ||
            durationInput.isEmpty() ||
            offerInput.isEmpty() ||
            description.isEmpty()
        ) {
            Toast.makeText(this, "Please complete all fields", Toast.LENGTH_SHORT).show()
            return
        }

        val estimatedDuration = durationInput.toDoubleOrNull()
        val offeredAmount = offerInput.toDoubleOrNull()

        if (estimatedDuration == null || offeredAmount == null) {
            Toast.makeText(this, "Invalid number input", Toast.LENGTH_SHORT).show()
            return
        }

        // ---------------- UI LOCK ----------------
        btnSubmit.isEnabled = false

        // ---------------- CHECK ACTIVE JOB FIRST ----------------

        firestore.collection("jobs")
            .whereEqualTo("clientId", currentUser.uid)
            .whereIn(
                "status",
                listOf(
                    "AVAILABLE",
                    "IN_PROGRESS",
                    "PENDING_VERIFICATION"
                )
            )
            .get()
            .addOnSuccessListener { snapshots ->

                // CLIENT ALREADY HAS ACTIVE JOB
                if (!snapshots.isEmpty) {
                    Toast.makeText(
                        this,
                        "You already have an active request",
                        Toast.LENGTH_LONG
                    ).show()
                    btnSubmit.isEnabled = true
                    return@addOnSuccessListener
                }

                // ---------------- UPLOAD IMAGES THEN CREATE JOB ----------------
                uploadImagesAndCreateJob(currentUser.uid, clientName, clientAddress, jobTitle, serviceCategory, estimatedDuration, offeredAmount, description)
            }
            .addOnFailureListener {
                btnSubmit.isEnabled = true
                Toast.makeText(this, "Error checking active jobs", Toast.LENGTH_SHORT).show()
            }
    }

    private fun uploadImagesAndCreateJob(
        uid: String,
        clientName: String,
        clientAddress: String,
        jobTitle: String,
        serviceCategory: String,
        estimatedDuration: Double,
        offeredAmount: Double,
        description: String
    ) {
        if (selectedImages.isEmpty()) {
            finalizeJobCreation(uid, clientName, clientAddress, jobTitle, serviceCategory, estimatedDuration, offeredAmount, description, emptyList())
            return
        }

        val uploadedUrls = mutableListOf<String>()
        var uploadCount = 0

        selectedImages.forEach { uri ->
            MediaManager.get().upload(uri)
                .option("folder", "job_requests")
                .callback(object : UploadCallback {
                    override fun onStart(requestId: String?) {}
                    override fun onProgress(requestId: String?, bytes: Long, totalBytes: Long) {}
                    override fun onSuccess(requestId: String?, resultData: MutableMap<Any?, Any?>?) {
                        val imageUrl = resultData?.get("secure_url").toString()
                        uploadedUrls.add(imageUrl)
                        uploadCount++
                        if (uploadCount == selectedImages.size) {
                            finalizeJobCreation(uid, clientName, clientAddress, jobTitle, serviceCategory, estimatedDuration, offeredAmount, description, uploadedUrls)
                        }
                    }
                    override fun onError(requestId: String?, error: ErrorInfo?) {
                        uploadCount++
                        if (uploadCount == selectedImages.size) {
                            finalizeJobCreation(uid, clientName, clientAddress, jobTitle, serviceCategory, estimatedDuration, offeredAmount, description, uploadedUrls)
                        }
                    }
                    override fun onReschedule(requestId: String?, error: ErrorInfo?) {}
                }).dispatch()
        }
    }

    private fun finalizeJobCreation(
        uid: String,
        clientName: String,
        clientAddress: String,
        jobTitle: String,
        serviceCategory: String,
        estimatedDuration: Double,
        offeredAmount: Double,
        description: String,
        jobImages: List<String>
    ) {
        val jobId = firestore.collection("jobs").document().id
        val job = Job(
            jobId = jobId,
            clientId = uid,
            clientName = clientName,
            clientAddress = clientAddress,
            jobTitle = jobTitle,
            serviceCategory = serviceCategory,
            scheduledDate = selectedDate,
            scheduledTime = selectedTime,
            estimatedDurationHours = estimatedDuration,
            offeredAmount = offeredAmount,
            description = description,
            status = "AVAILABLE",
            jobImages = jobImages
        )

        firestore.collection("jobs")
            .document(jobId)
            .set(job)
            .addOnSuccessListener {
                Toast.makeText(this, "Job Submitted Successfully", Toast.LENGTH_SHORT).show()
                val intent = Intent(this, ClientDashboardActivity::class.java)
                intent.putExtra(ClientDashboardActivity.OPEN_FRAGMENT, "REQUESTS")
                intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                startActivity(intent)
                finish()
            }
            .addOnFailureListener {
                btnSubmit.isEnabled = true
                Toast.makeText(this, "Failed to submit job", Toast.LENGTH_SHORT).show()
            }
    }
}