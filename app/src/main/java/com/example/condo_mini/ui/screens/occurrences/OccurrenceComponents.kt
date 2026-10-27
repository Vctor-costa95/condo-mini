package com.example.condo_mini.ui.screens.occurrences

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import com.example.condo_mini.domain.model.Occurrence
import com.example.condo_mini.domain.model.OccurrenceMessage
import com.example.condo_mini.domain.model.OccurrenceStatus
import com.example.condo_mini.ui.components.AttachPhotoButton
import com.example.condo_mini.ui.components.Eyebrow
import com.example.condo_mini.ui.components.MAX_ATTACHED_PHOTOS
import com.example.condo_mini.ui.components.PhotoStrip
import com.example.condo_mini.ui.components.RemovableThumbnail
import com.example.condo_mini.ui.components.StatusTone
import com.example.condo_mini.ui.components.condoTextFieldColors
import com.example.condo_mini.ui.util.formatDateTime

internal fun OccurrenceStatus.tone(): StatusTone = when (this) {
    OccurrenceStatus.OPEN -> StatusTone.INFO
    OccurrenceStatus.IN_REVIEW -> StatusTone.WARNING
    OccurrenceStatus.NEEDS_INFO -> StatusTone.ACCENT
    OccurrenceStatus.RESOLVED -> StatusTone.SUCCESS
}

/**
 * Relato original + mensagens. Administração à esquerda (champanhe),
 * morador à direita (neutro), como em um chat discreto.
 */
@Composable
internal fun OccurrenceThread(occurrence: Occurrence, modifier: Modifier = Modifier) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        occurrence.messages.forEach { MessageBubble(it) }
    }
}

@Composable
private fun MessageBubble(message: OccurrenceMessage) {
    val scheme = MaterialTheme.colorScheme
    val fromManagement = message.fromManagement
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (fromManagement) Arrangement.Start else Arrangement.End
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = 300.dp)
                .clip(MaterialTheme.shapes.medium)
                .background(if (fromManagement) scheme.tertiaryContainer else scheme.surfaceContainer)
                .padding(14.dp)
        ) {
            Eyebrow(
                if (fromManagement) message.author.ifBlank { "Administração" } else "Morador · ${message.author}",
                color = if (fromManagement) scheme.onTertiaryContainer else scheme.onSurfaceVariant
            )
            if (message.text.isNotBlank()) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(message.text, style = MaterialTheme.typography.bodyMedium)
            }
            if (message.photoUrls.isNotEmpty()) {
                Spacer(modifier = Modifier.height(8.dp))
                PhotoStrip(message.photoUrls, viewerTitle = message.author.ifBlank { "Mensagem" }, size = 64.dp)
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                message.sentAt.formatDateTime(),
                style = MaterialTheme.typography.labelMedium,
                color = scheme.outline
            )
        }
    }
}

/** Campo de texto com anexo de fotos e botão de envio redondo. Envia texto, fotos ou ambos. */
@Composable
internal fun ReplyField(
    placeholder: String,
    onSend: (text: String, photoUrls: List<String>) -> Unit,
    modifier: Modifier = Modifier,
    sending: Boolean = false
) {
    var text by rememberSaveable { mutableStateOf("") }
    var photos by rememberSaveable { mutableStateOf(listOf<String>()) }
    val canSend = (text.isNotBlank() || photos.isNotEmpty()) && !sending

    if (photos.isNotEmpty()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            photos.forEach { url -> RemovableThumbnail(url, onRemove = { photos = photos - url }, size = 64.dp) }
        }
    }
    Row(modifier = modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        AttachPhotoButton(
            enabled = photos.size < MAX_ATTACHED_PHOTOS && !sending,
            onAdd = { url -> photos = photos + url }
        )
        OutlinedTextField(
            value = text,
            onValueChange = { text = it.take(500) },
            placeholder = { Text(placeholder) },
            shape = MaterialTheme.shapes.medium,
            colors = condoTextFieldColors(),
            maxLines = 4,
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
            modifier = Modifier.weight(1f)
        )
        Spacer(modifier = Modifier.width(8.dp))
        FilledIconButton(
            onClick = {
                onSend(text, photos)
                text = ""
                photos = emptyList()
            },
            enabled = canSend,
            colors = IconButtonDefaults.filledIconButtonColors(
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary
            ),
            modifier = Modifier.size(52.dp)
        ) {
            if (sending) {
                CircularProgressIndicator(
                    modifier = Modifier.size(18.dp),
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.onPrimary
                )
            } else {
                Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Enviar")
            }
        }
    }
}
