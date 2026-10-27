package com.example.condo_mini.ui.screens.admin.units

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.PersonAdd
import androidx.compose.material.icons.outlined.PictureAsPdf
import androidx.compose.material.icons.outlined.UploadFile
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.condo_mini.domain.model.CondoUnit
import com.example.condo_mini.domain.model.UnitDocument
import com.example.condo_mini.domain.model.User
import com.example.condo_mini.ui.components.CondoCard
import com.example.condo_mini.ui.components.Eyebrow
import com.example.condo_mini.ui.components.InitialsAvatar
import com.example.condo_mini.ui.components.LoadingState
import com.example.condo_mini.ui.components.PillChip
import com.example.condo_mini.ui.components.SectionHeader
import com.example.condo_mini.ui.components.StatusChip
import com.example.condo_mini.ui.components.StatusTone
import com.example.condo_mini.ui.util.formatDateTime
import com.example.condo_mini.ui.util.formatFileSize
import com.example.condo_mini.ui.util.formatMonth
import com.example.condo_mini.ui.util.monthName
import java.time.YearMonth

@Composable
fun AdminUnitsRoute(
    snackbarHostState: SnackbarHostState,
    modifier: Modifier = Modifier,
    viewModel: AdminUnitsViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    // Unidade que receberá o PDF escolhido; sobrevive à ida ao seletor de arquivos.
    var pdfTargetId by rememberSaveable { mutableStateOf<String?>(null) }
    val pickPdf = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        val target = pdfTargetId
        pdfTargetId = null
        if (uri == null || target == null) return@rememberLauncherForActivityResult
        val unit = uiState.rows.firstOrNull { it.unit.id == target }?.unit
            ?: uiState.detail?.unit?.takeIf { it.id == target }
        unit?.let { viewModel.onPdfPicked(it, uri.toString()) }
    }
    val onUploadPdf: (CondoUnit) -> Unit = { unit ->
        pdfTargetId = unit.id
        pickPdf.launch(arrayOf("application/pdf"))
    }

    LaunchedEffect(uiState.userMessage) {
        uiState.userMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.onMessageShown()
        }
    }

    AdminUnitsScreen(
        uiState = uiState,
        onMonthSelected = viewModel::onMonthSelected,
        onBlockSelected = viewModel::onBlockSelected,
        onOpenUnit = viewModel::onOpenUnit,
        onUploadPdf = onUploadPdf,
        modifier = modifier
    )

    uiState.detail?.let { detail ->
        UnitDetailSheet(
            detail = detail,
            monthName = uiState.month.monthName(),
            uploading = detail.unit.id in uiState.uploading,
            onUploadPdf = { onUploadPdf(detail.unit) },
            onDeleteDocument = viewModel::onDeleteDocument,
            onAssign = { viewModel.onAssign(it, detail.unit) },
            onUnassign = viewModel::onUnassign,
            onDismiss = viewModel::onCloseUnit
        )
    }
}

@Composable
fun AdminUnitsScreen(
    uiState: AdminUnitsUiState,
    onMonthSelected: (YearMonth) -> Unit,
    onBlockSelected: (String) -> Unit,
    onOpenUnit: (CondoUnit) -> Unit,
    onUploadPdf: (CondoUnit) -> Unit,
    modifier: Modifier = Modifier
) {
    if (uiState.isLoading) {
        LoadingState(modifier)
        return
    }
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            Column(modifier = Modifier.padding(horizontal = 20.dp)) {
                MonthProgress(uiState)
            }
        }
        item {
            LazyRow(
                contentPadding = PaddingValues(horizontal = 20.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(uiState.months) { month ->
                    PillChip(selected = month == uiState.month, onClick = { onMonthSelected(month) }) {
                        Text(month.monthName().replaceFirstChar { it.uppercase() }, style = MaterialTheme.typography.labelLarge)
                    }
                }
            }
        }
        item {
            Row(
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                uiState.blocks.forEach { block ->
                    PillChip(
                        selected = block == uiState.block,
                        onClick = { onBlockSelected(block) },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Bloco $block", style = MaterialTheme.typography.labelLarge)
                    }
                }
            }
        }
        items(uiState.rows, key = { it.unit.id }) { row ->
            UnitRowCard(
                row = row,
                uploading = row.unit.id in uiState.uploading,
                onClick = { onOpenUnit(row.unit) },
                onUploadPdf = { onUploadPdf(row.unit) },
                modifier = Modifier.padding(horizontal = 20.dp)
            )
        }
    }
}

@Composable
private fun MonthProgress(uiState: AdminUnitsUiState) {
    val progress = if (uiState.totalUnits == 0) 0f else uiState.sentInMonth / uiState.totalUnits.toFloat()
    CondoCard(contentPadding = PaddingValues(20.dp)) {
        Eyebrow("Boletos de ${uiState.month.monthName()}")
        Spacer(modifier = Modifier.height(6.dp))
        Row(verticalAlignment = Alignment.Bottom) {
            Text("${uiState.sentInMonth}", style = MaterialTheme.typography.displaySmall)
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                "de ${uiState.totalUnits} unidades com PDF",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 6.dp)
            )
        }
        Spacer(modifier = Modifier.height(12.dp))
        LinearProgressIndicator(
            progress = { progress },
            color = MaterialTheme.colorScheme.tertiary,
            trackColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            strokeCap = StrokeCap.Round,
            drawStopIndicator = {},
            modifier = Modifier
                .fillMaxWidth()
                .height(6.dp)
        )
    }
}

@Composable
private fun UnitRowCard(
    row: UnitRow,
    uploading: Boolean,
    onClick: () -> Unit,
    onUploadPdf: () -> Unit,
    modifier: Modifier = Modifier
) {
    CondoCard(onClick = onClick, contentPadding = PaddingValues(start = 18.dp, end = 10.dp, top = 14.dp, bottom = 14.dp), modifier = modifier) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(row.unit.number, style = MaterialTheme.typography.headlineSmall, modifier = Modifier.width(56.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    row.residents.joinToString { it.name }.ifBlank { "Sem moradores" },
                    style = MaterialTheme.typography.titleSmall,
                    color = if (row.residents.isEmpty()) MaterialTheme.colorScheme.outline else MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    when (row.residents.size) {
                        0 -> "Toque para alocar"
                        1 -> "1 morador"
                        else -> "${row.residents.size} moradores"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            when {
                uploading -> CircularProgressIndicator(
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.tertiary,
                    modifier = Modifier
                        .padding(horizontal = 18.dp)
                        .size(20.dp)
                )
                row.document != null -> StatusChip("PDF enviado", StatusTone.SUCCESS, Modifier.padding(end = 8.dp))
                else -> TextButton(onClick = onUploadPdf) {
                    Icon(Icons.Outlined.UploadFile, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Enviar PDF", style = MaterialTheme.typography.labelLarge)
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun UnitDetailSheet(
    detail: UnitDetail,
    monthName: String,
    uploading: Boolean,
    onUploadPdf: () -> Unit,
    onDeleteDocument: (UnitDocument) -> Unit,
    onAssign: (User) -> Unit,
    onUnassign: (User) -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var choosingResident by rememberSaveable(detail.unit.id) { mutableStateOf(false) }

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
                .padding(bottom = 16.dp)
        ) {
            Eyebrow("Bloco ${detail.unit.block}")
            Spacer(modifier = Modifier.height(6.dp))
            Text("Apto ${detail.unit.number}", style = MaterialTheme.typography.headlineLarge)
            Spacer(modifier = Modifier.height(12.dp))

            SectionHeader("Moradores", trailing = "${detail.residents.size}")
            if (detail.residents.isEmpty()) {
                Text(
                    "Nenhum morador alocado nesta unidade.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 8.dp)
                )
            }
            detail.residents.forEach { resident ->
                PersonRow(resident) {
                    TextButton(onClick = { onUnassign(resident) }) {
                        Text("Desalocar", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.labelLarge)
                    }
                }
            }
            Spacer(modifier = Modifier.height(4.dp))
            if (!choosingResident) {
                OutlinedButton(
                    onClick = { choosingResident = true },
                    shape = MaterialTheme.shapes.medium,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Outlined.PersonAdd, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Alocar morador", style = MaterialTheme.typography.labelLarge)
                }
            } else {
                CondoCard(contentPadding = PaddingValues(vertical = 8.dp)) {
                    Text(
                        "Moradores sem unidade",
                        style = MaterialTheme.typography.titleSmall,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                    )
                    if (detail.unassigned.isEmpty()) {
                        Text(
                            "Todos os moradores já têm unidade. Cadastre novos na aba Cadastros.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                        )
                    }
                    detail.unassigned.forEach { candidate ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    onAssign(candidate)
                                    choosingResident = false
                                }
                                .padding(horizontal = 16.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            InitialsAvatar(candidate.name, size = 36.dp)
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(candidate.name, style = MaterialTheme.typography.titleSmall)
                                Text(
                                    candidate.email,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Icon(
                                Icons.Outlined.PersonAdd,
                                contentDescription = "Alocar ${candidate.name}",
                                tint = MaterialTheme.colorScheme.tertiary
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
            SectionHeader("Boletos em PDF", trailing = "${detail.documents.size}")
            Spacer(modifier = Modifier.height(4.dp))
            OutlinedButton(
                onClick = onUploadPdf,
                enabled = !uploading,
                shape = MaterialTheme.shapes.medium,
                modifier = Modifier.fillMaxWidth()
            ) {
                if (uploading) {
                    CircularProgressIndicator(strokeWidth = 2.dp, modifier = Modifier.size(16.dp))
                } else {
                    Icon(Icons.Outlined.UploadFile, contentDescription = null, modifier = Modifier.size(18.dp))
                }
                Spacer(modifier = Modifier.width(8.dp))
                Text("Enviar PDF de $monthName", style = MaterialTheme.typography.labelLarge)
            }
            if (detail.documents.isEmpty()) {
                Text(
                    "Nenhum PDF enviado. Sem ele, o morador vê o boleto padrão do sistema.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 10.dp)
                )
            }
            detail.documents.forEachIndexed { index, document ->
                if (index > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                Row(
                    modifier = Modifier.padding(vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Outlined.PictureAsPdf, contentDescription = null, tint = MaterialTheme.colorScheme.tertiary)
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(document.reference.formatMonth(), style = MaterialTheme.typography.titleSmall)
                        Text(
                            "${document.fileName} · ${document.sizeBytes.formatFileSize()}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            "Enviado em ${document.uploadedAt.formatDateTime()}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.outline
                        )
                    }
                    IconButton(onClick = { onDeleteDocument(document) }) {
                        Icon(
                            Icons.Outlined.Delete,
                            contentDescription = "Remover PDF de ${document.reference.formatMonth()}",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

@Composable
internal fun PersonRow(user: User, trailing: @Composable () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        InitialsAvatar(user.name, size = 40.dp)
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(user.name, style = MaterialTheme.typography.titleSmall)
            Text(
                listOf(user.email, user.phone).filter { it.isNotBlank() }.joinToString(" · "),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        trailing()
    }
}
