package com.example.newtacks.company

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import com.example.newtacks.R
import com.google.android.material.button.MaterialButton
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

class CompanyHomeFragment : Fragment() {

    private val firestore = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val view = inflater.inflate(R.layout.fragment_company_home, container, false)

        view.findViewById<MaterialButton>(R.id.btnCreateHiring).setOnClickListener {
            checkActiveHiringAndOpen()
        }

        return view
    }

    private fun checkActiveHiringAndOpen() {
        val uid = auth.currentUser?.uid ?: return
        
        firestore.collection("hiring")
            .whereEqualTo("companyId", uid)
            .whereEqualTo("status", "OPEN")
            .get(com.google.firebase.firestore.Source.SERVER)
            .addOnSuccessListener { snapshots ->
                if (!snapshots.isEmpty) {
                    Toast.makeText(requireContext(), "You already have an active hiring post", Toast.LENGTH_LONG).show()
                } else {
                    // Start Activity (To be created)
                    val intent = Intent(requireContext(), CreateHiringActivity::class.java)
                    startActivity(intent)
                }
            }
            .addOnFailureListener {
                Toast.makeText(requireContext(), "Error checking hiring status", Toast.LENGTH_SHORT).show()
            }
    }
}