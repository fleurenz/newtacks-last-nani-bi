package com.example.newtacks.authentication

import android.app.Dialog
import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import android.widget.LinearLayout
import com.example.newtacks.R

class OnboardingActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        setContentView(R.layout.activity_onboarding)

        val topSection  = findViewById<LinearLayout>(R.id.topSection)
        val bottomCard  = findViewById<LinearLayout>(R.id.bottomCard)

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { _, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())

            topSection.setPadding(
                topSection.paddingLeft,
                systemBars.top,
                topSection.paddingRight,
                topSection.paddingBottom
            )

            bottomCard.setPadding(
                bottomCard.paddingLeft,
                bottomCard.paddingTop,
                bottomCard.paddingRight,
                systemBars.bottom + resources.getDimensionPixelSize(R.dimen.spacing_24)
            )

            insets
        }

        findViewById<Button>(R.id.btnGetStarted).setOnClickListener {
            startActivity(Intent(this, RoleSelectionActivity::class.java))
        }

        findViewById<Button>(R.id.btnLogin).setOnClickListener {
            startActivity(Intent(this, LoginActivity::class.java))
        }

        findViewById<TextView>(R.id.tvTerms).apply {
            val fullText  = "By continuing, you agree to our Terms of Service and Privacy Policy"
            val spannable = android.text.SpannableString(fullText)
            val start     = fullText.indexOf("Terms of Service and Privacy Policy")
            val end       = fullText.length
            spannable.setSpan(android.text.style.ForegroundColorSpan(android.graphics.Color.parseColor("#1E88E5")), start, end, 0)
            spannable.setSpan(android.text.style.UnderlineSpan(), start, end, 0)
            spannable.setSpan(android.text.style.StyleSpan(android.graphics.Typeface.BOLD), start, end, 0)
            text = spannable
            setOnClickListener { showTermsDialog() }
        }
    }

    private fun showTermsDialog() {
        val dialog = Dialog(this)
        dialog.setContentView(R.layout.dialog_role_select)
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
        dialog.window?.setLayout(
            (resources.displayMetrics.widthPixels * 0.88).toInt(),
            android.view.ViewGroup.LayoutParams.WRAP_CONTENT
        )
        dialog.findViewById<android.widget.ImageView>(R.id.dialogIcon)
            .setImageResource(R.drawable.ic_nav_account)
        dialog.findViewById<TextView>(R.id.dialogTitle).text   = "Terms of Service"
        dialog.findViewById<TextView>(R.id.dialogMessage).text =
            "By using Tacks, you agree to connect honestly with clients and workers. " +
                    "We are not responsible for disputes between parties. " +
                    "All transactions are between the client and worker directly. " +
                    "Your data is kept private and never sold to third parties."

        dialog.findViewById<com.google.android.material.button.MaterialButton>(R.id.dialogBtnNegative)
            .apply {
                text = "Close"
                setOnClickListener { dialog.dismiss() }
            }.visibility = android.view.View.VISIBLE

        dialog.findViewById<com.google.android.material.button.MaterialButton>(R.id.dialogBtnPositive)
            .apply {
                text = "Got it"
                setOnClickListener { dialog.dismiss() }
            }

        dialog.show()
    }
}