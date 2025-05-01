package com.example.spotan

import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel

class AuthorizationViewModel : ViewModel() {
    // Состояние авторизации
    var authStatus = mutableStateOf("")
        private set

    fun setAuthStatus(status: String) {
        authStatus.value = status
    }
}