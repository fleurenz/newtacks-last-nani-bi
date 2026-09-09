package com.example.newtacks.company

import android.app.Dialog
import android.content.Intent
import android.os.Bundle
import android.view.*
import android.widget.*
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.fragment.app.Fragment
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import coil.load
import coil.transform.CircleCropTransformation
import com.example.newtacks.R
import com.example.newtacks.authentication.OnboardingActivity
import com.example.newtacks.chatbot.data.remote.RetrofitClient
import com.example.newtacks.chatbot.data.repository.ChatRepository
import com.example.newtacks.models.User
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

class CompanyAccountFragment : Fragment() {

    private val firestore = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()
    
    private lateinit var tvCompanyName: TextView
    private lateinit var ivCompanyProfile: ImageView
    private lateinit var menuLogout: View
    private lateinit var menuEditProfile: View
    private lateinit var swipeRefresh: SwipeRefreshLayout
    
    private var currentAboutUs: String = ""

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_company_account, container, false)

        tvCompanyName    = view.findViewById(R.id.tvCompanyName)
        ivCompanyProfile = view.findViewById(R.id.ivCompanyProfile)
        menuLogout       = view.findViewById(R.id.menuLogout)
        menuEditProfile  = view.findViewById(R.id.menuEditProfile)
        swipeRefresh     = view.findViewById(R.id.swipeRefreshAccount)

        // Robust Inset Handling: Use spacer for status bar
        val statusBarSpacer = view.findViewById<View>(R.id.statusBarSpacer)
        ViewCompat.setOnApplyWindowInsetsListener(view) { _, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            val params = statusBarSpacer.layoutParams
            params.height = systemBars.top
            statusBarSpacer.layoutParams = params
            insets
        }

        loadProfile()
        setupLogout()
        setupEditProfileMenu()

        swipeRefresh.setOnRefreshListener {
            loadProfile()
        }

        return view
    }

    override fun onHiddenChanged(hidden: Boolean) {
        super.onHiddenChanged(hidden)
        if (!hidden) loadProfile()
    }

    private fun loadProfile() {
        val uid = auth.currentUser?.uid ?: return
        firestore.collection("users").document(uid).get().addOnSuccessListener { doc ->
            val user = doc.toObject(User::class.java) ?: return@addOnSuccessListener
            
            tvCompanyName.text = user.companyName ?: user.name
            
            currentAboutUs = user.aboutUs ?: ""

            if (user.profileImage.isNotEmpty()) {
                ivCompanyProfile.load(user.profileImage) {
                    crossfade(true)
                    placeholder(R.drawable.ic_person_placeholder)
                    error(R.drawable.ic_person_placeholder)
                    transformations(CircleCropTransformation())
                }
            }
            swipeRefresh.isRefreshing = false
        }.addOnFailureListener {
            swipeRefresh.isRefreshing = false
        }
    }

    private fun setupEditProfileMenu() {
        menuEditProfile.setOnClickListener {
            startActivity(Intent(requireContext(), CompanyEditProfileActivity::class.java))
        }
    }

    private fun setupLogout() {
        menuLogout.setOnClickListener {
            showLogoutConfirmDialog()
        }
    }

    private fun showLogoutConfirmDialog() {
        val dialog = Dialog(requireContext())
        dialog.setContentView(R.layout.dialog_role_select)
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
        dialog.window?.setLayout(
            (resources.displayMetrics.widthPixels * 0.88).toInt(),
            ViewGroup.LayoutParams.WRAP_CONTENT
        )

        dialog.findViewById<ImageView>(R.id.dialogIcon).setImageResource(R.drawable.ic_nav_account)
        dialog.findViewById<TextView>(R.id.dialogTitle).text = "Logout"
        dialog.findViewById<TextView>(R.id.dialogMessage).text = "Are you sure you want to log out?"

        dialog.findViewById<com.google.android.material.button.MaterialButton>(R.id.dialogBtnPositive)
            .setOnClickListener {
                dialog.dismiss()
                ChatRepository.getInstance(RetrofitClient.chatApiService).clearSession()
                // Stop notification service on logout
                requireContext().stopService(android.content.Intent(requireContext(), com.example.newtacks.utils.NotificationService::class.java))
                
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
