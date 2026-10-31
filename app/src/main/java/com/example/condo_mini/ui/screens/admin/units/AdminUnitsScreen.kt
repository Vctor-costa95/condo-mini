package com.example.condo_mini.ui.screens.admin.units

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Apartment
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
import androidx.compose.material3.Surface
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.condo_mini.domain.model.CondoUnit
import com.example.condo_mini.domain.model.GROUND_FLOOR
import com.example.condo_mini.domain.model.Tower
import com.example.condo_mini.domain.model.UnitDocument
import com.example.condo_mini.domain.model.User
import com.example.condo_mini.ui.components.CondoCard
import com.example.condo_mini.ui.components.EmptyState
import com.example.condo_mini.ui.components.Eyebrow
import com.example.condo_mini.ui.components.HeroCard
import com.example.condo_mini.ui.components.InitialsAvatar
import com.example.condo_mini.ui.components.LoadingState
import com.example.condo_mini.ui.components.PillChip
import com.example.condo_mini.ui.components.PrimaryButton
import com.example.condo_mini.ui.components.SectionHeader
import com.example.condo_mini.ui.theme.CondoTheme
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
        uiState.condo?.units?.firstOrNull { it.id == target }?.let { viewModel.onPdfPicked(it, uri.toString()) }
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
        onTowerSelected = viewModel::onTowerSelected,
        onOpenUnit = viewModel::onOpenUnit,
        modifier = modifier
    )

    uiState.detail?.let { detail ->
        UnitDetailSheet(
            detail = detail,
            month = uiState.month,
            uploading = detail.unit.id in uiState.uploading,
            onUploadPdf = { onUploadPdf(detail.unit) },
            onDeleteDocument = viewModel::onDeleteDocument,
            onAssign = { viewModel.onAssign(it, detail.unit) },
            onUnassign = { viewModel.onUnassign(it, detail.unit) },
            onDismiss = viewModel::onCloseUnit
        )
    }
}

@Composable
fun AdminUnitsScreen(
    uiState: AdminUnitsUiState,
    onMonthSelected: (YearMonth) -> Unit,
    onTowerSelected: (Tower) -> Unit,
    onOpenUnit: (CondoUnit) -> Unit,
    modifier: Modifier = Modifier
) {
    if (uiState.isLoading) {
        LoadingState(modifier)
        return
    }
    val condo = uiState.condo
    if (condo == null || condo.towers.isEmpty()) {
        EmptyState(
            icon = Icons.Outlined.Apartment,
            title = "Sem unidades",
            message = "Cadastre os blocos deste condomínio para ver os apartamentos.",
            modifier = modifier
        )
        return
    }
    // Outro condomínio ou bloco: volta ao topo em vez de cair no meio do prédio novo.
    val listState = rememberLazyListState()
    LaunchedEffect(condo.id, uiState.tower?.id) { listState.scrollToItem(0) }
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        state = listState,
        contentPadding = PaddingValues(bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        item { MonthSummary(uiState, modifier = Modifier.padding(horizontal = 20.dp)) }
        item {
            LazyRow(
                contentPadding = PaddingValues(horizontal = 20.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.padding(top = 8.dp)
            ) {
                items(uiState.months) { month ->
                    PillChip(selected = month == uiState.month, onClick = { onMonthSelected(month) }) {
                        Text(month.monthName().replaceFirstChar { it.uppercase() }, style = MaterialTheme.typography.labelLarge)
                    }
                }
            }
        }
        if (condo.towers.size > 1) {
            item {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 20.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(condo.towers, key = { it.id }) { tower ->
                        PillChip(selected = tower.id == uiState.tower?.id, onClick = { onTowerSelected(tower) }) {
                            Text(tower.name, style = MaterialTheme.typography.labelLarge)
                        }
                    }
                }
            }
        }
        uiState.tower?.let { tower ->
            item { TowerHeader(tower, modifier = Modifier.padding(horizontal = 20.dp)) }
        }
        items(uiState.floors, key = { "floor-${it.floor}" }) { row ->
            FloorLine(
                row = row,
                perRow = tilesPerRow(row.cells.size),
                uploading = uiState.uploading,
                onOpenUnit = onOpenUnit,
                modifier = Modifier.padding(horizontal = 20.dp)
            )
        }
        // Térreo sem apartamentos: só a referência de chão do mapa.
        if (uiState.floors.none { it.floor == GROUND_FLOOR }) {
            item {
                Text(
                    "Térreo",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.outline,
                    modifier = Modifier.padding(start = 20.dp, top = 4.dp)
                )
            }
        }
    }
}

/** Até 6 por linha; andares com mais apartamentos quebram em duas linhas equilibradas. */
private fun tilesPerRow(count: Int): Int =
    if (count <= 6) count.coerceAtLeast(1) else (count + 1) / 2

@Composable
private fun MonthSummary(uiState: AdminUnitsUiState, modifier: Modifier = Modifier) {
    val condo = CondoTheme.colors
    val progress = if (uiState.totalUnits == 0) 0f else uiState.sentInMonth / uiState.totalUnits.toFloat()
    HeroCard(modifier = modifier) {
        Eyebrow("Boletos de ${uiState.month.monthName()}", color = condo.accent)
        Spacer(modifier = Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.Bottom) {
            Text("${uiState.sentInMonth}", style = MaterialTheme.typography.displaySmall, color = condo.onHero)
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                "de ${uiState.totalUnits} unidades com PDF",
                style = MaterialTheme.typography.bodyMedium,
                color = condo.onHeroMuted,
                modifier = Modifier.padding(bottom = 6.dp)
            )
        }
        Spacer(modifier = Modifier.height(14.dp))
        LinearProgressIndicator(
            progress = { progress },
            color = condo.accent,
            trackColor = condo.onHero.copy(alpha = 0.12f),
            strokeCap = StrokeCap.Round,
            drawStopIndicator = {},
            modifier = Modifier
                .fillMaxWidth()
                .height(6.dp)
        )
        Spacer(modifier = Modifier.height(16.dp))
        Row {
            HeroStat("Ocupadas", "${uiState.occupiedUnits}", Modifier.weight(1f))
            HeroStat("Vagas", "${uiState.totalUnits - uiState.occupiedUnits}", Modifier.weight(1f))
            HeroStat("Blocos", "${uiState.condo?.towers?.size ?: 0}", Modifier.weight(1f))
        }
    }
}

@Composable
private fun HeroStat(label: String, value: String, modifier: Modifier = Modifier) {
    val condo = CondoTheme.colors
    Column(modifier = modifier) {
        Text(label, style = MaterialTheme.typography.bodySmall, color = condo.onHeroMuted)
        Text(value, style = MaterialTheme.typography.titleLarge, color = condo.onHero)
    }
}

@Composable
private fun TowerHeader(tower: Tower, modifier: Modifier = Modifier) {
    Column(modifier = modifier.padding(top = 12.dp, bottom = 4.dp)) {
        Row(verticalAlignment = Alignment.Bottom) {
            Text(tower.name, style = MaterialTheme.typography.headlineMedium, modifier = Modifier.weight(1f))
            Text(
                "${tower.floors} andares · ${tower.totalUnits} aptos",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 4.dp)
            )
        }
        Spacer(modifier = Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            LegendItem("Ocupado") { TileSwatch(occupied = true) }
            LegendItem("Vago") { TileSwatch(occupied = false) }
            LegendItem("PDF do mês") { PdfDot() }
        }
    }
}

@Composable
private fun LegendItem(label: String, swatch: @Composable () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        swatch()
        Spacer(modifier = Modifier.width(6.dp))
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun TileSwatch(occupied: Boolean) {
    val scheme = MaterialTheme.colorScheme
    val shape = MaterialTheme.shapes.extraSmall
    Box(
        modifier = Modifier
            .size(14.dp)
            .clip(shape)
            .background(if (occupied) scheme.primary else scheme.surfaceContainerLow)
            .then(if (occupied) Modifier else Modifier.border(1.dp, scheme.outlineVariant, shape))
    )
}

@Composable
private fun PdfDot(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .size(7.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.tertiary)
    )
}

@Composable
private fun FloorLine(
    row: FloorRow,
    perRow: Int,
    uploading: Set<String>,
    onOpenUnit: (CondoUnit) -> Unit,
    modifier: Modifier = Modifier
) {
    // Andar que quebra em duas linhas ganha respiro extra para não se misturar com o vizinho.
    val wraps = row.cells.size > perRow
    Row(modifier = modifier.padding(vertical = if (wraps) 6.dp else 0.dp), verticalAlignment = Alignment.Top) {
        Text(
            if (row.floor == GROUND_FLOOR) "T" else "${row.floor}º",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.End,
            modifier = Modifier
                .width(32.dp)
                .padding(top = if (row.cells.isEmpty()) 4.dp else 14.dp, end = 8.dp)
        )
        if (row.cells.isEmpty()) {
            // Andar de lazer, garagem, casa de máquinas: fica no mapa para a contagem bater.
            Text(
                "Sem apartamentos",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.outline,
                modifier = Modifier
                    .weight(1f)
                    .border(1.dp, MaterialTheme.colorScheme.outlineVariant, MaterialTheme.shapes.small)
                    .padding(horizontal = 12.dp, vertical = 6.dp)
            )
            return@Row
        }
        FlowRow(
            modifier = Modifier.weight(1f),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
            maxItemsInEachRow = perRow
        ) {
            row.cells.forEach { cell ->
                UnitTile(
                    cell = cell,
                    uploading = cell.unit.id in uploading,
                    onClick = { onOpenUnit(cell.unit) },
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
private fun UnitTile(cell: UnitCell, uploading: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val scheme = MaterialTheme.colorScheme
    val occupied = cell.residents.isNotEmpty()
    Surface(
        onClick = onClick,
        shape = MaterialTheme.shapes.small,
        color = if (occupied) scheme.primary else scheme.surfaceContainerLow,
        border = if (occupied) null else BorderStroke(1.dp, scheme.outlineVariant),
        modifier = modifier.height(46.dp)
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                cell.unit.number,
                style = MaterialTheme.typography.labelLarge,
                color = if (occupied) scheme.onPrimary else scheme.outline,
                maxLines = 1
            )
            when {
                uploading -> CircularProgressIndicator(
                    strokeWidth = 1.5.dp,
                    color = scheme.tertiary,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(4.dp)
                        .size(9.dp)
                )
                cell.document != null -> PdfDot(
                    Modifier
                        .align(Alignment.TopEnd)
                        .padding(6.dp)
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun UnitDetailSheet(
    detail: UnitDetail,
    month: YearMonth,
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
            Eyebrow("${detail.unit.tower} · ${if (detail.unit.floor == GROUND_FLOOR) "Térreo" else "${detail.unit.floor}º andar"}")
            Spacer(modifier = Modifier.height(6.dp))
            Text("Apto ${detail.unit.number}", style = MaterialTheme.typography.headlineLarge)
            Spacer(modifier = Modifier.height(16.dp))

            // Ação principal do mês, no topo: enviar o boleto desta unidade.
            PrimaryButton(
                text = if (detail.documents.any { it.reference == month }) {
                    "Substituir PDF de ${month.monthName()}"
                } else {
                    "Enviar PDF de ${month.monthName()}"
                },
                onClick = onUploadPdf,
                loading = uploading,
                trailingIcon = Icons.Outlined.UploadFile
            )

            Spacer(modifier = Modifier.height(12.dp))
            SectionHeader("Moradores", trailing = "${detail.residents.size}")
            if (detail.residents.isEmpty()) {
                Text(
                    "Apartamento vago.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 8.dp)
                )
            }
            detail.residents.forEach { resident ->
                val others = resident.units.filter { it != detail.unit.label }
                PersonRow(resident, note = others.takeIf { it.isNotEmpty() }?.joinToString(prefix = "Também no ")) {
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
internal fun PersonRow(user: User, note: String? = null, trailing: @Composable () -> Unit) {
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
            note?.let {
                Text(it, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.tertiary)
            }
        }
        trailing()
    }
}
