package com.example.newtacks.company

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.example.newtacks.R
import com.example.newtacks.models.HiringPost
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.gson.Gson

class CompanyHiringFragment : Fragment() {

    private val firestore = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()
    
    private lateinit var rvJobPosts: RecyclerView
    private lateinit var swipeRefresh: SwipeRefreshLayout
    private lateinit var tabActive: TextView
    private lateinit var tabDraft: TextView
    private lateinit var tabClosed: TextView
    private lateinit var btnFilter: ImageView
    
    private var postsListener: ListenerRegistration? = null
    private val allPosts = mutableListOf<HiringPost>()
    private lateinit var adapter: CompanyPostAdapter
    
    private var currentTab = "ACTIVE"
    private var isNewestFirst = true

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val view = inflater.inflate(R.layout.fragment_company_hiring, container, false)
        
        rvJobPosts   = view.findViewById(R.id.rvJobPosts)
        swipeRefresh = view.findViewById(R.id.swipeRefreshPosts)
        tabActive    = view.findViewById(R.id.tabActive)
        tabDraft     = view.findViewById(R.id.tabDraft)
        tabClosed    = view.findViewById(R.id.tabClosed)
        btnFilter    = view.findViewById(R.id.btnFilter)
        
        // Robust Inset Handling: Use spacer for status bar
        val statusBarSpacer = view.findViewById<View>(R.id.statusBarSpacer)
        ViewCompat.setOnApplyWindowInsetsListener(view) { _, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            val params = statusBarSpacer.layoutParams
            params.height = systemBars.top
            statusBarSpacer.layoutParams = params
            insets
        }
        
        setupRecyclerView()
        setupTabs()
        
        btnFilter.setOnClickListener {
            toggleSorting()
        }
        
        view.findViewById<View>(R.id.btnCreateJobPosting).setOnClickListener {
            startActivity(Intent(requireContext(), CreateHiringActivity::class.java))
        }

        listenForPosts()

        swipeRefresh.setOnRefreshListener {
            listenForPosts()
        }
        
        return view
    }

    private fun setupRecyclerView() {
        adapter = CompanyPostAdapter(emptyList(), { post ->
            // On Edit Click
            val intent = Intent(requireContext(), CreateHiringActivity::class.java)
            intent.putExtra("EDIT_POST_JSON", Gson().toJson(post))
            startActivity(intent)
        }, { post ->
            // On Item Click
            val intent = Intent(requireContext(), HiringDetailsActivity::class.java)
            intent.putExtra("HIRING_POST_JSON", Gson().toJson(post))
            startActivity(intent)
        })
        rvJobPosts.layoutManager = LinearLayoutManager(requireContext())
        rvJobPosts.adapter = adapter
    }

    private fun setupTabs() {
        tabActive.setOnClickListener { switchTab("ACTIVE") }
        tabDraft.setOnClickListener { switchTab("DRAFT") }
        tabClosed.setOnClickListener { switchTab("CLOSED") }
    }

    private fun switchTab(tab: String) {
        currentTab = tab
        
        // Update UI (Pill Style)
        val tabs = listOf(tabActive, tabDraft, tabClosed)
        tabs.forEach { 
            it.background = null
            it.setTextColor(Color.parseColor("#64748B"))
            it.paint.isFakeBoldText = false
        }

        when (tab) {
            "ACTIVE" -> {
                tabActive.setBackgroundResource(R.drawable.bg_tab_selected)
                tabActive.setTextColor(Color.parseColor("#1E293B"))
                tabActive.paint.isFakeBoldText = true
            }
            "DRAFT" -> {
                tabDraft.setBackgroundResource(R.drawable.bg_tab_selected)
                tabDraft.setTextColor(Color.parseColor("#1E293B"))
                tabDraft.paint.isFakeBoldText = true
            }
            "CLOSED" -> {
                tabClosed.setBackgroundResource(R.drawable.bg_tab_selected)
                tabClosed.setTextColor(Color.parseColor("#1E293B"))
                tabClosed.paint.isFakeBoldText = true
            }
        }
        
        filterAndDisplayPosts()
    }

    private fun listenForPosts() {
        val uid = auth.currentUser?.uid ?: return
        
        postsListener?.remove()
        postsListener = firestore.collection("hiring")
            .whereEqualTo("companyId", uid)
            .addSnapshotListener { snapshots, error ->
                swipeRefresh.isRefreshing = false
                if (error != null) return@addSnapshotListener
                
                allPosts.clear()
                snapshots?.documents?.forEach { doc ->
                    doc.toObject(HiringPost::class.java)?.let { post ->
                        allPosts.add(post.copy(hiringId = doc.id))
                    }
                }
                filterAndDisplayPosts()
            }
    }

    private fun toggleSorting() {
        isNewestFirst = !isNewestFirst
        
        // Update Icon
        if (isNewestFirst) {
            btnFilter.setImageResource(R.drawable.ic_sort_latest)
        } else {
            btnFilter.setImageResource(R.drawable.ic_sort_oldest)
        }

        val message = if (isNewestFirst) "Sorting: Latest First" else "Sorting: Oldest First"
        Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show()
        filterAndDisplayPosts()
    }

    private fun filterAndDisplayPosts() {
        var filtered = when (currentTab) {
            "ACTIVE" -> allPosts.filter { it.status == "OPEN" }
            "DRAFT"  -> allPosts.filter { it.status == "DRAFT" }
            "CLOSED" -> allPosts.filter { it.status == "CLOSED" || it.status == "EXPIRED" }
            else     -> allPosts
        }

        // Apply Sorting
        filtered = if (isNewestFirst) {
            filtered.sortedByDescending { it.createdAt }
        } else {
            filtered.sortedBy { it.createdAt }
        }

        adapter.updateData(filtered)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        postsListener?.remove()
    }
}
