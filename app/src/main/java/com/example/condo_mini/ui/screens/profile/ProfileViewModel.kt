package com.example.condo_mini.ui.screens.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.condo_mini.data.AppContainer
import com.example.condo_mini.data.remote.ApiClient
import com.example.condo_mini.domain.model.User
import com.example.condo_mini.domain.repository.SessionRepository
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class ProfileViewModel(
    private val sessionRepository: SessionRepository = AppContainer.sessionRepository,
    private val apiClient: ApiClient? = AppContainer.apiClient
) : ViewModel() {

    val user: StateFlow<User?> = sessionRepository.currentUser

    fun onSaveContact(email: String, phone: String) {
        viewModelScope.launch {
            sessionRepository.updateContact(email, phone).onFailure { apiClient?.report(it) }
        }
    }
}
