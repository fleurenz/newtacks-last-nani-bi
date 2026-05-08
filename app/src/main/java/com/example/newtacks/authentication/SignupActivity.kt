package com.example.newtacks.authentication

import android.app.Dialog
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.*
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.example.newtacks.R
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

class SignupActivity : AppCompatActivity() {

    private lateinit var viewModel: SignupViewModel
    private var selectedRole: String = "CLIENT"

    // IMAGE URI
    private var imageUri: Uri? = null

    // IMAGE PICKER
    private val pickImage =
        registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->

            if (uri != null) {
                imageUri = uri

                findViewById<ImageView>(R.id.imgProfile)
                    .setImageURI(uri)
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        WindowCompat.setDecorFitsSystemWindows(window, false)

        setContentView(R.layout.activity_signup)

        selectedRole = intent.getStringExtra("ROLE") ?: "CLIENT"

        val topSection = findViewById<LinearLayout>(R.id.topSection)

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.signupRoot)) { _, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())

            topSection.setPadding(
                topSection.paddingLeft,
                systemBars.top,
                topSection.paddingRight,
                topSection.paddingBottom
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
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    return SignupViewModel(repo) as T
                }
            }
        )[SignupViewModel::class.java]

        setupUIByRole()

        val email = findViewById<EditText>(R.id.etEmail)
        val password = findViewById<EditText>(R.id.etPassword)
        val confirm = findViewById<EditText>(R.id.etConfirmPassword)
        val btn = findViewById<Button>(R.id.btnRegister)

        // CHOOSE IMAGE BUTTON
        findViewById<Button>(R.id.btnChoosePhoto).setOnClickListener {
            pickImage.launch("image/*")
        }

        btn.setOnClickListener {

            val emailText = email.text.toString()
            val passwordText = password.text.toString()
            val confirmText = confirm.text.toString()

            var name = ""
            var phone = ""
            var address = ""
            var companyName = ""
            var hrName = ""
            var experience: Int? = null
            val categories = mutableListOf<String>()

            when (selectedRole) {

                "CLIENT" -> {
                    name = findViewById<EditText>(R.id.etClientName).text.toString()
                    phone = findViewById<EditText>(R.id.etClientPhone).text.toString()
                    address = findViewById<EditText>(R.id.etClientAddress).text.toString()
                }

                "WORKER" -> {
                    name = findViewById<EditText>(R.id.etWorkerName).text.toString()
                    phone = findViewById<EditText>(R.id.etWorkerPhone).text.toString()
                    address = findViewById<EditText>(R.id.etWorkerAddress).text.toString()

                    val expText = findViewById<EditText>(R.id.etExperience).text.toString()
                    experience = if (expText.isNotEmpty()) expText.toInt() else 0

                    if (findViewById<CheckBox>(R.id.cbPlumbing).isChecked)
                        categories.add("Plumbing")

                    if (findViewById<CheckBox>(R.id.cbElectrical).isChecked)
                        categories.add("Electrical")

                    if (findViewById<CheckBox>(R.id.cbCarpentry).isChecked)
                        categories.add("Carpentry")
                }

                "COMPANY" -> {
                    companyName = findViewById<EditText>(R.id.etCompanyName).text.toString()
                    hrName = findViewById<EditText>(R.id.etHRName).text.toString()
                    phone = findViewById<EditText>(R.id.etCompanyPhone).text.toString()
                    address = findViewById<EditText>(R.id.etCompanyAddress).text.toString()
                }
            }

            // SEND TO VIEWMODEL (NO UPLOAD HERE)
            viewModel.register(
                imageUri = imageUri,
                email = emailText,
                password = passwordText,
                confirmPassword = confirmText,
                role = selectedRole,
                name = name,
                phone = phone,
                address = address,
                companyName = companyName,
                hrName = hrName,
                categories = categories,
                experience = experience
            )
        }

        observeState()

        findViewById<Button>(R.id.btnCancel).setOnClickListener {
            handleBackPress()
        }

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                handleBackPress()
            }
        })
    }

    private fun handleBackPress() {
        if (isFormDirty()) {
            showDiscardDialog()
        } else {
            finish()
        }
    }

    private fun isFormDirty(): Boolean {
        val email = findViewById<EditText>(R.id.etEmail).text.toString()
        val password = findViewById<EditText>(R.id.etPassword).text.toString()
        return email.isNotEmpty() || password.isNotEmpty() || imageUri != null
    }

    private fun showDiscardDialog() {
        val dialog = Dialog(this)
        dialog.setContentView(R.layout.dialog_role_select)
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
        dialog.window?.setLayout(
            (resources.displayMetrics.widthPixels * 0.88).toInt(),
            android.view.ViewGroup.LayoutParams.WRAP_CONTENT
        )

        dialog.findViewById<ImageView>(R.id.dialogIcon).setImageResource(R.drawable.ic_close)
        dialog.findViewById<TextView>(R.id.dialogTitle).text = "Discard Changes?"
        dialog.findViewById<TextView>(R.id.dialogMessage).text = "Are you sure you want to discard your registration progress?"

        dialog.findViewById<com.google.android.material.button.MaterialButton>(R.id.dialogBtnPositive).apply {
            this.text = "Discard"
            this.setOnClickListener {
                dialog.dismiss()
                finish()
            }
        }

        dialog.findViewById<com.google.android.material.button.MaterialButton>(R.id.dialogBtnNegative).apply {
            this.text = "Keep Filling"
            this.setOnClickListener {
                dialog.dismiss()
            }
        }

        dialog.show()
    }

    private fun setupUIByRole() {

        val clientGroup = findViewById<View>(R.id.clientGroup)
        val workerGroup = findViewById<View>(R.id.workerGroup)
        val companyGroup = findViewById<View>(R.id.companyGroup)

        clientGroup.visibility = View.GONE
        workerGroup.visibility = View.GONE
        companyGroup.visibility = View.GONE

        when (selectedRole) {

            "CLIENT" -> clientGroup.visibility = View.VISIBLE
            "WORKER" -> workerGroup.visibility = View.VISIBLE
            "COMPANY" -> companyGroup.visibility = View.VISIBLE
        }
    }

    private fun observeState() {

        viewModel.signupState.observe(this) { state ->

            when (state) {

                is SignupState.Loading -> {
                    Toast.makeText(this, "Creating account...", Toast.LENGTH_SHORT).show()
                }

                is SignupState.Success -> {
                    Toast.makeText(this, "Account created", Toast.LENGTH_SHORT).show()
                    startActivity(Intent(this, LoginActivity::class.java))
                    finish()
                }

                is SignupState.Error -> {
                    Toast.makeText(this, state.message, Toast.LENGTH_SHORT).show()
                }

                else -> {}
            }
        }
    }
}