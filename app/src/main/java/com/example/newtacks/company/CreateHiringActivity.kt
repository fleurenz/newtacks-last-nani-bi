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
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import java.util.*

class CreateHiringActivity : AppCompatActivity() {

    private lateinit var auth: FirebaseAuth
    private lateinit var db: FirebaseFirestore
    private lateinit var fusedLocationClient: FusedLocationProviderClient

    private lateinit var etCompanyName: EditText
    private lateinit var etCompanyAddress: EditText
    private lateinit var switchLocation: com.google.android.material.switchmaterial.SwitchMaterial
    
    private lateinit var etHiringTitle: EditText
    private lateinit var cbCarpentry: CheckBox
    private lateinit var cbPlumbing: CheckBox
    private lateinit var cbMasonry: CheckBox
    private lateinit var cbWelding: CheckBox
    private lateinit var cbPainting: CheckBox
    
    private lateinit var rgEmployment: RadioGroup
    private lateinit var etDailyRate: EditText
    
    private lateinit var layoutSelectedImages: LinearLayout
    private lateinit var btnAddPhoto: Button
    private lateinit var etJobDescription: EditText
    private lateinit var etResponsibilities: EditText
    
    private lateinit var btnSelectClosingDate: Button
    private lateinit var btnSubmit: Button

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
    }

    private fun setupToolbar() {
        val toolbar = findViewById<androidx.appcompat.widget.Toolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)
        toolbar.setNavigationOnClickListener { finish() }
    }

    private fun initializeViews() {
        etCompanyName = findViewById(R.id.etCompanyName)
        etCompanyAddress = findViewById(R.id.etCompanyAddress)
        switchLocation = findViewById(R.id.switchRealTimeLocation)
        etHiringTitle = findViewById(R.id.etHiringTitle)
        
        cbCarpentry = findViewById(R.id.cbCarpentry)
        cbPlumbing = findViewById(R.id.cbPlumbing)
        cbMasonry = findViewById(R.id.cbMasonry)
        cbWelding = findViewById(R.id.cbWelding)
        cbPainting = findViewById(R.id.cbPainting)
        
        rgEmployment = findViewById(R.id.rgEmploymentType)
        etDailyRate = findViewById(R.id.etDailyRate)
        
        layoutSelectedImages = findViewById(R.id.layoutSelectedImages)
        btnAddPhoto = findViewById(R.id.btnAddPhoto)
        etJobDescription = findViewById(R.id.etJobDescription)
        etResponsibilities = findViewById(R.id.etResponsibilities)
        
        btnSelectClosingDate = findViewById(R.id.btnSelectClosingDate)
        btnSubmit = findViewById(R.id.btnSubmitHiring)
    }

    private fun updateImagesUI() {
        layoutSelectedImages.removeAllViews()
        selectedImageUris.forEachIndexed { index, uri ->
            val imageView = ImageView(this)
            val params = LinearLayout.LayoutParams(200, 200)
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
                etCompanyName.setText(user.companyName ?: user.name)
                etCompanyAddress.setText(user.address)
                
                profileAddress = user.address
                profileLat = user.latitude ?: 0.0
                profileLng = user.longitude ?: 0.0
                
                selectedLat = profileLat
                selectedLng = profileLng
            }
        }
    }

    private fun setupListeners() {
        switchLocation.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) detectLocation() else restoreProfileLocation()
        }

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
            }, cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH)).show()
        }

        btnSubmit.setOnClickListener { submitHiring() }
    }

    private fun detectLocation() {
        if (androidx.core.content.ContextCompat.checkSelfPermission(this, android.Manifest.permission.ACCESS_FINE_LOCATION) 
            != android.content.pm.PackageManager.PERMISSION_GRANTED) {
            Toast.makeText(this, "Location permission required", Toast.LENGTH_SHORT).show()
            switchLocation.isChecked = false
            return
        }

        fusedLocationClient.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, null)
            .addOnSuccessListener { loc ->
                if (loc != null) {
                    selectedLat = loc.latitude
                    selectedLng = loc.longitude
                    reverseGeocode(loc.latitude, loc.longitude)
                }
            }
    }

    private fun reverseGeocode(lat: Double, lng: Double) {
        try {
            val addresses = Geocoder(this, Locale.getDefault()).getFromLocation(lat, lng, 1)
            if (!addresses.isNullOrEmpty()) {
                etCompanyAddress.setText(addresses[0].getAddressLine(0))
            }
        } catch (e: Exception) { e.printStackTrace() }
    }

    private fun restoreProfileLocation() {
        etCompanyAddress.setText(profileAddress)
        selectedLat = profileLat
        selectedLng = profileLng
    }

    private fun submitHiring() {
        val title = etHiringTitle.text.toString().trim()
        val rateText = etDailyRate.text.toString().trim()
        val description = etJobDescription.text.toString().trim()
        val responsibilities = etResponsibilities.text.toString().trim()
        
        val services = mutableListOf<String>()
        if (cbCarpentry.isChecked) services.add("Carpentry")
        if (cbPlumbing.isChecked) services.add("Plumbing")
        if (cbMasonry.isChecked) services.add("Masonry")
        if (cbWelding.isChecked) services.add("Welding")
        if (cbPainting.isChecked) services.add("Painting")

        val selectedRbId = rgEmployment.checkedRadioButtonId
        val empType = when (selectedRbId) {
            R.id.rbPartTime -> "PART_TIME"
            R.id.rbFullTime -> "FULL_TIME"
            R.id.rbProject -> "PROJECT"
            else -> ""
        }

        if (title.isEmpty() || rateText.isEmpty() || services.isEmpty() || empType.isEmpty() || expiresAtTimestamp == 0L || description.isEmpty() || responsibilities.isEmpty()) {
            Toast.makeText(this, "Please fill all required fields", Toast.LENGTH_SHORT).show()
            return
        }

        val rate = rateText.toDoubleOrNull() ?: 0.0
        val uid = auth.currentUser?.uid ?: return
        
        btnSubmit.isEnabled = false
        Toast.makeText(this, "Publishing...", Toast.LENGTH_SHORT).show()

        uploadImagesAndSubmit(selectedImageUris) { imageUrls ->
            val hiringId = db.collection("hiring").document().id
            val now = System.currentTimeMillis()
            val post = HiringPost(
                hiringId = hiringId,
                companyId = uid,
                companyName = etCompanyName.text.toString(),
                companyAddress = etCompanyAddress.text.toString(),
                jobTitle = title,
                serviceCategories = services,
                employmentType = empType,
                latitude = selectedLat,
                longitude = selectedLng,
                dailyRate = rate,
                description = description,
                responsibilities = responsibilities,
                images = imageUrls,
                createdAt = now,
                expiresAt = expiresAtTimestamp
            )

            db.collection("hiring").document(hiringId).set(post)
                .addOnSuccessListener {
                    Toast.makeText(this, "Hiring post published", Toast.LENGTH_SHORT).show()
                    val intent = Intent(this, CompanyDashboardActivity::class.java)
                    intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                    startActivity(intent)
                    finish()
                }
                .addOnFailureListener {
                    btnSubmit.isEnabled = true
                    Toast.makeText(this, "Failed to post", Toast.LENGTH_SHORT).show()
                }
        }
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
