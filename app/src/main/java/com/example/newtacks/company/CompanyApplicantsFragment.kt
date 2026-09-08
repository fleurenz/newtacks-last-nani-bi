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
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.gson.Gson

class CompanyApplicantsFragment : Fragment() {

    private val db = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()

    private lateinit var rvApplicants: RecyclerView
    private lateinit var swipeRefresh: SwipeRefreshLayout
    private lateinit var tabAll: TextView
    private lateinit var tabNew: TextView
    private lateinit var tabInterview: TextView
    private lateinit var tabHired: TextView

    private var appsListener: ListenerRegistration? = null
    private val allData = mutableListOf<Pair<User, Application>>()
    private lateinit var adapter: CompanyApplicantFullAdapter
    
    private var currentTab = "ALL"

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
            Toast.makeText(requireContext(), "No new notifications", Toast.LENGTH_SHORT).show()
        }

        setupRecyclerView()
        setupTabs()

        listenForApplications()

        swipeRefresh.setOnRefreshListener {
            listenForApplications()
        }

        return view
    }

    private fun setupRecyclerView() {
        adapter = CompanyApplicantFullAdapter(emptyList()) { user, app ->
            // On Item Click: Open the Job Details activity but focused on the applicants tab
            // For now, let's just open the HiringDetailsActivity for that specific post.
            // We'll need the HiringPost object. 
            fetchPostAndOpen(app.hiringId)
        }
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
                    .whereIn("uid", workerIds.take(10))
                    .get()
                    .addOnSuccessListener { userSnapshots ->
                        val userMap = userSnapshots.documents.associate { 
                            it.id to it.toObject(User::class.java)!!
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
        val filtered = when (currentTab) {
            "NEW"       -> allData.filter { it.second.status == "APPLIED" }
            "INTERVIEW" -> allData.filter { it.second.status == "INTERVIEW_SCHEDULED" }
            "HIRED"     -> allData.filter { it.second.status == "HIRED" }
            else        -> allData
        }
        adapter.updateData(filtered)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        appsListener?.remove()
    }
}
