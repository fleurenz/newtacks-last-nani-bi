package com.example.newtacks.authentication

import android.net.Uri
import android.content.Context
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.example.newtacks.utils.SecurityUtils
import com.google.gson.Gson

class SignupViewModel(
    private val repo: AuthRepository
) : ViewModel() {

    val signupState = MutableLiveData<SignupState>()

    fun startMagicLinkVerification(context: Context, data: SignupData) {
        if (data.email.isEmpty() || data.password.isEmpty() || data.phone.isEmpty()) {
            signupState.value = SignupState.Error("Fields cannot be empty")
            return
        }
        
        signupState.value = SignupState.Loading

        // 1. Send Magic Link
        repo.sendMagicLink(data.email).addOnCompleteListener { task ->
            if (task.isSuccessful) {
                // 2. Save Data Securely
                val prefs = SecurityUtils.getEncryptedPrefs(context)
                val json = Gson().toJson(data)
                prefs.edit().putString("pending_signup_data", json).apply()
                prefs.edit().putString("pending_magic_email", data.email).apply()
                
                signupState.value = SignupState.Progress("Verification email sent! Please check your inbox.")
            } else {
                signupState.value = SignupState.Error(task.exception?.message ?: "Failed to send verification email")
            }
        }
    }

    fun completeRegistrationWithLink(context: Context, email: String, link: String) {
        signupState.value = SignupState.Loading
        
        val prefs = SecurityUtils.getEncryptedPrefs(context)
        val json = prefs.getString("pending_signup_data", null) ?: return
        val data = Gson().fromJson(json, SignupData::class.java)

        repo.signInWithMagicLink(email, link) { result ->
            result.onSuccess { uid ->
                // Now finalize the profile and set the password
                repo.finalizeMagicLinkProfile(
                    uid = uid,
                    data = data,
                    onProgress = { msg -> signupState.postValue(SignupState.Progress(msg)) },
                    onResult = { finalResult ->
                        finalResult.onSuccess { signupState.value = SignupState.Success }
                        finalResult.onFailure { signupState.value = SignupState.Error(it.message ?: "Profile finalization failed") }
                    }
                )
            }.onFailure {
                signupState.value = SignupState.Error(it.message ?: "Verification failed")
            }
        }
    }

    fun register(
        imageUri: Uri?,
        email: String,
        password: String,
        confirmPassword: String,
        role: String,
        name: String,
        phone: String,
        address: String,
        latitude: Double? = null,
        longitude: Double? = null,
        companyName: String? = null,
        hrName: String? = null,
        aboutUs: String? = null,
        categories: List<String>? = null,
        experience: Int? = null
    ) {
        if (email.isEmpty() || password.isEmpty() || phone.isEmpty()) {
            signupState.value = SignupState.Error("Fields cannot be empty")
            return
        }
        if (phone.length != 11) {
            signupState.value = SignupState.Error("Contact number must be 11 digits")
            return
        }
        if (password != confirmPassword) {
            signupState.value = SignupState.Error("Passwords do not match")
            return
        }

        signupState.value = SignupState.Loading

        repo.register(
            imageUri = imageUri,
            email = email,
            password = password,
            role = role,
            name = name,
            phone = phone,
            address = address,
            latitude = latitude,
            longitude = longitude,
            companyName = companyName,
            hrName = hrName,
            aboutUs = aboutUs,
            categories = categories,
            experience = experience,
            onProgress = { message ->
                signupState.postValue(SignupState.Progress(message)) // ✅
            }
        ) { result ->
            result.onSuccess {
                signupState.value = SignupState.Success
            }
            result.onFailure {
                signupState.value = SignupState.Error(it.message ?: "Signup failed")
            }
        }
    }
}