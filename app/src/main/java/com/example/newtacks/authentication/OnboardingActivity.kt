package com.example.newtacks.authentication

import android.app.Dialog
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.text.SpannableString
import android.text.TextPaint
import android.text.method.LinkMovementMethod
import android.text.style.ClickableSpan
import android.view.View
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import android.widget.LinearLayout
import com.example.newtacks.R
import com.example.newtacks.utils.LegalDocumentActivity
import com.example.newtacks.utils.PrivacySecurityActivity

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
            val spannable = SpannableString(fullText)
            
            val termsStart = fullText.indexOf("Terms of Service")
            val termsEnd   = termsStart + "Terms of Service".length
            
            val privacyStart = fullText.indexOf("Privacy Policy")
            val privacyEnd   = privacyStart + "Privacy Policy".length
            
            val blue = Color.parseColor("#1E88E5")
            
            // Terms link
            spannable.setSpan(object : ClickableSpan() {
                override fun onClick(widget: View) {
                    LegalDocumentActivity.start(
                        this@OnboardingActivity,
                        LegalDocumentActivity.TITLE_TERMS,
                        "Last updated: September 2026",
                        PrivacySecurityActivity.TERMS_OF_SERVICE_TEXT
                    )
                }
                override fun updateDrawState(ds: TextPaint) {
                    ds.color = blue
                    ds.isUnderlineText = true
                    ds.isFakeBoldText = true
                }
            }, termsStart, termsEnd, 0)
            
            // Privacy link
            spannable.setSpan(object : ClickableSpan() {
                override fun onClick(widget: View) {
                    LegalDocumentActivity.start(
                        this@OnboardingActivity,
                        LegalDocumentActivity.TITLE_PRIVACY,
                        "Last updated: September 2026",
                        PrivacySecurityActivity.PRIVACY_POLICY_TEXT
                    )
                }
                override fun updateDrawState(ds: TextPaint) {
                    ds.color = blue
                    ds.isUnderlineText = true
                    ds.isFakeBoldText = true
                }
            }, privacyStart, privacyEnd, 0)
            
            text = spannable
            movementMethod = LinkMovementMethod.getInstance()
        }
    }
}
