package com.example.newtacks.worker

import android.os.Bundle
import android.view.*
import android.widget.*
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.example.newtacks.R
import com.example.newtacks.models.HiringPost
import com.google.android.material.tabs.TabLayout
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import coil.load
import android.widget.ImageView
import android.view.View
import android.widget.LinearLayout
import com.example.newtacks.utils.ImageUtils

class WorkerHiringFragment : Fragment() {

    private lateinit var rvHiring: RecyclerView
    private lateinit var tabLayout: TabLayout
    private lateinit var swipeRefresh: SwipeRefreshLayout
    private lateinit var layoutHeader: LinearLayout
    private lateinit var layoutEmptyState: LinearLayout
    private lateinit var tvEmptyTitle: TextView
    private lateinit var tvEmptyDesc: TextView

    private val db = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()
    private var hiringListener: ListenerRegistration? = null
    
    private val fullHiringList = mutableListOf<HiringPost>()
    private val displayList = mutableListOf<HiringPost>()
    private lateinit var adapter: HiringAdapter

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val view = inflater.inflate(R.layout.fragment_worker_hiring, container, false)

        rvHiring         = view.findViewById(R.id.rvHiring)
        tabLayout        = view.findViewById(R.id.tabLayoutHiring)
        swipeRefresh     = view.findViewById(R.id.swipeRefreshHiring)
        layoutHeader     = view.findViewById(R.id.layoutHeader)
        layoutEmptyState = view.findViewById(R.id.layoutEmptyState)
        tvEmptyTitle     = view.findViewById(R.id.tvEmptyTitle)
        tvEmptyDesc      = view.findViewById(R.id.tvEmptyDesc)

        rvHiring.layoutManager = LinearLayoutManager(requireContext())
        adapter = HiringAdapter(displayList, auth.currentUser?.uid) { post ->
            showHiringPreview(post)
        }
        rvHiring.adapter = adapter

        tabLayout.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab?) {
                filterList()
            }
            override fun onTabUnselected(tab: TabLayout.Tab?) {}
            override fun onTabReselected(tab: TabLayout.Tab?) {}
        })

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

        listenForHiringPosts()

        swipeRefresh.setOnRefreshListener {
            listenForHiringPosts()
        }

        return view
    }

    private fun listenForHiringPosts() {
        hiringListener = db.collection("hiring")
            .whereEqualTo("status", "OPEN")
            .addSnapshotListener { snapshots, _ ->
                if (snapshots == null) return@addSnapshotListener
                val now = System.currentTimeMillis()
                fullHiringList.clear()
                for (doc in snapshots) {
                    val post = doc.toObject(HiringPost::class.java)
                    if (post.expiresAt == 0L || post.expiresAt > now) {
                        fullHiringList.add(post)
                    }
                }
                swipeRefresh.isRefreshing = false
                filterList()
            }
    }

    private fun filterList() {
        val uid = auth.currentUser?.uid ?: ""
        displayList.clear()
        
        if (tabLayout.selectedTabPosition == 0) {
            // Available
            displayList.addAll(fullHiringList)
            tvEmptyTitle.text = "No Available Hiring"
            tvEmptyDesc.text = "Companies are not posting yet. Check back later."
        } else {
            // Applied
            displayList.addAll(fullHiringList.filter { it.applicants.contains(uid) })
            tvEmptyTitle.text = "No Applications Sent"
            tvEmptyDesc.text = "You haven't applied to any companies yet."
        }
        
        adapter.notifyDataSetChanged()
        layoutEmptyState.visibility = if (displayList.isEmpty()) View.VISIBLE else View.GONE
        rvHiring.visibility = if (displayList.isEmpty()) View.GONE else View.VISIBLE
    }

    private fun showHiringPreview(post: HiringPost) {
        val intent = android.content.Intent(requireContext(), com.example.newtacks.company.HiringDetailsActivity::class.java)
        intent.putExtra("HIRING_POST_JSON", com.google.gson.Gson().toJson(post))
        startActivity(intent)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        hiringListener?.remove()
    }
}
