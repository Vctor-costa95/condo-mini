package com.example.condo_mini.ui.screens.manager.documents

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material.icons.outlined.PictureAsPdf
import androidx.compose.material.icons.outlined.UploadFile
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.condo_mini.domain.model.CondoDocument
import com.example.condo_mini.domain.model.DocumentCategory
import com.example.condo_mini.ui.components.EmptyState
import com.example.condo_mini.ui.components.Eyebrow
import com.example.condo_mini.ui.components.IconBadge
import com.example.condo_mini.ui.components.LoadingState
import com.example.condo_mini.ui.components.PillChip
import com.example.condo_mini.ui.components.PrimaryButton
import com.example.condo_mini.ui.components.SectionHeader
import com.example.condo_mini.ui.components.condoTextFieldColors
import com.example.condo_mini.ui.screens.documents.documentSections
import com.example.condo_mini.ui.screens.main.CondoSnackbarHost
import com.example.condo_mini.ui.util.formatDate

@Composable
fun ManagerDocumentsRoute(
    onOpenDocument: (CondoDocument) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ManagerDocumentsViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current
    val pickPdf = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) viewModel.onPdfPicked(uri.toString(), context.displayName(uri))
    }

    LaunchedEffect(uiState.userMessage) {
        uiState.userMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.onMessageShown()
        }
    }

    ManagerDocumentsScreen(
        uiState = uiState,
        snackbarHostState = snackbarHostState,
        onNewDocument = viewModel::onNewDocument,
        onOpenDocument = onOpenDocument,
        onDelete = viewModel::onDelete,
        modifier = modifier
    )

    uiState.form?.let { form ->
        NewDocumentSheet(
            form = form,
            current = uiState.currentOfFormCategory,
            onCategoryChanged = viewModel::onCategoryChanged,
            onTitleChanged = viewModel::onTitleChanged,
            onPickFile = { pickPdf.launch(arrayOf("application/pdf")) },
            onReplaceCurrentChanged = viewModel::onReplaceCurrentChanged,
            onPublish = viewModel::onPublish,
            onDismiss = viewModel::onDismissForm
        )
    }
}

@Composable
fun ManagerDocumentsScreen(
    uiState: ManagerDocumentsUiState,
    snackbarHostState: SnackbarHostState,
    onNewDocument: () -> Unit,
    onOpenDocument: (CondoDocument) -> Unit,
    onDelete: (CondoDocument) -> Unit,
    modifier: Modifier = Modifier
) {
    var toDelete by remember { mutableStateOf<CondoDocument?>(null) }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets(0),
        snackbarHost = { CondoSnackbarHost(snackbarHostState) },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = onNewDocument,
                icon = { Icon(Icons.Default.Add, contentDescription = null) },
                text = { Text("Novo documento", style = MaterialTheme.typography.labelLarge) },
                shape = MaterialTheme.shapes.medium,
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                elevation = FloatingActionButtonDefaults.elevation(defaultElevation = 2.dp)
            )
        }
    ) { innerPadding ->
        Box(modifier = Modifier.padding(innerPadding)) {
            when {
                uiState.isLoading -> LoadingState()
                uiState.documents.isEmpty() -> EmptyState(
                    icon = Icons.Outlined.FolderOpen,
                    title = "Nenhum documento",
                    message = "Publique o regimento, a convenção e as atas para os moradores consultarem pelo perfil."
                )

                else -> LazyColumn(
                    contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 104.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    item {
                        Text(
                            "Os moradores encontram estes documentos no perfil, em “Documentos do condomínio”.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp)
                        )
                    }
                    documentSections(
                        documents = uiState.documents,
                        onOpenDocument = onOpenDocument,
                        footer = { document ->
                            TextButton(
                                onClick = { toDelete = document },
                                contentPadding = PaddingValues(horizontal = 0.dp)
                            ) {
                                Text(
                                    "Excluir",
                                    style = MaterialTheme.typography.labelLarge,
                                    color = MaterialTheme.colorScheme.error
                                )
                            }
                        }
                    )
                }
            }
        }
    }

    toDelete?.let { document ->
        AlertDialog(
            onDismissRequest = { toDelete = null },
            containerColor = MaterialTheme.colorScheme.background,
            title = { Text("Excluir documento?", style = MaterialTheme.typography.headlineSmall) },
            text = { Text("“${document.title}” deixará de aparecer para os moradores.") },
            confirmButton = {
                TextButton(onClick = {
                    onDelete(document)
                    toDelete = null
                }) { Text("Excluir", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { toDelete = null }) {
                    Text("Manter", color = MaterialTheme.colorScheme.onSurface)
                }
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun NewDocumentSheet(
    form: DocumentForm,
    current: CondoDocument?,
    onCategoryChanged: (DocumentCategory) -> Unit,
    onTitleChanged: (String) -> Unit,
    onPickFile: () -> Unit,
    onReplaceCurrentChanged: (Boolean) -> Unit,
    onPublish: () -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.background
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp)
                .navigationBarsPadding()
                .imePadding()
                .padding(bottom = 16.dp)
        ) {
            Eyebrow("Documentos do condomínio")
            Spacer(modifier = Modifier.height(6.dp))
            Text("Novo documento", style = MaterialTheme.typography.headlineLarge)
            Spacer(modifier = Modifier.height(20.dp))

            SectionHeader("Tipo")
            Spacer(modifier = Modifier.height(4.dp))
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                DocumentCategory.entries.forEach { category ->
                    PillChip(selected = form.category == category, onClick = { onCategoryChanged(category) }) {
                        Text(category.label, style = MaterialTheme.typography.labelLarge)
                    }
                }
            }
            Spacer(modifier = Modifier.height(20.dp))

            FilePickerField(fileName = form.fileName, onClick = onPickFile)
            Spacer(modifier = Modifier.height(12.dp))

            OutlinedTextField(
                value = form.title,
                onValueChange = onTitleChanged,
                label = { Text("Título") },
                placeholder = if (form.category == DocumentCategory.MINUTES) {
                    { Text("Ex.: Ata da AGO · prestação de contas") }
                } else {
                    null
                },
                singleLine = true,
                shape = MaterialTheme.shapes.medium,
                colors = condoTextFieldColors(),
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                modifier = Modifier.fillMaxWidth()
            )

            if (current != null) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Substituir a versão atual", style = MaterialTheme.typography.titleSmall)
                        Text(
                            "Remove “${current.title}”, publicado em ${current.uploadedAt.toLocalDate().formatDate()}.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = form.replaceCurrent,
                        onCheckedChange = onReplaceCurrentChanged,
                        colors = SwitchDefaults.colors(
                            checkedTrackColor = MaterialTheme.colorScheme.primary,
                            checkedThumbColor = MaterialTheme.colorScheme.tertiaryContainer
                        )
                    )
                }
            }

            form.error?.let {
                Spacer(modifier = Modifier.height(8.dp))
                Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            }
            Spacer(modifier = Modifier.height(16.dp))
            PrimaryButton(text = "Publicar documento", onClick = onPublish, loading = form.isSubmitting)
        }
    }
}

/** Campo que abre o seletor de PDF do sistema e mostra o arquivo escolhido. */
@Composable
private fun FilePickerField(fileName: String?, onClick: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Surface(
        onClick = onClick,
        shape = MaterialTheme.shapes.medium,
        color = scheme.surfaceContainerLow,
        border = BorderStroke(1.dp, if (fileName != null) scheme.primary else scheme.outlineVariant),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconBadge(
                icon = if (fileName != null) Icons.Outlined.PictureAsPdf else Icons.Outlined.UploadFile,
                size = 40.dp,
                container = scheme.tertiaryContainer,
                tint = scheme.tertiary
            )
            Spacer(modifier = Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    fileName ?: "Escolher arquivo PDF",
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    if (fileName != null) "Toque para trocar o arquivo" else "Do celular, Drive ou e-mail",
                    style = MaterialTheme.typography.bodySmall,
                    color = scheme.onSurfaceVariant
                )
            }
        }
    }
}

private fun Context.displayName(uri: Uri): String? = runCatching {
    contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
        if (cursor.moveToFirst()) cursor.getString(0) else null
    }
}.getOrNull()
