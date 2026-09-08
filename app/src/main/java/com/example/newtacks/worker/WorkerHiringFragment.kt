package com.example.newtacks.worker

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.*
import android.widget.*
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.example.newtacks.R
import com.example.newtacks.models.Application
import com.example.newtacks.models.HiringPost
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.gson.Gson

class WorkerHiringFragment : Fragment() {

    private lateinit var rvHiring: RecyclerView
    private lateinit var swipeRefresh: SwipeRefreshLayout
    private lateinit var layoutHeader: View
    private lateinit var layoutEmptyState: LinearLayout
    private lateinit var tvEmptyTitle: TextView
    private lateinit var tvEmptyDesc: TextView
    
    private lateinit var tabAvailable: TextView
    private lateinit var tabMyApplications: TextView
    private lateinit var etSearch: EditText
    
    private lateinit var layoutSearch: View
    private lateinit var layoutFilterStatus: View

    private val db = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()
    
    private var hiringListener: ListenerRegistration? = null
    private var applicationsListener: ListenerRegistration? = null
    
    private val allHiringPosts = mutableListOf<HiringPost>()
    private val myApplications = mutableListOf<Application>()
    
    private lateinit var postsAdapter: HiringAdapter
    private lateinit var applicationsAdapter: WorkerApplicationAdapter
    
    private var currentTab = "AVAILABLE" // AVAILABLE, MY_APPS

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val view = inflater.inflate(R.layout.fragment_worker_hiring, container, false)

        rvHiring           = view.findViewById(R.id.rvHiring)
        swipeRefresh       = view.findViewById(R.id.swipeRefreshHiring)
        layoutHeader       = view.findViewById(R.id.layoutHeader)
        layoutEmptyState   = view.findViewById(R.id.layoutEmptyState)
        tvEmptyTitle       = view.findViewById(R.id.tvEmptyTitle)
        tvEmptyDesc        = view.findViewById(R.id.tvEmptyDesc)
        
        tabAvailable       = view.findViewById(R.id.tabAvailable)
        tabMyApplications  = view.findViewById(R.id.tabMyApplications)
        etSearch           = view.findViewById(R.id.etSearchKeywords)
        
        layoutSearch       = view.findViewById(R.id.layoutSearch)
        layoutFilterStatus = view.findViewById(R.id.layoutFilterStatus)

        setupAdapters()
        setupTabs()
        setupSearch()

        // Robust Inset Handling: Use spacer for status bar
        val statusBarSpacer = view.findViewById<View>(R.id.statusBarSpacer)
        ViewCompat.setOnApplyWindowInsetsListener(view) { _, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            val params = statusBarSpacer.layoutParams
            params.height = systemBars.top
            statusBarSpacer.layoutParams = params
            insets
        }

        view.findViewById<View>(R.id.btnFilterStatus).setOnClickListener {
            Toast.makeText(requireContext(), "Filter logic coming soon!", Toast.LENGTH_SHORT).show()
        }

        listenForData()

        swipeRefresh.setOnRefreshListener {
            view.findViewById<View>(R.id.btnFilterStatus).setOnClickListener {
            Toast.makeText(requireContext(), "Filter logic coming soon!", Toast.LENGTH_SHORT).show()
        }

        listenForData()
        }

        return view
    }

    private fun setupAdapters() {
        postsAdapter = HiringAdapter(mutableListOf(), auth.currentUser?.uid) { post ->
            showHiringPreview(post)
        }
        
        applicationsAdapter = WorkerApplicationAdapter(mutableListOf()) { app ->
            fetchPostAndOpen(app.hiringId)
        }
        
        rvHiring.layoutManager = LinearLayoutManager(requireContext())
        rvHiring.adapter = postsAdapter
    }

    private fun setupTabs() {
        tabAvailable.setOnClickListener { switchTab("AVAILABLE") }
        tabMyApplications.setOnClickListener { switchTab("MY_APPS") }
    }

    private fun switchTab(tab: String) {
        currentTab = tab
        
        // Reset Styles
        tabAvailable.background = null
        tabAvailable.setTextColor(Color.parseColor("#64748B"))
        tabAvailable.paint.isFakeBoldText = false
        
        tabMyApplications.background = null
        tabMyApplications.setTextColor(Color.parseColor("#64748B"))
        tabMyApplications.paint.isFakeBoldText = false

        when (tab) {
            "AVAILABLE" -> {
                tabAvailable.setBackgroundResource(R.drawable.bg_tab_selected)
                tabAvailable.backgroundTintList = android.content.res.ColorStateList.valueOf(Color.parseColor("#EBF2FF"))
                tabAvailable.setTextColor(Color.parseColor("#0F325E"))
                tabAvailable.paint.isFakeBoldText = true
                rvHiring.adapter = postsAdapter
                layoutSearch.visibility = View.VISIBLE
                layoutFilterStatus.visibility = View.GONE
            }
            "MY_APPS" -> {
                tabMyApplications.setBackgroundResource(R.drawable.bg_tab_selected)
                tabMyApplications.backgroundTintList = android.content.res.ColorStateList.valueOf(Color.parseColor("#EBF2FF"))
                tabMyApplications.setTextColor(Color.parseColor("#0F325E"))
                tabMyApplications.paint.isFakeBoldText = true
                rvHiring.adapter = applicationsAdapter
                layoutSearch.visibility = View.GONE
                layoutFilterStatus.visibility = View.VISIBLE
            }
        }
        
        filterAndDisplay()
    }

    private fun setupSearch() {
        etSearch.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                filterAndDisplay()
            }
            override fun afterTextChanged(s: Editable?) {}
        })
    }

    private fun listenForData() {
        val uid = auth.currentUser?.uid ?: ""
        
        // 1. Listen for Hiring Posts
        hiringListener?.remove()
        hiringListener = db.collection("hiring")
            .whereEqualTo("status", "OPEN")
            .addSnapshotListener { snapshots, _ ->
                allHiringPosts.clear()
                snapshots?.forEach { doc ->
                    val post = doc.toObject(HiringPost::class.java).copy(hiringId = doc.id)
                    allHiringPosts.add(post)
                }
                swipeRefresh.isRefreshing = false
                filterAndDisplay()
            }
            
        // 2. Listen for All My Applications
        applicationsListener?.remove()
        applicationsListener = db.collection("applications")
            .whereEqualTo("workerId", uid)
            .addSnapshotListener { snapshots, _ ->
                myApplications.clear()
                snapshots?.toObjects(Application::class.java)?.let {
                    myApplications.addAll(it)
                }
                filterAndDisplay()
            }
    }

    private fun filterAndDisplay() {
        val query = etSearch.text.toString().trim().lowercase()
        
        if (currentTab == "AVAILABLE") {
            val filtered = allHiringPosts.filter { 
                it.jobTitle.lowercase().contains(query) || it.companyName.lowercase().contains(query)
            }
            postsAdapter.updateData(filtered)
            updateEmptyState(filtered.isEmpty())
        } else {
            val filtered = myApplications.filter {
                it.jobTitle.lowercase().contains(query)
            }
            applicationsAdapter.updateData(filtered)
            updateEmptyState(filtered.isEmpty())
        }
    }

    private fun updateEmptyState(isEmpty: Boolean) {
        layoutEmptyState.visibility = if (isEmpty) View.VISIBLE else View.GONE
        rvHiring.visibility = if (isEmpty) View.GONE else View.VISIBLE
        
        if (isEmpty) {
            if (currentTab == "AVAILABLE") {
                tvEmptyTitle.text = "No Available Hiring"
                tvEmptyDesc.text = "Check back later for new opportunities."
            } else {
                tvEmptyTitle.text = "No Applications"
                tvEmptyDesc.text = "You haven't applied to any jobs yet."
            }
        }
    }

    private fun showHiringPreview(post: HiringPost) {
        val intent = Intent(requireContext(), com.example.newtacks.company.HiringDetailsActivity::class.java)
        intent.putExtra("HIRING_POST_JSON", Gson().toJson(post))
        startActivity(intent)
    }

    private fun fetchPostAndOpen(hiringId: String) {
        db.collection("hiring").document(hiringId).get().addOnSuccessListener { doc ->
            val post = doc.toObject(HiringPost::class.java)?.copy(hiringId = doc.id)
            if (post != null) {
                showHiringPreview(post)
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        hiringListener?.remove()
        applicationsListener?.remove()
    }
}
