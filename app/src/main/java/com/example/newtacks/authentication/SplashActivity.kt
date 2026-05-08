package com.example.newtacks.authentication

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.newtacks.ClientDashboardActivity
import com.example.newtacks.CompanyDashboardActivity
import com.example.newtacks.R
import com.example.newtacks.WorkerDashboardActivity
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

class SplashActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_splash)

        Handler(Looper.getMainLooper()).postDelayed({
            val user = FirebaseAuth.getInstance().currentUser
            if (user != null) {
                // Fetch role and route
                FirebaseFirestore.getInstance().collection("users")
                    .document(user.uid)
                    .get()
                    .addOnSuccessListener { document ->
                        if (document != null && document.exists()) {
                            val role = document.getString("role")
                            routeUser(role)
                        } else {
                            // User document doesn't exist, sign out and go to onboarding
                            FirebaseAuth.getInstance().signOut()
                            navigateToOnboarding()
                        }
                    }
                    .addOnFailureListener { e ->
                        Toast.makeText(this, "Connection error: ${e.message}", Toast.LENGTH_SHORT).show()
                        navigateToOnboarding()
                    }
            } else {
                navigateToOnboarding()
            }
        }, 2000)
    }

    private fun navigateToOnboarding() {
        startActivity(Intent(this, OnboardingActivity::class.java))
        finish()
    }

    private fun routeUser(role: String?) {
        val intent = when (role) {
            "CLIENT" -> Intent(this, ClientDashboardActivity::class.java)
            "WORKER" -> Intent(this, WorkerDashboardActivity::class.java)
            "COMPANY" -> Intent(this, CompanyDashboardActivity::class.java)
            else -> Intent(this, OnboardingActivity::class.java)
        }
        startActivity(intent)
        finish()
    }
}
