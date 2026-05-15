package com.example.newtacks.client

import android.app.Dialog
import android.content.Intent
import android.os.Bundle
import android.view.*
import android.widget.Button
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.fragment.app.Fragment
import coil.load
import coil.transform.CircleCropTransformation
import com.example.newtacks.authentication.OnboardingActivity
import com.example.newtacks.R
import com.example.newtacks.chatbot.data.remote.RetrofitClient
import com.example.newtacks.chatbot.data.repository.ChatRepository
import com.example.newtacks.models.User
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

class ClientAccountFragment : Fragment() {

    private val firestore = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()
    private lateinit var tvName: TextView
    private lateinit var tvEmail: TextView
    private lateinit var tvAddress: TextView
    private lateinit var tvTotalRequests: TextView
    private lateinit var tvCompletedRequests: TextView
    private lateinit var ivProfile: ImageView
    private lateinit var btnLogout: Button
    private lateinit var layoutHeader: LinearLayout

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val view = inflater.inflate(R.layout.fragment_client_account, container, false)

        tvName              = view.findViewById(R.id.tvName)
        tvEmail             = view.findViewById(R.id.tvEmail)
        tvAddress           = view.findViewById(R.id.tvAddress)
        tvTotalRequests     = view.findViewById(R.id.tvTotalRequests)
        tvCompletedRequests = view.findViewById(R.id.tvCompletedRequests)
        ivProfile           = view.findViewById(R.id.ivProfile)
        btnLogout           = view.findViewById(R.id.btnLogout)
        layoutHeader        = view.findViewById(R.id.layoutHeader)

        ViewCompat.setOnApplyWindowInsetsListener(view) { _, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            layoutHeader.setPadding(
                layoutHeader.paddingLeft,
                systemBars.top + resources.getDimensionPixelSize(R.dimen.header_padding_top),
                layoutHeader.paddingRight,
                layoutHeader.paddingBottom
            )
            insets
        }

        loadProfile()
        loadStats()
        setupLogout()
        return view
    }

    // --------------------------------------------------
    // PROFILE
    // --------------------------------------------------
    private fun loadProfile() {
        val uid = auth.currentUser?.uid ?: return
        firestore.collection("users")
            .document(uid)
            .get()
            .addOnSuccessListener { doc ->
                val user = doc.toObject(User::class.java) ?: return@addOnSuccessListener
                tvName.text    = user.name
                tvEmail.text   = user.email
                tvAddress.text = user.address

                if (user.profileImage.isNotEmpty()) {
                    ivProfile.load(user.profileImage) {
                        crossfade(true)
                        placeholder(R.drawable.ic_person_placeholder)
                        error(R.drawable.ic_person_placeholder)
                        transformations(CircleCropTransformation())
                    }
                }
            }
    }

    // --------------------------------------------------
    // STATS
    // --------------------------------------------------
    private fun loadStats() {
        val uid = auth.currentUser?.uid ?: return
        firestore.collection("jobs")
            .whereEqualTo("clientId", uid)
            .get()
            .addOnSuccessListener { snapshot ->
                tvTotalRequests.text = "${snapshot.size()}"
            }
        firestore.collection("jobs")
            .whereEqualTo("clientId", uid)
            .whereEqualTo("status", "COMPLETED")
            .get()
            .addOnSuccessListener { snapshot ->
                tvCompletedRequests.text = "${snapshot.size()}"
            }
    }

    // --------------------------------------------------
    // LOGOUT
    // --------------------------------------------------
    private fun setupLogout() {
        btnLogout.setOnClickListener {
            showLogoutConfirmDialog()
        }
    }

    private fun showLogoutConfirmDialog() {
        val dialog = Dialog(requireContext())
        dialog.setContentView(R.layout.dialog_role_select)
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
        dialog.window?.setLayout(
            (resources.displayMetrics.widthPixels * 0.88).toInt(),
            android.view.ViewGroup.LayoutParams.WRAP_CONTENT
        )

        dialog.findViewById<ImageView>(R.id.dialogIcon).setImageResource(R.drawable.ic_nav_account)
        dialog.findViewById<TextView>(R.id.dialogTitle).text   = "Logout"
        dialog.findViewById<TextView>(R.id.dialogMessage).text = "Are you sure you want to log out?"

        dialog.findViewById<com.google.android.material.button.MaterialButton>(R.id.dialogBtnPositive)
            .setOnClickListener {
                dialog.dismiss()
                // Clear Tey's memory on logout
                ChatRepository.getInstance(RetrofitClient.chatApiService).clearSession()
                auth.signOut()
                Toast.makeText(requireContext(), "Logged out", Toast.LENGTH_SHORT).show()
                val intent = Intent(requireContext(), OnboardingActivity::class.java)
                intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                startActivity(intent)
            }

        dialog.findViewById<com.google.android.material.button.MaterialButton>(R.id.dialogBtnNegative)
            .setOnClickListener { dialog.dismiss() }

        dialog.show()
    }
}