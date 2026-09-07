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
import com.example.newtacks.models.Application
import com.example.newtacks.models.HiringPost
import com.google.android.material.tabs.TabLayout
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration

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
    private var interviewListener: ListenerRegistration? = null
    private var hiredListener: ListenerRegistration? = null
    
    private val fullHiringList = mutableListOf<HiringPost>()
    private val interviewList = mutableListOf<Application>()
    private val hiredList = mutableListOf<Application>()
    private val displayPosts = mutableListOf<HiringPost>()
    
    private lateinit var postsAdapter: HiringAdapter
    private lateinit var interviewAdapter: InterviewRequestAdapter
    private lateinit var hiredAdapter: HiredHistoryAdapter

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

        setupAdapters()

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

        listenForData()

        swipeRefresh.setOnRefreshListener {
            listenForData()
        }

        return view
    }

    private fun setupAdapters() {
        postsAdapter = HiringAdapter(displayPosts, auth.currentUser?.uid) { post ->
            showHiringPreview(post)
        }
        
        interviewAdapter = InterviewRequestAdapter(interviewList) { app, action ->
            handleInterviewAction(app, action)
        }

        hiredAdapter = HiredHistoryAdapter(hiredList)
        
        rvHiring.layoutManager = LinearLayoutManager(requireContext())
    }

    private fun listenForData() {
        val uid = auth.currentUser?.uid ?: ""
        
        // Listen for Hiring Posts
        hiringListener?.remove()
        hiringListener = db.collection("hiring")
            .whereEqualTo("status", "OPEN")
            .addSnapshotListener { snapshots, _ ->
                fullHiringList.clear()
                snapshots?.forEach { doc ->
                    val post = doc.toObject(HiringPost::class.java).copy(hiringId = doc.id)
                    if (post.expiresAt == 0L || post.expiresAt > System.currentTimeMillis()) {
                        fullHiringList.add(post)
                    }
                }
                swipeRefresh.isRefreshing = false
                filterList()
            }
            
        // Listen for Interviews
        interviewListener?.remove()
        interviewListener = db.collection("applications")
            .whereEqualTo("workerId", uid)
            .whereEqualTo("status", "INTERVIEW_SCHEDULED")
            .addSnapshotListener { snapshots, _ ->
                interviewList.clear()
                snapshots?.toObjects(Application::class.java)?.let {
                    interviewList.addAll(it)
                }
                filterList()
            }

        // Listen for Hired History
        hiredListener?.remove()
        hiredListener = db.collection("applications")
            .whereEqualTo("workerId", uid)
            .whereEqualTo("status", "HIRED")
            .addSnapshotListener { snapshots, _ ->
                hiredList.clear()
                snapshots?.toObjects(Application::class.java)?.let {
                    hiredList.addAll(it)
                }
                filterList()
            }
    }

    private fun filterList() {
        val uid = auth.currentUser?.uid ?: ""
        displayPosts.clear()
        
        when (tabLayout.selectedTabPosition) {
            0 -> { // Available
                rvHiring.adapter = postsAdapter
                displayPosts.addAll(fullHiringList)
                tvEmptyTitle.text = "No Available Hiring"
                tvEmptyDesc.text = "Check back later for new opportunities."
            }
            1 -> { // Applied
                rvHiring.adapter = postsAdapter
                displayPosts.addAll(fullHiringList.filter { it.applicants.contains(uid) })
                tvEmptyTitle.text = "No Applications Sent"
                tvEmptyDesc.text = "Apply to jobs from the Available tab."
            }
            2 -> { // Interviews
                rvHiring.adapter = interviewAdapter
                tvEmptyTitle.text = "No Interviews"
                tvEmptyDesc.text = "You'll see scheduled interviews here."
            }
            3 -> { // Hired
                rvHiring.adapter = hiredAdapter
                tvEmptyTitle.text = "No Hiring History"
                tvEmptyDesc.text = "Successfully hired jobs will appear here."
            }
        }
        
        postsAdapter.notifyDataSetChanged()
        interviewAdapter.notifyDataSetChanged()
        hiredAdapter.notifyDataSetChanged()
        
        val isEmpty = when (tabLayout.selectedTabPosition) {
            2 -> interviewList.isEmpty()
            3 -> hiredList.isEmpty()
            else -> displayPosts.isEmpty()
        }
        
        layoutEmptyState.visibility = if (isEmpty) View.VISIBLE else View.GONE
        rvHiring.visibility = if (isEmpty) View.GONE else View.VISIBLE
    }

    private fun handleInterviewAction(app: Application, action: String) {
        db.collection("applications").document(app.applicationId)
            .update("workerResponse", action)
            .addOnSuccessListener {
                val msg = if (action == "ACCEPTED") "Interview date accepted!" else "Reschedule request sent."
                Toast.makeText(requireContext(), msg, Toast.LENGTH_SHORT).show()
                
                com.example.newtacks.utils.NotificationHelper.sendNotification(
                    app.companyId,
                    "Interview Response",
                    "A worker has $action the interview schedule for ${app.jobTitle}.",
                    "APPLICANTS"
                )
            }
    }

    private fun showHiringPreview(post: HiringPost) {
        val intent = android.content.Intent(requireContext(), com.example.newtacks.company.HiringDetailsActivity::class.java)
        intent.putExtra("HIRING_POST_JSON", com.google.gson.Gson().toJson(post))
        startActivity(intent)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        hiringListener?.remove()
        interviewListener?.remove()
        hiredListener?.remove()
    }
}
