package com.example.newtacks.authentication

sealed class SignupState {
    object Loading : SignupState()
    object Success : SignupState()
    data class Error(val message: String) : SignupState()
    data class Progress(val message: String) : SignupState()
}