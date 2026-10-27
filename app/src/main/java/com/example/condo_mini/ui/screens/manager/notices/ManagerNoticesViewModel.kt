package com.example.condo_mini.ui.screens.manager.notices

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.condo_mini.data.AppContainer
import com.example.condo_mini.domain.model.Notice
import com.example.condo_mini.domain.model.NoticeCategory
import com.example.condo_mini.domain.repository.NoticeRepository
import com.example.condo_mini.domain.repository.SessionRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class NoticeForm(
    val category: NoticeCategory = NoticeCategory.MANAGER,
    val title: String = "",
    val body: String = "",
    val pinned: Boolean = false,
    val isSubmitting: Boolean = false,
    val error: String? = null
)

data class ManagerNoticesUiState(
    val isLoading: Boolean = true,
    val pinned: List<Notice> = emptyList(),
    val others: List<Notice> = emptyList(),
    val form: NoticeForm? = null,
    val userMessage: String? = null
)

class ManagerNoticesViewModel(
    private val noticeRepository: NoticeRepository = AppContainer.noticeRepository,
    private val sessionRepository: SessionRepository = AppContainer.sessionRepository
) : ViewModel() {

    private val form = MutableStateFlow<NoticeForm?>(null)
    private val userMessage = MutableStateFlow<String?>(null)

    val uiState: StateFlow<ManagerNoticesUiState> =
        combine(noticeRepository.observeNotices(), form, userMessage) { notices, form, message ->
            val sorted = notices.sortedByDescending { it.publishedAt }
            ManagerNoticesUiState(
                isLoading = false,
                pinned = sorted.filter { it.isPinned },
                others = sorted.filterNot { it.isPinned },
                form = form,
                userMessage = message
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ManagerNoticesUiState())

    fun onNewNotice() {
        form.value = NoticeForm()
    }

    fun onDismissForm() {
        form.value = null
    }

    fun onCategoryChanged(category: NoticeCategory) = form.update { it?.copy(category = category, error = null) }

    fun onTitleChanged(title: String) = form.update { it?.copy(title = title.take(MAX_TITLE), error = null) }

    fun onBodyChanged(body: String) = form.update { it?.copy(body = body.take(MAX_BODY), error = null) }

    fun onPinnedChanged(pinned: Boolean) = form.update { it?.copy(pinned = pinned) }

    fun onPublish() {
        val current = form.value ?: return
        val error = when {
            current.title.isBlank() -> "Informe um título."
            current.body.trim().length < MIN_BODY -> "Escreva o aviso com pelo menos $MIN_BODY caracteres."
            else -> null
        }
        if (error != null) {
            form.update { it?.copy(error = error) }
            return
        }
        form.update { it?.copy(isSubmitting = true) }
        viewModelScope.launch {
            val name = sessionRepository.currentUser.value?.name.orEmpty()
            noticeRepository.publish(
                title = current.title,
                body = current.body,
                category = current.category,
                pinned = current.pinned,
                author = if (name.isBlank()) "Síndico" else "Síndico - $name"
            )
            form.value = null
            userMessage.value = "Aviso publicado para todos os moradores."
        }
    }

    fun onDelete(notice: Notice) {
        viewModelScope.launch {
            noticeRepository.delete(notice.id)
            userMessage.value = "Aviso removido do mural."
        }
    }

    fun onMessageShown() {
        userMessage.value = null
    }

    companion object {
        const val MAX_TITLE = 80
        const val MAX_BODY = 1_000
        const val MIN_BODY = 10
    }
}
