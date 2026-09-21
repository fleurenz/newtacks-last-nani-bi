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
import com.example.newtacks.client.*
import com.example.newtacks.utils.ChatbotUtils
import com.google.android.gms.location.*
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration

class ClientDashboardActivity : AppCompatActivity() {

    private var backPressedTime: Long = 0
    private var jobHandshakeListener: ListenerRegistration? = null
    
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private var locationCallback: LocationCallback? = null
    
    private var fragmentHome: ClientHomeFragment? = null
    private var fragmentRequests: ClientRequestsFragment? = null
    private var fragmentHistory: ClientHistoryFragment? = null
    private var fragmentAccount: ClientAccountFragment? = null
    
    private var activeFragment: Fragment? = null

    fun switchTab(tabId: Int) {
        val bottomNav = findViewById<BottomNavigationView>(R.id.clientBottomNav)
        bottomNav.selectedItemId = tabId
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_client_dashboard)

        val bottomNav = findViewById<BottomNavigationView>(R.id.clientBottomNav)

        ViewCompat.setOnApplyWindowInsetsListener(bottomNav) { v, insets ->
            val navBars = insets.getInsets(WindowInsetsCompat.Type.navigationBars())
            val statusBars = insets.getInsets(WindowInsetsCompat.Type.statusBars())
            
            v.updatePadding(bottom = navBars.bottom)
            
            // DYNAMIC POSITIONING: Align with status bar + small padding
            val btnNotif = findViewById<View>(R.id.btnFloatingNotifications)
            btnNotif.updateLayoutParams<android.view.ViewGroup.MarginLayoutParams> {
                // Perfect spot: Just below the status bar with 12dp of extra breathing room
                topMargin = statusBars.top + (12 * resources.displayMetrics.density).toInt()
            }

            insets
        }

        val fragmentToOpen = intent.getStringExtra(OPEN_FRAGMENT)
        val startId = intent.getStringExtra("OPEN_ID")
        
        if (savedInstanceState == null) {
            fragmentHome = ClientHomeFragment()
            fragmentRequests = ClientRequestsFragment()
            fragmentHistory = ClientHistoryFragment()
            fragmentAccount = ClientAccountFragment()

            activeFragment = when (fragmentToOpen) {
                "REQUESTS" -> fragmentRequests
                "HISTORY"  -> fragmentHistory
                "ACCOUNT"  -> fragmentAccount
                else       -> fragmentHome
            }

            supportFragmentManager.beginTransaction().apply {
                add(R.id.clientFragmentContainer, fragmentAccount!!, "account").hide(fragmentAccount!!)
                add(R.id.clientFragmentContainer, fragmentHistory!!, "history").hide(fragmentHistory!!)
                add(R.id.clientFragmentContainer, fragmentRequests!!, "requests").hide(fragmentRequests!!)
                add(R.id.clientFragmentContainer, fragmentHome!!, "home").hide(fragmentHome!!)
                show(activeFragment!!)
            }.commit()

            // Initial notification button state
            findViewById<View>(R.id.btnFloatingNotifications).visibility = 
                if (activeFragment == fragmentAccount) View.GONE else View.VISIBLE

            // Process specific ID navigation if needed
            if (!startId.isNullOrEmpty()) {
                if (fragmentToOpen == "HISTORY") {
                    com.example.newtacks.receipt.ReceiptDetailActivity.open(this, startId)
                }
            }

            // Pre-check for active jobs to auto-switch if needed
            if (fragmentToOpen == null) {
                checkActiveJobs(bottomNav)
            }
        } else {
            fragmentHome = supportFragmentManager.findFragmentByTag("home") as? ClientHomeFragment
            fragmentRequests = supportFragmentManager.findFragmentByTag("requests") as? ClientRequestsFragment
            fragmentHistory = supportFragmentManager.findFragmentByTag("history") as? ClientHistoryFragment
            fragmentAccount = supportFragmentManager.findFragmentByTag("account") as? ClientAccountFragment

            val fragments = listOf(fragmentHome, fragmentRequests, fragmentHistory, fragmentAccount)
            activeFragment = fragments.find { it?.isVisible == true } ?: fragmentHome
        }

        val fabChat = findViewById<FloatingActionButton>(R.id.fabChat)
        ChatbotUtils.setupChatbot(this, fabChat, "client")

        val btnNotif = findViewById<View>(R.id.btnFloatingNotifications)
        val tvNotifBadge = findViewById<TextView>(R.id.tvFloatingNotificationBadge)
        btnNotif.setOnClickListener {
            com.example.newtacks.utils.NotificationHelper.showNotificationDialog(this)
        }
        com.example.newtacks.utils.NotificationHelper.setupNotificationBadge(this, tvNotifBadge)
        
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        listenForJobHandshake()
        listenForUnreadMessages()

        ViewCompat.setOnApplyWindowInsetsListener(bottomNav) { v, insets ->
            val navBars = insets.getInsets(WindowInsetsCompat.Type.navigationBars())
            val statusBars = insets.getInsets(WindowInsetsCompat.Type.statusBars())
            
            v.updatePadding(bottom = navBars.bottom)
            
            // DYNAMIC POSITIONING: Align with status bar + small padding
            val btnNotif = findViewById<View>(R.id.btnFloatingNotifications)
            btnNotif.updateLayoutParams<android.view.ViewGroup.MarginLayoutParams> {
                // Perfect spot: Just below the status bar with 12dp of extra breathing room
                topMargin = statusBars.top + (12 * resources.displayMetrics.density).toInt()
            }

            insets
        }

        // Start background service for vital notifications
        val serviceIntent = Intent(this, com.example.newtacks.utils.NotificationService::class.java)
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            startForegroundService(serviceIntent)
        } else {
            startService(serviceIntent)
        }

        bottomNav.setOnItemSelectedListener { item ->
            val target = when (item.itemId) {
                R.id.nav_home     -> fragmentHome
                R.id.nav_requests -> fragmentRequests
                R.id.nav_history  -> fragmentHistory
                R.id.nav_account  -> fragmentAccount
                else -> null
            }
            
            // Hide notification button on Account screen with animation
            val btnNotif = findViewById<View>(R.id.btnFloatingNotifications)
            if (item.itemId == R.id.nav_account) {
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
                true
            } else false
        }

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

    private fun checkActiveJobs(bottomNav: BottomNavigationView) {
        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return
        FirebaseFirestore.getInstance().collection("jobs")
            .whereEqualTo("clientId", uid)
            .get(com.google.firebase.firestore.Source.SERVER)
            .addOnSuccessListener { snapshots ->
                val activeStatuses = listOf("AVAILABLE", "IN_PROGRESS", "HEADING_TO_CLIENT", "ARRIVED", "PENDING_VERIFICATION", "REJECTED_BY_CLIENT")
                val hasActiveJob = snapshots.documents.any { it.getString("status") in activeStatuses }
                
                if (hasActiveJob && bottomNav.selectedItemId != R.id.nav_requests) {
                    switchTab(R.id.nav_requests)
                }
            }
    }

    private fun listenForJobHandshake() {
        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return
        
        jobHandshakeListener = FirebaseFirestore.getInstance().collection("jobs")
            .whereEqualTo("clientId", uid)
            .addSnapshotListener { snapshots, _ ->
                if (snapshots == null || snapshots.isEmpty) {
                    stopLocationUpdates()
                    return@addSnapshotListener
                }
                
                val handshakeStatuses = listOf("IN_PROGRESS", "HEADING_TO_CLIENT", "ARRIVED", "REJECTED_BY_CLIENT")
                val activeHandshake = snapshots.documents.find { 
                    it.getString("status") in handshakeStatuses 
                }

                if (activeHandshake != null) {
                    val bottomNav = findViewById<BottomNavigationView>(R.id.clientBottomNav)
                    if (bottomNav.selectedItemId != R.id.nav_requests) {
                        switchTab(R.id.nav_requests)
                        Toast.makeText(this, "A worker has accepted your request!", Toast.LENGTH_SHORT).show()
                    }
                    startLocationUpdates()
                } else {
                    stopLocationUpdates()
                }
            }
    }

    private fun startLocationUpdates() {
        if (locationCallback != null) return

        val hasPermission = androidx.core.content.ContextCompat.checkSelfPermission(this, android.Manifest.permission.ACCESS_FINE_LOCATION) == android.content.pm.PackageManager.PERMISSION_GRANTED
        if (!hasPermission) return

        val request = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 10000)
            .setMinUpdateIntervalMillis(5000)
            .build()

        locationCallback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                val loc = result.lastLocation ?: return
                updateClientLocationInFirestore(loc.latitude, loc.longitude)
            }
        }

        if (androidx.core.content.ContextCompat.checkSelfPermission(this, android.Manifest.permission.ACCESS_FINE_LOCATION) 
            == android.content.pm.PackageManager.PERMISSION_GRANTED) {
            fusedLocationClient.requestLocationUpdates(request, locationCallback!!, android.os.Looper.getMainLooper())
        }
    }

    private fun stopLocationUpdates() {
        locationCallback?.let { fusedLocationClient.removeLocationUpdates(it) }
        locationCallback = null
    }

    private fun updateClientLocationInFirestore(lat: Double, lng: Double) {
        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return
        FirebaseFirestore.getInstance().collection("users").document(uid)
            .update(mapOf(
                "latitude" to lat,
                "longitude" to lng,
                "lastActive" to System.currentTimeMillis()
            ))
    }

    private var unreadMessagesListener: ListenerRegistration? = null
    private fun listenForUnreadMessages() {
        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return
        val db = FirebaseFirestore.getInstance()
        val bottomNav = findViewById<BottomNavigationView>(R.id.clientBottomNav)

        unreadMessagesListener = db.collection("chats")
            .whereEqualTo("receiverId", uid)
            .whereEqualTo("read", false)
            .addSnapshotListener { snapshots, _ ->
                val unreadCount = snapshots?.size() ?: 0
                val badge = bottomNav.getOrCreateBadge(R.id.nav_requests)
                badge.isVisible = unreadCount > 0
                if (unreadCount > 0) badge.number = unreadCount
            }
    }

    override fun onDestroy() {
        super.onDestroy()
        unreadMessagesListener?.remove()
        stopLocationUpdates()
        jobHandshakeListener?.remove()
    }

    companion object {
        const val OPEN_FRAGMENT = "OPEN_FRAGMENT"
    }
}
