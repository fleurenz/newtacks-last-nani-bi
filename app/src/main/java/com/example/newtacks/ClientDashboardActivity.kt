package com.example.newtacks

import android.os.Bundle
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.fragment.app.Fragment
import com.example.newtacks.client.ClientAccountFragment
import com.example.newtacks.client.ClientHistoryFragment
import com.example.newtacks.client.ClientHomeFragment
import com.example.newtacks.client.ClientRequestsFragment
import com.example.newtacks.chatbot.presentation.ui.ChatActivity
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.android.material.floatingactionbutton.FloatingActionButton

class ClientDashboardActivity : AppCompatActivity() {

    private var backPressedTime: Long = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // ✅ Edge-to-edge
        WindowCompat.setDecorFitsSystemWindows(window, false)

        setContentView(R.layout.activity_client_dashboard)

        val bottomNav = findViewById<BottomNavigationView>(R.id.clientBottomNav)

        // ✅ Apply insets — status bar on top, nav bar on bottom nav
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.clientRootLayout)) { _, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())

            // Push bottom nav up above gesture/nav bar
            bottomNav.setPadding(
                bottomNav.paddingLeft,
                bottomNav.paddingTop,
                bottomNav.paddingRight,
                systemBars.bottom
            )

            insets
        }

        val fragmentToOpen = intent.getStringExtra(OPEN_FRAGMENT)
        if (fragmentToOpen == "REQUESTS") {
            bottomNav.selectedItemId = R.id.nav_requests
            replaceFragment(ClientRequestsFragment())
        } else {
            bottomNav.selectedItemId = R.id.nav_home
            replaceFragment(ClientHomeFragment())
        }

        findViewById<FloatingActionButton>(R.id.fabChat).setOnClickListener {
            val intent = android.content.Intent(this, ChatActivity::class.java)
            intent.putExtra("USER_ROLE", "client")
            startActivity(intent)
        }

        bottomNav.setOnItemSelectedListener {
            when (it.itemId) {
                R.id.nav_home -> {
                    replaceFragment(ClientHomeFragment())
                    true
                }
                R.id.nav_requests -> {
                    replaceFragment(ClientRequestsFragment())
                    true
                }
                R.id.nav_history -> {
                    replaceFragment(ClientHistoryFragment())
                    true
                }
                R.id.nav_account -> {
                    replaceFragment(ClientAccountFragment())
                    true
                }
                else -> false
            }
        }

        // Handle double back to exit
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (backPressedTime + 2000 > System.currentTimeMillis()) {
                    finishAffinity()
                } else {
                    Toast.makeText(this@ClientDashboardActivity, "Press back again to exit", Toast.LENGTH_SHORT).show()
                }
                backPressedTime = System.currentTimeMillis()
            }
        })
    }

    private fun replaceFragment(fragment: Fragment) {
        supportFragmentManager
            .beginTransaction()
            .setCustomAnimations(
                R.anim.fade_in,
                R.anim.fade_out,
                R.anim.fade_in,
                R.anim.fade_out
            )
            .replace(R.id.clientFragmentContainer, fragment)
            .commit()
    }

    companion object {
        const val OPEN_FRAGMENT = "OPEN_FRAGMENT"
    }
}
