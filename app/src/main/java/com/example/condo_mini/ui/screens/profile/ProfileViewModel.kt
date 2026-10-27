package com.example.condo_mini.ui.screens.profile

import androidx.lifecycle.ViewModel
import com.example.condo_mini.data.AppContainer
import com.example.condo_mini.domain.model.User
import com.example.condo_mini.domain.repository.SessionRepository
import kotlinx.coroutines.flow.StateFlow

class ProfileViewModel(
    private val sessionRepository: SessionRepository = AppContainer.sessionRepository
) : ViewModel() {

    val user: StateFlow<User?> = sessionRepository.currentUser

    fun onSaveContact(email: String, phone: String) {
        sessionRepository.updateContact(email, phone)
    }
}
