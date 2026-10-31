package com.example.condo_mini.ui.components

import android.content.Context
import android.content.Intent
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.PictureAsPdf
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import java.io.File

/**
 * Visualizador de PDF em tela cheia (boletos, documentos do condomínio): cabeçalho
 * com voltar, páginas com zoom e botão de compartilhar quando [onShareClick] existe.
 */
@Composable
fun PdfViewerScreen(
    eyebrow: String,
    title: String,
    isLoading: Boolean,
    error: String?,
    pages: List<ImageBitmap>,
    pageLabel: String,
    onBackClick: () -> Unit,
    onShareClick: (() -> Unit)?,
    modifier: Modifier = Modifier
) {
    val scheme = MaterialTheme.colorScheme
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(scheme.background)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(start = 8.dp, end = 20.dp, top = 12.dp, bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBackClick) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Voltar")
            }
            Spacer(modifier = Modifier.width(4.dp))
            Column(modifier = Modifier.weight(1f)) {
                Eyebrow(eyebrow)
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    title,
                    style = MaterialTheme.typography.headlineMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        HorizontalDivider(color = scheme.outlineVariant)

        when {
            isLoading -> LoadingState(modifier = Modifier.weight(1f))
            error != null || pages.isEmpty() -> EmptyState(
                icon = Icons.Outlined.PictureAsPdf,
                title = "PDF indisponível",
                message = error ?: "Tente novamente em instantes.",
                modifier = Modifier.weight(1f)
            )

            else -> ZoomableBox(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .background(scheme.surfaceContainer)
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    pages.forEachIndexed { index, page ->
                        Image(
                            bitmap = page,
                            contentDescription = "Página ${index + 1} $pageLabel",
                            contentScale = ContentScale.FillWidth,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(MaterialTheme.shapes.small)
                                .border(1.dp, scheme.outlineVariant, MaterialTheme.shapes.small)
                        )
                    }
                }
            }
        }

        onShareClick?.let { share ->
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(scheme.background)
                    .navigationBarsPadding()
                    .padding(horizontal = 20.dp, vertical = 12.dp)
            ) {
                Text(
                    "Toque duas vezes ou use pinça para ampliar.",
                    style = MaterialTheme.typography.bodySmall,
                    color = scheme.onSurfaceVariant,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 10.dp),
                    textAlign = TextAlign.Center
                )
                PrimaryButton(
                    text = "Compartilhar PDF",
                    onClick = share,
                    trailingIcon = Icons.Outlined.Share
                )
            }
        }
    }
}

/** Abre a folha de compartilhamento do sistema com o PDF (via FileProvider). */
fun sharePdf(context: Context, file: File, subject: String, chooserTitle: String) {
    val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "application/pdf"
        putExtra(Intent.EXTRA_STREAM, uri)
        putExtra(Intent.EXTRA_SUBJECT, subject)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    context.startActivity(Intent.createChooser(intent, chooserTitle))
}
