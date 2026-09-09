package com.example.newtacks.company

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.example.newtacks.R
import com.example.newtacks.models.Application
import com.example.newtacks.models.HiringPost
import com.example.newtacks.models.User
import com.example.newtacks.utils.NotificationHelper
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.gson.Gson
import com.google.android.material.bottomsheet.BottomSheetDialog

class CompanyApplicantsFragment : Fragment() {

    private val db = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()

    private lateinit var rvApplicants: RecyclerView
    private lateinit var swipeRefresh: SwipeRefreshLayout
    private lateinit var tabAll: TextView
    private lateinit var tabNew: TextView
    private lateinit var tabInterview: TextView
    private lateinit var tabHired: TextView
    
    private lateinit var layoutActiveFilter: View
    private lateinit var tvActiveFilterName: TextView
    private lateinit var btnClearFilter: View

    private var appsListener: ListenerRegistration? = null
    private val allData = mutableListOf<Pair<User, Application>>()
    private val allHiringPosts = mutableListOf<HiringPost>()
    private lateinit var adapter: CompanyApplicantFullAdapter
    
    private var currentTab = "ALL"
    private var selectedJobFilterId: String? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val view = inflater.inflate(R.layout.fragment_company_applicants, container, false)
        
        rvApplicants = view.findViewById(R.id.rvApplicants)
        swipeRefresh = view.findViewById(R.id.swipeRefreshApplicants)
        tabAll = view.findViewById(R.id.tabAll)
        tabNew = view.findViewById(R.id.tabNew)
        tabInterview = view.findViewById(R.id.tabInterview)
        tabHired = view.findViewById(R.id.tabHired)
        
        layoutActiveFilter = view.findViewById(R.id.layoutActiveFilter)
        tvActiveFilterName = view.findViewById(R.id.tvActiveFilterName)
        btnClearFilter     = view.findViewById(R.id.btnClearFilter)

        // Robust Inset Handling: Use spacer for status bar
        val statusBarSpacer = view.findViewById<View>(R.id.statusBarSpacer)
        ViewCompat.setOnApplyWindowInsetsListener(view) { _, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            val params = statusBarSpacer.layoutParams
            params.height = systemBars.top
            statusBarSpacer.layoutParams = params
            insets
        }

        view.findViewById<View>(R.id.btnNotifications).setOnClickListener {
            NotificationHelper.showNotificationDialog(requireContext())
        }

        setupRecyclerView()
        setupTabs()

        view.findViewById<View>(R.id.btnFilter).setOnClickListener {
            showFilterDialog()
        }

        btnClearFilter.setOnClickListener {
            clearJobFilter()
        }

        listenForApplications()
        fetchHiringPosts()

        swipeRefresh.setOnRefreshListener {
            listenForApplications()
        }

        return view
    }

    private fun setupRecyclerView() {
        adapter = CompanyApplicantFullAdapter(emptyList(), { user, app ->
            // On Item Click: Open Job Details
            fetchPostAndOpen(app.hiringId)
        }, { user ->
            // On "View Profile" Label Click: Open Full Profile directly
            val intent = Intent(requireContext(), com.example.newtacks.worker.WorkerProfileActivity::class.java)
            intent.putExtra("WORKER_ID", user.uid)
            startActivity(intent)
        })
        rvApplicants.layoutManager = LinearLayoutManager(requireContext())
        rvApplicants.adapter = adapter
    }

    private fun fetchPostAndOpen(hiringId: String) {
        db.collection("hiring").document(hiringId).get().addOnSuccessListener { doc ->
            val post = doc.toObject(HiringPost::class.java)?.copy(hiringId = doc.id)
            if (post != null) {
                val intent = Intent(requireContext(), HiringDetailsActivity::class.java)
                intent.putExtra("HIRING_POST_JSON", Gson().toJson(post))
                intent.putExtra("FOCUS_TAB", 2) // Tell it to open Applicants tab
                startActivity(intent)
            }
        }
    }

    private fun setupTabs() {
        tabAll.setOnClickListener { switchTab("ALL") }
        tabNew.setOnClickListener { switchTab("NEW") }
        tabInterview.setOnClickListener { switchTab("INTERVIEW") }
        tabHired.setOnClickListener { switchTab("HIRED") }
    }

    fun selectTab(tab: String) {
        switchTab(tab)
    }

    private fun switchTab(tab: String) {
        currentTab = tab
        
        // Reset all
        val tabs = listOf(tabAll, tabNew, tabInterview, tabHired)
        tabs.forEach { 
            it.background = null
            it.setTextColor(Color.parseColor("#64748B"))
            it.paint.isFakeBoldText = false
        }

        when (tab) {
            "ALL" -> {
                tabAll.setBackgroundResource(R.drawable.bg_tab_selected)
                tabAll.setTextColor(Color.parseColor("#1E293B"))
                tabAll.paint.isFakeBoldText = true
            }
            "NEW" -> {
                tabNew.setBackgroundResource(R.drawable.bg_tab_selected)
                tabNew.setTextColor(Color.parseColor("#1E293B"))
                tabNew.paint.isFakeBoldText = true
            }
            "INTERVIEW" -> {
                tabInterview.setBackgroundResource(R.drawable.bg_tab_selected)
                tabInterview.setTextColor(Color.parseColor("#1E293B"))
                tabInterview.paint.isFakeBoldText = true
            }
            "HIRED" -> {
                tabHired.setBackgroundResource(R.drawable.bg_tab_selected)
                tabHired.setTextColor(Color.parseColor("#1E293B"))
                tabHired.paint.isFakeBoldText = true
            }
        }
        
        filterAndDisplay()
    }

    private fun listenForApplications() {
        val uid = auth.currentUser?.uid ?: return
        
        appsListener?.remove()
        appsListener = db.collection("applications")
            .whereEqualTo("companyId", uid)
            .addSnapshotListener { snapshots, error ->
                swipeRefresh.isRefreshing = false
                if (error != null || snapshots == null) return@addSnapshotListener
                
                val applications = snapshots.toObjects(Application::class.java)
                if (applications.isEmpty()) {
                    allData.clear()
                    filterAndDisplay()
                    return@addSnapshotListener
                }

                // Fetch user profiles for all applicants
                val workerIds = applications.map { it.workerId }.distinct()
                
                // Firestore whereIn limit is 10. For simplicity in this update, we take first 10.
                // In production, we'd chunk this.
                db.collection("users")
                    .whereIn(com.google.firebase.firestore.FieldPath.documentId(), workerIds.take(10))
                    .get()
                    .addOnSuccessListener { userSnapshots ->
                        val userMap = userSnapshots.documents.associate { 
                            it.id to it.toObject(User::class.java)!!.copy(uid = it.id)
                        }
                        
                        allData.clear()
                        applications.forEach { app ->
                            userMap[app.workerId]?.let { user ->
                                allData.add(user to app)
                            }
                        }
                        filterAndDisplay()
                    }
            }
    }

    private fun filterAndDisplay() {
        var filtered = when (currentTab) {
            "NEW"       -> allData.filter { it.second.status == "APPLIED" }
            "INTERVIEW" -> allData.filter { it.second.status == "INTERVIEW_SCHEDULED" }
            "HIRED"     -> allData.filter { it.second.status == "HIRED" }
            else        -> allData
        }
        
        // Apply Job Posting Filter
        selectedJobFilterId?.let { jobId ->
            filtered = filtered.filter { it.second.hiringId == jobId }
        }

        adapter.updateData(filtered)
    }

    private fun fetchHiringPosts() {
        val uid = auth.currentUser?.uid ?: return
        db.collection("hiring")
            .whereEqualTo("companyId", uid)
            .get()
            .addOnSuccessListener { snapshots ->
                allHiringPosts.clear()
                snapshots.documents.forEach { doc ->
                    doc.toObject(HiringPost::class.java)?.let { post ->
                        allHiringPosts.add(post.copy(hiringId = doc.id))
                    }
                }
            }
    }

    private fun showFilterDialog() {
        val dialog = BottomSheetDialog(requireContext(), R.style.BottomSheetDialogTheme)
        val dialogView = layoutInflater.inflate(R.layout.dialog_filter_job_postings, null, false)
        dialog.setContentView(dialogView)

        val rvFilter = dialogView.findViewById<RecyclerView>(R.id.rvFilterJobs)
        val btnApply = dialogView.findViewById<View>(R.id.btnApplyFilter)

        // Add "All posts" option
        val options = mutableListOf<HiringPost?>()
        options.add(null) // Represents "All posts"
        options.addAll(allHiringPosts)

        var tempSelectedId = selectedJobFilterId

        rvFilter.layoutManager = LinearLayoutManager(requireContext())
        rvFilter.adapter = object : RecyclerView.Adapter<RecyclerView.ViewHolder>() {
            override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
                val v = layoutInflater.inflate(R.layout.item_filter_job, parent, false)
                return object : RecyclerView.ViewHolder(v) {}
            }

            override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
                val post = options[position]
                val tv = holder.itemView.findViewById<TextView>(R.id.tvJobTitle)
                
                tv.text = post?.jobTitle ?: "All posts"
                
                // Highlight selection
                val isSelected = if (post == null) tempSelectedId == null else post.hiringId == tempSelectedId
                if (isSelected) {
                    tv.setBackgroundResource(R.drawable.bg_tab_selected)
                    tv.setTextColor(Color.parseColor("#1C6EC6"))
                    tv.paint.isFakeBoldText = true
                } else {
                    tv.background = null
                    tv.setTextColor(Color.parseColor("#1E293B"))
                    tv.paint.isFakeBoldText = false
                }

                tv.setOnClickListener {
                    tempSelectedId = post?.hiringId
                    notifyDataSetChanged()
                }
            }

            override fun getItemCount() = options.size
        }

        btnApply.setOnClickListener {
            selectedJobFilterId = tempSelectedId
            updateFilterUI()
            filterAndDisplay()
            dialog.dismiss()
        }

        dialog.show()
    }

    private fun updateFilterUI() {
        if (selectedJobFilterId == null) {
            layoutActiveFilter.visibility = View.GONE
        } else {
            layoutActiveFilter.visibility = View.VISIBLE
            val job = allHiringPosts.find { it.hiringId == selectedJobFilterId }
            tvActiveFilterName.text = job?.jobTitle ?: "Filtered Post"
        }
    }

    private fun clearJobFilter() {
        selectedJobFilterId = null
        updateFilterUI()
        filterAndDisplay()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        appsListener?.remove()
    }
}
