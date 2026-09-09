package com.example.newtacks.company

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import coil.load
import coil.transform.CircleCropTransformation
import com.example.newtacks.utils.NotificationHelper
import com.example.newtacks.R
import com.example.newtacks.models.HiringPost
import com.example.newtacks.models.User
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.gson.Gson

class CompanyHomeFragment : Fragment() {

    private val db = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()
    
    private lateinit var tvCompanyName: TextView
    private lateinit var ivCompanyProfile: ImageView
    private lateinit var rvActivePosts: RecyclerView
    private lateinit var swipeRefresh: SwipeRefreshLayout
    private lateinit var tvInterviewCount: TextView
    
    private var profileListener: ListenerRegistration? = null
    private var postsListener: ListenerRegistration? = null
    private var agendaListener: ListenerRegistration? = null
    private val activePosts = mutableListOf<HiringPost>()
    private lateinit var adapter: CompanyPostAdapter

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val view = inflater.inflate(R.layout.fragment_company_home, container, false)

        tvCompanyName = view.findViewById(R.id.tvCompanyName)
        ivCompanyProfile = view.findViewById(R.id.ivCompanyProfile)
        rvActivePosts = view.findViewById(R.id.rvActivePostsHome)
        swipeRefresh = view.findViewById(R.id.swipeRefreshHome)
        tvInterviewCount = view.findViewById(R.id.tvInterviewCount)

        view.findViewById<View>(R.id.btnNotifications).setOnClickListener {
            NotificationHelper.showNotificationDialog(requireContext())
        }

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
        listenForProfile()
        listenForActivePosts()
        listenForAgenda()

        view.findViewById<View>(R.id.btnCreateHiringMain).setOnClickListener {
            startActivity(Intent(requireContext(), CreateHiringActivity::class.java))
        }

        view.findViewById<View>(R.id.btnViewInterviews).setOnClickListener {
            (activity as? com.example.newtacks.CompanyDashboardActivity)?.switchToApplicants("INTERVIEW")
        }

        view.findViewById<View>(R.id.btnSeeAllPosts).setOnClickListener {
            (activity as? com.example.newtacks.CompanyDashboardActivity)?.switchToPosts()
        }

        swipeRefresh.setOnRefreshListener {
            refreshData()
        }

        return view
    }

    override fun onHiddenChanged(hidden: Boolean) {
        super.onHiddenChanged(hidden)
        if (!hidden) {
            refreshData()
        }
    }

    private fun refreshData() {
        listenForProfile()
        listenForActivePosts()
        listenForAgenda()
        swipeRefresh.isRefreshing = false
    }

    private fun setupRecyclerView() {
        adapter = CompanyPostAdapter(emptyList(), { post ->
            val intent = Intent(requireContext(), CreateHiringActivity::class.java)
            intent.putExtra("EDIT_POST_JSON", Gson().toJson(post))
            startActivity(intent)
        }, { post ->
            val intent = Intent(requireContext(), HiringDetailsActivity::class.java)
            intent.putExtra("HIRING_POST_JSON", Gson().toJson(post))
            startActivity(intent)
        })
        rvActivePosts.layoutManager = LinearLayoutManager(requireContext())
        rvActivePosts.adapter = adapter
    }

    private fun listenForProfile() {
        val uid = auth.currentUser?.uid ?: return
        
        // Show placeholder immediately while loading
        ivCompanyProfile.setImageResource(R.drawable.ic_person_placeholder)
        
        profileListener?.remove()
        profileListener = db.collection("users").document(uid).addSnapshotListener { snapshot, _ ->
            val user = snapshot?.toObject(User::class.java) ?: return@addSnapshotListener
            
            tvCompanyName.text = user.companyName ?: user.name
            
            if (user.profileImage.isNotEmpty()) {
                ivCompanyProfile.load(user.profileImage) {
                    crossfade(true)
                    placeholder(R.drawable.ic_person_placeholder)
                    error(R.drawable.ic_person_placeholder)
                    transformations(CircleCropTransformation())
                }
            }
        }
    }

    private fun listenForActivePosts() {
        val uid = auth.currentUser?.uid ?: return
        postsListener?.remove()
        postsListener = db.collection("hiring")
            .whereEqualTo("companyId", uid)
            .whereEqualTo("status", "OPEN")
            .limit(2) // Only show top 2 on home
            .addSnapshotListener { snapshots, _ ->
                swipeRefresh.isRefreshing = false
                activePosts.clear()
                snapshots?.documents?.forEach { doc ->
                    doc.toObject(HiringPost::class.java)?.let {
                        activePosts.add(it.copy(hiringId = doc.id))
                    }
                }
                adapter.updateData(activePosts)
            }
    }

    private fun listenForAgenda() {
        val uid = auth.currentUser?.uid ?: return
        
        // Calculate the time range for "Today"
        val calendar = java.util.Calendar.getInstance()
        calendar.set(java.util.Calendar.HOUR_OF_DAY, 0)
        calendar.set(java.util.Calendar.MINUTE, 0)
        calendar.set(java.util.Calendar.SECOND, 0)
        val startTime = calendar.timeInMillis
        
        calendar.set(java.util.Calendar.HOUR_OF_DAY, 23)
        calendar.set(java.util.Calendar.MINUTE, 59)
        calendar.set(java.util.Calendar.SECOND, 59)
        val endTime = calendar.timeInMillis

        agendaListener?.remove()
        agendaListener = db.collection("applications")
            .whereEqualTo("companyId", uid)
            .whereEqualTo("status", "INTERVIEW_SCHEDULED")
            .whereGreaterThanOrEqualTo("interviewDate", startTime)
            .whereLessThanOrEqualTo("interviewDate", endTime)
            .addSnapshotListener { snapshots, _ ->
                val count = snapshots?.size() ?: 0
                tvInterviewCount.text = "Interviews Today ($count)"
            }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        profileListener?.remove()
        postsListener?.remove()
        agendaListener?.remove()
    }
}
