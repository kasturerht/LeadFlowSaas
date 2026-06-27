package com.enterprise.leadflow.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import javax.inject.Inject

sealed class AuthState {
    object Idle : AuthState()
    object Loading : AuthState()
    data class Authenticated(val userId: String, val userName: String) : AuthState()
    data class Unauthenticated(val error: String? = null) : AuthState()
}

@HiltViewModel
class AuthViewModel @Inject constructor(
    private val auth: FirebaseAuth,
    private val db: FirebaseFirestore
) : ViewModel() {

    private val _authState = MutableStateFlow<AuthState>(AuthState.Idle)
    val authState: StateFlow<AuthState> = _authState

    init {
        checkCurrentUser()
    }

    fun checkCurrentUser() {
        _authState.value = AuthState.Loading
        val currentUser = auth.currentUser
        if (currentUser != null) {
            viewModelScope.launch {
                try {
                    val doc = db.collection("users").document(currentUser.uid).get().await()
                    if (doc.exists() && doc.getString("role") == "telecaller") {
                        val name = doc.getString("name") ?: "Agent"
                        _authState.value = AuthState.Authenticated(currentUser.uid, name)
                    } else {
                        auth.signOut()
                        _authState.value = AuthState.Unauthenticated("Invalid role or user not found.")
                    }
                } catch (e: Exception) {
                    auth.signOut()
                    _authState.value = AuthState.Unauthenticated(e.message)
                }
            }
        } else {
            _authState.value = AuthState.Unauthenticated()
        }
    }

    fun logout() {
        auth.signOut()
        _authState.value = AuthState.Unauthenticated()
    }
}
