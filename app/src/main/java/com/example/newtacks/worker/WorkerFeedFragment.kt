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
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import coil.load
import coil.transform.CircleCropTransformation
import com.example.newtacks.R
import com.example.newtacks.models.Job
import com.example.newtacks.models.User
import com.example.newtacks.utils.ImageUtils
import com.example.newtacks.utils.RouteApiService
import com.example.newtacks.utils.RouteUtils
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.button.MaterialButton
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.android.material.switchmaterial.SwitchMaterial
import com.google.android.material.tabs.TabLayout
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.example.newtacks.models.HiringPost
import com.example.newtacks.models.FeedOpportunity
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
    private lateinit var fabToggleList: View
    private lateinit var tvRequestBadge: TextView
    private lateinit var fabMyLocation: ImageButton
    private lateinit var swipeRefresh: SwipeRefreshLayout
    private lateinit var cardListOverlay: View
    private lateinit var cardNavInfo: View
    private lateinit var tvNavDistance: TextView
    private lateinit var tvNavEstimate: TextView
    private lateinit var mapView: MapView
    private var mapLibreMap: MapLibreMap? = null
    private lateinit var tabLayoutFeed: TabLayout
    private lateinit var tvWorkerNameHeader: TextView

    private lateinit var bottomSheetBehavior: BottomSheetBehavior<View>
    private lateinit var bsView: View

    private lateinit var fabZoomIn: ImageButton
    private lateinit var fabZoomOut: ImageButton

    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private val db = FirebaseFirestore.getInstance()
    private var availableJobsListener: ListenerRegistration? = null
    private var activeHandshakeListener: ListenerRegistration? = null
    private var hiringListener: ListenerRegistration? = null
    
    private val availableJobs = mutableListOf<Job>()
    private val myActiveHandshakeJobs = mutableListOf<Job>()
    private val hiringList = mutableListOf<com.example.newtacks.models.HiringPost>()
    private val combinedOpportunities = mutableListOf<com.example.newtacks.models.FeedOpportunity>()

    private val markerIdToEntityId = mutableMapOf<Long, String>()

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
        tvRequestBadge      = view.findViewById(R.id.tvRequestBadge)
        fabMyLocation       = view.findViewById(R.id.fabMyLocation)
        swipeRefresh        = view.findViewById(R.id.swipeRefreshFeed)
        cardListOverlay     = view.findViewById(R.id.cardListOverlay)
        cardNavInfo         = view.findViewById(R.id.cardNavInfo)
        tvNavDistance       = view.findViewById(R.id.tvNavDistance)
        tvNavEstimate       = view.findViewById(R.id.tvNavEstimate)
        mapView             = view.findViewById(R.id.mapView)
        tabLayoutFeed       = view.findViewById(R.id.tabLayoutFeed)
        tvWorkerNameHeader  = view.findViewById(R.id.tvWorkerNameHeader)

        bsView = view.findViewById(R.id.jobDetailsBottomSheet)
        bottomSheetBehavior = BottomSheetBehavior.from(bsView)
        bottomSheetBehavior.state = BottomSheetBehavior.STATE_HIDDEN
        
        // Set height to half of the screen
        val displayMetrics = resources.displayMetrics
        bsView.layoutParams.height = displayMetrics.heightPixels / 2
        
        bottomSheetBehavior.addBottomSheetCallback(object : BottomSheetBehavior.BottomSheetCallback() {
            override fun onStateChanged(bottomSheet: View, newState: Int) {
                if (newState == BottomSheetBehavior.STATE_HIDDEN) {
                    mapLibreMap?.setPadding(0, 0, 0, 0)
                }
            }
            override fun onSlide(bottomSheet: View, slideOffset: Float) {}
        })

        fabZoomIn           = view.findViewById(R.id.fabZoomIn)
        fabZoomOut          = view.findViewById(R.id.fabZoomOut)

        mapView.onCreate(savedInstanceState)

        loadWorkerName()

        view.findViewById<View>(R.id.btnHelp).setOnClickListener {
            Toast.makeText(requireContext(), "Support coming soon", Toast.LENGTH_SHORT).show()
        }
        
        view.findViewById<View>(R.id.btnNotifications).setOnClickListener {
            com.example.newtacks.utils.NotificationHelper.showNotificationDialog(requireContext())
        }

        recyclerView.layoutManager = LinearLayoutManager(requireContext())
        adapter = WorkerJobAdapter(combinedOpportunities) { opportunity -> 
            cardListOverlay.visibility = View.GONE
            
            when (opportunity) {
                is FeedOpportunity.ClientJob -> {
                    showJobDetailsBottomSheet(opportunity.job, opportunity.distanceStr)
                }
                is FeedOpportunity.CompanyHiring -> {
                    // hiring can still use the old preview or I can make one for it too?
                    // The request asked for "job requests" specifically
                    zoomToLocation(opportunity.latitude, opportunity.longitude)
                    showHiringPreview(opportunity.post)
                }
                is FeedOpportunity.ActiveJob -> {
                    showJobDetailsBottomSheet(opportunity.job, opportunity.distanceStr)
                }
            }
        }
        recyclerView.adapter = adapter

        fabToggleList.setOnClickListener {
            if (bottomSheetBehavior.state != BottomSheetBehavior.STATE_HIDDEN) {
                bottomSheetBehavior.state = BottomSheetBehavior.STATE_HIDDEN
            }
            cardListOverlay.visibility = if (cardListOverlay.visibility == View.VISIBLE) View.GONE else View.VISIBLE
        }

        swipeRefresh.setOnRefreshListener {
            // Force a refresh of everything
            listenForJobs()
            // Map style might also need a refresh if offline server was restarted, but usually not needed.
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

        tabLayoutFeed.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab?) {
                updateCombinedList()
            }
            override fun onTabUnselected(tab: TabLayout.Tab?) {}
            override fun onTabReselected(tab: TabLayout.Tab?) {}
        })

        ViewCompat.setOnApplyWindowInsetsListener(view) { _, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.findViewById<View>(R.id.layoutHeader).updateLayoutParams<ViewGroup.MarginLayoutParams> {
                topMargin = systemBars.top
            }
            insets
        }

        listenForJobs()
        initMap()
        return view
    }

    private fun loadWorkerName() {
        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return
        db.collection("users").document(uid).get().addOnSuccessListener { doc ->
            if (isAdded) {
                val name = doc.getString("name") ?: "Worker"
                tvWorkerNameHeader.text = name
            }
        }
    }

    private fun showJobDetailsBottomSheet(job: Job, distanceStr: String) {
        val ivProfile   = bsView.findViewById<ImageView>(R.id.ivClientProfileBS)
        val tvName      = bsView.findViewById<TextView>(R.id.tvClientNameBS)
        val tvDistance  = bsView.findViewById<TextView>(R.id.tvDistanceBS)
        val tvService   = bsView.findViewById<TextView>(R.id.tvServiceTypeBS)
        val tvAddress   = bsView.findViewById<TextView>(R.id.tvAddressBS)
        val tvTime      = bsView.findViewById<TextView>(R.id.tvTimeBS)
        val tvDate      = bsView.findViewById<TextView>(R.id.tvDateBS)
        val btnImages   = bsView.findViewById<TextView>(R.id.btnViewImagesBS)
        val tvRate      = bsView.findViewById<TextView>(R.id.tvRateBS)
        val tvDesc      = bsView.findViewById<TextView>(R.id.tvDescriptionBS)
        val btnDecline  = bsView.findViewById<Button>(R.id.btnDeclineBS)
        val btnAccept   = bsView.findViewById<Button>(R.id.btnAcceptBS)

        tvName.text = job.clientName
        tvDistance.text = distanceStr
        tvService.text = job.serviceCategory
        tvAddress.text = job.clientAddress
        tvTime.text = job.scheduledTime
        tvDate.text = job.scheduledDate
        tvRate.text = "₱${job.offeredAmount}/${job.rateType}"
        tvDesc.text = job.description

        // Load client profile pic
        db.collection("users").document(job.clientId).get().addOnSuccessListener { doc ->
            val user = doc.toObject(User::class.java)
            user?.profileImage?.let { url ->
                ivProfile.load(url) {
                    crossfade(true)
                    placeholder(R.drawable.ic_person_placeholder)
                    transformations(CircleCropTransformation())
                }
            }
        }

        btnImages.setOnClickListener {
            if (job.jobImages.isNotEmpty()) {
                ImageUtils.showFullscreenImage(requireContext(), job.jobImages[0])
            } else {
                Toast.makeText(requireContext(), "No images provided", Toast.LENGTH_SHORT).show()
            }
        }

        btnDecline.setOnClickListener {
            bottomSheetBehavior.state = BottomSheetBehavior.STATE_HIDDEN
        }

        btnAccept.setOnClickListener {
            btnAccept.isEnabled = false
            acceptJob(job) { success ->
                if (success) {
                    bottomSheetBehavior.state = BottomSheetBehavior.STATE_HIDDEN
                } else {
                    btnAccept.isEnabled = true
                }
            }
        }

        // Show bottom sheet
        bottomSheetBehavior.state = BottomSheetBehavior.STATE_EXPANDED
        
        // Pan map with offset
        val latLng = LatLng(job.latitude, job.longitude)
        // Adjust padding for half-screen sheet (roughly 45% of height to be safe)
        val offset = (resources.displayMetrics.heightPixels * 0.45).toInt()
        mapLibreMap?.setPadding(0, 0, 0, offset) 
        mapLibreMap?.animateCamera(CameraUpdateFactory.newLatLngZoom(latLng, 16.0))
    }

    fun zoomToLocation(lat: Double, lng: Double) {
        mapLibreMap?.setPadding(0, 0, 0, 0)
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
                        updateCombinedList()
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
                    val job = doc.toObject(Job::class.java)?.copy(jobId = doc.id)
                    if (job != null) availableJobs.add(job)
                }
                updateCombinedList()
                swipeRefresh.isRefreshing = false
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
                        val job = doc.toObject(Job::class.java)?.copy(jobId = doc.id)
                        if (job != null && job.status in handshakeStatuses) {
                            myActiveHandshakeJobs.add(job)
                            newActiveJobId = job.jobId
                        }
                    }

                    if (newActiveJobId != activeJobId) {
                        activeJobId = newActiveJobId
                        lastRoutePoints = null
                        lastWorkerLatLng = null
                    }

                    updateCombinedList()
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
                    val post = doc.toObject(com.example.newtacks.models.HiringPost::class.java)?.copy(hiringId = doc.id)
                    if (post != null && (post.expiresAt == 0L || post.expiresAt > now)) {
                        hiringList.add(post)
                    }
                }
                updateCombinedList()
                updateMapMarkers()
            }
    }

    private fun updateCombinedList() {
        combinedOpportunities.clear()
        
        val isJobRequestsTab = tabLayoutFeed.selectedTabPosition == 0
        
        if (isJobRequestsTab) {
            // Add Client Jobs
            for (job in availableJobs) {
                combinedOpportunities.add(FeedOpportunity.ClientJob(job))
            }
            // Add Active Handshake Jobs
            for (job in myActiveHandshakeJobs) {
                combinedOpportunities.add(FeedOpportunity.ActiveJob(job))
            }
        } else {
            // Add Company Posts
            for (post in hiringList) {
                combinedOpportunities.add(FeedOpportunity.CompanyHiring(post))
            }
        }

        // Sort by distance if location is available and calculate distance string
        val workerLoc = mapLibreMap?.locationComponent?.lastKnownLocation
        if (workerLoc != null) {
            for (opportunity in combinedOpportunities) {
                val results = FloatArray(1)
                android.location.Location.distanceBetween(
                    workerLoc.latitude, workerLoc.longitude,
                    opportunity.latitude, opportunity.longitude,
                    results
                )
                opportunity.distanceStr = com.example.newtacks.utils.DistanceUtils.formatDistance(results[0]) + " away"
            }
            
            combinedOpportunities.sortBy { opportunity ->
                val results = FloatArray(1)
                android.location.Location.distanceBetween(
                    workerLoc.latitude, workerLoc.longitude,
                    opportunity.latitude, opportunity.longitude,
                    results
                )
                results[0]
            }
        } else {
            for (opportunity in combinedOpportunities) {
                opportunity.distanceStr = "Distance unknown"
            }
        }
        
        // Update badge count to show TOTAL requests (Client + Company)
        val totalRequests = availableJobs.size + hiringList.size
        tvRequestBadge.visibility = if (totalRequests > 0) View.VISIBLE else View.GONE
        tvRequestBadge.text = totalRequests.toString()
        
        adapter.notifyDataSetChanged()
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
                
                // Use default MapLibre compass as requested
                map.uiSettings.isCompassEnabled = true
                map.uiSettings.setCompassGravity(Gravity.BOTTOM or Gravity.START)
                // Offset compass from bottom nav area (64dp + 16dp margin)
                val density = resources.displayMetrics.density
                val offset = (80 * density).toInt()
                map.uiSettings.setCompassMargins(48, 0, 0, offset)
                
                // Set initial map padding for bottom nav
                map.setPadding(0, 0, 0, (64 * density).toInt())
                
                enableUserLocation(map, moveCamera = true)
                updateMapMarkers()
            }

            map.addOnMapClickListener {
                cardListOverlay.visibility = View.GONE
                false
            }

            map.setOnMarkerClickListener { marker ->
                val entityId = markerIdToEntityId[marker.id]
                
                if (entityId != null) {
                    val job = availableJobs.find { it.jobId == entityId } 
                        ?: myActiveHandshakeJobs.find { it.jobId == entityId }
                    
                    if (job != null) {
                        // Find distance string for this job
                        val opp = combinedOpportunities.find { 
                            (it is FeedOpportunity.ClientJob && it.job.jobId == job.jobId) ||
                            (it is FeedOpportunity.ActiveJob && it.job.jobId == job.jobId)
                        }
                        val dist = opp?.distanceStr ?: "-- km away"
                        showJobDetailsBottomSheet(job, dist)
                    } else {
                        val hiringPost = hiringList.find { it.hiringId == entityId }
                        if (hiringPost != null) {
                            showHiringPreview(hiringPost)
                        }
                    }
                }
                true
            }
        }
    }

    private fun updateMapMarkers() {
        val map = mapLibreMap ?: return
        map.clear()
        markerIdToEntityId.clear()
        
        var isHeadingToClient = false

        for (job in availableJobs) {
            if (job.latitude != 0.0 && job.longitude != 0.0) {
                val marker = map.addMarker(
                    MarkerOptions()
                        .position(LatLng(job.latitude, job.longitude))
                        .title(job.jobTitle)
                        .snippet("Available: ₱${job.offeredAmount}")
                )
                markerIdToEntityId[marker.id] = job.jobId
            }
        }

        for (post in hiringList) {
            if (post.latitude != 0.0 && post.longitude != 0.0) {
                val marker = map.addMarker(
                    MarkerOptions()
                        .position(LatLng(post.latitude, post.longitude))
                        .title(post.jobTitle)
                        .snippet("Company: ₱${post.dailyRate}/day")
                )
                markerIdToEntityId[marker.id] = post.hiringId
            }
        }

        val workerLoc = map.locationComponent.lastKnownLocation
        
        for (job in myActiveHandshakeJobs) {
            if (job.latitude != 0.0 && job.longitude != 0.0) {
                val marker = map.addMarker(
                    MarkerOptions()
                        .position(LatLng(job.latitude, job.longitude))
                        .title(job.jobTitle)
                        .snippet("My Active Job: ${job.status}")
                )
                markerIdToEntityId[marker.id] = job.jobId

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

    private fun showHiringPreview(post: HiringPost) {
        val intent = android.content.Intent(requireContext(), com.example.newtacks.company.HiringDetailsActivity::class.java)
        intent.putExtra("HIRING_POST_JSON", com.google.gson.Gson().toJson(post))
        startActivity(intent)
    }

    private fun acceptJob(job: Job, onResult: (Boolean) -> Unit = {}) {
        if (ContextCompat.checkSelfPermission(requireContext(), android.Manifest.permission.ACCESS_FINE_LOCATION) 
            != android.content.pm.PackageManager.PERMISSION_GRANTED) {
            @Suppress("DEPRECATION")
            requestPermissions(arrayOf(android.Manifest.permission.ACCESS_FINE_LOCATION), 1002)
            onResult(false)
            return
        }

        fusedLocationClient.lastLocation.addOnSuccessListener { location ->
            if (location == null) {
                Toast.makeText(requireContext(), "Please turn on your GPS to accept jobs", Toast.LENGTH_LONG).show()
                onResult(false)
                return@addOnSuccessListener
            }

            processJobAcceptance(job, location, onResult)
        }
    }

    private fun processJobAcceptance(job: Job, location: android.location.Location, onResult: (Boolean) -> Unit) {
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
                    onResult(false)
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
                            com.example.newtacks.utils.NotificationHelper.sendNotification(
                                job.clientId,
                                "Job Accepted",
                                "A worker has accepted your ${job.jobTitle} request.",
                                "REQUESTS"
                            )
                            onResult(true)
                            (activity as? com.example.newtacks.WorkerDashboardActivity)?.switchTab(R.id.nav_job)
                        }.addOnFailureListener { e ->
                            Toast.makeText(requireContext(), "Error: ${e.message}", Toast.LENGTH_SHORT).show()
                            onResult(false)
                        }
                    }.addOnFailureListener {
                        onResult(false)
                    }
            }.addOnFailureListener {
                onResult(false)
            }
    }

    override fun onStart() { super.onStart(); mapView.onStart() }
    override fun onResume() { 
        super.onResume()
        mapView.onResume()
        if (!isHidden) {
            handler.post(refreshWaypointTask)
        }
    }
    
    override fun onPause() { 
        super.onPause()
        mapView.onPause()
        handler.removeCallbacks(refreshWaypointTask)
    }

    override fun onHiddenChanged(hidden: Boolean) {
        super.onHiddenChanged(hidden)
        if (hidden) {
            handler.removeCallbacks(refreshWaypointTask)
            mapView.onPause()
        } else {
            handler.post(refreshWaypointTask)
            mapView.onResume()
            updateMapMarkers()
        }
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
