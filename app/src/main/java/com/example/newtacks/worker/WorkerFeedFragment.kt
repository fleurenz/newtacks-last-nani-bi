package com.example.newtacks.worker

import android.os.Bundle
import android.view.*
import android.widget.*
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import coil.load
import com.example.newtacks.R
import com.example.newtacks.models.Job
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
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapView

class WorkerFeedFragment : Fragment() {

    private lateinit var recyclerView: RecyclerView
    private lateinit var adapter: WorkerJobAdapter
    private lateinit var layoutHeader: LinearLayout
    private lateinit var layoutEmptyState: LinearLayout
    private lateinit var tabLayout: TabLayout
    private lateinit var layoutListContainer: LinearLayout
    private lateinit var layoutMapContainer: FrameLayout
    private lateinit var mapView: MapView
    private var mapLibreMap: MapLibreMap? = null

    private lateinit var btnWorkerProfile: MaterialButton
    private lateinit var fabZoomIn: FloatingActionButton
    private lateinit var fabZoomOut: FloatingActionButton

    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private val db = FirebaseFirestore.getInstance()
    private var listener: ListenerRegistration? = null
    private val jobList = mutableListOf<Job>()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val view = inflater.inflate(R.layout.fragment_worker_feed, container, false)

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(requireActivity())

        recyclerView        = view.findViewById(R.id.workerFeedRecycler)
        layoutHeader        = view.findViewById(R.id.layoutHeader)
        layoutEmptyState    = view.findViewById(R.id.layoutEmptyState)
        tabLayout           = view.findViewById(R.id.tabLayout)
        layoutListContainer = view.findViewById(R.id.layoutListContainer)
        layoutMapContainer  = view.findViewById(R.id.layoutMapContainer)
        mapView             = view.findViewById(R.id.mapView)

        btnWorkerProfile    = view.findViewById(R.id.btnWorkerProfile)
        fabZoomIn           = view.findViewById(R.id.fabZoomIn)
        fabZoomOut          = view.findViewById(R.id.fabZoomOut)

        mapView.onCreate(savedInstanceState)
        setupWorkerInfo()

        recyclerView.layoutManager = LinearLayoutManager(requireContext())
        adapter = WorkerJobAdapter(jobList) { job -> showJobPreview(job) }
        recyclerView.adapter = adapter

        // --------------------------------------------------
        // ✅ TAB SWITCHING
        // --------------------------------------------------
        tabLayout.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab?) {
                if (tab?.position == 0) {
                    layoutListContainer.visibility = View.VISIBLE
                    layoutMapContainer.visibility = View.GONE
                } else {
                    layoutListContainer.visibility = View.GONE
                    layoutMapContainer.visibility = View.VISIBLE
                    initMap()
                }
            }
            override fun onTabUnselected(tab: TabLayout.Tab?) {}
            override fun onTabReselected(tab: TabLayout.Tab?) {}
        })

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
            layoutHeader.setPadding(
                layoutHeader.paddingLeft,
                systemBars.top + resources.getDimensionPixelSize(R.dimen.header_padding_top),
                layoutHeader.paddingRight,
                layoutHeader.paddingBottom
            )
            insets
        }

        listenForJobs()
        return view
    }

    // --------------------------------------------------
    // WORKER INFO & LOCATION
    // --------------------------------------------------
    private fun setupWorkerInfo() {
        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return
        db.collection("users").document(uid).get().addOnSuccessListener { doc ->
            val name = doc.getString("name") ?: "Worker Account"
            btnWorkerProfile.text = name
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        if (requestCode == 1002 && grantResults.isNotEmpty() && grantResults[0] == android.content.pm.PackageManager.PERMISSION_GRANTED) {
            Toast.makeText(requireContext(), "Permission granted! Tap Accept again to confirm.", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(requireContext(), "Location permission is required to accept jobs", Toast.LENGTH_SHORT).show()
        }
    }

    // --------------------------------------------------
    // LIVE JOB FEED
    // --------------------------------------------------
    private fun listenForJobs() {
        listener = db.collection("jobs")
            .whereEqualTo("status", "AVAILABLE")
            .addSnapshotListener { snapshots, _ ->
                if (snapshots == null) return@addSnapshotListener
                jobList.clear()
                for (doc in snapshots) {
                    val job = doc.toObject(Job::class.java)
                    jobList.add(job)
                }
                adapter.notifyDataSetChanged()
                updateMapMarkers()

                // toggle empty state
                layoutEmptyState.visibility =
                    if (jobList.isEmpty()) View.VISIBLE else View.GONE
                recyclerView.visibility =
                    if (jobList.isEmpty()) View.GONE else View.VISIBLE
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
                // 2. Initial camera position (Davao City)
                // Using Davao City Hall coordinates as a solid reference point
                val davaoCityHall = LatLng(7.0648, 125.6079) 
                map.moveCamera(CameraUpdateFactory.newLatLngZoom(davaoCityHall, 13.0))
                
                // Set zoom boundaries to match typical OSM city files
                map.setMinZoomPreference(2.0)
                map.setMaxZoomPreference(18.0)
                
                updateMapMarkers()
            }
        }
    }

    private fun updateMapMarkers() {
        val map = mapLibreMap ?: return
        map.clear()
        for (job in jobList) {
            if (job.latitude != 0.0 && job.longitude != 0.0) {
                map.addMarker(
                    MarkerOptions()
                        .position(LatLng(job.latitude, job.longitude))
                        .title(job.jobTitle)
                        .snippet("₱${job.offeredAmount}")
                )
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
                        }
                    }
            }
    }

    // --------------------------------------------------
    // CLEANUP & LIFECYCLE
    // --------------------------------------------------
    override fun onStart() { super.onStart(); mapView.onStart() }
    override fun onResume() { super.onResume(); mapView.onResume() }
    override fun onPause() { super.onPause(); mapView.onPause() }
    override fun onStop() { super.onStop(); mapView.onStop() }
    override fun onLowMemory() { super.onLowMemory(); mapView.onLowMemory() }
    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        mapView.onSaveInstanceState(outState)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        mapView.onDestroy()
        listener?.remove()
    }
}