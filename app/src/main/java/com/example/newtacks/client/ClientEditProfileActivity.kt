package com.example.newtacks.client

import android.os.Bundle
import android.view.View
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import com.example.newtacks.R
import com.example.newtacks.models.User
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

class ClientEditProfileActivity : AppCompatActivity() {

    private val firestore = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()

    private lateinit var etName: EditText
    private lateinit var etPhone: EditText
    private lateinit var etAddress: EditText
    private lateinit var loadingOverlay: View

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_client_edit_profile)

        val toolbar = findViewById<Toolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)
        toolbar.setNavigationOnClickListener { finish() }

        etName = findViewById(R.id.etName)
        etPhone = findViewById(R.id.etPhone)
        etAddress = findViewById(R.id.etAddress)
        loadingOverlay = findViewById(R.id.loadingOverlay)

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
                
                etName.setText(user.name)
                etPhone.setText(user.phone)
                etAddress.setText(user.address)
            }
            .addOnFailureListener {
                loadingOverlay.visibility = View.GONE
                Toast.makeText(this, "Failed to load data", Toast.LENGTH_SHORT).show()
            }
    }

    private fun saveData() {
        val uid = auth.currentUser?.uid ?: return
        
        val name = etName.text.toString().trim()
        val phone = etPhone.text.toString().trim()
        val address = etAddress.text.toString().trim()

        if (name.isEmpty() || phone.isEmpty() || address.isEmpty()) {
            Toast.makeText(this, "Please fill in all fields", Toast.LENGTH_SHORT).show()
            return
        }

        loadingOverlay.visibility = View.VISIBLE
        
        val updates = mapOf(
            "name" to name,
            "phone" to phone,
            "address" to address
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