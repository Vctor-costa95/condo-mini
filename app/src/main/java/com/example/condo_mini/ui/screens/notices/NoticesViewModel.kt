package com.example.condo_mini.ui.screens.notices

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.condo_mini.data.AppContainer
import com.example.condo_mini.domain.model.Notice
import com.example.condo_mini.domain.model.NoticeCategory
import com.example.condo_mini.domain.repository.NoticeRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

data class NoticesUiState(
    val isLoading: Boolean = true,
    val selectedCategory: NoticeCategory? = null,
    val pinned: List<Notice> = emptyList(),
    val others: List<Notice> = emptyList()
)

class NoticesViewModel(
    noticeRepository: NoticeRepository = AppContainer.noticeRepository
) : ViewModel() {

    private val selectedCategory = MutableStateFlow<NoticeCategory?>(null)

    val uiState: StateFlow<NoticesUiState> =
        combine(noticeRepository.observeNotices(), selectedCategory) { notices, category ->
            val filtered = notices
                .filter { category == null || it.category == category }
                .sortedByDescending { it.publishedAt }
            NoticesUiState(
                isLoading = false,
                selectedCategory = category,
                pinned = filtered.filter { it.isPinned },
                others = filtered.filterNot { it.isPinned }
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), NoticesUiState())

    fun onCategorySelected(category: NoticeCategory?) {
        selectedCategory.value = category
    }
}
