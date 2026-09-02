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
import com.example.newtacks.worker.account.WorkerReviewsActivity
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

class CompanyAccountFragment : Fragment() {

    private val firestore = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()
    
    private lateinit var tvCompanyName: TextView
    private lateinit var tvCompanyRating: TextView
    private lateinit var tvAboutUs: TextView
    private lateinit var ivCompanyProfile: ImageView
    private lateinit var layoutHeader: LinearLayout
    private lateinit var menuLogout: LinearLayout
    private lateinit var menuReviews: LinearLayout
    private lateinit var btnEditAbout: ImageView
    private lateinit var swipeRefresh: SwipeRefreshLayout

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_company_account, container, false)

        tvCompanyName = view.findViewById(R.id.tvCompanyName)
        tvCompanyRating = view.findViewById(R.id.tvCompanyRating)
        tvAboutUs = view.findViewById(R.id.tvAboutUs)
        ivCompanyProfile = view.findViewById(R.id.ivCompanyProfile)
        layoutHeader = view.findViewById(R.id.layoutHeader)
        menuLogout = view.findViewById(R.id.menuLogout)
        menuReviews = view.findViewById(R.id.menuReviews)
        btnEditAbout = view.findViewById(R.id.btnEditAbout)
        swipeRefresh = view.findViewById(R.id.swipeRefreshAccount)

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
        setupLogout()
        setupReviewsMenu()
        setupEditAbout()

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
            
            val avg = doc.getDouble("ratingAverage") ?: user.rating
            val count = doc.getLong("ratingCount") ?: user.totalRatings.toLong()
            tvCompanyRating.text = "%.1f (%d reviews)".format(avg, count)

            tvAboutUs.text = user.aboutUs ?: "No information provided."

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

    private fun setupEditAbout() {
        btnEditAbout.setOnClickListener {
            showEditAboutDialog()
        }
    }

    private fun showEditAboutDialog() {
        val dialog = Dialog(requireContext())
        dialog.setContentView(R.layout.dialog_role_select)
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
        dialog.window?.setLayout(
            (resources.displayMetrics.widthPixels * 0.9).toInt(),
            ViewGroup.LayoutParams.WRAP_CONTENT
        )

        val title = dialog.findViewById<TextView>(R.id.dialogTitle)
        val message = dialog.findViewById<TextView>(R.id.dialogMessage)
        val btnSave = dialog.findViewById<com.google.android.material.button.MaterialButton>(R.id.dialogBtnPositive)
        val btnCancel = dialog.findViewById<com.google.android.material.button.MaterialButton>(R.id.dialogBtnNegative)
        val icon = dialog.findViewById<ImageView>(R.id.dialogIcon)

        icon.setImageResource(R.drawable.ic_plus)
        title.text = "Edit About Us"
        message.visibility = View.GONE

        // Add EditText dynamically
        val container = dialog.findViewById<LinearLayout>(R.id.dialogRoot)
        val editText = EditText(requireContext()).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                setMargins(24, 0, 24, 24)
            }
            hint = "Tell us about your company..."
            setText(if (tvAboutUs.text == "No information provided.") "" else tvAboutUs.text)
            minLines = 3
            gravity = Gravity.TOP
            setBackgroundResource(R.drawable.bg_message_input)
            setPadding(16, 16, 16, 16)
        }
        container.addView(editText, 3) // Add after the divider

        btnSave.text = "Save"
        btnSave.setOnClickListener {
            val newAbout = editText.text.toString().trim()
            saveAboutUs(newAbout)
            dialog.dismiss()
        }

        btnCancel.text = "Cancel"
        btnCancel.setOnClickListener { dialog.dismiss() }

        dialog.show()
    }

    private fun saveAboutUs(text: String) {
        val uid = auth.currentUser?.uid ?: return
        firestore.collection("users").document(uid)
            .update("aboutUs", if (text.isEmpty()) null else text)
            .addOnSuccessListener {
                tvAboutUs.text = if (text.isEmpty()) "No information provided." else text
                Toast.makeText(requireContext(), "Profile updated", Toast.LENGTH_SHORT).show()
            }
    }

    private fun setupReviewsMenu() {
        menuReviews.setOnClickListener {
            val intent = Intent(requireContext(), WorkerReviewsActivity::class.java)
            intent.putExtra("TARGET_UID", auth.currentUser?.uid)
            startActivity(intent)
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
