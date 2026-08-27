package com.example.newtacks.utils

import com.google.firebase.firestore.FirebaseFirestore

object ChatUtils {
    
    fun deleteChatHistory(jobId: String) {
        val db = FirebaseFirestore.getInstance()
        db.collection("chats")
            .whereEqualTo("jobId", jobId)
            .get()
            .addOnSuccessListener { snapshots ->
                val batch = db.batch()
                for (doc in snapshots) {
                    batch.delete(doc.reference)
                }
                batch.commit().addOnSuccessListener {
                    android.util.Log.d("ChatUtils", "Chat history deleted for job: $jobId")
                }
            }
    }
}