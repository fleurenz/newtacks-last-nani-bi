package com.example.newtacks.utils

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.view.View
import android.widget.TextView
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.example.newtacks.R
import com.example.newtacks.authentication.SplashActivity
import com.google.firebase.firestore.DocumentChange

object NotificationHelper {
    private const val CHANNEL_ID = "newtacks_notifications"
    private const val CHANNEL_NAME = "STRACT Job Updates"
    private const val CHANNEL_DESC = "Notifications for job acceptance, arrival, and completion."

    // Keep track of IDs we've already notified to prevent duplicates/looping
    private val processedNotificationIds = mutableSetOf<String>()

    fun showNotification(context: Context, title: String, message: String, targetFragment: String? = null, targetId: String? = null) {
        createNotificationChannel(context)

        val intent = Intent(context, SplashActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            if (targetFragment != null) {
                putExtra("TARGET_FRAGMENT", targetFragment)
            }
            if (targetId != null) {
                putExtra("TARGET_ID", targetId)
            }
        }
        val pendingIntent = PendingIntent.getActivity(
            context, 0, intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_check_circle)
            .setContentTitle(title)
            .setContentText(message)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setDefaults(NotificationCompat.DEFAULT_ALL)
            .setCategory(NotificationCompat.CATEGORY_MESSAGE)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)

        android.util.Log.d("NotificationHelper", "Showing notification: $title - $message (Target: $targetFragment, ID: $targetId)")

        with(NotificationManagerCompat.from(context)) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                if (context.checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) == android.content.pm.PackageManager.PERMISSION_GRANTED) {
                    notify(System.currentTimeMillis().toInt(), builder.build())
                }
            } else {
                notify(System.currentTimeMillis().toInt(), builder.build())
            }
        }
    }

    private fun createNotificationChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val notificationManager: NotificationManager =
                context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            
            val existingChannel = notificationManager.getNotificationChannel(CHANNEL_ID)
            if (existingChannel != null && existingChannel.importance >= NotificationManager.IMPORTANCE_HIGH) {
                return
            }

            val importance = NotificationManager.IMPORTANCE_HIGH
            val channel = NotificationChannel(CHANNEL_ID, CHANNEL_NAME, importance).apply {
                description = CHANNEL_DESC
                enableLights(true)
                lightColor = android.graphics.Color.BLUE
                enableVibration(true)
                lockscreenVisibility = NotificationCompat.VISIBILITY_PUBLIC
            }
            notificationManager.createNotificationChannel(channel)
        }
    }

    private var notificationListener: com.google.firebase.firestore.ListenerRegistration? = null

    fun listenForNotifications(context: Context) {
        val uid = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.uid ?: return
        val db = com.google.firebase.firestore.FirebaseFirestore.getInstance()

        // CLEANUP: Delete notifications older than 30 days
        val thirtyDaysAgo = java.util.Date(System.currentTimeMillis() - (30L * 24 * 60 * 60 * 1000))
        db.collection("notifications")
            .whereEqualTo("to", uid)
            .whereLessThan("timestamp", thirtyDaysAgo)
            .get()
            .addOnSuccessListener { sn ->
                if (!sn.isEmpty) {
                    val batch = db.batch()
                    for (d in sn) batch.delete(d.reference)
                    batch.commit()
                }
            }

        notificationListener?.remove()

        notificationListener = db.collection("notifications")
            .whereEqualTo("to", uid)
            .whereEqualTo("notifiedTray", false)
            .addSnapshotListener { snapshots, error ->
                if (error != null) {
                    android.util.Log.e("NotificationHelper", "Listener error: ${error.message}")
                    return@addSnapshotListener
                }
                if (snapshots == null) return@addSnapshotListener

                for (change in snapshots.documentChanges) {
                    if (change.type == DocumentChange.Type.ADDED) {
                        val doc = change.document
                        val title = doc.getString("title") ?: "New Update"
                        val message = doc.getString("message") ?: ""
                        val target = doc.getString("targetFragment")
                        val targetId = doc.getString("targetId")
                        
                        showNotification(context, title, message, target, targetId)

                        // Mark as notified in tray so it doesn't show again
                        doc.reference.update("notifiedTray", true)
                    }
                }
            }
    }

    fun sendNotification(toUid: String, title: String, message: String, targetFragment: String? = null, targetId: String? = null) {
        val db = com.google.firebase.firestore.FirebaseFirestore.getInstance()
        val data = mutableMapOf<String, Any>(
            "to" to toUid,
            "title" to title,
            "message" to message,
            "timestamp" to com.google.firebase.firestore.FieldValue.serverTimestamp(),
            "read" to false,
            "notifiedTray" to false
        )
        if (targetFragment != null) {
            data["targetFragment"] = targetFragment
        }
        if (targetId != null) {
            data["targetId"] = targetId
        }
        db.collection("notifications").add(data)
    }

    fun stopListening() {
        notificationListener?.remove()
        notificationListener = null
        processedNotificationIds.clear()
    }

    fun setupNotificationBadge(lifecycleOwner: androidx.lifecycle.LifecycleOwner, badgeView: TextView) {
        val uid = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.uid ?: return
        val db = com.google.firebase.firestore.FirebaseFirestore.getInstance()

        db.collection("notifications")
            .whereEqualTo("to", uid)
            .whereEqualTo("read", false)
            .addSnapshotListener { snapshots, _ ->
                if (snapshots == null) return@addSnapshotListener
                
                val count = snapshots.size()
                
                if (count > 0) {
                    badgeView.visibility = View.VISIBLE
                    badgeView.text = if (count > 9) "9+" else count.toString()
                } else {
                    badgeView.visibility = View.GONE
                }
            }
    }

    fun showNotificationDialog(context: Context) {
        val uid = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.uid ?: return
        val db = com.google.firebase.firestore.FirebaseFirestore.getInstance()

        val dialog = android.app.Dialog(context)
        dialog.setContentView(R.layout.dialog_notifications)
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
        dialog.window?.setLayout(
            (context.resources.displayMetrics.widthPixels * 0.95).toInt(),
            (context.resources.displayMetrics.heightPixels * 0.8).toInt()
        )

        val rv = dialog.findViewById<androidx.recyclerview.widget.RecyclerView>(R.id.rvNotifications)
        val layoutEmpty = dialog.findViewById<View>(R.id.layoutEmpty)
        val btnClearAll = dialog.findViewById<View>(R.id.btnClearAll)
        val btnClose = dialog.findViewById<View>(R.id.btnClose)

        val notifList = mutableListOf<com.example.newtacks.models.InAppNotification>()
        val adapter = NotificationAdapter(notifList) { notif ->
            // Mark as read when clicked, but DON'T delete automatically
            db.collection("notifications").document(notif.id).update("read", true)
            dialog.dismiss()
            
            // DYNAMIC NAVIGATION: Redirect user based on targetFragment
            navigateToTarget(context, notif.targetFragment ?: "", notif.targetId)
        }

        rv.layoutManager = androidx.recyclerview.widget.LinearLayoutManager(context)
        rv.adapter = adapter

        // Listen for notifications for this user
        val listener = db.collection("notifications")
            .whereEqualTo("to", uid)
            .addSnapshotListener { snapshots, error ->
                if (error != null) {
                    android.util.Log.e("NotificationHelper", "Listener error: ${error.message}")
                    return@addSnapshotListener
                }
                
                if (snapshots != null) {
                    val allNotifs = snapshots.toObjects(com.example.newtacks.models.InAppNotification::class.java)
                    // Sort locally to avoid index requirements and potential listener failures
                    val sortedList = allNotifs.sortedByDescending { it.timestamp }
                    
                    notifList.clear()
                    notifList.addAll(sortedList)
                    adapter.notifyDataSetChanged()
                    
                    layoutEmpty.visibility = if (notifList.isEmpty()) View.VISIBLE else View.GONE
                    btnClearAll.visibility = if (notifList.isEmpty()) View.GONE else View.VISIBLE
                }
            }

        btnClearAll.setOnClickListener {
            // Show confirmation dialog before clearing
            android.app.AlertDialog.Builder(context)
                .setTitle("Clear All Notifications")
                .setMessage("Are you sure you want to delete all notifications? This cannot be undone.")
                .setPositiveButton("Clear All") { _, _ ->
                    db.collection("notifications")
                        .whereEqualTo("to", uid)
                        .get()
                        .addOnSuccessListener { sn ->
                            if (sn.isEmpty) return@addOnSuccessListener
                            val batch = db.batch()
                            for (d in sn) batch.delete(d.reference)
                            batch.commit()
                        }
                }
                .setNegativeButton("Cancel", null)
                .show()
        }

        btnClose.setOnClickListener { dialog.dismiss() }
        dialog.setOnDismissListener { listener.remove() }
        dialog.show()
    }

    private fun navigateToTarget(context: Context, target: String, targetId: String?) {
        // Special case: If it's a chat notification
        if (target == "CHAT" && !targetId.isNullOrEmpty()) {
            val db = com.google.firebase.firestore.FirebaseFirestore.getInstance()
            db.collection("jobs").document(targetId).get().addOnSuccessListener { doc ->
                val job = doc.toObject(com.example.newtacks.models.Job::class.java)?.copy(jobId = doc.id)
                if (job != null) {
                    val intent = Intent(context, com.example.newtacks.chatbot.presentation.ui.TransactionChatActivity::class.java).apply {
                        putExtra("JOB_ID", job.jobId)
                        putExtra("WORKER_ID", job.workerId)
                        putExtra("OTHER_USER_ID", if (com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.uid == job.workerId) job.clientId else job.workerId)
                        putExtra("JOB_TITLE", job.jobTitle)
                    }
                    context.startActivity(intent)
                }
            }
            return
        }

        // Special case: If we have an ID for a receipt, open it directly regardless of context
        if (target == "HISTORY" && !targetId.isNullOrEmpty()) {
            com.example.newtacks.receipt.ReceiptDetailActivity.open(context, targetId)
            return
        }

        // Special case: If it's a hiring post details
        if (target == "HIRING_DETAILS" && !targetId.isNullOrEmpty()) {
            val db = com.google.firebase.firestore.FirebaseFirestore.getInstance()
            db.collection("hiring").document(targetId).get().addOnSuccessListener { doc ->
                val post = doc.toObject(com.example.newtacks.models.HiringPost::class.java)?.copy(hiringId = doc.id)
                if (post != null) {
                    val intent = Intent(context, com.example.newtacks.company.HiringDetailsActivity::class.java)
                    intent.putExtra("HIRING_POST_JSON", com.google.gson.Gson().toJson(post))
                    context.startActivity(intent)
                }
            }
            return
        }

        when (context) {
            is com.example.newtacks.WorkerDashboardActivity -> {
                when (target) {
                    "JOB", "CHAT" -> context.switchTab(R.id.nav_job)
                    "HISTORY" -> context.switchTab(R.id.nav_history)
                    "HIRING" -> context.switchTab(R.id.nav_hiring)
                    "FEED" -> context.switchTab(R.id.nav_feed)
                    "ACCOUNT" -> context.switchTab(R.id.nav_account)
                }
            }
            is com.example.newtacks.ClientDashboardActivity -> {
                when (target) {
                    "REQUESTS", "CHAT" -> context.switchTab(R.id.nav_requests)
                    "HISTORY" -> context.switchTab(R.id.nav_history)
                    "HOME" -> context.switchTab(R.id.nav_home)
                    "ACCOUNT" -> context.switchTab(R.id.nav_account)
                }
            }
            is com.example.newtacks.CompanyDashboardActivity -> {
                when (target) {
                    "APPLICANTS", "CHAT" -> context.switchToApplicants()
                    "POSTS" -> context.switchToPosts()
                    "ACCOUNT" -> { /* Handle account switch if needed */ }
                }
            }
        }
    }
}
