package com.example.newtacks.authentication

import android.net.Uri
import com.cloudinary.android.MediaManager
import com.cloudinary.android.callback.ErrorInfo
import com.cloudinary.android.callback.UploadCallback
import com.example.newtacks.models.User
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

class AuthRepository(
    private val auth: FirebaseAuth,
    private val db: FirebaseFirestore
) {

    fun register(
        imageUri: Uri?,
        email: String,
        password: String,
        role: String,
        name: String,
        phone: String,
        address: String,
        companyName: String?,
        hrName: String?,
        categories: List<String>?,
        experience: Int?,
        onProgress: (String) -> Unit,       // ✅ new
        onResult: (Result<Unit>) -> Unit
    ) {
        if (imageUri != null) {
            onProgress("Uploading photo...") // ✅
            MediaManager.get().upload(imageUri)
                .option("folder", "newtacks_profiles")
                .callback(object : UploadCallback {
                    override fun onStart(requestId: String?) {}

                    override fun onProgress(
                        requestId: String?,
                        bytes: Long,
                        totalBytes: Long
                    ) {
                        val percent = if (totalBytes > 0)
                            ((bytes * 100) / totalBytes).toInt()
                        else 0
                        onProgress("Uploading photo... $percent%") // ✅
                    }

                    override fun onSuccess(
                        requestId: String?,
                        resultData: MutableMap<Any?, Any?>?
                    ) {
                        val imageUrl = resultData?.get("secure_url").toString()
                        onProgress("Creating your account...") // ✅
                        createFirebaseUser(
                            email       = email,
                            password    = password,
                            role        = role,
                            name        = name,
                            phone       = phone,
                            address     = address,
                            companyName = companyName,
                            hrName      = hrName,
                            categories  = categories,
                            experience  = experience,
                            profileImage = imageUrl,
                            onProgress  = onProgress,
                            onResult    = onResult
                        )
                    }

                    override fun onError(requestId: String?, error: ErrorInfo?) {
                        onResult(Result.failure(Exception("Image upload failed")))
                    }

                    override fun onReschedule(requestId: String?, error: ErrorInfo?) {}
                })
                .dispatch()
        } else {
            onProgress("Creating your account...") // ✅
            createFirebaseUser(
                email        = email,
                password     = password,
                role         = role,
                name         = name,
                phone        = phone,
                address      = address,
                companyName  = companyName,
                hrName       = hrName,
                categories   = categories,
                experience   = experience,
                profileImage = "",
                onProgress   = onProgress,
                onResult     = onResult
            )
        }
    }

    private fun createFirebaseUser(
        email: String,
        password: String,
        role: String,
        name: String,
        phone: String,
        address: String,
        companyName: String?,
        hrName: String?,
        categories: List<String>?,
        experience: Int?,
        profileImage: String,
        onProgress: (String) -> Unit,       // ✅ new
        onResult: (Result<Unit>) -> Unit
    ) {
        auth.createUserWithEmailAndPassword(email, password)
            .addOnSuccessListener { result ->
                val uid = result.user?.uid ?: return@addOnSuccessListener
                onProgress("Saving your profile...") // ✅
                val user = User(
                    uid              = uid,
                    role             = role,
                    name             = name,
                    email            = email,
                    phone            = phone,
                    address          = address,
                    profileImage     = profileImage,
                    companyName      = if (role == "COMPANY") companyName else null,
                    hrName           = if (role == "COMPANY") hrName else null,
                    serviceCategories = if (role == "WORKER") categories else null,
                    serviceExperience = if (role == "WORKER") experience else null
                )
                db.collection("users")
                    .document(uid)
                    .set(user)
                    .addOnSuccessListener {
                        onResult(Result.success(Unit))
                    }
                    .addOnFailureListener {
                        onResult(Result.failure(it))
                    }
            }
            .addOnFailureListener {
                onResult(Result.failure(it))
            }
    }

    fun login(
        email: String,
        password: String,
        onResult: (Result<String>) -> Unit
    ) {
        auth.signInWithEmailAndPassword(email, password)
            .addOnSuccessListener { result ->
                val uid = result.user?.uid
                if (uid != null) {
                    onResult(Result.success(uid))
                } else {
                    onResult(Result.failure(Exception("User ID is null")))
                }
            }
            .addOnFailureListener {
                onResult(Result.failure(it))
            }
    }

    fun getUserRole(
        uid: String,
        onResult: (Result<String>) -> Unit
    ) {
        db.collection("users")
            .document(uid)
            .get()
            .addOnSuccessListener { document ->
                val role = document.getString("role")
                if (role != null) {
                    onResult(Result.success(role))
                } else {
                    onResult(Result.failure(Exception("Role not found")))
                }
            }
            .addOnFailureListener {
                onResult(Result.failure(it))
            }
    }
}