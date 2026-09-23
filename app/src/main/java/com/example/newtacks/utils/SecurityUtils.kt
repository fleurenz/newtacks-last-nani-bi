package com.example.newtacks.utils

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

object SecurityUtils {

    fun getEncryptedPrefs(context: Context): SharedPreferences {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()

        return EncryptedSharedPreferences.create(
            context,
            "stract_secure_prefs",
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }

    fun formatUserFriendlyErrorMessage(rawMessage: String?): String {
        if (rawMessage.isNullOrBlank()) return "An unexpected error occurred. Please try again."
        val msg = rawMessage.lowercase(java.util.Locale.getDefault())

        return when {
            msg.contains("invalid-credential") || msg.contains("invalid email") || msg.contains("wrong password") || msg.contains("no user record") || msg.contains("user not found") ->
                "Invalid email or password. Please check your login details."
            msg.contains("email-already-in-use") || msg.contains("already exists") ->
                "An account with this email address already exists."
            msg.contains("network") || msg.contains("connection") || msg.contains("unreachable") ->
                "Network error. Please check your internet connection and try again."
            msg.contains("too-many-requests") ->
                "Too many attempts. Please wait a moment and try again."
            msg.contains("expired") ->
                "The verification link has expired. Please request a new one."
            msg.contains("password") && (msg.contains("weak") || msg.contains("short")) ->
                "Password is too weak. Please use at least 6 characters."
            msg.contains("fields cannot be empty") ->
                "Please fill in all required fields."
            msg.contains("passwords do not match") ->
                "Passwords do not match. Please try again."
            msg.contains("11 digits") ->
                "Contact number must be exactly 11 digits."
            msg.contains("job already taken") ->
                "This request has already been accepted by another worker."
            msg.contains("failed to fetch role") ->
                "Unable to retrieve account role. Please try logging in again."
            else -> "Unable to process request. Please check your details and try again."
        }
    }
}