package com.example.newtacks.authentication

sealed class LoginState {
    object Loading : LoginState()
    data class Success(val uid: String, val role: String) : LoginState()
    data class Error(val message: String) : LoginState()
}