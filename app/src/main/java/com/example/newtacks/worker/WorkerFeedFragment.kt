package com.example.newtacks.worker

import android.os.Bundle
import android.view.*
import android.widget.*
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updateLayoutParams
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import coil.load
import com.example.newtacks.R
import com.example.newtacks.models.Job
import com.example.newtacks.utils.ImageUtils
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.material.button.MaterialButton
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.android.material.switchmaterial.SwitchMaterial
import com.google.android.material.tabs.TabLayout
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import org.maplibre.android.annotations.MarkerOptions
import org.maplibre.android.annotations.PolylineOptions
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.location.LocationComponentActivationOptions
import org.maplibre.android.location.modes.CameraMode
import org.maplibre.android.location.modes.RenderMode
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapView

class WorkerFeedFragment : Fragment() {

    private lateinit var recyclerView: RecyclerView
    private lateinit var adapter: WorkerJobAdapter
    private lateinit var fabToggleList: FloatingActionButton
    private lateinit var fabMyLocation: FloatingActionButton
    private lateinit var cardListOverlay: View
    private lateinit var mapView: MapView
    private var mapLibreMap: MapLibreMap? = null

    private lateinit var fabZoomIn: FloatingActionButton
    private lateinit var fabZoomOut: FloatingActionButton

    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private val db = FirebaseFirestore.getInstance()
    private var availableJobsListener: ListenerRegistration? = null
    private var activeHandshakeListener: ListenerRegistration? = null
    private var hiringListener: ListenerRegistration? = null
    
    private val availableJobs = mutableListOf<Job>()
    private val myActiveHandshakeJobs = mutableListOf<Job>()
    private val hiringList = mutableListOf<com.example.newtacks.models.HiringPost>()

    private val handler = android.os.Handler(android.os.Looper.getMainLooper())
    private val refreshWaypointTask = object : Runnable {
        override fun run() {
            if (myActiveHandshakeJobs.any { it.status == "HEADING_TO_CLIENT" }) {
                updateMapMarkers()
            }
            handler.postDelayed(this, 5000)
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val view = inflater.inflate(R.layout.fragment_worker_feed, container, false)

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(requireActivity())

        recyclerView        = view.findViewById(R.id.workerFeedRecycler)
        fabToggleList       = view.findViewById(R.id.fabToggleList)
        fabMyLocation       = view.findViewById(R.id.fabMyLocation)
        cardListOverlay     = view.findViewById(R.id.cardListOverlay)
        mapView             = view.findViewById(R.id.mapView)

        fabZoomIn           = view.findViewById(R.id.fabZoomIn)
        fabZoomOut          = view.findViewById(R.id.fabZoomOut)

        mapView.onCreate(savedInstanceState)

        recyclerView.layoutManager = LinearLayoutManager(requireContext())
        adapter = WorkerJobAdapter(availableJobs) { job -> 
            // When a job is clicked in the list:
            // 1. Hide the list overlay
            cardListOverlay.visibility = View.GONE
            // 2. Animate camera to the job location
            mapLibreMap?.animateCamera(CameraUpdateFactory.newLatLngZoom(LatLng(job.latitude, job.longitude), 15.0))
        }
        recyclerView.adapter = adapter

        fabToggleList.setOnClickListener {
            cardListOverlay.visibility = if (cardListOverlay.visibility == View.VISIBLE) View.GONE else View.VISIBLE
        }

        fabMyLocation.setOnClickListener {
            mapLibreMap?.let { map ->
                if (map.locationComponent.isLocationComponentActivated) {
                    val lastLoc = map.locationComponent.lastKnownLocation
                    if (lastLoc != null) {
                        map.animateCamera(CameraUpdateFactory.newLatLngZoom(LatLng(lastLoc.latitude, lastLoc.longitude), 15.0))
                    } else {
                        Toast.makeText(requireContext(), "Getting location...", Toast.LENGTH_SHORT).show()
                    }
                } else {
                    enableUserLocation(map)
                }
            }
        }

        // --------------------------------------------------
        // ✅ ZOOM CONTROLS
        // --------------------------------------------------
        fabZoomIn.setOnClickListener {
            mapLibreMap?.animateCamera(CameraUpdateFactory.zoomIn())
        }
        fabZoomOut.setOnClickListener {
            mapLibreMap?.animateCamera(CameraUpdateFactory.zoomOut())
        }

        // --------------------------------------------------
        // ✅ WINDOW INSETS
        // --------------------------------------------------
        ViewCompat.setOnApplyWindowInsetsListener(view) { _, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            fabToggleList.updateLayoutParams<ViewGroup.MarginLayoutParams> {
                topMargin = systemBars.top + (resources.displayMetrics.density * 16).toInt()
            }
            insets
        }

        listenForJobs()
        initMap() // Always init map now
        return view
    }

    fun zoomToLocation(lat: Double, lng: Double) {
        mapLibreMap?.animateCamera(CameraUpdateFactory.newLatLngZoom(LatLng(lat, lng), 15.0))
    }

    private fun setupWorkerInfo() {
        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return
        db.collection("users").document(uid).get().addOnSuccessListener { 
            // Update UI or logic here if needed
        }
    }

    private fun enableUserLocation(map: MapLibreMap, moveCamera: Boolean = false) {
        if (ContextCompat.checkSelfPermission(requireContext(), android.Manifest.permission.ACCESS_FINE_LOCATION) 
            == android.content.pm.PackageManager.PERMISSION_GRANTED) {
            
            val locationComponent = map.locationComponent
            val activationOptions = LocationComponentActivationOptions.builder(requireContext(), map.style!!)
                .build()
            
            locationComponent.activateLocationComponent(activationOptions)
            locationComponent.isLocationComponentEnabled = true
            locationComponent.cameraMode = CameraMode.NONE
            locationComponent.renderMode = RenderMode.COMPASS
            
            if (moveCamera) {
                // Try to get last location to center the map immediately
                fusedLocationClient.lastLocation.addOnSuccessListener { location ->
                    if (location != null) {
                        map.moveCamera(CameraUpdateFactory.newLatLngZoom(LatLng(location.latitude, location.longitude), 15.0))
                    } else {
                        // Fallback to Davao if GPS is off/null
                        val davaoCityHall = LatLng(7.0648, 125.6079) 
                        map.moveCamera(CameraUpdateFactory.newLatLngZoom(davaoCityHall, 13.0))
                    }
                }
            }
        } else {
            // Request permission if not granted
            @Suppress("DEPRECATION")
            requestPermissions(arrayOf(android.Manifest.permission.ACCESS_FINE_LOCATION), 1002)
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        if (requestCode == 1002 && grantResults.isNotEmpty() && grantResults[0] == android.content.pm.PackageManager.PERMISSION_GRANTED) {
            mapLibreMap?.let { enableUserLocation(it, moveCamera = true) }
        } else {
            Toast.makeText(requireContext(), "Location permission is required to see your position", Toast.LENGTH_SHORT).show()
        }
    }

    // --------------------------------------------------
    // LIVE JOB FEED
    // --------------------------------------------------
    private fun listenForJobs() {
        val currentUid = FirebaseAuth.getInstance().currentUser?.uid ?: ""

        // 1. Listen for AVAILABLE jobs (for everyone)
        availableJobsListener = db.collection("jobs")
            .whereEqualTo("status", "AVAILABLE")
            .addSnapshotListener { snapshots, _ ->
                if (snapshots == null) return@addSnapshotListener
                availableJobs.clear()
                for (doc in snapshots) {
                    val job = doc.toObject(Job::class.java)
                    availableJobs.add(job)
                }
                adapter.notifyDataSetChanged()
                updateMapMarkers()
            }

        // 2. Listen for MY active handshake jobs (until I arrive)
        if (currentUid.isNotEmpty()) {
            activeHandshakeListener = db.collection("jobs")
                .whereEqualTo("workerId", currentUid)
                .addSnapshotListener { snapshots, _ ->
                    if (snapshots == null) return@addSnapshotListener
                    myActiveHandshakeJobs.clear()
                    val handshakeStatuses = listOf("IN_PROGRESS", "HEADING_TO_CLIENT")
                    for (doc in snapshots) {
                        val job = doc.toObject(Job::class.java)
                        if (job.status in handshakeStatuses) {
                            myActiveHandshakeJobs.add(job)
                        }
                    }
                    updateMapMarkers()
                }
        }

        // 3. Listen for Company Hiring Posts
        hiringListener = db.collection("hiring")
            .whereEqualTo("status", "OPEN")
            .addSnapshotListener { snapshots, _ ->
                if (snapshots == null) return@addSnapshotListener
                val now = System.currentTimeMillis()
                hiringList.clear()
                for (doc in snapshots) {
                    val post = doc.toObject(com.example.newtacks.models.HiringPost::class.java)
                    if (post.expiresAt == 0L || post.expiresAt > now) {
                        hiringList.add(post)
                    }
                }
                updateMapMarkers()
            }
    }

    // --------------------------------------------------
    // MAP LOGIC
    // --------------------------------------------------
    private fun initMap() {
        if (mapLibreMap != null) {
            updateMapMarkers()
            return
        }

        mapView.getMapAsync { map ->
            mapLibreMap = map
            
            // 1. Load the local OSM style (powered by our localhost server)
            map.setStyle("asset://map_style.json") {
                // Set zoom boundaries to match typical OSM city files
                map.setMinZoomPreference(2.0)
                map.setMaxZoomPreference(18.0)
                
                enableUserLocation(map, moveCamera = true)
                updateMapMarkers()
            }

            map.addOnMapClickListener {
                // Optional: hide overlay if user taps map
                cardListOverlay.visibility = View.GONE
                false
            }

            map.setOnMarkerClickListener { marker ->
                // Search in all lists
                val job = availableJobs.find { it.jobTitle == marker.title } 
                    ?: myActiveHandshakeJobs.find { it.jobTitle == marker.title }
                
                if (job != null) {
                    showJobPreview(job)
                } else {
                    val hiringPost = hiringList.find { it.jobTitle == marker.title }
                    if (hiringPost != null) {
                        showHiringPreview(hiringPost)
                    }
                }
                true
            }
        }
    }

    private fun updateMapMarkers() {
        val map = mapLibreMap ?: return
        map.clear()
        
        // 1. Available Job Markers
        for (job in availableJobs) {
            if (job.latitude != 0.0 && job.longitude != 0.0) {
                map.addMarker(
                    MarkerOptions()
                        .position(LatLng(job.latitude, job.longitude))
                        .title(job.jobTitle)
                        .snippet("Available: ₱${job.offeredAmount}")
                )
            }
        }

        // 2. Company Hiring Markers
        for (post in hiringList) {
            if (post.latitude != 0.0 && post.longitude != 0.0) {
                map.addMarker(
                    MarkerOptions()
                        .position(LatLng(post.latitude, post.longitude))
                        .title(post.jobTitle)
                        .snippet("Company: ₱${post.dailyRate}/day")
                )
            }
        }

        // 3. My Active Job Markers & Waypoint Line
        val workerLoc = map.locationComponent.lastKnownLocation
        
        for (job in myActiveHandshakeJobs) {
            if (job.latitude != 0.0 && job.longitude != 0.0) {
                map.addMarker(
                    MarkerOptions()
                        .position(LatLng(job.latitude, job.longitude))
                        .title(job.jobTitle)
                        .snippet("My Active Job: ${job.status}")
                )

                // 3. Waypoint Line (Only if HEADING_TO_CLIENT)
                if (job.status == "HEADING_TO_CLIENT" && workerLoc != null) {
                    val points = listOf(
                        LatLng(workerLoc.latitude, workerLoc.longitude),
                        LatLng(job.latitude, job.longitude)
                    )
                    map.addPolyline(
                        PolylineOptions()
                            .addAll(points)
                            .color(android.graphics.Color.parseColor("#2563EB"))
                            .width(4f)
                    )
                }
            }
        }
    }

    // --------------------------------------------------
    // JOB PREVIEW POPUP
    // --------------------------------------------------
    private fun showJobPreview(job: Job) {
        val view = layoutInflater.inflate(R.layout.dialog_job_preview, null)
        val tvTitle   = view.findViewById<TextView>(R.id.tvTitle)
        val tvDetails = view.findViewById<TextView>(R.id.tvDetails)
        val tvDuration = view.findViewById<TextView>(R.id.tvDuration)
        val layoutImages = view.findViewById<LinearLayout>(R.id.layoutImages)
        val tvNoImages = view.findViewById<TextView>(R.id.tvNoImages)
        val btnAccept = view.findViewById<Button>(R.id.btnAccept)
        val btnClose  = view.findViewById<Button>(R.id.btnClose)

        tvTitle.text = job.jobTitle
        tvDetails.text = """
            Category: ${job.serviceCategory}
            Client: ${job.clientName}
            Address: ${job.clientAddress}
            Price: ₱${job.offeredAmount}
            Description: ${job.description}
        """.trimIndent()

        tvDuration.text = "Estimated Duration: ${job.estimatedDurationHours} hours"

        if (job.jobImages.isEmpty()) {
            tvNoImages.visibility = View.VISIBLE
        } else {
            tvNoImages.visibility = View.GONE
            job.jobImages.forEach { url ->
                val imageView = ImageView(requireContext())
                val size = resources.getDimensionPixelSize(R.dimen.preview_image_size)
                val params = LinearLayout.LayoutParams(size, size)
                params.setMargins(0, 0, 12, 0)
                imageView.layoutParams = params
                imageView.scaleType = ImageView.ScaleType.CENTER_CROP
                imageView.load(url) {
                    crossfade(true)
                    placeholder(R.drawable.bg_image_placeholder)
                }
                imageView.setOnClickListener {
                    ImageUtils.showFullscreenImage(requireContext(), url)
                }
                layoutImages.addView(imageView)
            }
        }

        val dialog = android.app.AlertDialog.Builder(requireContext())
            .setView(view)
            .create()

        btnClose.setOnClickListener { dialog.dismiss() }
        btnAccept.setOnClickListener {
            dialog.dismiss()
            acceptJob(job)
        }

        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
        dialog.show()
    }

    private fun showHiringPreview(post: com.example.newtacks.models.HiringPost) {
        val view = layoutInflater.inflate(R.layout.dialog_job_preview, null)
        val tvTitle   = view.findViewById<TextView>(R.id.tvTitle)
        val tvDetails = view.findViewById<TextView>(R.id.tvDetails)
        val btnAccept = view.findViewById<Button>(R.id.btnAccept)
        val btnClose  = view.findViewById<Button>(R.id.btnClose)
        
        tvTitle.text = post.jobTitle
        tvDetails.text = """
            Company: ${post.companyName}
            Address: ${post.companyAddress}
            Daily Rate: ₱${post.dailyRate}
            Employment: ${post.employmentType}
            Services: ${post.serviceCategories.joinToString(", ")}
        """.trimIndent()

        val uid = FirebaseAuth.getInstance().currentUser?.uid
        val hasApplied = uid != null && post.applicants.contains(uid)

        if (hasApplied) {
            btnAccept.text = "Already Applied"
            btnAccept.isEnabled = false
            btnAccept.alpha = 0.6f
        } else {
            btnAccept.text = "Apply Now"
            btnAccept.isEnabled = true
            btnAccept.alpha = 1.0f
        }

        // Hiring posts don't have images yet
        view.findViewById<View>(R.id.tvImagesLabel).visibility = View.GONE
        view.findViewById<View>(R.id.scrollImages).visibility = View.GONE
        view.findViewById<View>(R.id.tvDuration).visibility = View.GONE
        
        val dialog = android.app.AlertDialog.Builder(requireContext())
            .setView(view)
            .create()

        btnClose.setOnClickListener { dialog.dismiss() }
        btnAccept.setOnClickListener {
            dialog.dismiss()
            applyForHiring(post)
        }

        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
        dialog.show()
    }

    private fun applyForHiring(post: com.example.newtacks.models.HiringPost) {
        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return
        
        db.collection("hiring").document(post.hiringId)
            .update("applicants", com.google.firebase.firestore.FieldValue.arrayUnion(uid))
            .addOnSuccessListener {
                Toast.makeText(requireContext(), "Application sent!", Toast.LENGTH_SHORT).show()
            }
    }

    // --------------------------------------------------
    // ACCEPT JOB (FIRESTORE TRANSACTION)
    // --------------------------------------------------
    private fun acceptJob(job: Job) {
        // --------------------------------------------------
        // ✅ ENFORCE LOCATION RESTRICTION
        // --------------------------------------------------
        if (ContextCompat.checkSelfPermission(requireContext(), android.Manifest.permission.ACCESS_FINE_LOCATION) 
            != android.content.pm.PackageManager.PERMISSION_GRANTED) {
            @Suppress("DEPRECATION")
            requestPermissions(arrayOf(android.Manifest.permission.ACCESS_FINE_LOCATION), 1002)
            return
        }

        fusedLocationClient.lastLocation.addOnSuccessListener { location ->
            if (location == null) {
                Toast.makeText(requireContext(), "Please turn on your GPS to accept jobs", Toast.LENGTH_LONG).show()
                return@addOnSuccessListener
            }

            // Continue with job acceptance
            processJobAcceptance(job, location)
        }
    }

    private fun processJobAcceptance(job: Job, location: android.location.Location) {
        val currentUser = FirebaseAuth.getInstance().currentUser ?: return
        val workerId = currentUser.uid

        db.collection("jobs")
            .whereEqualTo("workerId", workerId)
            .get()
            .addOnSuccessListener { snapshots ->
                
                // Filter active statuses locally to avoid index issues
                val activeStatuses = listOf("IN_PROGRESS", "HEADING_TO_CLIENT", "ARRIVED", "PENDING_VERIFICATION")
                val hasActiveJob = snapshots.documents.any { 
                    val status = it.getString("status") ?: ""
                    status in activeStatuses 
                }

                if (hasActiveJob) {
                    android.widget.Toast.makeText(
                        requireContext(),
                        "Finish your current job first",
                        android.widget.Toast.LENGTH_LONG
                    ).show()
                    return@addOnSuccessListener
                }

                db.collection("users")
                    .document(workerId)
                    .get()
                    .addOnSuccessListener { doc ->
                        val workerName = doc.getString("name") ?: "Worker"

                        db.runTransaction { transaction ->
                            val ref = db.collection("jobs").document(job.jobId)
                            val snapshot = transaction.get(ref)
                            val status = snapshot.getString("status")

                            if (status != "AVAILABLE") {
                                throw Exception("Job already taken")
                            }

                            // Update job with worker info
                            transaction.update(
                                ref,
                                mapOf(
                                    "status"     to "IN_PROGRESS",
                                    "workerId"   to workerId,
                                    "workerName" to workerName,
                                    "acceptedAt" to System.currentTimeMillis()
                                )
                            )
                            
                            // Also update worker's location in their profile
                            val workerRef = db.collection("users").document(workerId)
                            transaction.update(workerRef, mapOf(
                                "latitude" to location.latitude,
                                "longitude" to location.longitude,
                                "isOnline" to true,
                                "lastActive" to System.currentTimeMillis()
                            ))
                        }.addOnSuccessListener {
                            (activity as? com.example.newtacks.WorkerDashboardActivity)?.switchTab(R.id.nav_job)
                        }
                    }
            }
    }

    // --------------------------------------------------
    // CLEANUP & LIFECYCLE
    // --------------------------------------------------
    override fun onStart() { super.onStart(); mapView.onStart() }
    override fun onResume() { 
        super.onResume()
        mapView.onResume()
        handler.post(refreshWaypointTask)
    }
    override fun onPause() { 
        super.onPause()
        mapView.onPause()
        handler.removeCallbacks(refreshWaypointTask)
    }
    override fun onStop() { super.onStop(); mapView.onStop() }
    override fun onLowMemory() { super.onLowMemory(); mapView.onLowMemory() }
    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        mapView.onSaveInstanceState(outState)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        mapView.onDestroy()
        availableJobsListener?.remove()
        activeHandshakeListener?.remove()
        hiringListener?.remove()
    }
}