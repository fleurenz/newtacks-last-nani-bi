package com.example.newtacks.authentication

sealed class SignupState {
    object Loading : SignupState()
    object Success : SignupState()
    data class Error(val message: String) : SignupState()
}