package com.example.condo_mini.ui.screens.parcels

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.outlined.Inventory2
import androidx.compose.material.icons.outlined.PhotoCamera
import androidx.compose.material.icons.outlined.TaskAlt
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.example.condo_mini.domain.model.Parcel
import com.example.condo_mini.domain.model.ParcelStatus
import com.example.condo_mini.ui.components.CondoCard
import com.example.condo_mini.ui.components.EmptyState
import com.example.condo_mini.ui.components.Eyebrow
import com.example.condo_mini.ui.components.IconBadge
import com.example.condo_mini.ui.components.LoadingState
import com.example.condo_mini.ui.components.PhotoViewerDialog
import com.example.condo_mini.ui.components.PillChip
import com.example.condo_mini.ui.theme.CondoTheme
import com.example.condo_mini.ui.util.formatDateTime
import com.example.condo_mini.ui.util.relative

@Composable
fun ParcelsRoute(
    modifier: Modifier = Modifier,
    viewModel: ParcelsViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    ParcelsScreen(uiState = uiState, modifier = modifier)
}

@Composable
fun ParcelsScreen(
    uiState: ParcelsUiState,
    modifier: Modifier = Modifier
) {
    var selectedTab by rememberSaveable { mutableIntStateOf(0) }
    var photoParcelId by rememberSaveable { mutableStateOf<String?>(null) }

    Column(modifier = modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            PillChip(selected = selectedTab == 0, onClick = { selectedTab = 0 }) {
                Text("Aguardando · ${uiState.waiting.size}", style = MaterialTheme.typography.labelLarge)
            }
            PillChip(selected = selectedTab == 1, onClick = { selectedTab = 1 }) {
                Text("Retiradas", style = MaterialTheme.typography.labelLarge)
            }
        }
        val parcels = if (selectedTab == 0) uiState.waiting else uiState.pickedUp
        when {
            uiState.isLoading -> LoadingState()
            parcels.isEmpty() -> EmptyState(
                icon = Icons.Default.Inventory2,
                title = if (selectedTab == 0) "Nada aguardando" else "Nenhuma retirada ainda",
                message = "Avisaremos assim que a portaria receber algo para você."
            )

            else -> LazyColumn(
                contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 32.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                if (selectedTab == 0) {
                    item {
                        Text(
                            "Apresente o código na portaria para retirar. Toque para ver a foto.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp)
                        )
                    }
                }
                items(parcels, key = { it.id }) { parcel ->
                    ParcelCard(
                        parcel = parcel,
                        onClick = parcel.photoUrl?.let { { photoParcelId = parcel.id } }
                    )
                }
            }
        }
    }

    (uiState.waiting + uiState.pickedUp)
        .firstOrNull { it.id == photoParcelId }
        ?.let { parcel -> ParcelPhotoViewer(parcel = parcel, onDismiss = { photoParcelId = null }) }
}

@Composable
private fun ParcelCard(parcel: Parcel, onClick: (() -> Unit)?) {
    val waiting = parcel.status == ParcelStatus.WAITING
    val scheme = MaterialTheme.colorScheme
    CondoCard(onClick = onClick, contentPadding = PaddingValues(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (parcel.photoUrl != null) {
                ParcelThumbnail(parcel.photoUrl)
            } else {
                IconBadge(
                    icon = if (waiting) Icons.Outlined.Inventory2 else Icons.Outlined.TaskAlt,
                    size = 64.dp,
                    container = if (waiting) scheme.tertiaryContainer else scheme.surfaceContainer,
                    tint = if (waiting) scheme.onTertiaryContainer else scheme.onSurfaceVariant
                )
            }
            Spacer(modifier = Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Eyebrow(parcel.carrier, color = scheme.onSurfaceVariant)
                Spacer(modifier = Modifier.height(2.dp))
                Text(parcel.description, style = MaterialTheme.typography.titleLarge)
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    if (waiting) {
                        "Chegou ${parcel.arrivedAt.relative()} · ${parcel.receivedBy}"
                    } else {
                        "Retirada em ${parcel.pickedUpAt?.formatDateTime().orEmpty()}"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = scheme.onSurfaceVariant
                )
            }
            if (waiting) {
                Spacer(modifier = Modifier.width(12.dp))
                Column(
                    modifier = Modifier
                        .border(1.dp, scheme.tertiary, MaterialTheme.shapes.medium)
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Eyebrow("Código")
                    Text(parcel.pickupCode, style = MaterialTheme.typography.headlineSmall)
                }
            }
        }
    }
}

@Composable
private fun ParcelThumbnail(url: String) {
    Box(modifier = Modifier.size(64.dp)) {
        AsyncImage(
            model = url,
            contentDescription = "Foto da encomenda",
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .fillMaxSize()
                .clip(MaterialTheme.shapes.medium)
                .background(MaterialTheme.colorScheme.surfaceContainer)
        )
        // Selo de câmera indica que há foto para abrir.
        Box(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(4.dp)
                .size(22.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primary),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                Icons.Outlined.PhotoCamera,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier.size(13.dp)
            )
        }
    }
}

@Composable
private fun ParcelPhotoViewer(parcel: Parcel, onDismiss: () -> Unit) {
    val condo = CondoTheme.colors
    PhotoViewerDialog(
        url = parcel.photoUrl,
        eyebrow = "Foto da portaria",
        title = parcel.description,
        onDismiss = onDismiss
    ) {
        Text(
            "${parcel.carrier} · recebida por ${parcel.receivedBy}",
            style = MaterialTheme.typography.bodyMedium,
            color = condo.onHero
        )
        Text(
            "${parcel.arrivedAt.formatDateTime()} · Código de retirada ${parcel.pickupCode}",
            style = MaterialTheme.typography.bodySmall,
            color = condo.onHeroMuted
        )
    }
}
