package com.example.newtacks.authentication

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import com.example.newtacks.ClientDashboardActivity
import com.example.newtacks.CompanyDashboardActivity
import com.example.newtacks.R
import com.example.newtacks.WorkerDashboardActivity
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

class SplashActivity : AppCompatActivity() {

    private lateinit var progressBar: ProgressBar
    private lateinit var tvStatus: TextView
    private val handler = Handler(Looper.getMainLooper())
    private var currentProgress = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        setContentView(R.layout.activity_splash)

        progressBar = findViewById(R.id.splashProgress)
        tvStatus    = findViewById(R.id.tvSplashStatus)

        startLoadingSequence()
    }

    // --------------------------------------------------
    // STEP 1 — animate to 40% while checking auth
    // --------------------------------------------------
    private fun startLoadingSequence() {
        updateStatus("Initializing...", 0)
        animateProgress(0, 30, 600) {
            checkAuth()
        }
    }

    // --------------------------------------------------
    // STEP 2 — check Firebase auth
    // --------------------------------------------------
    private fun checkAuth() {
        updateStatus("Checking session...", 30)
        animateProgress(30, 60, 400) {
            val user = FirebaseAuth.getInstance().currentUser
            if (user != null) {
                fetchUserRole(user.uid)
            } else {
                animateProgress(60, 100, 300) {
                    navigateToOnboarding()
                }
            }
        }
    }

    // --------------------------------------------------
    // STEP 3 — fetch role from Firestore
    // --------------------------------------------------
    private fun fetchUserRole(uid: String) {
        updateStatus("Loading profile...", 60)
        FirebaseFirestore.getInstance().collection("users")
            .document(uid)
            .get()
            .addOnSuccessListener { document ->
                updateStatus("Almost there...", 80)
                animateProgress(60, 100, 500) {
                    if (document != null && document.exists()) {
                        routeUser(document.getString("role"))
                    } else {
                        FirebaseAuth.getInstance().signOut()
                        navigateToOnboarding()
                    }
                }
            }
            .addOnFailureListener { e ->
                updateStatus("Connection error...", progressBar.progress)
                Toast.makeText(
                    this,
                    "Connection error: ${e.message}",
                    Toast.LENGTH_SHORT
                ).show()
                handler.postDelayed({ navigateToOnboarding() }, 1000)
            }
    }

    // --------------------------------------------------
    // ANIMATE PROGRESS BAR
    // --------------------------------------------------
    private fun animateProgress(from: Int, to: Int, duration: Long, onDone: () -> Unit) {
        val steps    = to - from
        val interval = duration / steps.coerceAtLeast(1)
        var current  = from

        val runnable = object : Runnable {
            override fun run() {
                if (current < to) {
                    current++
                    progressBar.progress = current
                    handler.postDelayed(this, interval)
                } else {
                    onDone()
                }
            }
        }
        handler.post(runnable)
    }

    // --------------------------------------------------
    // UPDATE STATUS TEXT
    // --------------------------------------------------
    private fun updateStatus(message: String, progress: Int) {
        tvStatus.text        = message
        progressBar.progress = progress
    }

    // --------------------------------------------------
    // NAVIGATE
    // --------------------------------------------------
    private fun navigateToOnboarding() {
        startActivity(Intent(this, OnboardingActivity::class.java))
        overridePendingTransition(R.anim.fade_in, R.anim.fade_out)
        finish()
    }

    private fun routeUser(role: String?) {
        val intent = when (role) {
            "CLIENT"  -> Intent(this, ClientDashboardActivity::class.java)
            "WORKER"  -> Intent(this, WorkerDashboardActivity::class.java)
            "COMPANY" -> Intent(this, CompanyDashboardActivity::class.java)
            else      -> Intent(this, OnboardingActivity::class.java)
        }
        startActivity(intent)
        overridePendingTransition(R.anim.fade_in, R.anim.fade_out)
        finish()
    }

    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacksAndMessages(null)
    }
}