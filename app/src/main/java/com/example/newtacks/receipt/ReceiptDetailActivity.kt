package com.example.newtacks.receipt

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.view.View
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.example.newtacks.R
import com.example.newtacks.models.Receipt
import com.example.newtacks.models.Review
import com.google.firebase.firestore.FirebaseFirestore
import java.io.OutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class ReceiptDetailActivity : AppCompatActivity() {

    private val db = FirebaseFirestore.getInstance()
    private var currentReceipt: Receipt? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_receipt_detail)

        val receiptId = intent.getStringExtra("receiptId") ?: return
        val showReview = intent.getBooleanExtra("showReview", false)

        loadReceipt(receiptId, showReview)
    }

    private fun loadReceipt(receiptId: String, showReview: Boolean) {

        db.collection("receipts")
            .document(receiptId)
            .get()
            .addOnSuccessListener { doc ->

                val receipt = doc.toObject(Receipt::class.java) ?: return@addOnSuccessListener
                currentReceipt = receipt

                findViewById<TextView>(R.id.tvReceiptTitle).text = receipt.jobTitle
                findViewById<TextView>(R.id.tvClientName).text = receipt.clientName
                findViewById<TextView>(R.id.tvWorkerName).text = receipt.workerName
                findViewById<TextView>(R.id.tvAmount).text = "₱${receipt.amount}"
                findViewById<TextView>(R.id.tvService).text = receipt.serviceCategory

                val ref = if (receipt.referenceNumber.isNotEmpty()) {
                    receipt.referenceNumber
                } else {
                    receipt.receiptId.filter { it.isDigit() }.take(8).let {
                        if (it.length == 8) it else "24921023"
                    }
                }
                findViewById<TextView>(R.id.tvReferenceNumber).text = "Ref: #TX-$ref"

                val sdf = SimpleDateFormat("dd/MM/yy hh:mm a", Locale.getDefault())
                findViewById<TextView>(R.id.tvRequestedDate).text = sdf.format(Date(receipt.createdAt))
                findViewById<TextView>(R.id.tvCompletedDate).text = sdf.format(Date(receipt.completedAt))
                findViewById<TextView>(R.id.tvPaymentMethod).text = receipt.paymentMethod
                
                if (showReview) {
                    showReviewDialog()
                }
            }

        findViewById<com.google.android.material.button.MaterialButton>(R.id.btnDownload).setOnClickListener {
            checkPermissionAndSave()
        }

        findViewById<com.google.android.material.button.MaterialButton>(R.id.btnOkay).setOnClickListener {
            finish()
        }
    }

    private fun showReviewDialog() {
        val receipt = currentReceipt ?: return
        val dialogView = layoutInflater.inflate(R.layout.dialog_review, null)
        val ratingBar = dialogView.findViewById<RatingBar>(R.id.ratingBar)
        val etComment = dialogView.findViewById<EditText>(R.id.etComment)
        val cbAnonymous = dialogView.findViewById<CheckBox>(R.id.cbAnonymous)

        AlertDialog.Builder(this, R.style.CustomAlertDialog)
            .setTitle("Rate your experience")
            .setView(dialogView)
            .setPositiveButton("Submit") { _, _ ->
                val review = Review(
                    reviewId = db.collection("reviews").document().id,
                    jobId = receipt.jobId,
                    clientId = receipt.clientId,
                    clientName = receipt.clientName,
                    workerId = receipt.workerId,
                    rating = ratingBar.rating,
                    comment = etComment.text.toString(),
                    isAnonymous = cbAnonymous.isChecked
                )
                saveReview(review)
            }
            .setNegativeButton("Skip", null)
            .show()
    }

    private fun saveReview(review: Review) {
        db.collection("reviews").document(review.reviewId).set(review)
            .addOnSuccessListener {
                updateWorkerRating(review)
            }
    }

    private fun updateWorkerRating(review: Review) {
        val workerRef = db.collection("users").document(review.workerId)
        db.runTransaction { transaction ->
            val snapshot = transaction.get(workerRef)
            val currentAvg = snapshot.getDouble("ratingAverage") ?: 0.0
            val count = snapshot.getLong("ratingCount") ?: 0
            val newCount = count + 1
            val newAvg = ((currentAvg * count) + review.rating) / newCount
            transaction.update(workerRef, mapOf("ratingAverage" to newAvg, "ratingCount" to newCount))
        }.addOnSuccessListener {
            Toast.makeText(this, "Thank you for your feedback!", Toast.LENGTH_SHORT).show()
        }
    }

    private fun checkPermissionAndSave() {
        if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.P) {
            if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.WRITE_EXTERNAL_STORAGE) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this, arrayOf(android.Manifest.permission.WRITE_EXTERNAL_STORAGE), 1001)
            } else {
                saveReceiptToGallery()
            }
        } else {
            saveReceiptToGallery()
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 1001) {
            if (grantResults.isNotEmpty() && grantResults[0] == android.content.pm.PackageManager.PERMISSION_GRANTED) {
                saveReceiptToGallery()
            } else {
                Toast.makeText(this, "Permission denied. Cannot save receipt.", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun saveReceiptToGallery() {
        val view = findViewById<LinearLayout>(R.id.receiptCardContent)
        
        // Ensure view is measured
        if (view.width == 0 || view.height == 0) {
            Toast.makeText(this, "Receipt not ready yet", Toast.LENGTH_SHORT).show()
            return
        }

        val bitmap = getBitmapFromView(view)
        
        val filename = "Tacks_Receipt_${System.currentTimeMillis()}.png"
        val contentValues = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, filename)
            put(MediaStore.MediaColumns.MIME_TYPE, "image/png")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.MediaColumns.RELATIVE_PATH, "Pictures/TacksReceipts")
                put(MediaStore.MediaColumns.IS_PENDING, 1)
            }
        }

        val contentResolver = contentResolver
        val imageUri = contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)

        imageUri?.let { uri ->
            try {
                val fos = contentResolver.openOutputStream(uri)
                fos?.use {
                    bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
                }
                
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    contentValues.clear()
                    contentValues.put(MediaStore.MediaColumns.IS_PENDING, 0)
                    contentResolver.update(uri, contentValues, null, null)
                }
                
                showSuccessDialog()
            } catch (e: Exception) {
                Toast.makeText(this, "Error saving receipt: ${e.message}", Toast.LENGTH_SHORT).show()
                e.printStackTrace()
            }
        } ?: run {
            Toast.makeText(this, "Failed to create MediaStore entry", Toast.LENGTH_SHORT).show()
        }
    }

    private fun getBitmapFromView(view: View): Bitmap {
        val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        view.draw(canvas)
        return bitmap
    }

    private fun showSuccessDialog() {
        AlertDialog.Builder(this)
            .setTitle("Success")
            .setMessage("Receipt has been successfully saved to your gallery.")
            .setPositiveButton("Okay!") { dialog, _ ->
                dialog.dismiss()
            }
            .setCancelable(false)
            .show()
    }



    companion object {

        fun open(context: Context, receiptId: String, showReview: Boolean = false) {

            val intent = Intent(context, ReceiptDetailActivity::class.java)
            intent.putExtra("receiptId", receiptId)
            intent.putExtra("showReview", showReview)
            context.startActivity(intent)
        }
    }
}