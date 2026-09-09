package com.example.newtacks.company

import android.location.Geocoder
import android.os.Bundle
import android.view.View
import android.widget.EditText
import android.widget.Toast
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

class CompanyEditProfileActivity : AppCompatActivity() {

    private val firestore = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()
    private lateinit var fusedLocationClient: FusedLocationProviderClient

    private lateinit var etName: EditText
    private lateinit var etPhone: EditText
    private lateinit var etAddress: EditText
    private lateinit var switchRealTimeLocation: SwitchMaterial
    private lateinit var etAbout: EditText
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
        setContentView(R.layout.activity_company_edit_profile)

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)

        val toolbar = findViewById<Toolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)
        toolbar.setNavigationOnClickListener { finish() }

        etName = findViewById(R.id.etName)
        etPhone = findViewById(R.id.etPhone)
        etAddress = findViewById(R.id.etAddress)
        switchRealTimeLocation = findViewById(R.id.switchRealTimeLocation)
        etAbout = findViewById(R.id.etAbout)
        loadingOverlay = findViewById(R.id.loadingOverlay)

        switchRealTimeLocation.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                detectRealTimeLocation()
            } else {
                etAddress.setText(profileAddress)
            }
        }

        loadCurrentData()

        findViewById<View>(R.id.btnSave).setOnClickListener {
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
                
                etName.setText(user.companyName ?: user.name)
                etPhone.setText(user.phone)
                etAddress.setText(user.address)
                profileAddress = user.address ?: ""
                selectedLat = user.latitude ?: 0.0
                selectedLng = user.longitude ?: 0.0
                etAbout.setText(user.aboutUs ?: "")
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
        
        val name = etName.text.toString().trim()
        val phone = etPhone.text.toString().trim()
        val address = etAddress.text.toString().trim()
        val about = etAbout.text.toString().trim()

        if (name.isEmpty() || phone.isEmpty() || address.isEmpty()) {
            Toast.makeText(this, "Please fill in all basic fields", Toast.LENGTH_SHORT).show()
            return
        }

        loadingOverlay.visibility = View.VISIBLE
        
        val updates = mapOf(
            "companyName" to name,
            "phone" to phone,
            "address" to address,
            "latitude" to selectedLat,
            "longitude" to selectedLng,
            "aboutUs" to about
        )

        firestore.collection("users").document(uid).update(updates)
            .addOnSuccessListener {
                loadingOverlay.visibility = View.GONE
                Toast.makeText(this, "Company profile updated", Toast.LENGTH_SHORT).show()
                finish()
            }
            .addOnFailureListener {
                loadingOverlay.visibility = View.GONE
                Toast.makeText(this, "Update failed: ${it.message}", Toast.LENGTH_SHORT).show()
            }
    }
}