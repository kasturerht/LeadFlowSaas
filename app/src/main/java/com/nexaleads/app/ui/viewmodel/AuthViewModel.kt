package com.nexaleads.app.ui.viewmodel

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
    data class Authenticated(val userId: String, val userName: String, val contactNumber: String, val orgId: String, val orgName: String) : AuthState()
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
                    val mappingDoc = db.collection("user_mappings").document(currentUser.uid).get().await()
                    if (mappingDoc.exists()) {
                        val orgId = mappingDoc.getString("orgId") ?: return@launch
                        
                        val orgBaseDoc = db.collection("organizations").document(orgId).get().await()
                        val orgName = if (orgBaseDoc.exists()) orgBaseDoc.getString("name")?.takeIf { it.isNotBlank() } ?: "ORGANIZATION" else "ORGANIZATION"

                        val doc = db.collection("organizations").document(orgId)
                                    .collection("users").document(currentUser.uid).get().await()
                        
                        if (doc.exists() && doc.getString("role") == "telecaller") {
                            val isActive = doc.getBoolean("isActive") ?: true
                            if (isActive) {
                                val name = doc.getString("name") ?: "Agent"
                                val contactNumber = doc.getString("contactNumber") ?: "+91 98347 83503"
                                _authState.value = AuthState.Authenticated(currentUser.uid, name, contactNumber, orgId, orgName)
                            } else {
                                auth.signOut()
                                _authState.value = AuthState.Unauthenticated("Your account has been disabled by Admin.")
                            }
                        } else {
                            auth.signOut()
                            _authState.value = AuthState.Unauthenticated("Invalid role or user not found.")
                        }
                    } else {
                        auth.signOut()
                        _authState.value = AuthState.Unauthenticated("User mapping not found. Contact Admin.")
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
