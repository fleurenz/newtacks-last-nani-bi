package com.example.newtacks.receipt

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.newtacks.R
import com.example.newtacks.models.Receipt
import com.example.newtacks.models.User
import com.google.firebase.firestore.FirebaseFirestore
import java.text.SimpleDateFormat
import java.util.*

sealed class HistoryItem {
    data class DateHeader(val date: String) : HistoryItem()
    data class ReceiptItem(val receipt: Receipt) : HistoryItem()
}

class ReceiptAdapter(
    private val onClick: (Receipt) -> Unit
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    private val items = mutableListOf<HistoryItem>()

    companion object {
        private const val TYPE_HEADER = 0
        private const val TYPE_RECEIPT = 1
    }

    fun submitList(receipts: List<Receipt>) {
        items.clear()
        if (receipts.isEmpty()) {
            notifyDataSetChanged()
            return
        }

        // Sort by date descending
        val sortedReceipts = receipts.sortedByDescending { it.completedAt }

        val sdf = SimpleDateFormat("dd/MM/yy", Locale.getDefault())
        var lastDate = ""

        for (receipt in sortedReceipts) {
            val dateStr = sdf.format(Date(receipt.completedAt))
            if (dateStr != lastDate) {
                items.add(HistoryItem.DateHeader(dateStr))
                lastDate = dateStr
            }
            items.add(HistoryItem.ReceiptItem(receipt))
        }
        notifyDataSetChanged()
    }

    override fun getItemViewType(position: Int): Int {
        return when (items[position]) {
            is HistoryItem.DateHeader -> TYPE_HEADER
            is HistoryItem.ReceiptItem -> TYPE_RECEIPT
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return when (viewType) {
            TYPE_HEADER -> {
                val view = inflater.inflate(R.layout.item_date_header, parent, false)
                DateHeaderViewHolder(view)
            }
            else -> {
                val view = inflater.inflate(R.layout.item_receipt, parent, false)
                ReceiptViewHolder(view)
            }
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (val item = items[position]) {
            is HistoryItem.DateHeader -> (holder as DateHeaderViewHolder).bind(item.date)
            is HistoryItem.ReceiptItem -> (holder as ReceiptViewHolder).bind(item.receipt)
        }
    }

    override fun getItemCount(): Int = items.size

    inner class DateHeaderViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val tvDate: TextView = itemView.findViewById(R.id.tvDateHeader)
        fun bind(date: String) {
            tvDate.text = date
        }
    }

    inner class ReceiptViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val title: TextView = itemView.findViewById(R.id.tvReceiptTitle)
        private val amount: TextView = itemView.findViewById(R.id.tvReceiptAmount)
        private val worker: TextView = itemView.findViewById(R.id.tvReceiptWorker)
        private val badge: TextView = itemView.findViewById(R.id.tvVerificationBadge)

        fun bind(receipt: Receipt) {
            title.text = receipt.jobTitle
            amount.text = "₱${receipt.amount}"
            worker.text = receipt.workerName

            // Fetch worker verification status for the badge
            FirebaseFirestore.getInstance().collection("users")
                .document(receipt.workerId)
                .get()
                .addOnSuccessListener { doc ->
                    val user = doc.toObject(User::class.java)
                    val status = user?.verificationStatus ?: 0
                    if (status > 0) {
                        badge.visibility = View.VISIBLE
                        badge.text = "NC$status"
                    } else {
                        badge.visibility = View.GONE
                    }
                }

            itemView.setOnClickListener {
                onClick(receipt)
            }
        }
    }
}