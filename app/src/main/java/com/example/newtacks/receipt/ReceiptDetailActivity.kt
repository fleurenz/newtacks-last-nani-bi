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
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.example.newtacks.R
import com.example.newtacks.models.Receipt
import com.google.firebase.firestore.FirebaseFirestore
import java.io.OutputStream
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

                val ref = if (receipt.referenceNumber.isNotEmpty()) {
                    receipt.referenceNumber
                } else {
                    // Fallback for old receipts
                    receipt.receiptId.filter { it.isDigit() }.take(8).let {
                        if (it.length == 8) it else "24921023" // Example fallback
                    }
                }
                findViewById<TextView>(R.id.tvReferenceNumber).text = "Ref: #TX-$ref"

                val sdf = SimpleDateFormat("dd/MM/yy hh:mm a", Locale.getDefault())
                findViewById<TextView>(R.id.tvRequestedDate).text = sdf.format(Date(receipt.createdAt))
                findViewById<TextView>(R.id.tvCompletedDate).text = sdf.format(Date(receipt.completedAt))
            }

        findViewById<com.google.android.material.button.MaterialButton>(R.id.btnDownload).setOnClickListener {
            checkPermissionAndSave()
        }

        findViewById<com.google.android.material.button.MaterialButton>(R.id.btnOkay).setOnClickListener {
            finish()
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

        fun open(context: Context, receiptId: String) {

            val intent = Intent(context, ReceiptDetailActivity::class.java)
            intent.putExtra("receiptId", receiptId)
            context.startActivity(intent)
        }
    }
}