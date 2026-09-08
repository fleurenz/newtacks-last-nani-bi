package com.example.newtacks.company

import android.app.DatePickerDialog
import android.content.Intent
import android.location.Geocoder
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import android.text.Editable
import android.text.TextWatcher
import android.content.res.ColorStateList
import androidx.core.widget.NestedScrollView
import coil.load
import coil.transform.RoundedCornersTransformation
import com.cloudinary.android.MediaManager
import com.cloudinary.android.callback.ErrorInfo
import com.cloudinary.android.callback.UploadCallback
import com.example.newtacks.CompanyDashboardActivity
import com.example.newtacks.R
import com.example.newtacks.models.HiringPost
import com.example.newtacks.models.User
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.material.button.MaterialButton
import com.google.android.material.button.MaterialButtonToggleGroup
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import java.util.*

class CreateHiringActivity : AppCompatActivity() {

    private lateinit var auth: FirebaseAuth
    private lateinit var db: FirebaseFirestore
    private lateinit var fusedLocationClient: FusedLocationProviderClient

    private lateinit var etCompanyName: EditText
    private lateinit var etCompanyAddress: EditText
    
    private lateinit var etHiringTitle: EditText
    private lateinit var cbCarpentry: CheckBox
    private lateinit var cbPlumbing: CheckBox
    private lateinit var cbMasonry: CheckBox
    private lateinit var cbWelding: CheckBox
    private lateinit var cbPainting: CheckBox
    private lateinit var cbOthers: CheckBox
    private lateinit var etOtherService: EditText
    
    private lateinit var toggleEmploymentType: MaterialButtonToggleGroup
    private lateinit var etDailyRate: EditText
    private lateinit var etVacancies: EditText
    
    private lateinit var layoutSelectedImages: LinearLayout
    private lateinit var btnAddPhoto: View
    private lateinit var etJobDescription: EditText
    private lateinit var etResponsibilities: EditText
    
    private lateinit var hiringScrollView: NestedScrollView
    private lateinit var btnSelectClosingDate: MaterialButton
    private lateinit var btnSubmit: Button
    private lateinit var btnDraft: Button

    private var selectedLat: Double = 0.0
    private var selectedLng: Double = 0.0
    private var profileAddress = ""
    private var profileLat = 0.0
    private var profileLng = 0.0
    private var expiresAtTimestamp: Long = 0
    
    private val selectedImageUris = mutableListOf<Uri>()

    private val pickImages = registerForActivityResult(ActivityResultContracts.GetMultipleContents()) { uris ->
        if (uris.isNotEmpty()) {
            selectedImageUris.addAll(uris)
            updateImagesUI()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_create_hiring)

        auth = FirebaseAuth.getInstance()
        db = FirebaseFirestore.getInstance()
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)

        setupToolbar()
        initializeViews()
        loadCompanyInfo()
        setupListeners()
        setupValidationListeners()
    }

    private fun setupToolbar() {
        val toolbar = findViewById<androidx.appcompat.widget.Toolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayShowTitleEnabled(false)
        toolbar.setNavigationOnClickListener { finish() }
    }

    private fun initializeViews() {
        hiringScrollView = findViewById(R.id.hiringScrollView)
        etCompanyAddress = findViewById(R.id.etCompanyAddress)
        etHiringTitle = findViewById(R.id.etHiringTitle)
        
        cbCarpentry = findViewById(R.id.cbCarpentry)
        cbPlumbing = findViewById(R.id.cbPlumbing)
        cbMasonry = findViewById(R.id.cbMasonry)
        cbWelding = findViewById(R.id.cbWelding)
        cbPainting = findViewById(R.id.cbPainting)
        cbOthers = findViewById(R.id.cbOthers)
        etOtherService = findViewById(R.id.etOtherService)
        
        toggleEmploymentType = findViewById(R.id.toggleEmploymentType)
        etDailyRate = findViewById(R.id.etDailyRate)
        etVacancies = findViewById(R.id.etVacancies)
        
        layoutSelectedImages = findViewById(R.id.layoutSelectedImages)
        btnAddPhoto = findViewById(R.id.btnAddPhotoCard)
        etJobDescription = findViewById(R.id.etJobDescription)
        etResponsibilities = findViewById(R.id.etResponsibilities)
        
        btnSelectClosingDate = findViewById(R.id.btnSelectClosingDate)
        btnSubmit = findViewById(R.id.btnSubmitHiring)
        btnDraft = findViewById(R.id.btnDraft)
    }

    private fun updateImagesUI() {
        layoutSelectedImages.removeAllViews()
        selectedImageUris.forEachIndexed { index, uri ->
            val imageView = ImageView(this)
            val params = LinearLayout.LayoutParams(160, 160)
            params.setMargins(0, 0, 16, 0)
            imageView.layoutParams = params
            imageView.scaleType = ImageView.ScaleType.CENTER_CROP
            imageView.load(uri) {
                transformations(RoundedCornersTransformation(8f))
            }
            
            imageView.setOnClickListener {
                selectedImageUris.removeAt(index)
                updateImagesUI()
            }
            
            layoutSelectedImages.addView(imageView)
        }
    }

    private fun loadCompanyInfo() {
        val uid = auth.currentUser?.uid ?: return
        db.collection("users").document(uid).get().addOnSuccessListener { doc ->
            val user = doc.toObject(User::class.java)
            if (user != null) {
                profileAddress = user.address ?: ""
                profileLat = user.latitude ?: 0.0
                profileLng = user.longitude ?: 0.0
                
                selectedLat = profileLat
                selectedLng = profileLng
                
                if (etCompanyAddress.text.isEmpty()) {
                    etCompanyAddress.setText(profileAddress)
                }
            }
        }
    }

    private fun setupListeners() {
        btnAddPhoto.setOnClickListener {
            pickImages.launch("image/*")
        }

        btnSelectClosingDate.setOnClickListener {
            val cal = Calendar.getInstance()
            DatePickerDialog(this, { _, y, m, d ->
                val closingCal = Calendar.getInstance()
                closingCal.set(y, m, d, 23, 59, 59)
                expiresAtTimestamp = closingCal.timeInMillis
                
                val dateStr = "${m + 1}/$d/$y"
                btnSelectClosingDate.text = "Close: $dateStr"
                
                // Reset error state
                btnSelectClosingDate.setStrokeColor(ColorStateList.valueOf(ContextCompat.getColor(this, R.color.primary)))
                btnSelectClosingDate.strokeWidth = resources.getDimensionPixelSize(R.dimen.normal_stroke_width)
            }, cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH)).show()
        }

        btnSubmit.setOnClickListener { submitHiring(status = "OPEN") }
        btnDraft.setOnClickListener { submitHiring(status = "DRAFT") }
    }

    private fun submitHiring(status: String) {
        val title = etHiringTitle.text.toString().trim()
        val rateText = etDailyRate.text.toString().trim()
        
        val services = mutableListOf<String>()
        if (cbCarpentry.isChecked) services.add("Carpentry")
        if (cbPlumbing.isChecked) services.add("Plumbing")
        if (cbMasonry.isChecked) services.add("Masonry")
        if (cbWelding.isChecked) services.add("Welding")
        if (cbPainting.isChecked) services.add("Painting")
        if (cbOthers.isChecked) {
            val other = etOtherService.text.toString().trim()
            if (other.isNotEmpty()) services.add(other)
        }

        val empType = when (toggleEmploymentType.checkedButtonId) {
            R.id.btnFullTime -> "FULL_TIME"
            R.id.btnPartTime -> "PART_TIME"
            R.id.btnContract -> "CONTRACT"
            else -> ""
        }

        if (!validateForm()) {
            Toast.makeText(this, "Please fill all required fields", Toast.LENGTH_SHORT).show()
            return
        }

        val rate = rateText.replace(Regex("[^0-9.]"), "").toDoubleOrNull() ?: 0.0
        val vacanciesText = etVacancies.text.toString().trim()
        val vacancies = vacanciesText.toIntOrNull() ?: 1
        val description = etJobDescription.text.toString().trim()
        val responsibilities = etResponsibilities.text.toString().trim()
        val uid = auth.currentUser?.uid ?: return
        
        btnSubmit.isEnabled = false
        btnDraft.isEnabled = false
        Toast.makeText(this, if (status == "OPEN") "Publishing..." else "Saving draft...", Toast.LENGTH_SHORT).show()

        uploadImagesAndSubmit(selectedImageUris) { imageUrls ->
            val hiringId = db.collection("hiring").document().id
            val now = System.currentTimeMillis()
            
            db.collection("users").document(uid).get().addOnSuccessListener { userDoc ->
                val companyName = userDoc.getString("companyName") ?: userDoc.getString("name") ?: "Company"
                
                val post = HiringPost(
                    hiringId = hiringId,
                    companyId = uid,
                    companyName = companyName,
                    companyAddress = etCompanyAddress.text.toString(),
                    jobTitle = title,
                    serviceCategories = services,
                    employmentType = empType,
                    latitude = selectedLat,
                    longitude = selectedLng,
                    dailyRate = rate,
                    vacancies = vacancies,
                    description = description,
                    responsibilities = responsibilities,
                    images = imageUrls,
                    status = status,
                    createdAt = now,
                    expiresAt = expiresAtTimestamp
                )

                db.collection("hiring").document(hiringId).set(post)
                    .addOnSuccessListener {
                        Toast.makeText(this, if (status == "OPEN") "Hiring post published" else "Draft saved", Toast.LENGTH_SHORT).show()
                        val intent = Intent(this, CompanyDashboardActivity::class.java)
                        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                        startActivity(intent)
                        finish()
                    }
                    .addOnFailureListener {
                        btnSubmit.isEnabled = true
                        btnDraft.isEnabled = true
                        Toast.makeText(this, "Failed to post", Toast.LENGTH_SHORT).show()
                    }
            }
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

        etHiringTitle.addTextChangedListener(watcher)
        etCompanyAddress.addTextChangedListener(watcher)
        etDailyRate.addTextChangedListener(watcher)
        etVacancies.addTextChangedListener(watcher)
        etJobDescription.addTextChangedListener(watcher)
        etResponsibilities.addTextChangedListener(watcher)
    }

    private fun validateForm(): Boolean {
        var isValid = true
        var firstErrorView: View? = null

        val title = etHiringTitle.text.toString().trim()
        val address = etCompanyAddress.text.toString().trim()
        val rate = etDailyRate.text.toString().trim()
        val vacancies = etVacancies.text.toString().trim()
        val description = etJobDescription.text.toString().trim()
        val responsibilities = etResponsibilities.text.toString().trim()

        val services = mutableListOf<String>()
        if (cbCarpentry.isChecked) services.add("Carpentry")
        if (cbPlumbing.isChecked) services.add("Plumbing")
        if (cbMasonry.isChecked) services.add("Masonry")
        if (cbWelding.isChecked) services.add("Welding")
        if (cbPainting.isChecked) services.add("Painting")
        if (cbOthers.isChecked) {
            val other = etOtherService.text.toString().trim()
            if (other.isNotEmpty()) services.add(other)
        }

        if (title.isEmpty()) {
            etHiringTitle.error = "Job title is required"
            if (firstErrorView == null) firstErrorView = etHiringTitle
            isValid = false
        }

        if (toggleEmploymentType.checkedButtonId == View.NO_ID) {
            if (firstErrorView == null) firstErrorView = toggleEmploymentType
            isValid = false
            Toast.makeText(this, "Please select employment type", Toast.LENGTH_SHORT).show()
        }

        if (address.isEmpty()) {
            etCompanyAddress.error = "Location is required"
            if (firstErrorView == null) firstErrorView = etCompanyAddress
            isValid = false
        }

        if (rate.isEmpty()) {
            etDailyRate.error = "Salary is required"
            if (firstErrorView == null) firstErrorView = etDailyRate
            isValid = false
        }

        if (vacancies.isEmpty()) {
            etVacancies.error = "Number of vacancies is required"
            if (firstErrorView == null) firstErrorView = etVacancies
            isValid = false
        }

        if (description.isEmpty()) {
            etJobDescription.error = "Description is required"
            if (firstErrorView == null) firstErrorView = etJobDescription
            isValid = false
        }

        if (responsibilities.isEmpty()) {
            etResponsibilities.error = "Responsibilities are required"
            if (firstErrorView == null) firstErrorView = etResponsibilities
            isValid = false
        }

        if (services.isEmpty()) {
            if (firstErrorView == null) firstErrorView = cbCarpentry
            isValid = false
            Toast.makeText(this, "Please select at least one service", Toast.LENGTH_SHORT).show()
        }

        if (expiresAtTimestamp == 0L) {
            btnSelectClosingDate.setStrokeColor(ColorStateList.valueOf(ContextCompat.getColor(this, R.color.error_red)))
            btnSelectClosingDate.strokeWidth = resources.getDimensionPixelSize(R.dimen.error_stroke_width)
            if (firstErrorView == null) firstErrorView = btnSelectClosingDate
            isValid = false
        }

        firstErrorView?.let {
            it.requestFocus()
            val location = IntArray(2)
            it.getLocationInWindow(location)
            hiringScrollView.smoothScrollTo(0, location[1] - 200)
        }

        return isValid
    }

    private fun uploadImagesAndSubmit(uris: List<Uri>, onComplete: (List<String>) -> Unit) {
        if (uris.isEmpty()) {
            onComplete(emptyList())
            return
        }

        val uploadedUrls = Collections.synchronizedList(mutableListOf<String>())
        var uploadCount = 0

        uris.forEach { uri ->
            MediaManager.get().upload(uri)
                .option("folder", "hiring_posts")
                .callback(object : UploadCallback {
                    override fun onStart(requestId: String?) {}
                    override fun onProgress(requestId: String?, bytes: Long, totalBytes: Long) {}
                    override fun onSuccess(requestId: String?, resultData: MutableMap<Any?, Any?>?) {
                        val imageUrl = resultData?.get("secure_url").toString()
                        uploadedUrls.add(imageUrl)
                        synchronized(this@CreateHiringActivity) {
                            uploadCount++
                            if (uploadCount == uris.size) {
                                runOnUiThread { onComplete(uploadedUrls) }
                            }
                        }
                    }

                    override fun onError(requestId: String?, error: ErrorInfo?) {
                        synchronized(this@CreateHiringActivity) {
                            uploadCount++
                            if (uploadCount == uris.size) {
                                runOnUiThread { onComplete(uploadedUrls) }
                            }
                        }
                    }

                    override fun onReschedule(requestId: String?, error: ErrorInfo?) {}
                }).dispatch()
        }
    }
}