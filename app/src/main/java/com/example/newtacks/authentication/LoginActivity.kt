package com.example.newtacks.authentication

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.EditText
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
        val signUp   = findViewById<android.widget.TextView>(R.id.goToSignup)

        btn.setOnClickListener { viewModel.login(email.text.toString(), password.text.toString()) }
        signUp.setOnClickListener { startActivity(Intent(this, RoleSelectionActivity::class.java)) }

        observeState()
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
                    Toast.makeText(this, "Welcome!", Toast.LENGTH_SHORT).show()
                    routeUser(state.role)
                    finish()
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
}