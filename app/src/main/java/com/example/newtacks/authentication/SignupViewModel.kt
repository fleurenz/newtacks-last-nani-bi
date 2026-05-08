package com.example.newtacks.authentication

import android.net.Uri
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel

class SignupViewModel(
    private val repo: AuthRepository
) : ViewModel() {

    val signupState = MutableLiveData<SignupState>()

    fun register(
        imageUri: Uri?,
        email: String,
        password: String,
        confirmPassword: String,
        role: String,
        name: String,
        phone: String,
        address: String,
        companyName: String? = null,
        hrName: String? = null,
        categories: List<String>? = null,
        experience: Int? = null
    ) {

        // VALIDATION
        if (email.isEmpty() || password.isEmpty()) {

            signupState.value =
                SignupState.Error("Fields cannot be empty")

            return
        }

        if (password != confirmPassword) {

            signupState.value =
                SignupState.Error("Passwords do not match")

            return
        }

        signupState.value = SignupState.Loading

        // CALL REPOSITORY
        repo.register(
            imageUri = imageUri,
            email = email,
            password = password,
            role = role,
            name = name,
            phone = phone,
            address = address,
            companyName = companyName,
            hrName = hrName,
            categories = categories,
            experience = experience
        ) { result ->

            result.onSuccess {

                signupState.value =
                    SignupState.Success
            }

            result.onFailure {

                signupState.value =
                    SignupState.Error(
                        it.message ?: "Signup failed"
                    )
            }
        }
    }
}