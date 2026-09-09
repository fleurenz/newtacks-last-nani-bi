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
import androidx.core.content.ContextCompat
import android.text.Editable
import android.text.TextWatcher
import android.content.res.ColorStateList
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import android.text.InputType
import com.example.newtacks.models.Job
import com.example.newtacks.models.User
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import android.net.Uri
import androidx.activity.result.contract.ActivityResultContracts
import com.cloudinary.android.MediaManager
import com.cloudinary.android.callback.ErrorInfo
import com.cloudinary.android.callback.UploadCallback
import com.example.newtacks.chatbot.presentation.ui.ChatActivity
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import android.location.Geocoder
import java.util.Locale
import java.util.*

class CreateJobActivity : AppCompatActivity() {

    private lateinit var auth: FirebaseAuth
    private lateinit var firestore: FirebaseFirestore

    private lateinit var etJobTitle: EditText
    private lateinit var etClientName: EditText
    private lateinit var etClientAddress: EditText
    private lateinit var switchRealTimeLocation: com.google.android.material.switchmaterial.SwitchMaterial

    private lateinit var fusedLocationClient: FusedLocationProviderClient

    private lateinit var spinnerServiceType: Spinner
    private lateinit var spinnerRateType: Spinner
    private lateinit var btnSelectDate: com.google.android.material.button.MaterialButton
    private lateinit var btnSelectTime: com.google.android.material.button.MaterialButton
    private lateinit var etOfferAmount: EditText
    private lateinit var etDescription: EditText

    private lateinit var createJobScrollView: View
    private lateinit var loadingOverlay: View
    private lateinit var tvLoadingMessage: TextView
    private lateinit var tvToolbarTitle: TextView

    private lateinit var btnCancel: Button
    private lateinit var btnSubmit: Button

    private lateinit var btnAddPhotoCard: View
    private lateinit var layoutImages: LinearLayout

    private var selectedDate = ""
    private var selectedTime = ""
    private var selectedLat: Double = 0.0
    private var selectedLng: Double = 0.0

    private var profileAddress = ""
    private var profileLat = 0.0
    private var profileLng = 0.0

    private var isSubmitting = false
    private var isDetectingLocation = false
    private var editingJobId: String? = null

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

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        auth = FirebaseAuth.getInstance()
        firestore = FirebaseFirestore.getInstance()

        // Robust Inset Handling: Use spacer for status bar
        val statusBarSpacer = findViewById<View>(R.id.statusBarSpacer)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(android.R.id.content)) { _, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            val params = statusBarSpacer.layoutParams
            params.height = systemBars.top
            statusBarSpacer.layoutParams = params
            insets
        }

        // ===== TOOLBAR =====
        val toolbar = findViewById<androidx.appcompat.widget.Toolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayShowTitleEnabled(false)
        toolbar.setNavigationOnClickListener { finish() }

        initializeViews()
        setupStatusBarPadding()
        
        editingJobId = intent.getStringExtra("EDIT_JOB_ID")
        if (editingJobId != null) {
            tvToolbarTitle.text = "Edit Job Post"
            btnSubmit.text = "Update Job"
            loadJobForEditing(editingJobId!!)
        } else {
            loadClientInformation()
        }
        
        setupServiceSpinner()
        setupRateSpinner()
        setupDatePicker()
        setupTimePicker()

        btnCancel.setOnClickListener { handleBackPress() }
        btnSubmit.setOnClickListener { submitJob() }

        setupValidationListeners()

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                handleBackPress()
            }
        })
    }

    private fun setupStatusBarPadding() {
        val toolbar = findViewById<androidx.appcompat.widget.Toolbar>(R.id.toolbar)
        val rootView = findViewById<View>(android.R.id.content)
        ViewCompat.setOnApplyWindowInsetsListener(toolbar) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.updatePadding(top = systemBars.top + (resources.displayMetrics.density * 8).toInt())
            insets
        }
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
        createJobScrollView = findViewById(R.id.createJobScrollView)
        loadingOverlay = findViewById(R.id.loadingOverlay)
        tvLoadingMessage = findViewById(R.id.tvLoadingMessage)
        tvToolbarTitle = findViewById(R.id.tvToolbarTitle)
        
        etJobTitle = findViewById(R.id.etJobTitle)
        etClientName = findViewById(R.id.etClientName)
        etClientAddress = findViewById(R.id.etClientAddress)
        switchRealTimeLocation = findViewById(R.id.switchRealTimeLocation)

        switchRealTimeLocation.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                detectRealTimeLocation()
            } else {
                isDetectingLocation = false
                restoreProfileLocation()
            }
        }

        spinnerServiceType = findViewById(R.id.spinnerServiceType)
        spinnerRateType = findViewById(R.id.spinnerRateType)

        btnSelectDate = findViewById(R.id.btnSelectDate)
        btnSelectTime = findViewById(R.id.btnSelectTime)

        etOfferAmount = findViewById(R.id.etOfferAmount)
        etDescription = findViewById(R.id.etDescription)

        btnCancel = findViewById(R.id.btnCancel)
        btnSubmit = findViewById(R.id.btnSubmit)

        btnAddPhotoCard = findViewById(R.id.btnAddPhotoCard)
        layoutImages = findViewById(R.id.layoutImages)

        btnAddPhotoCard.setOnClickListener {
            pickImage.launch("image/*")
        }

        findViewById<com.google.android.material.floatingactionbutton.FloatingActionButton>(R.id.fabChat).setOnClickListener {
            val intent = Intent(this, ChatActivity::class.java)
            intent.putExtra("USER_ROLE", "client")
            startActivity(intent)
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
                    
                    profileAddress = user.address
                    profileLat = user.latitude ?: 0.0
                    profileLng = user.longitude ?: 0.0
                    
                    selectedLat = profileLat
                    selectedLng = profileLng

                    etClientName.isEnabled = false
                }
            }
            .addOnFailureListener {
                Toast.makeText(this, "Failed to load user info", Toast.LENGTH_SHORT).show()
            }
    }

    private fun loadJobForEditing(jobId: String) {
        loadingOverlay.visibility = View.VISIBLE
        tvLoadingMessage.text = "Loading job details..."
        
        firestore.collection("jobs").document(jobId).get()
            .addOnSuccessListener { doc ->
                loadingOverlay.visibility = View.GONE
                val job = doc.toObject(Job::class.java) ?: return@addOnSuccessListener
                
                etJobTitle.setText(job.jobTitle)
                etDescription.setText(job.description)
                etOfferAmount.setText(job.offeredAmount.toInt().toString())
                selectedDate = job.scheduledDate
                btnSelectDate.text = selectedDate
                selectedTime = job.scheduledTime
                btnSelectTime.text = selectedTime
                
                etClientName.setText(job.clientName)
                etClientAddress.setText(job.clientAddress)
                selectedLat = job.latitude
                selectedLng = job.longitude
                
                // Pre-select service
                val services = arrayOf("Plumbing", "Electrical", "Carpentry", "Masonry", "Welding", "Painting", "Landscaping", "Others")
                val index = services.indexOf(job.serviceCategory)
                if (index != -1) spinnerServiceType.setSelection(index)
                
                // Pre-select rate
                val rates = arrayOf("One-time", "Per Hour", "Per Day")
                val rateIndex = rates.indexOf(job.rateType)
                if (rateIndex != -1) spinnerRateType.setSelection(rateIndex)
                
                // Note: handling existing images for editing would require more logic (showing them as URLs)
                // For now, we'll just keep it simple. If they add new images, they replace or add to the list?
                // The user said "necessary ones", so let's focus on text data first.
            }
            .addOnFailureListener {
                loadingOverlay.visibility = View.GONE
                Toast.makeText(this, "Failed to load job", Toast.LENGTH_SHORT).show()
            }
    }

    // ---------------- SERVICE TYPE + TITLE ----------------

    private fun setupServiceSpinner() {
        val services = arrayOf(
            "Plumbing", "Electrical", "Carpentry", "Masonry", "Welding", "Painting", "Landscaping", "Others"
        )

        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, services)
        spinnerServiceType.adapter = adapter

        // Pre-select service if passed from intent
        val preselected = intent.getStringExtra("SELECTED_SERVICE")
        if (preselected != null) {
            val index = services.indexOf(preselected)
            if (index != -1) {
                spinnerServiceType.setSelection(index)
                updateJobTitleField(preselected) // Force immediate update
            }
        } else {
            updateJobTitleField(services[0])
        }

        spinnerServiceType.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                updateJobTitleField(services[position])
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }
    }

    private fun updateJobTitleField(selectedService: String) {
        val isOthers = selectedService == "Others"
        tvToolbarTitle.text = getString(R.string.request_service_format, selectedService)

        if (isOthers) {
            etJobTitle.isEnabled = true
            etJobTitle.isFocusable = true
            etJobTitle.isFocusableInTouchMode = true
            etJobTitle.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
            
            // Clear if it has the default "Request" suffix
            val currentText = etJobTitle.text.toString()
            if (currentText.contains(" Request")) {
                etJobTitle.setText("")
            }
            
            etJobTitle.hint = "e.g. Broken Faucet Repair"
            etJobTitle.requestFocus()
        } else {
            val generatedTitle = "$selectedService Request"
            etJobTitle.setText(generatedTitle)
            
            // Strictly lock the field
            etJobTitle.inputType = InputType.TYPE_NULL
            etJobTitle.isFocusable = false
            etJobTitle.isFocusableInTouchMode = false
            etJobTitle.isEnabled = true // Keep it "bright" but uneditable
            etJobTitle.error = null
        }
    }

    private fun setupRateSpinner() {
        val rates = arrayOf("One-time", "Per Hour", "Per Day")
        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, rates)
        spinnerRateType.adapter = adapter
    }

    // ---------------- DATE PICKER ----------------

    private fun setupDatePicker() {
        btnSelectDate.setOnClickListener {
            val calendar = Calendar.getInstance()
            val dialog = DatePickerDialog(
                this,
                { _, year, month, day ->
                    selectedDate = "${month + 1}/$day/$year"
                    btnSelectDate.text = selectedDate
                    btnSelectDate.setStrokeColor(ColorStateList.valueOf(ContextCompat.getColor(this, R.color.stroke_color)))
                    btnSelectDate.strokeWidth = resources.getDimensionPixelSize(R.dimen.normal_stroke_width)
                    
                    // If date changed, reset time to prevent "past time" on today's date
                    selectedTime = ""
                    btnSelectTime.text = "Select Time"
                },
                calendar.get(Calendar.YEAR),
                calendar.get(Calendar.MONTH),
                calendar.get(Calendar.DAY_OF_MONTH)
            )
            // Prevent selecting past dates
            dialog.datePicker.minDate = System.currentTimeMillis() - 1000
            dialog.show()
        }
    }

    // ---------------- TIME PICKER ----------------

    private fun setupTimePicker() {
        btnSelectTime.setOnClickListener {
            if (selectedDate.isEmpty()) {
                Toast.makeText(this, "Please select a date first", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val calendar = Calendar.getInstance()
            TimePickerDialog(
                this,
                { _, hour, minute ->
                    // Validate if selected date is today
                    val today = Calendar.getInstance()
                    val todayStr = "${today.get(Calendar.MONTH) + 1}/${today.get(Calendar.DAY_OF_MONTH)}/${today.get(Calendar.YEAR)}"
                    
                    if (selectedDate == todayStr) {
                        val selectedCal = Calendar.getInstance()
                        selectedCal.set(Calendar.HOUR_OF_DAY, hour)
                        selectedCal.set(Calendar.MINUTE, minute)
                        
                        if (selectedCal.before(today)) {
                            Toast.makeText(this, "Cannot select a past time for today", Toast.LENGTH_SHORT).show()
                            return@TimePickerDialog
                        }
                    }

                    selectedTime = String.format(Locale.getDefault(), "%02d:%02d", hour, minute)
                    btnSelectTime.text = selectedTime
                    btnSelectTime.setStrokeColor(ColorStateList.valueOf(ContextCompat.getColor(this, R.color.stroke_color)))
                    btnSelectTime.strokeWidth = resources.getDimensionPixelSize(R.dimen.normal_stroke_width)
                },
                calendar.get(Calendar.HOUR_OF_DAY),
                calendar.get(Calendar.MINUTE),
                true
            ).show()
        }
    }

    private fun detectRealTimeLocation() {
        if (androidx.core.content.ContextCompat.checkSelfPermission(this, android.Manifest.permission.ACCESS_FINE_LOCATION) 
            != android.content.pm.PackageManager.PERMISSION_GRANTED) {
            requestPermissionLauncher.launch(android.Manifest.permission.ACCESS_FINE_LOCATION)
            return
        }

        isDetectingLocation = true
        etClientAddress.setText("Detecting location...")

        fusedLocationClient.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, null)
            .addOnSuccessListener { location ->
                isDetectingLocation = false
                if (location != null) {
                    selectedLat = location.latitude
                    selectedLng = location.longitude
                    reverseGeocode(location.latitude, location.longitude)
                } else {
                    Toast.makeText(this, "Could not get location. Ensure GPS is on.", Toast.LENGTH_SHORT).show()
                    switchRealTimeLocation.isChecked = false
                    restoreProfileLocation()
                }
            }
            .addOnFailureListener {
                isDetectingLocation = false
                Toast.makeText(this, "Location detection failed", Toast.LENGTH_SHORT).show()
                switchRealTimeLocation.isChecked = false
                restoreProfileLocation()
            }
    }

    private fun reverseGeocode(lat: Double, lng: Double) {
        try {
            val geocoder = Geocoder(this, Locale.getDefault())
            val addresses = geocoder.getFromLocation(lat, lng, 1)
            if (addresses != null && addresses.isNotEmpty()) {
                etClientAddress.setText(addresses[0].getAddressLine(0))
            } else {
                etClientAddress.setText("Coordinates found, but address unavailable")
            }
        } catch (e: Exception) {
            e.printStackTrace()
            etClientAddress.setText("Coordinates found, but address unavailable")
        }
    }

    private fun restoreProfileLocation() {
        etClientAddress.setText(profileAddress)
        selectedLat = profileLat
        selectedLng = profileLng
    }

    private val requestPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
            if (isGranted) detectRealTimeLocation()
            else switchRealTimeLocation.isChecked = false
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
        if (isSubmitting) return

        val currentUser = auth.currentUser
        if (currentUser == null) {
            Toast.makeText(this, "User not authenticated", Toast.LENGTH_SHORT).show()
            return
        }

        if (isDetectingLocation) {
            Toast.makeText(this, "Still detecting location. Please wait.", Toast.LENGTH_SHORT).show()
            return
        }

        val jobTitle = etJobTitle.text.toString().trim()
        val clientName = etClientName.text.toString().trim()
        val clientAddress = etClientAddress.text.toString().trim()
        val serviceCategory = spinnerServiceType.selectedItem.toString()
        val offerInput = etOfferAmount.text.toString().trim()
        val description = etDescription.text.toString().trim()
        val rateType = spinnerRateType.selectedItem.toString()

        if (!validateForm()) {
            Toast.makeText(this, "Please complete all fields", Toast.LENGTH_SHORT).show()
            return
        }

        if (selectedLat == 0.0 || selectedLng == 0.0) {
            Toast.makeText(this, "Location coordinates not found.", Toast.LENGTH_LONG).show()
            return
        }

        val offeredAmount = offerInput.toDoubleOrNull()
        if (offeredAmount == null) {
            Toast.makeText(this, "Invalid number input", Toast.LENGTH_SHORT).show()
            return
        }

        isSubmitting = true
        loadingOverlay.visibility = View.VISIBLE
        
        if (editingJobId != null) {
            tvLoadingMessage.text = "Updating job..."
            // For updates, we skip the "active job check" because this IS the active job
            uploadImagesAndCreateJob(currentUser.uid, clientName, clientAddress, jobTitle, serviceCategory, offeredAmount, description, rateType)
            return
        }

        tvLoadingMessage.text = "Checking for active jobs..."

        firestore.collection("jobs")
            .whereEqualTo("clientId", currentUser.uid)
            .get(com.google.firebase.firestore.Source.SERVER)
            .addOnSuccessListener { snapshots ->
                val activeStatuses = listOf("AVAILABLE", "IN_PROGRESS", "HEADING_TO_CLIENT", "ARRIVED", "PENDING_VERIFICATION", "REJECTED_BY_CLIENT")
                val hasActiveJob = snapshots.documents.any { 
                    val status = it.getString("status") ?: ""
                    status in activeStatuses 
                }

                if (hasActiveJob) {
                    Toast.makeText(this, "You already have an active request", Toast.LENGTH_LONG).show()
                    isSubmitting = false
                    loadingOverlay.visibility = View.GONE
                    return@addOnSuccessListener
                }

                tvLoadingMessage.text = "Uploading images..."
                uploadImagesAndCreateJob(currentUser.uid, clientName, clientAddress, jobTitle, serviceCategory, offeredAmount, description, rateType)
            }
            .addOnFailureListener {
                isSubmitting = false
                loadingOverlay.visibility = View.GONE
                Toast.makeText(this, "Error checking active jobs", Toast.LENGTH_SHORT).show()
            }
    }

    private fun setupValidationListeners() {
        val watcher = object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                val focusedView = currentFocus
                if (focusedView is EditText) {
                    focusedView.error = null
                }
            }
        }
        etJobTitle.addTextChangedListener(watcher)
        etClientAddress.addTextChangedListener(watcher)
        etOfferAmount.addTextChangedListener(watcher)
        etDescription.addTextChangedListener(watcher)
    }

    private fun validateForm(): Boolean {
        var isValid = true
        var firstErrorView: View? = null

        if (etJobTitle.text.toString().trim().isEmpty()) {
            etJobTitle.error = "Job title is required"
            if (firstErrorView == null) firstErrorView = etJobTitle
            isValid = false
        }
        if (etClientAddress.text.toString().trim().isEmpty() || etClientAddress.text.toString() == "Detecting location...") {
            etClientAddress.error = "Address is required"
            if (firstErrorView == null) firstErrorView = etClientAddress
            isValid = false
        }
        if (selectedDate.isEmpty()) {
            btnSelectDate.setStrokeColor(ColorStateList.valueOf(ContextCompat.getColor(this, R.color.error_red)))
            btnSelectDate.strokeWidth = resources.getDimensionPixelSize(R.dimen.error_stroke_width)
            if (firstErrorView == null) firstErrorView = btnSelectDate
            isValid = false
        }
        if (selectedTime.isEmpty()) {
            btnSelectTime.setStrokeColor(ColorStateList.valueOf(ContextCompat.getColor(this, R.color.error_red)))
            btnSelectTime.strokeWidth = resources.getDimensionPixelSize(R.dimen.error_stroke_width)
            if (firstErrorView == null) firstErrorView = btnSelectTime
            isValid = false
        }
        if (etOfferAmount.text.toString().trim().isEmpty()) {
            etOfferAmount.error = "Offer amount is required"
            if (firstErrorView == null) firstErrorView = etOfferAmount
            isValid = false
        }
        if (etDescription.text.toString().trim().isEmpty()) {
            etDescription.error = "Description is required"
            if (firstErrorView == null) firstErrorView = etDescription
            isValid = false
        }

        firstErrorView?.let {
            it.requestFocus()
            val location = IntArray(2)
            it.getLocationInWindow(location)
            (createJobScrollView as? ScrollView)?.smoothScrollTo(0, location[1] - 200)
            (createJobScrollView as? androidx.core.widget.NestedScrollView)?.smoothScrollTo(0, location[1] - 200)
        }
        return isValid
    }

    private fun uploadImagesAndCreateJob(
        uid: String,
        clientName: String,
        clientAddress: String,
        jobTitle: String,
        serviceCategory: String,
        offeredAmount: Double,
        description: String,
        rateType: String
    ) {
        if (selectedImages.isEmpty()) {
            finalizeJobCreation(uid, clientName, clientAddress, jobTitle, serviceCategory, offeredAmount, description, rateType, emptyList())
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
                            finalizeJobCreation(uid, clientName, clientAddress, jobTitle, serviceCategory, offeredAmount, description, rateType, uploadedUrls)
                        }
                    }
                    override fun onError(requestId: String?, error: ErrorInfo?) {
                        uploadCount++
                        if (uploadCount == selectedImages.size) {
                            finalizeJobCreation(uid, clientName, clientAddress, jobTitle, serviceCategory, offeredAmount, description, rateType, uploadedUrls)
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
        offeredAmount: Double,
        description: String,
        rateType: String,
        jobImages: List<String>
    ) {
        tvLoadingMessage.text = if (editingJobId != null) "Updating request..." else "Finalizing request..."
        val jobsRef = firestore.collection("jobs")
        
        if (editingJobId != null) {
            val updates = mutableMapOf<String, Any>(
                "jobTitle" to jobTitle,
                "clientAddress" to clientAddress,
                "serviceCategory" to serviceCategory,
                "offeredAmount" to offeredAmount,
                "description" to description,
                "rateType" to rateType,
                "scheduledDate" to selectedDate,
                "scheduledTime" to selectedTime,
                "latitude" to selectedLat,
                "longitude" to selectedLng
            )
            // Only update images if new ones were added
            if (jobImages.isNotEmpty()) {
                updates["jobImages"] = jobImages
            }
            
            jobsRef.document(editingJobId!!).update(updates)
                .addOnSuccessListener {
                    Toast.makeText(this, "Job Updated Successfully", Toast.LENGTH_SHORT).show()
                    finish()
                }
                .addOnFailureListener {
                    isSubmitting = false
                    loadingOverlay.visibility = View.GONE
                    Toast.makeText(this, "Update failed", Toast.LENGTH_SHORT).show()
                }
            return
        }

        jobsRef.whereEqualTo("clientId", uid).get().addOnSuccessListener { snapshots ->
            val activeStatuses = listOf("AVAILABLE", "IN_PROGRESS", "HEADING_TO_CLIENT", "ARRIVED", "PENDING_VERIFICATION", "REJECTED_BY_CLIENT")
            val hasActiveJob = snapshots.documents.any { it.getString("status") in activeStatuses }

            if (hasActiveJob) {
                Toast.makeText(this, "You already have an active request", Toast.LENGTH_LONG).show()
                isSubmitting = false
                loadingOverlay.visibility = View.GONE
                return@addOnSuccessListener
            }

            val jobId = jobsRef.document().id
            val job = Job(
                jobId = jobId,
                clientId = uid,
                clientName = clientName,
                clientAddress = clientAddress,
                jobTitle = jobTitle,
                serviceCategory = serviceCategory,
                scheduledDate = selectedDate,
                scheduledTime = selectedTime,
                offeredAmount = offeredAmount,
                rateType = rateType,
                description = description,
                status = "AVAILABLE",
                jobImages = jobImages,
                latitude = selectedLat,
                longitude = selectedLng,
                createdAt = System.currentTimeMillis()
            )

            jobsRef.document(jobId).set(job)
                .addOnSuccessListener {
                    Toast.makeText(this, "Job Submitted Successfully", Toast.LENGTH_SHORT).show()
                    val intent = Intent(this, ClientDashboardActivity::class.java)
                    intent.putExtra(ClientDashboardActivity.OPEN_FRAGMENT, "REQUESTS")
                    intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                    startActivity(intent)
                    finish()
                }
                .addOnFailureListener {
                    isSubmitting = false
                    loadingOverlay.visibility = View.GONE
                    Toast.makeText(this, "Failed to submit job", Toast.LENGTH_SHORT).show()
                }
        }.addOnFailureListener {
            isSubmitting = false
            loadingOverlay.visibility = View.GONE
            Toast.makeText(this, "Verification failed", Toast.LENGTH_SHORT).show()
        }
    }
}