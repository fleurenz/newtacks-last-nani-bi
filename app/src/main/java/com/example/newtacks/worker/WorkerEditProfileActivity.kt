package com.example.newtacks.worker

import android.location.Geocoder
import android.os.Bundle
import android.view.View
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import com.example.newtacks.R
import com.example.newtacks.models.User
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.material.switchmaterial.SwitchMaterial
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import java.util.Locale

class WorkerEditProfileActivity : AppCompatActivity() {

    private val firestore = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()
    private lateinit var fusedLocationClient: FusedLocationProviderClient

    private lateinit var etName: EditText
    private lateinit var etPhone: EditText
    private lateinit var etAddress: EditText
    private lateinit var switchRealTimeLocation: SwitchMaterial
    private lateinit var etExperience: EditText
    private lateinit var etAbout: EditText
    private lateinit var cbPlumbing: CheckBox
    private lateinit var cbElectrical: CheckBox
    private lateinit var cbCarpentry: CheckBox
    private lateinit var cbMasonry: CheckBox
    private lateinit var cbWelding: CheckBox
    private lateinit var cbPainting: CheckBox
    private lateinit var cbLandscaping: CheckBox
    private lateinit var btnSave: Button
    private lateinit var loadingOverlay: View

    private var selectedLat: Double = 0.0
    private var selectedLng: Double = 0.0
    private var profileAddress = ""

    private val requestPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
            if (isGranted) detectRealTimeLocation()
            else switchRealTimeLocation.isChecked = false
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_worker_edit_profile)

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)

        val toolbar = findViewById<Toolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)
        toolbar.setNavigationOnClickListener { finish() }

        etName = findViewById(R.id.etName)
        etPhone = findViewById(R.id.etPhone)
        etAddress = findViewById(R.id.etAddress)
        switchRealTimeLocation = findViewById(R.id.switchRealTimeLocation)
        etExperience = findViewById(R.id.etExperience)
        etAbout = findViewById(R.id.etAbout)
        
        cbPlumbing = findViewById(R.id.cbPlumbing)
        cbElectrical = findViewById(R.id.cbElectrical)
        cbCarpentry = findViewById(R.id.cbCarpentry)
        cbMasonry = findViewById(R.id.cbMasonry)
        cbWelding = findViewById(R.id.cbWelding)
        cbPainting = findViewById(R.id.cbPainting)
        cbLandscaping = findViewById(R.id.cbLandscaping)
        
        btnSave = findViewById(R.id.btnSave)
        loadingOverlay = findViewById(R.id.loadingOverlay)

        switchRealTimeLocation.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                detectRealTimeLocation()
            } else {
                etAddress.setText(profileAddress)
            }
        }

        loadCurrentData()

        btnSave.setOnClickListener {
            saveData()
        }
    }

    private fun loadCurrentData() {
        val uid = auth.currentUser?.uid ?: return
        loadingOverlay.visibility = View.VISIBLE
        
        firestore.collection("users").document(uid).get()
            .addOnSuccessListener { doc ->
                loadingOverlay.visibility = View.GONE
                val user = doc.toObject(User::class.java) ?: return@addOnSuccessListener
                
                etName.setText(user.name)
                etPhone.setText(user.phone)
                etAddress.setText(user.address)
                profileAddress = user.address
                selectedLat = user.latitude ?: 0.0
                selectedLng = user.longitude ?: 0.0

                etExperience.setText((user.serviceExperience ?: 0).toString())
                etAbout.setText(user.aboutUs ?: "")
                
                user.serviceCategories?.forEach { category ->
                    when (category) {
                        "Plumbing" -> cbPlumbing.isChecked = true
                        "Electrical" -> cbElectrical.isChecked = true
                        "Carpentry" -> cbCarpentry.isChecked = true
                        "Masonry" -> cbMasonry.isChecked = true
                        "Welding" -> cbWelding.isChecked = true
                        "Painting" -> cbPainting.isChecked = true
                        "Landscaping" -> cbLandscaping.isChecked = true
                    }
                }
            }
            .addOnFailureListener {
                loadingOverlay.visibility = View.GONE
                Toast.makeText(this, "Failed to load data", Toast.LENGTH_SHORT).show()
            }
    }

    private fun detectRealTimeLocation() {
        if (androidx.core.content.ContextCompat.checkSelfPermission(this, android.Manifest.permission.ACCESS_FINE_LOCATION) 
            != android.content.pm.PackageManager.PERMISSION_GRANTED) {
            requestPermissionLauncher.launch(android.Manifest.permission.ACCESS_FINE_LOCATION)
            return
        }

        etAddress.setText("Detecting location...")

        fusedLocationClient.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, null)
            .addOnSuccessListener { location ->
                if (location != null) {
                    selectedLat = location.latitude
                    selectedLng = location.longitude
                    reverseGeocode(location.latitude, location.longitude)
                } else {
                    Toast.makeText(this, "Could not get location. Ensure GPS is on.", Toast.LENGTH_SHORT).show()
                    switchRealTimeLocation.isChecked = false
                    etAddress.setText(profileAddress)
                }
            }
            .addOnFailureListener {
                Toast.makeText(this, "Location detection failed", Toast.LENGTH_SHORT).show()
                switchRealTimeLocation.isChecked = false
                etAddress.setText(profileAddress)
            }
    }

    private fun reverseGeocode(lat: Double, lng: Double) {
        try {
            val geocoder = Geocoder(this, Locale.getDefault())
            val addresses = geocoder.getFromLocation(lat, lng, 1)
            if (!addresses.isNullOrEmpty()) {
                val address = addresses[0].getAddressLine(0)
                etAddress.setText(address)
            } else {
                etAddress.setText("Address not found")
            }
        } catch (e: Exception) {
            e.printStackTrace()
            etAddress.setText("Coordinates found, but address unavailable")
        }
    }

    private fun saveData() {
        val uid = auth.currentUser?.uid ?: return
        
        val name = etName.text.toString()
        val phone = etPhone.text.toString()
        val address = etAddress.text.toString()
        val expText = etExperience.text.toString()
        val experience = if (expText.isNotEmpty()) expText.toInt() else 0
        val about = etAbout.text.toString()
        
        val categories = mutableListOf<String>()
        if (cbPlumbing.isChecked) categories.add("Plumbing")
        if (cbElectrical.isChecked) categories.add("Electrical")
        if (cbCarpentry.isChecked) categories.add("Carpentry")
        if (cbMasonry.isChecked) categories.add("Masonry")
        if (cbWelding.isChecked) categories.add("Welding")
        if (cbPainting.isChecked) categories.add("Painting")
        if (cbLandscaping.isChecked) categories.add("Landscaping")

        if (name.isBlank() || phone.isBlank() || address.isBlank()) {
            Toast.makeText(this, "Please fill in all basic fields", Toast.LENGTH_SHORT).show()
            return
        }

        loadingOverlay.visibility = View.VISIBLE
        
        val updates = mapOf(
            "name" to name,
            "phone" to phone,
            "address" to address,
            "latitude" to selectedLat,
            "longitude" to selectedLng,
            "serviceExperience" to experience,
            "aboutUs" to about,
            "serviceCategories" to categories
        )

        firestore.collection("users").document(uid).update(updates)
            .addOnSuccessListener {
                loadingOverlay.visibility = View.GONE
                Toast.makeText(this, "Profile updated successfully", Toast.LENGTH_SHORT).show()
                finish()
            }
            .addOnFailureListener {
                loadingOverlay.visibility = View.GONE
                Toast.makeText(this, "Update failed: ${it.message}", Toast.LENGTH_SHORT).show()
            }
    }
}