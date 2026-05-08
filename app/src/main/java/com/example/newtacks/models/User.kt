package com.example.newtacks.models

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

    // 🔧 WORKER ONLY
    val serviceCategories: List<String>? = null,
    val serviceExperience: Int? = null,

    // ⭐ WORKER RATING SYSTEM
    val rating: Double = 0.0,
    val totalRatings: Int = 0,

    // ✅ VERIFICATION STATUS (0: Unverified, 1: NC1, 2: NC2, 3: NC3)
    val verificationStatus: Int = 0,
    val nc1CertificateUrl: String? = null,
    val nc2CertificateUrl: String? = null,
    val nc3CertificateUrl: String? = null
)