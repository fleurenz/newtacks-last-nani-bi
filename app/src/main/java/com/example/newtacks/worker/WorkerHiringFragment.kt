package com.example.newtacks.worker

import android.os.Bundle
import android.view.*
import android.widget.*
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.newtacks.R
import com.example.newtacks.models.HiringPost
import com.google.android.material.tabs.TabLayout
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration

class WorkerHiringFragment : Fragment() {

    private lateinit var rvHiring: RecyclerView
    private lateinit var tabLayout: TabLayout
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
        val view = layoutInflater.inflate(R.layout.dialog_job_preview, null)
        val tvTitle   = view.findViewById<TextView>(R.id.tvTitle)
        val tvDetails = view.findViewById<TextView>(R.id.tvDetails)
        val btnAccept = view.findViewById<Button>(R.id.btnAccept)
        val btnClose  = view.findViewById<Button>(R.id.btnClose)
        
        tvTitle.text = post.jobTitle
        tvDetails.text = """
            Company: ${post.companyName}
            Address: ${post.companyAddress}
            Daily Rate: ₱${post.dailyRate}
            Employment: ${post.employmentType}
            Services: ${post.serviceCategories.joinToString(", ")}
        """.trimIndent()

        val uid = auth.currentUser?.uid
        val hasApplied = uid != null && post.applicants.contains(uid)

        if (hasApplied) {
            btnAccept.text = "Already Applied"
            btnAccept.isEnabled = false
            btnAccept.alpha = 0.6f
        } else {
            btnAccept.text = "Apply Now"
            btnAccept.isEnabled = true
            btnAccept.alpha = 1.0f
        }

        view.findViewById<View>(R.id.tvImagesLabel).visibility = View.GONE
        view.findViewById<View>(R.id.scrollImages).visibility = View.GONE
        view.findViewById<View>(R.id.tvDuration).visibility = View.GONE
        
        val dialog = android.app.AlertDialog.Builder(requireContext())
            .setView(view)
            .create()

        btnClose.setOnClickListener { dialog.dismiss() }
        btnAccept.setOnClickListener {
            dialog.dismiss()
            applyForHiring(post)
        }

        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
        dialog.show()
    }

    private fun applyForHiring(post: HiringPost) {
        val uid = auth.currentUser?.uid ?: return
        db.collection("hiring").document(post.hiringId)
            .update("applicants", com.google.firebase.firestore.FieldValue.arrayUnion(uid))
            .addOnSuccessListener {
                Toast.makeText(requireContext(), "Application sent!", Toast.LENGTH_SHORT).show()
            }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        hiringListener?.remove()
    }
}
