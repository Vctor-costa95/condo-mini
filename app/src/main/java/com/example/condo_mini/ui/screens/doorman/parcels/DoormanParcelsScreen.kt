package com.example.condo_mini.ui.screens.doorman.parcels

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.outlined.Inventory2
import androidx.compose.material.icons.outlined.PhotoCamera
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.example.condo_mini.domain.model.CondoUnit
import com.example.condo_mini.domain.model.ParcelStatus
import com.example.condo_mini.domain.model.User
import com.example.condo_mini.ui.components.CondoCard
import com.example.condo_mini.ui.components.EmptyState
import com.example.condo_mini.ui.components.Eyebrow
import com.example.condo_mini.ui.components.HeroCard
import com.example.condo_mini.ui.components.IconBadge
import com.example.condo_mini.ui.components.LoadingState
import com.example.condo_mini.ui.components.PhotoViewerDialog
import com.example.condo_mini.ui.components.PillChip
import com.example.condo_mini.ui.components.PrimaryButton
import com.example.condo_mini.ui.components.SectionHeader
import com.example.condo_mini.ui.components.UnitPicker
import com.example.condo_mini.ui.components.firstNames
import com.example.condo_mini.ui.components.condoTextFieldColors
import com.example.condo_mini.ui.components.rememberPhotoPicker
import com.example.condo_mini.ui.screens.main.CondoSnackbarHost
import com.example.condo_mini.ui.theme.CondoTheme
import com.example.condo_mini.ui.util.formatDateTime
import com.example.condo_mini.ui.util.relative

@Composable
fun DoormanParcelsRoute(
    modifier: Modifier = Modifier,
    viewModel: DoormanParcelsViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(uiState.userMessage) {
        uiState.userMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.onMessageShown()
        }
    }

    DoormanParcelsScreen(
        uiState = uiState,
        snackbarHostState = snackbarHostState,
        onFilterSelected = viewModel::onFilterSelected,
        onQueryChanged = viewModel::onQueryChanged,
        onNewParcel = viewModel::onNewParcel,
        onDeliver = viewModel::onDeliver,
        modifier = modifier
    )

    uiState.form?.let { form ->
        RegisterParcelSheet(
            form = form,
            units = uiState.units,
            residentsByUnit = uiState.residentsByUnit,
            onChange = viewModel::onFormChanged,
            onRegister = viewModel::onRegister,
            onDismiss = viewModel::onDismissForm
        )
    }

    uiState.delivering?.let { row ->
        DeliverDialog(
            row = row,
            // Quem buscou pelo código já digitou: vem preenchido.
            prefilledCode = uiState.query.trim().takeIf { it == row.parcel.pickupCode }.orEmpty(),
            error = uiState.deliverError,
            isDelivering = uiState.isDelivering,
            onConfirm = viewModel::onConfirmDeliver,
            onDismiss = viewModel::onDismissDeliver
        )
    }
}

@Composable
fun DoormanParcelsScreen(
    uiState: DoormanParcelsUiState,
    snackbarHostState: SnackbarHostState,
    onFilterSelected: (DoormanFilter) -> Unit,
    onQueryChanged: (String) -> Unit,
    onNewParcel: () -> Unit,
    onDeliver: (ParcelRow) -> Unit,
    modifier: Modifier = Modifier
) {
    var photoToView by rememberSaveable { mutableStateOf<String?>(null) }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets(0),
        snackbarHost = { CondoSnackbarHost(snackbarHostState) },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = onNewParcel,
                icon = { Icon(Icons.Outlined.PhotoCamera, contentDescription = null) },
                text = { Text("Registrar chegada", style = MaterialTheme.typography.labelLarge) },
                shape = MaterialTheme.shapes.medium,
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                elevation = FloatingActionButtonDefaults.elevation(defaultElevation = 2.dp)
            )
        }
    ) { innerPadding ->
        Box(modifier = Modifier.padding(innerPadding)) {
            if (uiState.isLoading) {
                LoadingState()
                return@Box
            }
            LazyColumn(
                contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 104.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                item { Summary(uiState) }
                item {
                    OutlinedTextField(
                        value = uiState.query,
                        onValueChange = onQueryChanged,
                        placeholder = { Text("Código de retirada ou apartamento") },
                        leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
                        singleLine = true,
                        shape = MaterialTheme.shapes.medium,
                        colors = condoTextFieldColors(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        DoormanFilter.entries.forEach { filter ->
                            PillChip(selected = uiState.filter == filter, onClick = { onFilterSelected(filter) }) {
                                Text(filter.label, style = MaterialTheme.typography.labelLarge)
                            }
                        }
                    }
                }
                if (uiState.rows.isEmpty()) {
                    item {
                        EmptyState(
                            icon = Icons.Default.Inventory2,
                            title = if (uiState.query.isNotBlank()) "Nada encontrado" else "Tudo entregue",
                            message = if (uiState.query.isNotBlank()) {
                                "Confira o código com o morador ou busque pelo número do apartamento."
                            } else {
                                "Nenhuma encomenda esperando retirada."
                            },
                            modifier = Modifier.height(300.dp)
                        )
                    }
                }
                items(uiState.rows, key = { it.parcel.id }) { row ->
                    ParcelCard(
                        row = row,
                        onPhotoClick = row.parcel.photoUrl?.let { url -> { photoToView = url } },
                        onDeliver = { onDeliver(row) }
                    )
                }
            }
        }
    }

    photoToView?.let { url ->
        PhotoViewerDialog(url = url, eyebrow = "Foto da portaria", title = "Encomenda", onDismiss = { photoToView = null })
    }
}

@Composable
private fun Summary(uiState: DoormanParcelsUiState) {
    val condo = CondoTheme.colors
    HeroCard {
        Eyebrow("Na portaria agora", color = condo.accent)
        Spacer(modifier = Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.Bottom) {
            Text("${uiState.waitingCount}", style = MaterialTheme.typography.displaySmall, color = condo.onHero)
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                if (uiState.waitingCount == 1) "encomenda aguardando" else "encomendas aguardando",
                style = MaterialTheme.typography.bodyMedium,
                color = condo.onHeroMuted,
                modifier = Modifier.padding(bottom = 6.dp)
            )
        }
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            when (uiState.deliveredToday) {
                0 -> "Nenhuma entregue hoje"
                1 -> "1 entregue hoje"
                else -> "${uiState.deliveredToday} entregues hoje"
            },
            style = MaterialTheme.typography.bodySmall,
            color = condo.onHeroMuted
        )
    }
}

@Composable
private fun ParcelCard(row: ParcelRow, onPhotoClick: (() -> Unit)?, onDeliver: () -> Unit) {
    val parcel = row.parcel
    val waiting = parcel.status == ParcelStatus.WAITING
    val scheme = MaterialTheme.colorScheme
    CondoCard(contentPadding = PaddingValues(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (parcel.photoUrl != null) {
                AsyncImage(
                    model = parcel.photoUrl,
                    contentDescription = "Foto da encomenda",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .size(64.dp)
                        .clip(MaterialTheme.shapes.medium)
                        .background(scheme.surfaceContainer)
                        .then(if (onPhotoClick != null) Modifier.clickable(onClick = onPhotoClick) else Modifier)
                )
            } else {
                IconBadge(icon = Icons.Outlined.Inventory2, size = 64.dp)
            }
            Spacer(modifier = Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Eyebrow("${parcel.carrier} · ${parcel.description}", color = scheme.onSurfaceVariant)
                Spacer(modifier = Modifier.height(2.dp))
                Text(parcel.unit.replace(" - ", " · "), style = MaterialTheme.typography.titleLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    row.residents.joinToString().ifBlank { "Apartamento sem morador cadastrado" },
                    style = MaterialTheme.typography.bodySmall,
                    color = scheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    if (waiting) {
                        "Chegou ${parcel.arrivedAt.relative()} · ${parcel.receivedBy}"
                    } else {
                        "Entregue em ${parcel.pickedUpAt?.formatDateTime().orEmpty()}"
                    },
                    style = MaterialTheme.typography.labelMedium,
                    color = scheme.outline
                )
            }
        }
        if (waiting) {
            Spacer(modifier = Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                // O código fica só com o morador: a portaria confere, nunca vê.
                Text("Aguardando retirada", style = MaterialTheme.typography.labelLarge, color = scheme.tertiary, modifier = Modifier.weight(1f))
                OutlinedButton(onClick = onDeliver, shape = MaterialTheme.shapes.medium) {
                    Text("Entregar", style = MaterialTheme.typography.labelLarge)
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RegisterParcelSheet(
    form: RegisterForm,
    units: List<CondoUnit>,
    residentsByUnit: Map<String, List<User>>,
    onChange: ((RegisterForm) -> RegisterForm) -> Unit,
    onRegister: () -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val picker = rememberPhotoPicker { url -> onChange { it.copy(photoUrl = url) } }

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
            Eyebrow("Portaria")
            Spacer(modifier = Modifier.height(6.dp))
            Text("Nova encomenda", style = MaterialTheme.typography.headlineLarge)
            Spacer(modifier = Modifier.height(16.dp))

            // Foto primeiro: é o que o morador vê e o que evita "não é minha".
            PhotoArea(photoUrl = form.photoUrl, onTakePhoto = picker.takePhoto, onPickFromGallery = picker.pickFromGallery)

            Spacer(modifier = Modifier.height(8.dp))
            SectionHeader("Para qual apartamento?")
            Spacer(modifier = Modifier.height(4.dp))
            UnitPicker(
                units = units,
                selected = setOfNotNull(form.unit.takeIf { it.isNotBlank() }),
                onUnitClick = { unit -> onChange { it.copy(unit = unit.label) } },
                // Nome de quem mora no tile: a portaria reconhece o apartamento pelo morador.
                unitCaption = { unit -> residentsByUnit[unit.label].firstNames() ?: "Vago" }
            )
            if (form.unit.isNotBlank() && residentsByUnit[form.unit].isNullOrEmpty()) {
                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    "Ninguém deste apartamento usa o app. Avise pelo interfone.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            }

            Spacer(modifier = Modifier.height(8.dp))
            SectionHeader("Quem entregou")
            ChoiceChips(CARRIERS, form.carrier) { value -> onChange { it.copy(carrier = value) } }

            Spacer(modifier = Modifier.height(8.dp))
            SectionHeader("Volume")
            ChoiceChips(PACKAGE_TYPES, form.description) { value -> onChange { it.copy(description = value) } }

            form.error?.let {
                Spacer(modifier = Modifier.height(12.dp))
                Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            }
            Spacer(modifier = Modifier.height(20.dp))
            PrimaryButton(text = "Registrar e avisar morador", onClick = onRegister, loading = form.isSaving)
        }
    }
}

@Composable
private fun PhotoArea(photoUrl: String?, onTakePhoto: () -> Unit, onPickFromGallery: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    if (photoUrl == null) {
        Surface(
            onClick = onTakePhoto,
            shape = MaterialTheme.shapes.large,
            color = scheme.surfaceContainerLow,
            border = BorderStroke(1.dp, scheme.outlineVariant),
            modifier = Modifier
                .fillMaxWidth()
                .height(160.dp)
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                Icon(Icons.Outlined.PhotoCamera, contentDescription = null, tint = scheme.tertiary, modifier = Modifier.size(32.dp))
                Spacer(modifier = Modifier.height(8.dp))
                Text("Fotografar a encomenda", style = MaterialTheme.typography.titleSmall)
                Text("Mostre a etiqueta, se der", style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant)
            }
        }
        TextButton(onClick = onPickFromGallery) {
            Icon(Icons.Outlined.PhotoLibrary, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(6.dp))
            Text("Escolher da galeria", style = MaterialTheme.typography.labelLarge)
        }
    } else {
        AsyncImage(
            model = photoUrl,
            contentDescription = "Foto da encomenda",
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .fillMaxWidth()
                .height(200.dp)
                .clip(MaterialTheme.shapes.large)
                .background(scheme.surfaceContainer)
        )
        TextButton(onClick = onTakePhoto) {
            Icon(Icons.Outlined.PhotoCamera, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(6.dp))
            Text("Tirar outra foto", style = MaterialTheme.typography.labelLarge)
        }
    }
}

@Composable
private fun ChoiceChips(options: List<String>, selected: String, onSelect: (String) -> Unit) {
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        options.forEach { option ->
            PillChip(selected = selected == option, onClick = { onSelect(option) }) {
                Text(option, style = MaterialTheme.typography.labelLarge)
            }
        }
    }
}

@Composable
private fun DeliverDialog(
    row: ParcelRow,
    prefilledCode: String,
    error: String?,
    isDelivering: Boolean,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var code by rememberSaveable(row.parcel.id) { mutableStateOf(prefilledCode) }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.background,
        title = { Text("Entregar encomenda", style = MaterialTheme.typography.headlineSmall) },
        text = {
            Column {
                Text("${row.parcel.description} · ${row.parcel.carrier}", style = MaterialTheme.typography.titleSmall)
                Text(
                    "${row.parcel.unit} · ${row.residents.joinToString().ifBlank { "sem morador cadastrado" }}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(16.dp))
                OutlinedTextField(
                    value = code,
                    onValueChange = { code = it.filter(Char::isDigit).take(4) },
                    label = { Text("Código que o morador mostrou") },
                    singleLine = true,
                    isError = error != null,
                    supportingText = error?.let { { Text(it) } },
                    shape = MaterialTheme.shapes.medium,
                    colors = condoTextFieldColors(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(code) }, enabled = code.length == 4 && !isDelivering) {
                Text("Confirmar entrega")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancelar", color = MaterialTheme.colorScheme.onSurface)
            }
        }
    )
}
