package com.example.newtacks

import android.os.Bundle
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
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
    
    private var activeFragment: Fragment? = null

    fun switchTab(tabId: Int) {
        val bottomNav = findViewById<BottomNavigationView>(R.id.clientBottomNav)
        bottomNav.selectedItemId = tabId
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // ✅ Edge-to-edge
        WindowCompat.setDecorFitsSystemWindows(window, false)

        setContentView(R.layout.activity_client_dashboard)

        val bottomNav = findViewById<BottomNavigationView>(R.id.clientBottomNav)

        // ✅ Apply insets
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.clientRootLayout)) { _, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            bottomNav.setPadding(
                bottomNav.paddingLeft,
                bottomNav.paddingTop,
                bottomNav.paddingRight,
                systemBars.bottom
            )
            insets
        }

        val fragmentToOpen = intent.getStringExtra(OPEN_FRAGMENT)
        
        if (savedInstanceState == null) {
            // Initial load
            if (fragmentToOpen == "REQUESTS") {
                bottomNav.selectedItemId = R.id.nav_requests
                activeFragment = ClientRequestsFragment()
            } else {
                // Determine initial fragment based on active job
                val uid = FirebaseAuth.getInstance().currentUser?.uid
                if (uid != null) {
                    // Start with home, then switch if needed
                    activeFragment = ClientHomeFragment()
                    
                    FirebaseFirestore.getInstance().collection("jobs")
                        .whereEqualTo("clientId", uid)
                        .get(com.google.firebase.firestore.Source.SERVER)
                        .addOnSuccessListener { snapshots ->
                            val activeStatuses = listOf("AVAILABLE", "IN_PROGRESS", "HEADING_TO_CLIENT", "ARRIVED", "PENDING_VERIFICATION")
                            val hasActiveJob = snapshots.documents.any { it.getString("status") in activeStatuses }
                            
                            if (hasActiveJob && bottomNav.selectedItemId != R.id.nav_requests) {
                                switchTab(R.id.nav_requests)
                            }
                        }
                } else {
                    activeFragment = ClientHomeFragment()
                }
            }
            
            // Only add if we have an active fragment
            activeFragment?.let { frag ->
                supportFragmentManager.beginTransaction()
                    .replace(R.id.clientFragmentContainer, frag, "active_client_frag")
                    .commit()
            }
        } else {
            // Restore active fragment reference
            activeFragment = supportFragmentManager.findFragmentByTag("active_client_frag")
        }

        val fabChat = findViewById<FloatingActionButton>(R.id.fabChat)
        ChatbotUtils.setupChatbot(this, fabChat, "client")
        
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        listenForJobHandshake()

        bottomNav.setOnItemSelectedListener {
            val target = when (it.itemId) {
                R.id.nav_home     -> ClientHomeFragment()
                R.id.nav_requests -> ClientRequestsFragment()
                R.id.nav_history  -> ClientHistoryFragment()
                R.id.nav_account  -> ClientAccountFragment()
                else -> null
            }
            
            if (target != null) {
                activeFragment = target
                replaceFragment(target)
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

    private fun replaceFragment(fragment: Fragment) {
        supportFragmentManager
            .beginTransaction()
            .setCustomAnimations(
                R.anim.fade_in,
                R.anim.fade_out,
                R.anim.fade_in,
                R.anim.fade_out
            )
            .replace(R.id.clientFragmentContainer, fragment, "active_client_frag")
            .commit()
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
                
                val handshakeStatuses = listOf("IN_PROGRESS", "HEADING_TO_CLIENT", "ARRIVED")
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

        // Silent permission check
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

    override fun onDestroy() {
        super.onDestroy()
        stopLocationUpdates()
        jobHandshakeListener?.remove()
    }

    companion object {
        const val OPEN_FRAGMENT = "OPEN_FRAGMENT"
    }
}
