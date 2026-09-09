package com.example.newtacks

import android.os.Bundle
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.core.view.updatePadding
import androidx.fragment.app.Fragment
import com.example.newtacks.company.*
import com.example.newtacks.utils.ChatbotUtils
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.android.material.floatingactionbutton.FloatingActionButton

class CompanyDashboardActivity : AppCompatActivity() {

    private var backPressedTime: Long = 0

    private var fragmentHome: CompanyHomeFragment? = null
    private var fragmentHiring: CompanyHiringFragment? = null
    private var fragmentApplicants: CompanyApplicantsFragment? = null
    private var fragmentAccount: CompanyAccountFragment? = null

    private var activeFragment: Fragment? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_company_dashboard)

        val bottomNav = findViewById<BottomNavigationView>(R.id.companyBottomNav)

        val fragmentToOpen = intent.getStringExtra("OPEN_FRAGMENT")

        // Use savedInstanceState check to prevent duplicate fragments on Activity recreation
        if (savedInstanceState == null) {
            fragmentAccount = CompanyAccountFragment()
            fragmentApplicants = CompanyApplicantsFragment()
            fragmentHiring  = CompanyHiringFragment()
            fragmentHome    = CompanyHomeFragment()
            activeFragment  = fragmentHome

            activeFragment = when (fragmentToOpen) {
                "ACCOUNT"    -> fragmentAccount
                "APPLICANTS" -> fragmentApplicants
                "POSTS"      -> fragmentHiring
                else         -> fragmentHome
            }

            supportFragmentManager.beginTransaction().apply {
                add(R.id.companyFragmentContainer, fragmentAccount!!, "account").hide(fragmentAccount!!)
                add(R.id.companyFragmentContainer, fragmentApplicants!!, "applicants").hide(fragmentApplicants!!)
                add(R.id.companyFragmentContainer, fragmentHiring!!, "hiring").hide(fragmentHiring!!)
                add(R.id.companyFragmentContainer, fragmentHome!!, "home")
                show(activeFragment!!)
            }.commit()
        } else {
            // Restore references
            fragmentAccount = supportFragmentManager.findFragmentByTag("account") as? CompanyAccountFragment
            fragmentApplicants = supportFragmentManager.findFragmentByTag("applicants") as? CompanyApplicantsFragment
            fragmentHiring  = supportFragmentManager.findFragmentByTag("hiring") as? CompanyHiringFragment
            fragmentHome    = supportFragmentManager.findFragmentByTag("home") as? CompanyHomeFragment

            val fragments = listOf(fragmentAccount, fragmentApplicants, fragmentHiring, fragmentHome)
            activeFragment = fragments.find { it?.isVisible == true } ?: fragmentHome
        }

        val fabChat = findViewById<FloatingActionButton>(R.id.fabChat)
        ChatbotUtils.setupChatbot(this, fabChat, "company")
        listenForUnreadMessages()

        ViewCompat.setOnApplyWindowInsetsListener(bottomNav) { v, insets ->
            val navBars = insets.getInsets(WindowInsetsCompat.Type.navigationBars())
            v.updatePadding(bottom = navBars.bottom)
            insets
        }

        // Start background service for vital notifications
        val serviceIntent = android.content.Intent(this, com.example.newtacks.utils.NotificationService::class.java)
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            startForegroundService(serviceIntent)
        } else {
            startService(serviceIntent)
        }

        bottomNav.setOnItemSelectedListener { item ->
            val target = when (item.itemId) {
                R.id.nav_company_home    -> fragmentHome
                R.id.nav_company_hiring  -> fragmentHiring
                R.id.nav_company_history -> fragmentApplicants
                R.id.nav_company_account -> fragmentAccount
                else             -> return@setOnItemSelectedListener false
            }

            if (target != null && target !== activeFragment) {
                supportFragmentManager.beginTransaction()
                    .setCustomAnimations(R.anim.smooth_fade_in, R.anim.smooth_fade_out)
                    .hide(activeFragment!!)
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

    private var unreadMessagesListener: com.google.firebase.firestore.ListenerRegistration? = null
    private fun listenForUnreadMessages() {
        val uid = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.uid ?: return
        val db = com.google.firebase.firestore.FirebaseFirestore.getInstance()
        val bottomNav = findViewById<BottomNavigationView>(R.id.companyBottomNav)

        unreadMessagesListener = db.collection("chats")
            .whereEqualTo("receiverId", uid)
            .whereEqualTo("read", false)
            .addSnapshotListener { snapshots, _ ->
                val unreadCount = snapshots?.size() ?: 0
                val badge = bottomNav.getOrCreateBadge(R.id.nav_company_hiring)
                badge.isVisible = unreadCount > 0
                if (unreadCount > 0) badge.number = unreadCount
            }
    }

    fun switchToApplicants(tabName: String = "ALL") {
        val bottomNav = findViewById<BottomNavigationView>(R.id.companyBottomNav)
        bottomNav.selectedItemId = R.id.nav_company_history // This is the ID for applicants
        fragmentApplicants?.selectTab(tabName)
    }

    fun switchToPosts() {
        val bottomNav = findViewById<BottomNavigationView>(R.id.companyBottomNav)
        bottomNav.selectedItemId = R.id.nav_company_hiring
    }

    override fun onDestroy() {
        super.onDestroy()
        unreadMessagesListener?.remove()
    }
}
