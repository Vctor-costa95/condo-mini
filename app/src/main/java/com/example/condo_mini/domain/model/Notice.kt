package com.example.condo_mini.domain.model

import java.time.LocalDateTime

enum class NoticeCategory(val label: String) {
    MANAGER("Síndico"),
    IMPORTANT("Importante"),
    MAINTENANCE("Manutenção"),
    EVENT("Evento")
}

data class Notice(
    val id: String,
    val title: String,
    val body: String,
    val category: NoticeCategory,
    val author: String,
    val publishedAt: LocalDateTime,
    val isPinned: Boolean = false
)
