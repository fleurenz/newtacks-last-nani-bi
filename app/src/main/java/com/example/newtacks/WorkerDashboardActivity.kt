package com.example.newtacks

import android.app.Dialog
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.ImageView
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
import com.example.newtacks.utils.ChatbotUtils
import com.example.newtacks.worker.*
import com.google.android.gms.location.*
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration

class WorkerDashboardActivity : AppCompatActivity() {

    companion object {
        const val OPEN_FRAGMENT = "OPEN_FRAGMENT"
        private var hasShownResumeReminderThisSession = false
    }

    private var backPressedTime: Long = 0

    // ✅ Fragments (Lazy or restored)
    private var fragmentFeed: WorkerFeedFragment? = null
    private var fragmentJob: WorkerJobFragment? = null
    private var fragmentHiring: WorkerHiringFragment? = null
    private var fragmentHistory: WorkerHistoryFragment? = null
    private var fragmentAccount: WorkerAccountFragment? = null

    private var activeFragment: Fragment? = null

    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private var locationCallback: LocationCallback? = null
    private var jobHandshakeListener: ListenerRegistration? = null

    fun switchTab(tabId: Int) {
        val bottomNav = findViewById<BottomNavigationView>(R.id.workerBottomNav)
        bottomNav.selectedItemId = tabId
    }

    fun focusMapOnLocation(lat: Double, lng: Double) {
        switchTab(R.id.nav_feed)
        fragmentFeed?.zoomToLocation(lat, lng)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_worker_dashboard)

        val bottomNav = findViewById<BottomNavigationView>(R.id.workerBottomNav)

        val startFragment = intent.getStringExtra(OPEN_FRAGMENT)
        val startId = intent.getStringExtra("OPEN_ID")
        
        // Handle fragment restoration during recreation
        if (savedInstanceState == null) {
            // Initial create
            fragmentAccount = WorkerAccountFragment()
            fragmentHistory = WorkerHistoryFragment()
            fragmentHiring  = WorkerHiringFragment()
            fragmentJob     = WorkerJobFragment()
            fragmentFeed    = WorkerFeedFragment()

            activeFragment = when (startFragment) {
                "JOB"     -> fragmentJob
                "HIRING"  -> fragmentHiring
                "HISTORY" -> fragmentHistory
                "ACCOUNT" -> fragmentAccount
                else      -> fragmentFeed
            }

            supportFragmentManager.beginTransaction().apply {
                add(R.id.workerFragmentContainer, fragmentAccount!!, "account").hide(fragmentAccount!!)
                add(R.id.workerFragmentContainer, fragmentHistory!!, "history").hide(fragmentHistory!!)
                add(R.id.workerFragmentContainer, fragmentHiring!!, "hiring").hide(fragmentHiring!!)
                add(R.id.workerFragmentContainer, fragmentJob!!, "job").hide(fragmentJob!!)
                add(R.id.workerFragmentContainer, fragmentFeed!!, "feed").hide(fragmentFeed!!)
                show(activeFragment!!)
            }.commit()

            // Initial notification button state
            findViewById<View>(R.id.btnFloatingNotifications).visibility = 
                if (activeFragment == fragmentAccount) View.GONE else View.VISIBLE

            // Process specific ID navigation if needed
            if (!startId.isNullOrEmpty()) {
                if (startFragment == "HISTORY") {
                    com.example.newtacks.receipt.ReceiptDetailActivity.open(this, startId)
                }
            }
        } else {
            // Restore references from FragmentManager
            fragmentAccount = supportFragmentManager.findFragmentByTag("account") as? WorkerAccountFragment
            fragmentHistory = supportFragmentManager.findFragmentByTag("history") as? WorkerHistoryFragment
            fragmentHiring  = supportFragmentManager.findFragmentByTag("hiring") as? WorkerHiringFragment
            fragmentJob     = supportFragmentManager.findFragmentByTag("job") as? WorkerJobFragment
            fragmentFeed    = supportFragmentManager.findFragmentByTag("feed") as? WorkerFeedFragment

            // Find which one was visible
            val fragments = listOf(fragmentAccount, fragmentHistory, fragmentHiring, fragmentJob, fragmentFeed)
            activeFragment = fragments.find { it?.isVisible == true } ?: fragmentFeed
        }

        bottomNav.selectedItemId = when (startFragment) {
            "JOB"     -> R.id.nav_job
            "HIRING"  -> R.id.nav_hiring
            "HISTORY" -> R.id.nav_history
            "ACCOUNT" -> R.id.nav_account
            else      -> R.id.nav_feed
        }

        val fabChat = findViewById<FloatingActionButton>(R.id.fabChat)
        ChatbotUtils.setupChatbot(this, fabChat, "worker")

        val btnNotif = findViewById<View>(R.id.btnFloatingNotifications)
        val tvNotifBadge = findViewById<TextView>(R.id.tvFloatingNotificationBadge)
        btnNotif.setOnClickListener {
            com.example.newtacks.utils.NotificationHelper.showNotificationDialog(this)
        }
        com.example.newtacks.utils.NotificationHelper.setupNotificationBadge(this, tvNotifBadge)
        
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        listenForActiveHandshake()
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
        val serviceIntent = Intent(this, com.example.newtacks.utils.NotificationService::class.java)
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            startForegroundService(serviceIntent)
        } else {
            startService(serviceIntent)
        }

        bottomNav.setOnItemSelectedListener { item ->
            val target = when (item.itemId) {
                R.id.nav_feed    -> fragmentFeed
                R.id.nav_job     -> fragmentJob
                R.id.nav_hiring  -> fragmentHiring
                R.id.nav_history -> fragmentHistory
                R.id.nav_account -> fragmentAccount
                else             -> return@setOnItemSelectedListener false
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
            }
            true
        }

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

    override fun onResume() {
        super.onResume()
        checkFirstTimeWorker()
    }

    private fun listenForActiveHandshake() {
        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return
        val db = FirebaseFirestore.getInstance()

        jobHandshakeListener = db.collection("jobs")
            .whereEqualTo("workerId", uid)
            .addSnapshotListener { snapshots, _ ->
                if (snapshots == null) return@addSnapshotListener
                
                val handshakeStatuses = listOf("IN_PROGRESS", "HEADING_TO_CLIENT", "ARRIVED")
                val hasActiveHandshake = snapshots.documents.any { 
                    it.getString("status") in handshakeStatuses 
                }

                if (hasActiveHandshake) {
                    startLocationUpdates()
                } else {
                    stopLocationUpdates()
                }
            }
    }

    private fun startLocationUpdates() {
        if (locationCallback != null) return

        // Silent permission check
        val hasPermission = androidx.core.content.ContextCompat.checkSelfPermission(this, android.Manifest.permission.ACCESS_FINE_LOCATION) == android.content.pm.PackageManager.PERMISSION_GRANTED
        if (!hasPermission) return

        val request = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 10000)
            .setMinUpdateIntervalMillis(5000)
            .build()

        locationCallback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                val loc = result.lastLocation ?: return
                updateWorkerLocationInFirestore(loc.latitude, loc.longitude)
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

    private fun updateWorkerLocationInFirestore(lat: Double, lng: Double) {
        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return
        FirebaseFirestore.getInstance().collection("users").document(uid)
            .update(mapOf(
                "latitude" to lat,
                "longitude" to lng,
                "lastActive" to System.currentTimeMillis()
            ))
    }

    private fun checkFirstTimeWorker() {
        if (hasShownResumeReminderThisSession) return
        
        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return
        FirebaseFirestore.getInstance().collection("users").document(uid).get().addOnSuccessListener { doc ->
            val resumeUrl = doc.getString("resumeUrl")
            if (resumeUrl.isNullOrEmpty()) {
                hasShownResumeReminderThisSession = true
                showWelcomeResumeDialog()
            }
        }
    }

    private fun showWelcomeResumeDialog() {
        val dialog = Dialog(this)
        dialog.setContentView(R.layout.dialog_role_select)
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
        dialog.window?.setLayout(
            (resources.displayMetrics.widthPixels * 0.88).toInt(),
            android.view.ViewGroup.LayoutParams.WRAP_CONTENT
        )

        val icon = dialog.findViewById<ImageView>(R.id.dialogIcon)
        val title = dialog.findViewById<TextView>(R.id.dialogTitle)
        val msg = dialog.findViewById<TextView>(R.id.dialogMessage)
        val btnPos = dialog.findViewById<com.google.android.material.button.MaterialButton>(R.id.dialogBtnPositive)
        val btnNeg = dialog.findViewById<com.google.android.material.button.MaterialButton>(R.id.dialogBtnNegative)

        icon.setImageResource(R.drawable.ic_inbox)
        icon.imageTintList = android.content.res.ColorStateList.valueOf(android.graphics.Color.parseColor("#004A99"))
        
        title.text = "Welcome to STRACT!"
        msg.text = "To start taking jobs and applying for positions, you must first upload your resume in the Account tab."
        
        btnPos.text = "Go to Account"
        btnPos.setOnClickListener {
            switchTab(R.id.nav_account)
            dialog.dismiss()
        }
        
        btnNeg.text = "Maybe Later"
        btnNeg.setOnClickListener { dialog.dismiss() }

        dialog.show()
    }

    private var unreadMessagesListener: ListenerRegistration? = null
    private fun listenForUnreadMessages() {
        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return
        val db = FirebaseFirestore.getInstance()
        val bottomNav = findViewById<BottomNavigationView>(R.id.workerBottomNav)

        unreadMessagesListener = db.collection("chats")
            .whereEqualTo("receiverId", uid)
            .whereEqualTo("read", false)
            .addSnapshotListener { snapshots, _ ->
                val unreadCount = snapshots?.size() ?: 0
                val badge = bottomNav.getOrCreateBadge(R.id.nav_job)
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
}
