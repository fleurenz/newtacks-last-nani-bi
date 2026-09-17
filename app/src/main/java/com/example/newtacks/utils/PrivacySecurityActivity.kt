package com.example.newtacks.utils

import android.app.Dialog
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.example.newtacks.R
import com.example.newtacks.authentication.OnboardingActivity
import com.example.newtacks.models.User
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

class PrivacySecurityActivity : AppCompatActivity() {

    private val auth = FirebaseAuth.getInstance()
    private val db = FirebaseFirestore.getInstance()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_privacy_security)

        val toolbar = findViewById<Toolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayShowTitleEnabled(false)
        toolbar.setNavigationOnClickListener { finish() }

        // Robust Inset Handling
        val statusBarSpacer = findViewById<View>(R.id.statusBarSpacer)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(android.R.id.content)) { _, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            statusBarSpacer.layoutParams.height = systemBars.top
            statusBarSpacer.requestLayout()
            insets
        }

        determineTheme()
        setupListeners()
    }

    private fun determineTheme() {
        val uid = auth.currentUser?.uid ?: return
        db.collection("users").document(uid).get().addOnSuccessListener { doc ->
            val user = doc.toObject(User::class.java)
            val themeColor = when (user?.role) {
                "WORKER"  -> Color.parseColor("#0F325E") // Worker Navy
                "COMPANY" -> Color.parseColor("#1C6EC6") // Company Blue
                else      -> Color.parseColor("#004A99") // Client Blue (Default)
            }
            applyTheme(themeColor)
        }
    }

    private fun applyTheme(color: Int) {
        findViewById<View>(R.id.statusBarSpacer).setBackgroundColor(color)
        findViewById<Toolbar>(R.id.toolbar).setBackgroundColor(color)

        // Update section headers
        findViewById<TextView>(R.id.tvSecurityHeader).setTextColor(color)
        findViewById<TextView>(R.id.tvPrivacyHeader).setTextColor(color)
    }

    private fun setupListeners() {
        findViewById<View>(R.id.cardChangePassword).setOnClickListener {
            Toast.makeText(this, "Change Password feature coming soon", Toast.LENGTH_SHORT).show()
        }

        findViewById<View>(R.id.cardTerms).setOnClickListener {
            LegalDocumentActivity.start(
                context = this,
                title = LegalDocumentActivity.TITLE_TERMS,
                lastUpdated = "Last updated: September 2026",
                body = TERMS_OF_SERVICE_TEXT
            )
        }

        findViewById<View>(R.id.cardPrivacyPolicy).setOnClickListener {
            LegalDocumentActivity.start(
                context = this,
                title = LegalDocumentActivity.TITLE_PRIVACY,
                lastUpdated = "Last updated: September 2026",
                body = PRIVACY_POLICY_TEXT
            )
        }

        findViewById<View>(R.id.cardDeleteAccount).setOnClickListener {
            showDeleteAccountDialog()
        }
    }

    private fun showDeleteAccountDialog() {
        val dialog = Dialog(this)
        dialog.setContentView(R.layout.dialog_role_select)
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
        dialog.window?.setLayout(
            (resources.displayMetrics.widthPixels * 0.88).toInt(),
            ViewGroup.LayoutParams.WRAP_CONTENT
        )

        dialog.findViewById<ImageView>(R.id.dialogIcon).setImageResource(R.drawable.ic_trash)
        dialog.findViewById<TextView>(R.id.dialogTitle).text = "Delete Account?"
        dialog.findViewById<TextView>(R.id.dialogMessage).text =
            "Are you sure you want to delete your account? You have 3 days to undo this action by logging back in with your credentials."

        val btnDelete = dialog.findViewById<com.google.android.material.button.MaterialButton>(R.id.dialogBtnPositive)
        btnDelete.text = "Delete"
        btnDelete.backgroundTintList = android.content.res.ColorStateList.valueOf(Color.parseColor("#DC2626"))
        btnDelete.setOnClickListener {
            dialog.dismiss()
            markAccountForDeletion()
        }

        dialog.findViewById<com.google.android.material.button.MaterialButton>(R.id.dialogBtnNegative).apply {
            text = "Cancel"
            setOnClickListener { dialog.dismiss() }
        }

        dialog.show()
    }

    private fun markAccountForDeletion() {
        val uid = auth.currentUser?.uid ?: return
        db.collection("users").document(uid).update("deletionTimestamp", System.currentTimeMillis())
            .addOnSuccessListener {
                Toast.makeText(this, "Account marked for deletion. You have 3 days to restore it.", Toast.LENGTH_LONG).show()
                auth.signOut()
                val intent = Intent(this, OnboardingActivity::class.java)
                intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                startActivity(intent)
                finish()
            }
            .addOnFailureListener {
                Toast.makeText(this, "Failed to request deletion.", Toast.LENGTH_SHORT).show()
            }
    }

    companion object {
        // TODO: Replace with your actual capstone-appropriate legal text before final submission.
        const val TERMS_OF_SERVICE_TEXT = """
By using this app, you agree to the following terms:

1. Account Responsibility
You are responsible for the accuracy of the information you provide, including your identity, skills, and certifications if registering as a worker.

2. Service Conduct
Clients and workers agree to communicate respectfully and to honor confirmed job requests. Repeated no-shows or cancellations may result in account restrictions.

3. Payments
Payment arrangements between clients and workers are handled directly between the two parties, as reflected in the job details at the time of the request.

4. Verification
Workers who submit certificates (NC1, NC2, NC3) for verification confirm that all submitted documents are authentic and belong to them.

5. Termination
We reserve the right to suspend or terminate accounts that violate these terms, including fraudulent activity, harassment, or repeated policy violations.

This is placeholder text for demonstration purposes as part of a capstone project and should be replaced with reviewed legal content before any public release.
        """

        const val PRIVACY_POLICY_TEXT = """
We collect and use your information only as described below:

1. Information We Collect
Your name, contact number, address, and — for workers — skills, experience, and certification documents you choose to upload.

2. How We Use It
To match clients with available workers, to display profile and verification information to the other party in a job request, and to improve app functionality.

3. Location Data
Location is used to show nearby job requests or available workers, and to provide live tracking while a job is active. Location access can be limited in your device settings.

4. Data Sharing
We do not sell your personal data. Information is shared only between the client and worker involved in a specific job request.

5. Data Retention
You may request account and data deletion at any time through the Delete Account option in this screen.

This is placeholder text for demonstration purposes as part of a capstone project and should be replaced with reviewed legal content before any public release.
        """
    }
}
