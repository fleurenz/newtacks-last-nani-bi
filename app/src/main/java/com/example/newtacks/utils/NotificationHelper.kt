package com.example.newtacks.utils

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.view.View
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.example.newtacks.R
import com.example.newtacks.authentication.SplashActivity
import com.google.firebase.firestore.DocumentChange

object NotificationHelper {
    private const val CHANNEL_ID = "newtacks_notifications"
    private const val CHANNEL_NAME = "NewTacks Job Updates"
    private const val CHANNEL_DESC = "Notifications for job acceptance, arrival, and completion."

    // Keep track of IDs we've already notified to prevent duplicates/looping
    private val processedNotificationIds = mutableSetOf<String>()

    fun showNotification(context: Context, title: String, message: String, targetFragment: String? = null) {
        createNotificationChannel(context)

        val intent = Intent(context, SplashActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            if (targetFragment != null) {
                putExtra("TARGET_FRAGMENT", targetFragment)
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

        android.util.Log.d("NotificationHelper", "Showing notification: $title - $message (Target: $targetFragment)")

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
                        
                        showNotification(context, title, message, target)

                        // Mark as notified in tray so it doesn't show again
                        doc.reference.update("notifiedTray", true)
                    }
                }
            }
    }

    fun sendNotification(toUid: String, title: String, message: String, targetFragment: String? = null) {
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
        db.collection("notifications").add(data)
    }

    fun stopListening() {
        notificationListener?.remove()
        notificationListener = null
        processedNotificationIds.clear()
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
            if (!notif.targetFragment.isNullOrEmpty()) {
                navigateToTarget(context, notif.targetFragment)
            }
        }

        rv.layoutManager = androidx.recyclerview.widget.LinearLayoutManager(context)
        rv.adapter = adapter

        // Listen for notifications for this user
        val listener = db.collection("notifications")
            .whereEqualTo("to", uid)
            .orderBy("timestamp", com.google.firebase.firestore.Query.Direction.DESCENDING)
            .addSnapshotListener { snapshots, _ ->
                if (snapshots != null) {
                    notifList.clear()
                    for (doc in snapshots) {
                        val n = doc.toObject(com.example.newtacks.models.InAppNotification::class.java)
                        notifList.add(n)
                    }
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

    private fun navigateToTarget(context: Context, target: String) {
        when (context) {
            is com.example.newtacks.WorkerDashboardActivity -> {
                when (target) {
                    "JOB", "CHAT" -> context.switchTab(R.id.nav_job)
                    "HISTORY" -> context.switchTab(R.id.nav_history)
                    "HIRING" -> context.switchTab(R.id.nav_hiring)
                    "FEED" -> context.switchTab(R.id.nav_feed)
                }
            }
            is com.example.newtacks.ClientDashboardActivity -> {
                when (target) {
                    "REQUESTS", "CHAT" -> context.switchTab(R.id.nav_requests)
                    "HISTORY" -> context.switchTab(R.id.nav_history)
                    "HOME" -> context.switchTab(R.id.nav_home)
                }
            }
            is com.example.newtacks.CompanyDashboardActivity -> {
                when (target) {
                    "APPLICANTS", "CHAT" -> context.switchToApplicants()
                    "POSTS" -> context.switchToPosts()
                }
            }
        }
    }
}
