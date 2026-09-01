package com.example.newtacks

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.fragment.app.Fragment
import com.example.newtacks.chatbot.presentation.ui.ChatActivity
import com.example.newtacks.company.*
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.android.material.floatingactionbutton.FloatingActionButton

class CompanyDashboardActivity : AppCompatActivity() {

    private var backPressedTime: Long = 0

    private val fragmentHome    = CompanyHomeFragment()
    private val fragmentHiring  = CompanyHiringFragment()
    private val fragmentHistory = CompanyHistoryFragment()
    private val fragmentAccount = CompanyAccountFragment()

    private var activeFragment: Fragment = fragmentHome

    private fun setupDraggableChatHead() {
        val fab = findViewById<FloatingActionButton>(R.id.fabChat)
        var dX = 0f
        var dY = 0f

        var startX = 0f
        var startY = 0f
        val clickThreshold = 10 // pixels

        fab.setOnTouchListener { view, event ->
            when (event.actionMasked) {
                android.view.MotionEvent.ACTION_DOWN -> {
                    dX = view.x - event.rawX
                    dY = view.y - event.rawY
                    startX = event.rawX
                    startY = event.rawY
                    view.animate().scaleX(1.1f).scaleY(1.1f).alpha(1.0f).setDuration(100).start()
                }
                android.view.MotionEvent.ACTION_MOVE -> {
                    view.y = event.rawY + dY
                    view.x = event.rawX + dX
                }
                android.view.MotionEvent.ACTION_UP -> {
                    val endX = event.rawX
                    val endY = event.rawY
                    val distance = Math.sqrt(Math.pow((endX - startX).toDouble(), 2.0) + Math.pow((endY - startY).toDouble(), 2.0))

                    if (distance < clickThreshold) {
                        view.performClick()
                    } else {
                        // Snap to edges
                        val screenWidth = resources.displayMetrics.widthPixels
                        val finalX = if (view.x + view.width / 2 < screenWidth / 2) {
                            16f // Snap to left
                        } else {
                            (screenWidth - view.width - 16).toFloat() // Snap to right
                        }

                        view.animate()
                            .x(finalX)
                            .scaleX(0.8f) // Shrink effect
                            .scaleY(0.8f)
                            .alpha(0.6f)  // Transparent effect
                            .setDuration(300)
                            .start()
                    }
                }
                else -> return@setOnTouchListener false
            }
            true
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        setContentView(R.layout.activity_company_dashboard)

        val bottomNav = findViewById<BottomNavigationView>(R.id.companyBottomNav)

        // ✅ Apply insets
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.companyRootLayout)) { _, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            bottomNav.setPadding(0, 0, 0, systemBars.bottom)
            insets
        }

        // ✅ Add ALL fragments once, hide all except the active one
        supportFragmentManager.beginTransaction().apply {
            add(R.id.companyFragmentContainer, fragmentAccount, "account").hide(fragmentAccount)
            add(R.id.companyFragmentContainer, fragmentHistory, "history").hide(fragmentHistory)
            add(R.id.companyFragmentContainer, fragmentHiring, "hiring").hide(fragmentHiring)
            add(R.id.companyFragmentContainer, fragmentHome, "home")
        }.commit()

        findViewById<FloatingActionButton>(R.id.fabChat).setOnClickListener {
            val intent = Intent(this, ChatActivity::class.java)
            intent.putExtra("USER_ROLE", "company")
            startActivity(intent)
        }

        setupDraggableChatHead()

        bottomNav.setOnItemSelectedListener { item ->
            val target = when (item.itemId) {
                R.id.nav_company_home    -> fragmentHome
                R.id.nav_company_hiring  -> fragmentHiring
                R.id.nav_company_history -> fragmentHistory
                R.id.nav_company_account -> fragmentAccount
                else             -> return@setOnItemSelectedListener false
            }

            if (target !== activeFragment) {
                supportFragmentManager.beginTransaction()
                    .setCustomAnimations(R.anim.fade_in, R.anim.fade_out)
                    .hide(activeFragment)
                    .show(target)
                    .commit()
                activeFragment = target
            }
            true
        }

        // Handle double back to exit
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (backPressedTime + 2000 > System.currentTimeMillis()) {
                    finishAffinity()
                } else {
                    Toast.makeText(this@CompanyDashboardActivity, "Press back again to exit", Toast.LENGTH_SHORT).show()
                }
                backPressedTime = System.currentTimeMillis()
            }
        })
    }
}