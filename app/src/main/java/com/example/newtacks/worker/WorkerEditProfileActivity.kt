package com.example.newtacks.worker

import android.os.Bundle
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import com.example.newtacks.R
import com.example.newtacks.models.User
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

class WorkerEditProfileActivity : AppCompatActivity() {

    private val firestore = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()

    private lateinit var etName: EditText
    private lateinit var etPhone: EditText
    private lateinit var etAddress: EditText
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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_worker_edit_profile)

        val toolbar = findViewById<Toolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)
        toolbar.setNavigationOnClickListener { finish() }

        etName = findViewById(R.id.etName)
        etPhone = findViewById(R.id.etPhone)
        etAddress = findViewById(R.id.etAddress)
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