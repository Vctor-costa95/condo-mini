package com.example.condo_mini.ui.util

import java.text.NumberFormat
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale

private val ptBr: Locale = Locale.forLanguageTag("pt-BR")
private val currency: NumberFormat = NumberFormat.getCurrencyInstance(ptBr)
private val dateFormatter = DateTimeFormatter.ofPattern("dd/MM/yyyy", ptBr)
private val dateTimeFormatter = DateTimeFormatter.ofPattern("dd/MM/yyyy 'às' HH:mm", ptBr)
private val weekdayFormatter = DateTimeFormatter.ofPattern("EEE, dd 'de' MMM", ptBr)
private val monthFormatter = DateTimeFormatter.ofPattern("MMMM 'de' yyyy", ptBr)

fun Double.toBrl(): String = currency.format(this)

private val weekdayShortFormatter = DateTimeFormatter.ofPattern("EEE", ptBr)
private val monthShortFormatter = DateTimeFormatter.ofPattern("MMM", ptBr)

fun LocalDate.formatDate(): String = format(dateFormatter)

/** "SEG", "TER"... */
fun LocalDate.weekdayShort(): String = format(weekdayShortFormatter).trimEnd('.').uppercase(ptBr)

/** "OUT", "NOV"... */
fun LocalDate.monthShort(): String = format(monthShortFormatter).trimEnd('.').uppercase(ptBr)

fun LocalDate.formatWeekday(): String = format(weekdayFormatter).replaceFirstChar { it.titlecase(ptBr) }

fun LocalDateTime.formatDateTime(): String = format(dateTimeFormatter)

fun YearMonth.formatMonth(): String = format(monthFormatter).replaceFirstChar { it.titlecase(ptBr) }

private val monthNameFormatter = DateTimeFormatter.ofPattern("MMMM", ptBr)

/** "dezembro" */
fun YearMonth.monthName(): String = format(monthNameFormatter)

/** "1,2 MB", "340 KB" */
fun Long.formatFileSize(): String = when {
    this >= 1_048_576 -> String.format(ptBr, "%.1f MB", this / 1_048_576.0)
    else -> "${(this / 1024).coerceAtLeast(1)} KB"
}

/** "há 3 horas", "ontem", "há 5 dias"... */
fun LocalDateTime.relative(now: LocalDateTime = LocalDateTime.now()): String {
    val minutes = ChronoUnit.MINUTES.between(this, now)
    val hours = ChronoUnit.HOURS.between(this, now)
    val days = ChronoUnit.DAYS.between(toLocalDate(), now.toLocalDate())
    return when {
        minutes < 1 -> "agora"
        minutes < 60 -> "há $minutes min"
        days == 0L -> "há $hours h"
        days == 1L -> "ontem"
        days < 30 -> "há $days dias"
        else -> toLocalDate().formatDate()
    }
}
