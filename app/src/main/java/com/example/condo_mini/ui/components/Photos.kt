package com.example.condo_mini.ui.components

import android.content.Context
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.AddAPhoto
import androidx.compose.material.icons.outlined.PhotoCamera
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.FileProvider
import coil.compose.AsyncImage
import com.example.condo_mini.ui.theme.CondoTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

/** Limite de fotos por registro ou mensagem. */
const val MAX_ATTACHED_PHOTOS = 3

private const val PHOTOS_DIR = "occurrence_photos"

/**
 * Arquivos de foto no armazenamento interno do app. Quando houver backend,
 * estes arquivos são enviados no upload e a URL retornada substitui o URI local.
 */
private object PhotoFiles {

    fun newFile(context: Context): File =
        File(File(context.filesDir, PHOTOS_DIR).apply { mkdirs() }, "${UUID.randomUUID()}.jpg")

    fun providerUri(context: Context, file: File): Uri =
        FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)

    /** Copia a imagem da galeria: o URI do seletor pode perder a permissão depois. */
    suspend fun importFromGallery(context: Context, source: Uri): String? = withContext(Dispatchers.IO) {
        runCatching {
            val target = newFile(context)
            context.contentResolver.openInputStream(source)?.use { input ->
                target.outputStream().use { input.copyTo(it) }
            } ?: return@runCatching null
            Uri.fromFile(target).toString()
        }.getOrNull()
    }
}

/** Ações de captura: câmera do sistema ou seletor de fotos (nenhuma exige permissão). */
@Stable
class PhotoPicker internal constructor(
    val takePhoto: () -> Unit,
    val pickFromGallery: () -> Unit
)

@Composable
fun rememberPhotoPicker(onPhotoAdded: (String) -> Unit): PhotoPicker {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    // Caminho salvo para sobreviver à recriação da Activity enquanto a câmera está aberta.
    var pendingPath by rememberSaveable { mutableStateOf<String?>(null) }

    val camera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { saved ->
        val file = pendingPath?.let(::File)
        if (saved && file != null && file.length() > 0) {
            onPhotoAdded(Uri.fromFile(file).toString())
        } else {
            file?.delete()
        }
        pendingPath = null
    }
    val gallery = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) {
            scope.launch { PhotoFiles.importFromGallery(context, uri)?.let(onPhotoAdded) }
        }
    }

    return remember(camera, gallery) {
        PhotoPicker(
            takePhoto = {
                val file = PhotoFiles.newFile(context)
                pendingPath = file.absolutePath
                camera.launch(PhotoFiles.providerUri(context, file))
            },
            pickFromGallery = {
                gallery.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
            }
        )
    }
}

/**
 * Bloco de anexos para formulários: miniaturas removíveis + atalhos de câmera e galeria.
 */
@Composable
fun PhotoAttachmentRow(
    photos: List<String>,
    onAdd: (String) -> Unit,
    onRemove: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val picker = rememberPhotoPicker(onAdd)
    Column(modifier = modifier) {
        SectionHeader("Fotos", trailing = "${photos.size}/$MAX_ATTACHED_PHOTOS")
        Spacer(modifier = Modifier.height(4.dp))
        Row(
            modifier = Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            photos.forEach { RemovableThumbnail(it, onRemove = { onRemove(it) }) }
            if (photos.size < MAX_ATTACHED_PHOTOS) {
                AddPhotoTile(Icons.Outlined.PhotoCamera, "Câmera", picker.takePhoto)
                AddPhotoTile(Icons.Outlined.PhotoLibrary, "Galeria", picker.pickFromGallery)
            }
        }
    }
}

/** Botão de anexo compacto (para campos de resposta) com menu câmera/galeria. */
@Composable
fun AttachPhotoButton(
    enabled: Boolean,
    onAdd: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val picker = rememberPhotoPicker(onAdd)
    var menuOpen by remember { mutableStateOf(false) }
    Box(modifier = modifier) {
        IconButton(onClick = { menuOpen = true }, enabled = enabled) {
            Icon(
                Icons.Outlined.AddAPhoto,
                contentDescription = "Anexar foto",
                tint = if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.outline
            )
        }
        DropdownMenu(
            expanded = menuOpen,
            onDismissRequest = { menuOpen = false },
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        ) {
            DropdownMenuItem(
                text = { Text("Tirar foto") },
                leadingIcon = { Icon(Icons.Outlined.PhotoCamera, contentDescription = null) },
                onClick = {
                    menuOpen = false
                    picker.takePhoto()
                }
            )
            DropdownMenuItem(
                text = { Text("Escolher da galeria") },
                leadingIcon = { Icon(Icons.Outlined.PhotoLibrary, contentDescription = null) },
                onClick = {
                    menuOpen = false
                    picker.pickFromGallery()
                }
            )
        }
    }
}

@Composable
fun RemovableThumbnail(url: String, onRemove: () -> Unit, size: Dp = 72.dp) {
    Box(modifier = Modifier.size(size)) {
        PhotoThumbnail(url, size = size)
        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(4.dp)
                .size(22.dp)
                .clip(CircleShape)
                .background(Color.Black.copy(alpha = 0.6f))
                .clickable(onClick = onRemove),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Default.Close, contentDescription = "Remover foto", tint = Color.White, modifier = Modifier.size(14.dp))
        }
    }
}

@Composable
private fun AddPhotoTile(icon: ImageVector, label: String, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier.size(72.dp)
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.tertiary)
            Spacer(modifier = Modifier.height(4.dp))
            Text(label, style = MaterialTheme.typography.labelMedium)
        }
    }
}

@Composable
fun PhotoThumbnail(
    url: String,
    modifier: Modifier = Modifier,
    size: Dp = 72.dp,
    onClick: (() -> Unit)? = null
) {
    AsyncImage(
        model = url,
        contentDescription = "Foto anexada",
        contentScale = ContentScale.Crop,
        modifier = modifier
            .size(size)
            .clip(MaterialTheme.shapes.medium)
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
    )
}

/** Fileira de fotos somente leitura; tocar abre em tela cheia. */
@Composable
fun PhotoStrip(
    photos: List<String>,
    viewerTitle: String,
    modifier: Modifier = Modifier,
    size: Dp = 72.dp
) {
    if (photos.isEmpty()) return
    var openIndex by rememberSaveable { mutableStateOf<Int?>(null) }
    Row(
        modifier = modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        photos.forEachIndexed { index, url ->
            PhotoThumbnail(url, size = size, onClick = { openIndex = index })
        }
    }
    openIndex?.let { index ->
        photos.getOrNull(index)?.let { url ->
            PhotoViewerDialog(
                url = url,
                eyebrow = if (photos.size > 1) "Foto ${index + 1} de ${photos.size}" else "Foto anexada",
                title = viewerTitle,
                onDismiss = { openIndex = null }
            )
        }
    }
}

/** Foto em tela cheia sobre fundo escuro, com zoom e legenda opcional. */
@Composable
fun PhotoViewerDialog(
    url: String?,
    eyebrow: String,
    title: String,
    onDismiss: () -> Unit,
    caption: (@Composable () -> Unit)? = null
) {
    val condo = CondoTheme.colors
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFF0B0D12))
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(start = 24.dp, end = 8.dp, top = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Eyebrow(eyebrow, color = condo.accent)
                    Text(title, style = MaterialTheme.typography.headlineSmall, color = condo.onHero)
                }
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Default.Close, contentDescription = "Fechar", tint = condo.onHero)
                }
            }
            ZoomableBox(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) {
                AsyncImage(
                    model = url,
                    contentDescription = title,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxWidth()
                )
            }
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(horizontal = 24.dp, vertical = 20.dp)
            ) {
                caption?.invoke()
            }
        }
    }
}
