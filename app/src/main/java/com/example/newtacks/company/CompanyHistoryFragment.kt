package com.example.newtacks.company

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.newtacks.R
import com.example.newtacks.models.HiringPost
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

class CompanyHistoryFragment : Fragment() {

    private val db = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()
    
    private lateinit var recyclerView: RecyclerView
    private lateinit var emptyState: LinearLayout
    private val historyList = mutableListOf<HiringPost>()
    private lateinit var adapter: CompanyHistoryAdapter

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        val view = inflater.inflate(R.layout.fragment_worker_history, container, false)
        
        recyclerView = view.findViewById(R.id.recyclerWorkerHistory)
        emptyState = view.findViewById(R.id.layoutEmptyState)
        
        recyclerView.layoutManager = LinearLayoutManager(requireContext())
        adapter = CompanyHistoryAdapter(historyList)
        recyclerView.adapter = adapter
        
        loadHistory()
        
        return view
    }

    private fun loadHistory() {
        val uid = auth.currentUser?.uid ?: return
        
        db.collection("hiring")
            .whereEqualTo("companyId", uid)
            .whereEqualTo("status", "COMPLETED") // Only show completed hires in history
            .get()
            .addOnSuccessListener { snapshots ->
                historyList.clear()
                for (doc in snapshots) {
                    val post = doc.toObject(HiringPost::class.java)
                    historyList.add(post)
                }
                adapter.notifyDataSetChanged()
                
                if (historyList.isEmpty()) {
                    emptyState.visibility = View.VISIBLE
                    recyclerView.visibility = View.GONE
                } else {
                    emptyState.visibility = View.GONE
                    recyclerView.visibility = View.VISIBLE
                }
            }
    }
}