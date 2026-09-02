package com.example.newtacks.worker

import android.os.Bundle
import android.view.*
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
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
import com.example.newtacks.utils.RouteApiService
import com.example.newtacks.utils.RouteUtils
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
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

class WorkerFeedFragment : Fragment() {

    private lateinit var recyclerView: RecyclerView
    private lateinit var adapter: WorkerJobAdapter
    private lateinit var fabToggleList: FloatingActionButton
    private lateinit var fabMyLocation: FloatingActionButton
    private lateinit var cardListOverlay: View
    private lateinit var cardNavInfo: View
    private lateinit var tvNavDistance: TextView
    private lateinit var tvNavEstimate: TextView
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

    private val routeService: RouteApiService by lazy {
        Retrofit.Builder()
            .baseUrl("https://router.project-osrm.org/")
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(RouteApiService::class.java)
    }

    private var lastRoutePoints: List<LatLng>? = null
    private var lastWorkerLatLng: LatLng? = null
    private var activeJobId: String? = null

    private val requestPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
            if (isGranted) {
                mapLibreMap?.let { enableUserLocation(it, moveCamera = true) }
            } else {
                context?.let {
                    Toast.makeText(it, "Location permission is required to see your position", Toast.LENGTH_SHORT).show()
                }
            }
        }

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
        cardNavInfo         = view.findViewById(R.id.cardNavInfo)
        tvNavDistance       = view.findViewById(R.id.tvNavDistance)
        tvNavEstimate       = view.findViewById(R.id.tvNavEstimate)
        mapView             = view.findViewById(R.id.mapView)

        fabZoomIn           = view.findViewById(R.id.fabZoomIn)
        fabZoomOut          = view.findViewById(R.id.fabZoomOut)

        mapView.onCreate(savedInstanceState)

        recyclerView.layoutManager = LinearLayoutManager(requireContext())
        adapter = WorkerJobAdapter(availableJobs) { job -> 
            cardListOverlay.visibility = View.GONE
            mapLibreMap?.animateCamera(CameraUpdateFactory.newLatLngZoom(LatLng(job.latitude, job.longitude), 15.0))
        }
        recyclerView.adapter = adapter

        fabToggleList.setOnClickListener {
            cardListOverlay.visibility = if (cardListOverlay.visibility == View.VISIBLE) View.GONE else View.VISIBLE
        }

        fabMyLocation.setOnClickListener {
            mapLibreMap?.let { map ->
                if (map.locationComponent.isLocationComponentActivated && map.locationComponent.isLocationComponentEnabled) {
                    val lastLoc = map.locationComponent.lastKnownLocation
                    if (lastLoc != null) {
                        map.animateCamera(CameraUpdateFactory.newLatLngZoom(LatLng(lastLoc.latitude, lastLoc.longitude), 15.0))
                    } else {
                        Toast.makeText(requireContext(), "Getting location...", Toast.LENGTH_SHORT).show()
                    }
                } else {
                    enableUserLocation(map, moveCamera = true, forceRequest = true)
                }
            }
        }

        fabZoomIn.setOnClickListener {
            mapLibreMap?.animateCamera(CameraUpdateFactory.zoomIn())
        }
        fabZoomOut.setOnClickListener {
            mapLibreMap?.animateCamera(CameraUpdateFactory.zoomOut())
        }

        ViewCompat.setOnApplyWindowInsetsListener(view) { _, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            fabToggleList.updateLayoutParams<ViewGroup.MarginLayoutParams> {
                topMargin = systemBars.top + (resources.displayMetrics.density * 16).toInt()
            }
            insets
        }

        listenForJobs()
        initMap()
        return view
    }

    fun zoomToLocation(lat: Double, lng: Double) {
        mapLibreMap?.animateCamera(CameraUpdateFactory.newLatLngZoom(LatLng(lat, lng), 15.0))
    }

    private fun enableUserLocation(map: MapLibreMap, moveCamera: Boolean = false, forceRequest: Boolean = false) {
        val context = context ?: return
        if (!isAdded) return
        val style = map.style ?: return

        val hasPermission = ContextCompat.checkSelfPermission(context, android.Manifest.permission.ACCESS_FINE_LOCATION) == android.content.pm.PackageManager.PERMISSION_GRANTED

        if (hasPermission) {
            val locationComponent = map.locationComponent
            val activationOptions = LocationComponentActivationOptions.builder(context, style)
                .build()
            
            locationComponent.activateLocationComponent(activationOptions)
            locationComponent.isLocationComponentEnabled = true
            locationComponent.cameraMode = CameraMode.NONE
            locationComponent.renderMode = RenderMode.COMPASS
            
            if (moveCamera) {
                fusedLocationClient.lastLocation.addOnSuccessListener { location ->
                    if (location != null && isAdded) {
                        map.moveCamera(CameraUpdateFactory.newLatLngZoom(LatLng(location.latitude, location.longitude), 15.0))
                    } else if (isAdded) {
                        val davaoCityHall = LatLng(7.0648, 125.6079) 
                        map.moveCamera(CameraUpdateFactory.newLatLngZoom(davaoCityHall, 13.0))
                    }
                }
            }
        } else if (forceRequest) {
            handler.post {
                if (isAdded) {
                    requestPermissionLauncher.launch(android.Manifest.permission.ACCESS_FINE_LOCATION)
                }
            }
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
    }

    private fun listenForJobs() {
        val currentUid = FirebaseAuth.getInstance().currentUser?.uid ?: ""

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

        if (currentUid.isNotEmpty()) {
            activeHandshakeListener = db.collection("jobs")
                .whereEqualTo("workerId", currentUid)
                .addSnapshotListener { snapshots, _ ->
                    if (snapshots == null) return@addSnapshotListener
                    myActiveHandshakeJobs.clear()
                    val handshakeStatuses = listOf("IN_PROGRESS", "HEADING_TO_CLIENT")
                    
                    var newActiveJobId: String? = null
                    for (doc in snapshots) {
                        val job = doc.toObject(Job::class.java)
                        if (job.status in handshakeStatuses) {
                            myActiveHandshakeJobs.add(job)
                            newActiveJobId = job.jobId
                        }
                    }

                    if (newActiveJobId != activeJobId) {
                        activeJobId = newActiveJobId
                        lastRoutePoints = null
                        lastWorkerLatLng = null
                    }

                    updateMapMarkers()
                }
        }

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

    private fun initMap() {
        if (mapLibreMap != null) {
            updateMapMarkers()
            return
        }

        mapView.getMapAsync { map ->
            mapLibreMap = map
            map.setStyle("asset://map_style.json") {
                map.setMinZoomPreference(2.0)
                map.setMaxZoomPreference(18.0)
                enableUserLocation(map, moveCamera = true)
                updateMapMarkers()
            }

            map.addOnMapClickListener {
                cardListOverlay.visibility = View.GONE
                false
            }

            map.setOnMarkerClickListener { marker ->
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
        
        var isHeadingToClient = false

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

        val workerLoc = map.locationComponent.lastKnownLocation
        
        for (job in myActiveHandshakeJobs) {
            if (job.latitude != 0.0 && job.longitude != 0.0) {
                map.addMarker(
                    MarkerOptions()
                        .position(LatLng(job.latitude, job.longitude))
                        .title(job.jobTitle)
                        .snippet("My Active Job: ${job.status}")
                )

                if (job.status == "HEADING_TO_CLIENT" && workerLoc != null) {
                    isHeadingToClient = true
                    val workerLatLng = LatLng(workerLoc.latitude, workerLoc.longitude)
                    val jobLatLng = LatLng(job.latitude, job.longitude)

                    lastRoutePoints?.let { points ->
                        map.addPolyline(
                            PolylineOptions()
                                .addAll(points)
                                .color(android.graphics.Color.parseColor("#2563EB"))
                                .width(5f)
                        )
                    }

                    if (shouldFetchNewRoute(workerLatLng)) {
                        fetchRouteFromOsrm(workerLatLng, jobLatLng)
                    }
                }
            }
        }
        
        if (isHeadingToClient) {
            cardNavInfo.visibility = View.VISIBLE
        } else {
            cardNavInfo.visibility = View.GONE
            lastRoutePoints = null
        }
    }

    private fun updateNavInfoUI(meters: Float, durationSec: Double) {
        if (!isAdded) return
        
        val distanceStr = com.example.newtacks.utils.DistanceUtils.formatDistance(meters)
        tvNavDistance.text = distanceStr

        val mins = (durationSec / 60).toInt().coerceAtLeast(1)
        val arrivalCal = java.util.Calendar.getInstance()
        arrivalCal.add(java.util.Calendar.SECOND, durationSec.toInt())
        val arrivalTime = java.text.SimpleDateFormat("h:mm a", java.util.Locale.getDefault()).format(arrivalCal.time)

        tvNavEstimate.text = String.format("%d mins • %s arrival", mins, arrivalTime)
    }

    private fun shouldFetchNewRoute(currentLoc: LatLng): Boolean {
        if (lastRoutePoints == null || lastWorkerLatLng == null) return true
        return currentLoc.distanceTo(lastWorkerLatLng!!) > 50
    }

    private fun fetchRouteFromOsrm(worker: LatLng, job: LatLng) {
        val coords = "${worker.longitude},${worker.latitude};${job.longitude},${job.latitude}"
        routeService.getRoute(coords).enqueue(object : retrofit2.Callback<com.example.newtacks.utils.OsrmResponse> {
            override fun onResponse(
                call: retrofit2.Call<com.example.newtacks.utils.OsrmResponse>,
                response: retrofit2.Response<com.example.newtacks.utils.OsrmResponse>
            ) {
                if (response.isSuccessful) {
                    val route = response.body()?.routes?.firstOrNull()
                    val encodedPoly = route?.geometry
                    if (encodedPoly != null) {
                        lastRoutePoints = RouteUtils.decodePolyline(encodedPoly)
                        lastWorkerLatLng = worker
                        
                        val distanceMeters = route.distance.toFloat()
                        val durationSeconds = route.duration
                        updateNavInfoUI(distanceMeters, durationSeconds)
                        updateMapMarkers()
                    }
                }
            }

            override fun onFailure(call: retrofit2.Call<com.example.newtacks.utils.OsrmResponse>, t: Throwable) {
                t.printStackTrace()
            }
        })
    }

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

    private fun acceptJob(job: Job) {
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

                            transaction.update(
                                ref,
                                mapOf(
                                    "status"     to "IN_PROGRESS",
                                    "workerId"   to workerId,
                                    "workerName" to workerName,
                                    "acceptedAt" to System.currentTimeMillis()
                                )
                            )
                            
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
