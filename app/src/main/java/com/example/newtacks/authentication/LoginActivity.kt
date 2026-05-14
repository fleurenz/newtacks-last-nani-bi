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
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

class LoginActivity : AppCompatActivity() {

    private lateinit var viewModel: LoginViewModel

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        setContentView(R.layout.activity_login)

        val topSection = findViewById<LinearLayout>(R.id.topSection)
        val bottomCard = findViewById<LinearLayout>(R.id.bottomCard)

        // Animate bottom card entry
        val slideUp = AnimationUtils.loadAnimation(this, R.anim.fade_in_slide_up)
        bottomCard.startAnimation(slideUp)

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { _, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            val imeInsets  = insets.getInsets(WindowInsetsCompat.Type.ime())

            topSection.setPadding(
                topSection.paddingLeft,
                systemBars.top,
                topSection.paddingRight,
                topSection.paddingBottom
            )

            // ✅ When keyboard is open imeInsets.bottom > 0, push card up
            val bottomPadding = maxOf(systemBars.bottom, imeInsets.bottom) +
                    resources.getDimensionPixelSize(R.dimen.spacing_24)

            bottomCard.setPadding(
                bottomCard.paddingLeft,
                bottomCard.paddingTop,
                bottomCard.paddingRight,
                bottomPadding
            )

            insets
        }

        val repo = AuthRepository(
            FirebaseAuth.getInstance(),
            FirebaseFirestore.getInstance()
        )

        viewModel = ViewModelProvider(
            this,
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    return LoginViewModel(repo) as T
                }
            }
        )[LoginViewModel::class.java]

        val email    = findViewById<EditText>(R.id.etEmail)
        val password = findViewById<EditText>(R.id.etPassword)
        val btn      = findViewById<Button>(R.id.btnLogin)
        val signUp   = findViewById<android.widget.TextView>(R.id.goToSignup)

        btn.setOnClickListener {
            viewModel.login(
                email.text.toString(),
                password.text.toString()
            )
        }

        signUp.setOnClickListener {
            startActivity(Intent(this, RoleSelectionActivity::class.java))
        }

        observeState()
    }

    private fun observeState() {
        val btnLogin      = findViewById<com.google.android.material.button.MaterialButton>(R.id.btnLogin)
        val loginProgress = findViewById<ProgressBar>(R.id.loginProgress)

        viewModel.loginState.observe(this) { state ->
            when (state) {
                is LoginState.Loading -> {
                    btnLogin.text      = ""           // ✅ hide text so spinner is centered
                    btnLogin.isEnabled = false        // ✅ prevent double tap
                    loginProgress.visibility = View.VISIBLE
                }
                is LoginState.Success -> {
                    loginProgress.visibility = View.GONE
                    btnLogin.isEnabled = true
                    btnLogin.text      = "Log In"
                    Toast.makeText(this, "Welcome!", Toast.LENGTH_SHORT).show()
                    routeUser(state.role)
                    finish()
                }
                is LoginState.Error -> {
                    loginProgress.visibility = View.GONE
                    btnLogin.isEnabled = true
                    btnLogin.text      = "Log In"
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
            else -> {
                Toast.makeText(this, "Unknown role: $role", Toast.LENGTH_SHORT).show()
                null
            }
        }
        intent?.let { startActivity(it) }
    }
}