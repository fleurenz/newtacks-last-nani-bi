package com.example.newtacks.receipt

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.example.newtacks.R
import com.example.newtacks.models.Receipt
import com.google.firebase.firestore.FirebaseFirestore
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class ReceiptDetailActivity : AppCompatActivity() {

    private val db = FirebaseFirestore.getInstance()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_receipt_detail)

        val receiptId = intent.getStringExtra("receiptId") ?: return

        loadReceipt(receiptId)
    }

    private fun loadReceipt(receiptId: String) {

        db.collection("receipts")
            .document(receiptId)
            .get()
            .addOnSuccessListener { doc ->

                val receipt = doc.toObject(Receipt::class.java) ?: return@addOnSuccessListener

                findViewById<TextView>(R.id.tvReceiptTitle).text = receipt.jobTitle
                findViewById<TextView>(R.id.tvClientName).text = receipt.clientName
                findViewById<TextView>(R.id.tvWorkerName).text = receipt.workerName
                findViewById<TextView>(R.id.tvAmount).text = "₱${receipt.amount}"
                findViewById<TextView>(R.id.tvService).text = receipt.serviceCategory

                val sdf = SimpleDateFormat("dd/MM/yy hh:mm a", Locale.getDefault())
                findViewById<TextView>(R.id.tvRequestedDate).text = sdf.format(Date(receipt.createdAt))
                findViewById<TextView>(R.id.tvCompletedDate).text = sdf.format(Date(receipt.completedAt))
            }

        findViewById<com.google.android.material.button.MaterialButton>(R.id.btnOkay).setOnClickListener {
            finish()
        }
    }



    companion object {

        fun open(context: Context, receiptId: String) {

            val intent = Intent(context, ReceiptDetailActivity::class.java)
            intent.putExtra("receiptId", receiptId)
            context.startActivity(intent)
        }
    }
}