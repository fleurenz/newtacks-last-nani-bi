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
    
    private var postsListener: ListenerRegistration? = null
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
        loadProfile()
        listenForActivePosts()
        updateAgendaCount()

        view.findViewById<View>(R.id.btnCreateHiringMain).setOnClickListener {
            startActivity(Intent(requireContext(), CreateHiringActivity::class.java))
        }

        view.findViewById<View>(R.id.btnViewInterviews).setOnClickListener {
            // Placeholder for viewing interviews
        }

        swipeRefresh.setOnRefreshListener {
            loadProfile()
            listenForActivePosts()
            updateAgendaCount()
        }

        return view
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

    private fun loadProfile() {
        val uid = auth.currentUser?.uid ?: return
        db.collection("users").document(uid).get().addOnSuccessListener { doc ->
            val user = doc.toObject(User::class.java) ?: return@addOnSuccessListener
            tvCompanyName.text = user.companyName ?: user.name
            if (user.profileImage.isNotEmpty()) {
                ivCompanyProfile.load(user.profileImage) {
                    crossfade(true)
                    placeholder(R.drawable.ic_person_placeholder)
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
            .limit(3) // Only show top 3 on home
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

    private fun updateAgendaCount() {
        val uid = auth.currentUser?.uid ?: return
        db.collection("applications")
            .whereEqualTo("companyId", uid)
            .whereEqualTo("status", "INTERVIEW_SCHEDULED")
            .get()
            .addOnSuccessListener { snapshots ->
                val count = snapshots.size()
                tvInterviewCount.text = "Interviews Today ($count)"
            }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        postsListener?.remove()
    }
}
