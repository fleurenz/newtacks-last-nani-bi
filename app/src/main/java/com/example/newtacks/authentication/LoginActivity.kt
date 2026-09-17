package com.example.newtacks.authentication

import android.app.Dialog
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import android.view.animation.AnimationUtils
import android.widget.LinearLayout
import android.widget.ProgressBar
import com.example.newtacks.ClientDashboardActivity
import com.example.newtacks.CompanyDashboardActivity
import com.example.newtacks.R
import com.example.newtacks.WorkerDashboardActivity
import com.example.newtacks.utils.SecurityUtils
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

class LoginActivity : AppCompatActivity() {

    private lateinit var viewModel: LoginViewModel
    private lateinit var repo: AuthRepository
    private val auth = FirebaseAuth.getInstance()
    private val db = FirebaseFirestore.getInstance()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        setContentView(R.layout.activity_login)

        val topSection = findViewById<LinearLayout>(R.id.topSection)
        val bottomCard = findViewById<LinearLayout>(R.id.bottomCard)

        val slideUp = AnimationUtils.loadAnimation(this, R.anim.fade_in_slide_up)
        bottomCard.startAnimation(slideUp)

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { _, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            val imeInsets  = insets.getInsets(WindowInsetsCompat.Type.ime())
            topSection.setPadding(topSection.paddingLeft, systemBars.top, topSection.paddingRight, topSection.paddingBottom)
            val bottomPadding = maxOf(systemBars.bottom, imeInsets.bottom) + resources.getDimensionPixelSize(R.dimen.spacing_24)
            bottomCard.setPadding(bottomCard.paddingLeft, bottomCard.paddingTop, bottomCard.paddingRight, bottomPadding)
            insets
        }

        repo = AuthRepository(FirebaseAuth.getInstance(), FirebaseFirestore.getInstance())
        viewModel = ViewModelProvider(this, object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T = LoginViewModel(repo) as T
        })[LoginViewModel::class.java]

        handleMagicLink(intent)

        val email    = findViewById<EditText>(R.id.etEmail)
        val password = findViewById<EditText>(R.id.etPassword)
        val btn      = findViewById<Button>(R.id.btnLogin)
        val signUp   = findViewById<TextView>(R.id.goToSignup)

        btn.setOnClickListener { viewModel.login(email.text.toString(), password.text.toString()) }
        signUp.setOnClickListener { startActivity(Intent(this, RoleSelectionActivity::class.java)) }

        setupTermsAndPrivacy()

        observeState()
    }

    private fun setupTermsAndPrivacy() {
        findViewById<TextView>(R.id.tvTerms).apply {
            val fullText  = "By continuing, you agree to our Terms of Service and Privacy Policy"
            val spannable = android.text.SpannableString(fullText)
            
            val termsStart = fullText.indexOf("Terms of Service")
            val termsEnd   = termsStart + "Terms of Service".length
            
            val privacyStart = fullText.indexOf("Privacy Policy")
            val privacyEnd   = privacyStart + "Privacy Policy".length
            
            val blue = android.graphics.Color.parseColor("#1E88E5")
            
            // Terms link
            spannable.setSpan(object : android.text.style.ClickableSpan() {
                override fun onClick(widget: View) {
                    com.example.newtacks.utils.LegalDocumentActivity.start(
                        this@LoginActivity,
                        com.example.newtacks.utils.LegalDocumentActivity.TITLE_TERMS,
                        "Last updated: September 2026",
                        com.example.newtacks.utils.PrivacySecurityActivity.TERMS_OF_SERVICE_TEXT
                    )
                }
                override fun updateDrawState(ds: android.text.TextPaint) {
                    ds.color = blue
                    ds.isUnderlineText = true
                    ds.isFakeBoldText = true
                }
            }, termsStart, termsEnd, 0)
            
            // Privacy link
            spannable.setSpan(object : android.text.style.ClickableSpan() {
                override fun onClick(widget: View) {
                    com.example.newtacks.utils.LegalDocumentActivity.start(
                        this@LoginActivity,
                        com.example.newtacks.utils.LegalDocumentActivity.TITLE_PRIVACY,
                        "Last updated: September 2026",
                        com.example.newtacks.utils.PrivacySecurityActivity.PRIVACY_POLICY_TEXT
                    )
                }
                override fun updateDrawState(ds: android.text.TextPaint) {
                    ds.color = blue
                    ds.isUnderlineText = true
                    ds.isFakeBoldText = true
                }
            }, privacyStart, privacyEnd, 0)
            
            text = spannable
            movementMethod = android.text.method.LinkMovementMethod.getInstance()
        }
    }

    private fun handleMagicLink(intent: Intent?) {
        val emailLink = intent?.data?.toString()
        if (repo.isMagicLink(emailLink)) {
            val prefs = SecurityUtils.getEncryptedPrefs(this)
            val email = prefs.getString("pending_magic_email", null)
            val isRegistration = prefs.contains("pending_signup_data")
            
            if (email != null && emailLink != null) {
                findViewById<ProgressBar>(R.id.loginProgress).visibility = View.VISIBLE
                
                if (isRegistration) {
                    // It's a registration flow, finish it
                    val signupViewModel = ViewModelProvider(this, object : ViewModelProvider.Factory {
                        @Suppress("UNCHECKED_CAST")
                        override fun <T : ViewModel> create(modelClass: Class<T>): T = SignupViewModel(repo) as T
                    })[SignupViewModel::class.java]

                    signupViewModel.completeRegistrationWithLink(this, email, emailLink)
                    
                    signupViewModel.signupState.observe(this) { state ->
                        when (state) {
                            is SignupState.Success -> {
                                // Clear data
                                prefs.edit().remove("pending_signup_data").remove("pending_magic_email").apply()
                                Toast.makeText(this, "Registration successful!", Toast.LENGTH_SHORT).show()
                                
                                // Now we need to know the role to route
                                repo.getUserRole(FirebaseAuth.getInstance().currentUser?.uid ?: "") { result ->
                                    result.onSuccess { role -> routeUser(role); finish() }
                                }
                            }
                            is SignupState.Error -> {
                                Toast.makeText(this, "Verification failed: ${state.message}", Toast.LENGTH_LONG).show()
                                findViewById<ProgressBar>(R.id.loginProgress).visibility = View.GONE
                            }
                            else -> {}
                        }
                    }
                } else {
                    // Regular magic link login
                    repo.signInWithMagicLink(email, emailLink) { result ->
                        result.onSuccess { uid ->
                            repo.getUserRole(uid) { roleResult ->
                                roleResult.onSuccess { role -> routeUser(role); finish() }
                            }
                        }.onFailure {
                            Toast.makeText(this, "Login failed: ${it.message}", Toast.LENGTH_LONG).show()
                            findViewById<ProgressBar>(R.id.loginProgress).visibility = View.GONE
                        }
                    }
                }
            }
        }
    }

    private fun observeState() {
        val btnLogin      = findViewById<com.google.android.material.button.MaterialButton>(R.id.btnLogin)
        val loginProgress = findViewById<ProgressBar>(R.id.loginProgress)
        viewModel.loginState.observe(this) { state ->
            when (state) {
                is LoginState.Loading -> {
                    btnLogin.text = ""; btnLogin.isEnabled = false
                    loginProgress.visibility = View.VISIBLE
                }
                is LoginState.Success -> {
                    loginProgress.visibility = View.GONE
                    btnLogin.isEnabled = true; btnLogin.text = "Log In"
                    checkAccountDeletion(state.uid, state.role)
                }
                is LoginState.Error -> {
                    loginProgress.visibility = View.GONE
                    btnLogin.isEnabled = true; btnLogin.text = "Log In"
                    Toast.makeText(this, state.message, Toast.LENGTH_SHORT).show()
                }
                else -> {}
            }
        }
    }

    private fun routeUser(role: String) {
        val intent = when (role) {
            "CLIENT"  -> Intent(this, ClientDashboardActivity::class.java)
            "WORKER"  -> Intent(this, WorkerDashboardActivity::class.java)
            "COMPANY" -> Intent(this, CompanyDashboardActivity::class.java)
            else -> { Toast.makeText(this, "Unknown role: $role", Toast.LENGTH_SHORT).show(); null }
        }
        intent?.let { startActivity(it) }
    }

    private fun checkAccountDeletion(uid: String, role: String) {
        db.collection("users").document(uid).get().addOnSuccessListener { doc ->
            val timestamp = doc.getLong("deletionTimestamp")
            if (timestamp != null) {
                val threeDaysInMillis = 3 * 24 * 60 * 60 * 1000L
                if (System.currentTimeMillis() - timestamp < threeDaysInMillis) {
                    showUndoDeletionDialog(uid, role)
                } else {
                    Toast.makeText(this, "Account has been permanently deleted.", Toast.LENGTH_LONG).show()
                    auth.signOut()
                }
            } else {
                Toast.makeText(this, "Welcome!", Toast.LENGTH_SHORT).show()
                routeUser(role)
                finish()
            }
        }
    }

    private fun showUndoDeletionDialog(uid: String, role: String) {
        val dialog = Dialog(this)
        dialog.setContentView(R.layout.dialog_role_select)
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
        dialog.window?.setLayout(
            (resources.displayMetrics.widthPixels * 0.88).toInt(),
            ViewGroup.LayoutParams.WRAP_CONTENT
        )

        dialog.findViewById<ImageView>(R.id.dialogIcon).setImageResource(R.drawable.ic_nav_account)
        dialog.findViewById<TextView>(R.id.dialogTitle).text = "Restore Account?"
        dialog.findViewById<TextView>(R.id.dialogMessage).text =
            "Your account is marked for deletion. Would you like to restore it and continue?"

        dialog.findViewById<com.google.android.material.button.MaterialButton>(R.id.dialogBtnPositive).apply {
            text = "Restore"
            setOnClickListener {
                dialog.dismiss()
                restoreAccount(uid, role)
            }
        }

        dialog.findViewById<com.google.android.material.button.MaterialButton>(R.id.dialogBtnNegative).apply {
            text = "No, Exit"
            setOnClickListener {
                dialog.dismiss()
                auth.signOut()
            }
        }

        dialog.show()
    }

    private fun restoreAccount(uid: String, role: String) {
        db.collection("users").document(uid).update("deletionTimestamp", null)
            .addOnSuccessListener {
                Toast.makeText(this, "Account restored successfully!", Toast.LENGTH_SHORT).show()
                routeUser(role)
                finish()
            }
    }
}