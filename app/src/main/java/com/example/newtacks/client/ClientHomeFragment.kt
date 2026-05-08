package com.example.newtacks.client

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.Toast
import androidx.fragment.app.Fragment
import com.example.newtacks.CreateJobActivity
import com.example.newtacks.R
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

class ClientHomeFragment : Fragment() {

    private val firestore = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {

        val view = inflater.inflate(
            R.layout.fragment_client_home,
            container,
            false
        )

        val redButton =
            view.findViewById<Button>(R.id.btnCreateJob)

        redButton.setOnClickListener {

            val currentUser = auth.currentUser ?: return@setOnClickListener

            // --------------------------------------------------
            // CHECK ACTIVE JOB FIRST
            // --------------------------------------------------

            firestore.collection("jobs")
                .whereEqualTo("clientId", currentUser.uid)
                .whereIn(
                    "status",
                    listOf(
                        "AVAILABLE",
                        "IN_PROGRESS",
                        "PENDING_VERIFICATION"
                    )
                )
                .get()
                .addOnSuccessListener { snapshots ->

                    // CLIENT HAS ACTIVE JOB
                    if (!snapshots.isEmpty) {

                        Toast.makeText(
                            requireContext(),
                            "Finish your active request first",
                            Toast.LENGTH_LONG
                        ).show()

                        return@addOnSuccessListener
                    }

                    // OPEN CREATE JOB SCREEN

                    val intent = Intent(
                        requireContext(),
                        CreateJobActivity::class.java
                    )

                    startActivity(intent)
                }
        }

        return view
    }
}