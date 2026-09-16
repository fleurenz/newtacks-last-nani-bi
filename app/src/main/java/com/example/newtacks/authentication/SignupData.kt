package com.example.newtacks.authentication

import android.net.Uri

data class SignupData(
    val email: String = "",
    val password: String = "",
    val role: String = "",
    val name: String = "",
    val phone: String = "",
    val address: String = "",
    val latitude: Double? = null,
    val longitude: Double? = null,
    val companyName: String? = null,
    val hrName: String? = null,
    val aboutUs: String? = null,
    val categories: List<String>? = null,
    val experience: Int? = null,
    val imageUriString: String? = null
) {
    val imageUri: Uri? get() = imageUriString?.let { Uri.parse(it) }
}