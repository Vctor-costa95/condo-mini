package com.example.condo_mini.ui.screens.notices

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Campaign
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.condo_mini.domain.model.Notice
import com.example.condo_mini.domain.model.NoticeCategory
import com.example.condo_mini.ui.components.CondoCard
import com.example.condo_mini.ui.components.EmptyState
import com.example.condo_mini.ui.components.Eyebrow
import com.example.condo_mini.ui.components.HeroCard
import com.example.condo_mini.ui.components.LoadingState
import com.example.condo_mini.ui.components.PillChip
import com.example.condo_mini.ui.components.SectionHeader
import com.example.condo_mini.ui.components.StatusChip
import com.example.condo_mini.ui.components.StatusTone
import com.example.condo_mini.ui.theme.CondoTheme
import com.example.condo_mini.ui.theme.CondominiTheme
import com.example.condo_mini.ui.util.relative
import java.time.LocalDateTime

@Composable
fun NoticesRoute(
    modifier: Modifier = Modifier,
    viewModel: NoticesViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    NoticesScreen(
        uiState = uiState,
        onCategorySelected = viewModel::onCategorySelected,
        modifier = modifier
    )
}

@Composable
fun NoticesScreen(
    uiState: NoticesUiState,
    onCategorySelected: (NoticeCategory?) -> Unit,
    modifier: Modifier = Modifier
) {
    if (uiState.isLoading) {
        LoadingState(modifier)
        return
    }
    val featured = uiState.pinned.firstOrNull()
    val otherPinned = uiState.pinned.drop(1)

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            CategoryFilter(selected = uiState.selectedCategory, onSelected = onCategorySelected)
        }
        if (featured == null && uiState.others.isEmpty()) {
            item {
                EmptyState(
                    icon = Icons.Default.Campaign,
                    title = "Nenhum aviso por aqui",
                    message = "Quando a administração publicar um comunicado, ele aparecerá nesta tela.",
                    modifier = Modifier.padding(top = 48.dp)
                )
            }
        }
        featured?.let {
            item(key = it.id) { FeaturedNotice(it, modifier = Modifier.padding(horizontal = 20.dp)) }
        }
        items(otherPinned, key = { it.id }) {
            NoticeCard(it, modifier = Modifier.padding(horizontal = 20.dp))
        }
        if (uiState.others.isNotEmpty()) {
            item { SectionHeader("Recentes", modifier = Modifier.padding(horizontal = 24.dp)) }
            items(uiState.others, key = { it.id }) {
                NoticeCard(it, modifier = Modifier.padding(horizontal = 20.dp))
            }
        }
    }
}

@Composable
private fun CategoryFilter(
    selected: NoticeCategory?,
    onSelected: (NoticeCategory?) -> Unit
) {
    LazyRow(
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        item {
            PillChip(selected = selected == null, onClick = { onSelected(null) }) {
                Text("Todos", style = MaterialTheme.typography.labelLarge)
            }
        }
        items(NoticeCategory.entries) { category ->
            PillChip(selected = selected == category, onClick = { onSelected(category) }) {
                Text(category.label, style = MaterialTheme.typography.labelLarge)
            }
        }
    }
}

/** Primeiro aviso fixado ganha destaque como cartão escuro. */
@Composable
internal fun FeaturedNotice(
    notice: Notice,
    modifier: Modifier = Modifier,
    footer: (@Composable () -> Unit)? = null
) {
    var expanded by rememberSaveable(notice.id) { mutableStateOf(false) }
    val condo = CondoTheme.colors
    HeroCard(onClick = { expanded = !expanded }, modifier = modifier.animateContentSize()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Eyebrow("Em destaque · ${notice.category.label}", color = condo.accent, modifier = Modifier.weight(1f))
            Text(notice.publishedAt.relative(), style = MaterialTheme.typography.labelMedium, color = condo.onHeroMuted)
        }
        Spacer(modifier = Modifier.height(16.dp))
        Text(notice.title, style = MaterialTheme.typography.headlineMedium, color = condo.onHero)
        Spacer(modifier = Modifier.height(10.dp))
        Text(
            notice.body,
            style = MaterialTheme.typography.bodyMedium,
            color = condo.onHeroMuted,
            maxLines = if (expanded) Int.MAX_VALUE else 3,
            overflow = TextOverflow.Ellipsis
        )
        Spacer(modifier = Modifier.height(20.dp))
        Box(
            modifier = Modifier
                .width(32.dp)
                .height(1.dp)
                .background(condo.accent)
        )
        Spacer(modifier = Modifier.height(10.dp))
        Text(notice.author, style = MaterialTheme.typography.labelLarge, color = condo.onHero)
        if (expanded && footer != null) {
            Spacer(modifier = Modifier.height(12.dp))
            footer()
        }
    }
}

/** [footer] aparece com o cartão expandido (ex.: ações do síndico). */
@Composable
internal fun NoticeCard(
    notice: Notice,
    modifier: Modifier = Modifier,
    footer: (@Composable () -> Unit)? = null
) {
    var expanded by rememberSaveable(notice.id) { mutableStateOf(false) }
    CondoCard(onClick = { expanded = !expanded }, modifier = modifier.animateContentSize()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            StatusChip(text = notice.category.label, tone = notice.category.tone())
            if (notice.isPinned) {
                Spacer(modifier = Modifier.width(8.dp))
                Eyebrow("Fixado")
            }
            Spacer(modifier = Modifier.weight(1f))
            Text(
                text = notice.publishedAt.relative(),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.outline
            )
        }
        Spacer(modifier = Modifier.height(14.dp))
        Text(text = notice.title, style = MaterialTheme.typography.titleLarge)
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = notice.body,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = if (expanded) Int.MAX_VALUE else 2,
            overflow = TextOverflow.Ellipsis
        )
        Spacer(modifier = Modifier.height(14.dp))
        Text(
            text = notice.author,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurface
        )
        if (expanded && footer != null) {
            Spacer(modifier = Modifier.height(8.dp))
            footer()
        }
    }
}

internal fun NoticeCategory.tone(): StatusTone = when (this) {
    NoticeCategory.MANAGER -> StatusTone.INFO
    NoticeCategory.IMPORTANT -> StatusTone.ERROR
    NoticeCategory.MAINTENANCE -> StatusTone.WARNING
    NoticeCategory.EVENT -> StatusTone.ACCENT
}

@Preview(showBackground = true)
@Composable
private fun NoticesScreenPreview() {
    CondominiTheme {
        NoticesScreen(
            uiState = NoticesUiState(
                isLoading = false,
                pinned = listOf(
                    Notice("1", "Assembleia geral", "Convocamos todos os condôminos...", NoticeCategory.MANAGER, "Síndico", LocalDateTime.now(), true)
                ),
                others = listOf(
                    Notice("2", "Festa junina", "Venha participar...", NoticeCategory.EVENT, "Comissão", LocalDateTime.now().minusDays(2))
                )
            ),
            onCategorySelected = {}
        )
    }
}
