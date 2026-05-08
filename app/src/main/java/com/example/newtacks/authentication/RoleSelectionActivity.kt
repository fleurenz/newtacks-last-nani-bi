package com.example.newtacks.authentication

import android.app.Dialog
import android.content.Intent
import android.os.Bundle
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.RadioGroup
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import com.example.newtacks.R

class RoleSelectionActivity : AppCompatActivity() {

    private lateinit var roleGroup: RadioGroup

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        setContentView(R.layout.activity_role_selection)

        val topSection    = findViewById<LinearLayout>(R.id.topSection)
        val bottomButtons = findViewById<LinearLayout>(R.id.bottomButtons)

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { _, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())

            topSection.setPadding(
                topSection.paddingLeft,
                systemBars.top,
                topSection.paddingRight,
                topSection.paddingBottom
            )

            bottomButtons.setPadding(
                bottomButtons.paddingLeft,
                bottomButtons.paddingTop,
                bottomButtons.paddingRight,
                systemBars.bottom
            )

            insets
        }

        roleGroup = findViewById(R.id.roleGroup)

        findViewById<LinearLayout>(R.id.cardClient).setOnClickListener {
            roleGroup.check(R.id.rbClient)
        }
        findViewById<LinearLayout>(R.id.cardWorker).setOnClickListener {
            roleGroup.check(R.id.rbWorker)
        }
        findViewById<LinearLayout>(R.id.cardCompany).setOnClickListener {
            roleGroup.check(R.id.rbCompany)
        }
        findViewById<Button>(R.id.btnContinue).setOnClickListener { handleContinue() }
        findViewById<Button>(R.id.btnBack).setOnClickListener {
            startActivity(Intent(this, OnboardingActivity::class.java))
            finish()
        }
        findViewById<TextView>(R.id.tvLogin).setOnClickListener {
            startActivity(Intent(this, LoginActivity::class.java))
            finish()
        }
    }


    private fun handleContinue() {
        val selectedRole = when (roleGroup.checkedRadioButtonId) {
            R.id.rbClient  -> "CLIENT"
            R.id.rbWorker  -> "WORKER"
            R.id.rbCompany -> "COMPANY"
            else           -> null
        }

        if (selectedRole == null) {
            showInfoDialog(
                iconRes   = R.drawable.ic_nav_account,
                title     = "Missing Selection",
                message   = "Please select a role first.",
                btnText   = "OK",
                onConfirm = null
            )
            return
        }

        confirmRole(selectedRole)
    }

    private fun confirmRole(role: String) {
        val roleLabel = role.lowercase().replaceFirstChar { it.uppercase() }

        val iconRes = when (role) {
            "CLIENT"  -> R.drawable.ic_nav_account
            "WORKER"  -> R.drawable.ic_nav_requests
            "COMPANY" -> R.drawable.ic_company
            else      -> R.drawable.ic_nav_account
        }

        showConfirmDialog(
            iconRes   = iconRes,
            title     = "Confirm Role",
            message   = "Are you sure you want to register as $roleLabel?",
            onConfirm = {
                val intent = Intent(this, SignupActivity::class.java)
                intent.putExtra("ROLE", role)
                startActivity(intent)
            }
        )
    }

    private fun showConfirmDialog(
        iconRes: Int,
        title: String,
        message: String,
        onConfirm: () -> Unit
    ) {
        val dialog = Dialog(this)
        dialog.setContentView(R.layout.dialog_role_select)
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
        dialog.window?.setLayout(
            (resources.displayMetrics.widthPixels * 0.88).toInt(),
            ViewGroup.LayoutParams.WRAP_CONTENT
        )

        dialog.findViewById<ImageView>(R.id.dialogIcon).setImageResource(iconRes)
        dialog.findViewById<TextView>(R.id.dialogTitle).text   = title
        dialog.findViewById<TextView>(R.id.dialogMessage).text = message

        dialog.findViewById<com.google.android.material.button.MaterialButton>(R.id.dialogBtnPositive)
            .setOnClickListener {
                dialog.dismiss()
                onConfirm()
            }

        dialog.findViewById<com.google.android.material.button.MaterialButton>(R.id.dialogBtnNegative)
            .setOnClickListener { dialog.dismiss() }

        dialog.show()
    }

    private fun showInfoDialog(
        iconRes: Int,
        title: String,
        message: String,
        btnText: String = "OK",
        onConfirm: (() -> Unit)?
    ) {
        val dialog = Dialog(this)
        dialog.setContentView(R.layout.dialog_role_select)  // ✅ missing ) was here
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
        dialog.window?.setLayout(
            (resources.displayMetrics.widthPixels * 0.88).toInt(),
            ViewGroup.LayoutParams.WRAP_CONTENT         // ✅ proper import, no android.view prefix needed
        )

        dialog.findViewById<ImageView>(R.id.dialogIcon).setImageResource(iconRes)
        dialog.findViewById<TextView>(R.id.dialogTitle).text   = title
        dialog.findViewById<TextView>(R.id.dialogMessage).text = message

        val btnPositive = dialog.findViewById<com.google.android.material.button.MaterialButton>(
            R.id.dialogBtnPositive
        )
        val btnNegative = dialog.findViewById<com.google.android.material.button.MaterialButton>(
            R.id.dialogBtnNegative
        )

        btnNegative.visibility = android.view.View.GONE
        btnPositive.text = btnText

        btnPositive.setOnClickListener {
            dialog.dismiss()
            onConfirm?.invoke()
        }

        dialog.show()
    }
}