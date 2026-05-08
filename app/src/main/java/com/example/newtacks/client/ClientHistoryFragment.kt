package com.example.newtacks.client

import android.os.Bundle
import android.view.View
import android.widget.LinearLayout
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.newtacks.R
import com.example.newtacks.receipt.ReceiptAdapter
import com.example.newtacks.receipt.ReceiptDetailActivity
import com.example.newtacks.models.Receipt
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration

class ClientHistoryFragment : Fragment(R.layout.fragment_client_history) {

    private val db = FirebaseFirestore.getInstance()
    private var listener: ListenerRegistration? = null

    private lateinit var recyclerView: RecyclerView
    private lateinit var adapter: ReceiptAdapter
    private lateinit var layoutHeader: LinearLayout
    private lateinit var layoutEmptyState: LinearLayout

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        recyclerView     = view.findViewById(R.id.recyclerHistory)
        layoutHeader     = view.findViewById(R.id.layoutHeader)
        layoutEmptyState = view.findViewById(R.id.layoutEmptyState)

        recyclerView.layoutManager = LinearLayoutManager(requireContext())
        adapter = ReceiptAdapter { receipt ->
            ReceiptDetailActivity.open(requireContext(), receipt.receiptId)
        }
        recyclerView.adapter = adapter

        // --------------------------------------------------
        // ✅ WINDOW INSETS
        // --------------------------------------------------
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

        listenReceipts()
    }

    private fun listenReceipts() {
        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return
        listener = db.collection("receipts")
            .whereEqualTo("clientId", uid)
            .addSnapshotListener { snapshots, _ ->
                val receipts = snapshots?.toObjects(Receipt::class.java) ?: emptyList()
                adapter.submitList(receipts)

                // toggle empty state
                layoutEmptyState.visibility =
                    if (receipts.isEmpty()) View.VISIBLE else View.GONE
                recyclerView.visibility =
                    if (receipts.isEmpty()) View.GONE else View.VISIBLE
            }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        listener?.remove()
    }
}