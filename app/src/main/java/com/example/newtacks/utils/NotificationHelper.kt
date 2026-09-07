package com.example.newtacks.utils

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
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

        notificationListener?.remove()
        processedNotificationIds.clear() // Reset cache for new session

        notificationListener = db.collection("notifications")
            .whereEqualTo("to", uid)
            .whereEqualTo("read", false)
            .addSnapshotListener { snapshots, error ->
                if (error != null) {
                    android.util.Log.e("NotificationHelper", "Listener error: ${error.message}")
                    return@addSnapshotListener
                }
                if (snapshots == null) return@addSnapshotListener

                for (change in snapshots.documentChanges) {
                    if (change.type == DocumentChange.Type.ADDED) {
                        val doc = change.document
                        val docId = doc.id
                        
                        // Prevent duplicate processing in the same listener session
                        if (processedNotificationIds.contains(docId)) continue
                        processedNotificationIds.add(docId)

                        val title = doc.getString("title") ?: "New Update"
                        val message = doc.getString("message") ?: ""
                        val target = doc.getString("targetFragment")
                        
                        showNotification(context, title, message, target)

                        // Clean up from DB
                        doc.reference.delete().addOnFailureListener {
                            // If delete fails, remove from local cache so we can try again if listener refreshes
                            processedNotificationIds.remove(docId)
                        }
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
            "read" to false
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
}
