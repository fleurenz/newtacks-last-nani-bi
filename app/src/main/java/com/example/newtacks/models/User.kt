package com.example.newtacks.models

import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.Exclude

data class User(
    val uid: String = "",
    val role: String = "",
    val name: String = "",
    val email: String = "",
    val phone: String = "",
    val address: String = "",

    val latitude: Double? = null,
    val longitude: Double? = null,

    // 🖼 PROFILE IMAGE (ALL USERS)
    val profileImage: String = "",

    // 🏢 COMPANY ONLY
    val companyName: String? = null,
    val hrName: String? = null,
    val aboutUs: String? = null,

    // 🔧 WORKER ONLY
    val serviceCategories: List<String>? = null,
    val serviceExperience: Int? = null,

    // ⭐ WORKER RATING SYSTEM
    val rating: Double = 0.0,
    val totalRatings: Int = 0,
    val ratingAverage: Double? = null,
    val ratingCount: Long? = null,

    // 🟢 ONLINE STATUS
    val isOnline: Boolean = false,
    val lastActive: Long = 0,

    // ✅ VERIFICATION STATUS (0: Unverified, 1: Trusted, 2: Pro)
    val verificationStatus: Int = 0,
    val resumeUrl: String? = null,
    val verifiedSkills: Map<String, String> = emptyMap(), // Map of "SkillName" -> "CertificateUrl"
    val otherCertificates: List<WorkerCertificate> = emptyList(),
    val deletionTimestamp: Long? = null
) {
    companion object {
        /**
         * ✅ SMART PIVOT: Merges flat "verifiedSkills.SkillName" fields into the verifiedSkills map.
         */
        fun fromSnapshot(doc: DocumentSnapshot): User? {
            val user = doc.toObject(User::class.java) ?: return null
            val rawData = doc.data ?: return user

            val manualVerifiedMap = mutableMapOf<String, String>()
            
            // 1. Check for flat fields like "verifiedSkills.Plumbing"
            rawData.forEach { (key, value) ->
                if (key.startsWith("verifiedSkills.") && value is String) {
                    val skillName = key.substringAfter("verifiedSkills.")
                    manualVerifiedMap[skillName] = value
                }
            }

            // 2. Merge with existing map (if any) and return
            val mergedMap = user.verifiedSkills.toMutableMap()
            mergedMap.putAll(manualVerifiedMap)
            
            return user.copy(verifiedSkills = mergedMap)
        }
    }
}

data class WorkerCertificate(
    val certId: String = "",
    val name: String = "",
    val type: String = "", // e.g., "NC1", "NC2", "NC3", "Other"
    val url: String = "",
    val timestamp: Long = System.currentTimeMillis()
)