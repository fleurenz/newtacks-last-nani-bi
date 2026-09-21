package com.example.newtacks

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.TextView
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.core.view.updateLayoutParams
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
        val startId = intent.getStringExtra("OPEN_ID")

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

            // Initial notification button state
            findViewById<View>(R.id.btnFloatingNotifications).visibility = 
                if (activeFragment == fragmentAccount) View.GONE else View.VISIBLE

            // Process specific ID navigation if needed
            if (!startId.isNullOrEmpty()) {
                if (fragmentToOpen == "HIRING_DETAILS") {
                    val db = com.google.firebase.firestore.FirebaseFirestore.getInstance()
                    db.collection("hiring").document(startId).get().addOnSuccessListener { doc ->
                        val post = doc.toObject(com.example.newtacks.models.HiringPost::class.java)?.copy(hiringId = doc.id)
                        if (post != null) {
                            val intent = Intent(this, com.example.newtacks.company.HiringDetailsActivity::class.java)
                            intent.putExtra("HIRING_POST_JSON", com.google.gson.Gson().toJson(post))
                            startActivity(intent)
                        }
                    }
                }
            }
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

        val btnNotif = findViewById<View>(R.id.btnFloatingNotifications)
        val tvNotifBadge = findViewById<TextView>(R.id.tvFloatingNotificationBadge)
        btnNotif.setOnClickListener {
            com.example.newtacks.utils.NotificationHelper.showNotificationDialog(this)
        }
        com.example.newtacks.utils.NotificationHelper.setupNotificationBadge(this, tvNotifBadge)
        
        listenForUnreadMessages()

        ViewCompat.setOnApplyWindowInsetsListener(bottomNav) { v, insets ->
            val navBars = insets.getInsets(WindowInsetsCompat.Type.navigationBars())
            val statusBars = insets.getInsets(WindowInsetsCompat.Type.statusBars())
            
            v.updatePadding(bottom = navBars.bottom)
            
            // DYNAMIC POSITIONING: Align with status bar + small padding
            val btnNotif = findViewById<View>(R.id.btnFloatingNotifications)
            btnNotif.updateLayoutParams<android.view.ViewGroup.MarginLayoutParams> {
                topMargin = statusBars.top + (12 * resources.displayMetrics.density).toInt()
            }

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

            // Hide notification button on Account screen with animation
            val btnNotif = findViewById<View>(R.id.btnFloatingNotifications)
            if (item.itemId == R.id.nav_company_account) {
                if (btnNotif.visibility == View.VISIBLE) {
                    btnNotif.animate().alpha(0f).scaleX(0.8f).scaleY(0.8f).setDuration(200).withEndAction {
                        btnNotif.visibility = View.GONE
                    }.start()
                }
            } else {
                if (btnNotif.visibility == View.GONE) {
                    btnNotif.visibility = View.VISIBLE
                    btnNotif.alpha = 0f
                    btnNotif.scaleX = 0.8f
                    btnNotif.scaleY = 0.8f
                    btnNotif.animate().alpha(1f).scaleX(1f).scaleY(1f).setDuration(250).start()
                }
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

    fun switchToApplicants(tabName: String = "ALL", jobFilterId: String? = null) {
        val bottomNav = findViewById<BottomNavigationView>(R.id.companyBottomNav)
        bottomNav.selectedItemId = R.id.nav_company_history // This is the ID for applicants
        fragmentApplicants?.selectTab(tabName)
        if (jobFilterId != null) {
            fragmentApplicants?.setJobFilter(jobFilterId)
        }
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
