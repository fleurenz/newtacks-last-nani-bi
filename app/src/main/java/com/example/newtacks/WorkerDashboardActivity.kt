package com.example.newtacks

import android.os.Bundle
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
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
        WindowCompat.setDecorFitsSystemWindows(window, false)
        setContentView(R.layout.activity_worker_dashboard)

        val bottomNav = findViewById<BottomNavigationView>(R.id.workerBottomNav)

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

        val startFragment = intent.getStringExtra(OPEN_FRAGMENT)
        
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
        
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        listenForActiveHandshake()

        bottomNav.setOnItemSelectedListener { item ->
            val target = when (item.itemId) {
                R.id.nav_feed    -> fragmentFeed
                R.id.nav_job     -> fragmentJob
                R.id.nav_hiring  -> fragmentHiring
                R.id.nav_history -> fragmentHistory
                R.id.nav_account -> fragmentAccount
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

    override fun onDestroy() {
        super.onDestroy()
        stopLocationUpdates()
        jobHandshakeListener?.remove()
    }
}
