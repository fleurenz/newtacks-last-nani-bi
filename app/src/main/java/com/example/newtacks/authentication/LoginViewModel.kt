package com.example.newtacks.authentication

import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel

class LoginViewModel(
    private val repo: AuthRepository
) : ViewModel() {

    val loginState = MutableLiveData<LoginState>()

    fun login(email: String, password: String) {

        loginState.value = LoginState.Loading

        repo.login(email, password) { result ->

            result.onSuccess { uid ->

                repo.getUserRole(uid) { roleResult ->

                    roleResult.onSuccess { role ->
                        loginState.value = LoginState.Success(uid, role)
                    }

                    roleResult.onFailure {
                        loginState.value =
                            LoginState.Error("Failed to fetch role")
                    }
                }
            }

            result.onFailure {
                loginState.value =
                    LoginState.Error(it.message ?: "Login failed")
            }
        }
    }
}