package com.example.newtacks

import android.os.Bundle
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.fragment.app.Fragment
import com.example.newtacks.chatbot.presentation.ui.ChatActivity
import com.example.newtacks.worker.*
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.android.material.floatingactionbutton.FloatingActionButton

class WorkerDashboardActivity : AppCompatActivity() {

    companion object {
        const val OPEN_FRAGMENT = "OPEN_FRAGMENT"
    }

    private var backPressedTime: Long = 0

    // ✅ Create fragment instances once — never recreated on tab switch
    private val fragmentFeed    = WorkerFeedFragment()
    private val fragmentJob     = WorkerJobFragment()
    private val fragmentHistory = WorkerHistoryFragment()
    private val fragmentAccount = WorkerAccountFragment()

    private var activeFragment: Fragment = fragmentFeed

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        setContentView(R.layout.activity_worker_dashboard)

        val bottomNav = findViewById<BottomNavigationView>(R.id.workerBottomNav)

        // ✅ Apply insets
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.workerRootLayout)) { _, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            bottomNav.setPadding(
                bottomNav.paddingLeft,
                bottomNav.paddingTop,
                bottomNav.paddingRight,
                systemBars.bottom
            )
            insets
        }

        // ✅ Determine which fragment to start on
        val startFragment = intent.getStringExtra(OPEN_FRAGMENT)
        activeFragment = when (startFragment) {
            "JOB"     -> fragmentJob
            "HISTORY" -> fragmentHistory
            "ACCOUNT" -> fragmentAccount
            else      -> fragmentFeed
        }

        // ✅ Add ALL fragments once, hide all except the active one
        supportFragmentManager.beginTransaction().apply {
            add(R.id.workerFragmentContainer, fragmentAccount, "account")
            hide(fragmentAccount)
            add(R.id.workerFragmentContainer, fragmentHistory, "history")
            hide(fragmentHistory)
            add(R.id.workerFragmentContainer, fragmentJob, "job")
            hide(fragmentJob)
            add(R.id.workerFragmentContainer, fragmentFeed, "feed")
            hide(fragmentFeed)
            show(activeFragment)
        }.commit()

        // ✅ Sync bottom nav selected item to match start fragment
        bottomNav.selectedItemId = when (startFragment) {
            "JOB"     -> R.id.nav_job
            "HISTORY" -> R.id.nav_history
            "ACCOUNT" -> R.id.nav_account
            else      -> R.id.nav_feed
        }

        findViewById<FloatingActionButton>(R.id.fabChat).setOnClickListener {
            val intent = android.content.Intent(this, ChatActivity::class.java)
            intent.putExtra("USER_ROLE", "worker")
            startActivity(intent)
        }

        bottomNav.setOnItemSelectedListener { item ->
            val target = when (item.itemId) {
                R.id.nav_feed    -> fragmentFeed
                R.id.nav_job     -> fragmentJob
                R.id.nav_history -> fragmentHistory
                R.id.nav_account -> fragmentAccount
                else             -> return@setOnItemSelectedListener false
            }

            // ✅ Only switch if tapping a DIFFERENT tab
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
                    Toast.makeText(this@WorkerDashboardActivity, "Press back again to exit", Toast.LENGTH_SHORT).show()
                }
                backPressedTime = System.currentTimeMillis()
            }
        })
    }
}
