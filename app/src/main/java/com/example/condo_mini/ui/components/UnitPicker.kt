package com.example.condo_mini.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.condo_mini.domain.model.CondoUnit
import com.example.condo_mini.domain.model.GROUND_FLOOR
import com.example.condo_mini.domain.model.User
import com.example.condo_mini.domain.model.floorLabel

/**
 * Escolha de apartamento em três passos: bloco → andar → apartamento.
 *
 * - [unitCaption]: linha embaixo do número (quem mora, "Vago"...), para a pessoa
 *   reconhecer o apartamento sem decorar números.
 * - [showSummary]: cartão no topo com o apartamento escolhido. Quem já mostra a
 *   escolha de outro jeito (os Cadastros, com vários) desliga.
 */
@Composable
fun UnitPicker(
    units: List<CondoUnit>,
    selected: Set<String>,
    onUnitClick: (CondoUnit) -> Unit,
    modifier: Modifier = Modifier,
    initialUnit: String? = selected.firstOrNull(),
    unitCaption: (CondoUnit) -> String? = { null },
    showSummary: Boolean = true,
    /** Espaços e botões menores, para caber numa tela só junto com o resto do formulário. */
    compact: Boolean = false
) {
    val start = remember(units) { units.firstOrNull { it.label == initialUnit } }
    var towerId by rememberSaveable { mutableStateOf(start?.towerId.orEmpty()) }
    var floor by rememberSaveable { mutableIntStateOf(start?.floor ?: -1) }

    val towers = remember(units) { units.distinctBy { it.towerId }.map { it.towerId to it.tower } }
    val currentTower = towerId.takeIf { id -> towers.any { it.first == id } } ?: towers.firstOrNull()?.first.orEmpty()
    val towerUnits = units.filter { it.towerId == currentTower }
    val floors = remember(towerUnits) { towerUnits.map { it.floor }.distinct().sorted() }
    // Sem andar escolhido: o mais baixo com apartamentos (térreo, se houver).
    val currentFloor = floor.takeIf { it in floors } ?: floors.firstOrNull() ?: 0
    val floorUnits = towerUnits.filter { it.floor == currentFloor }

    val stepGap = if (compact) 14.dp else 20.dp
    Column(modifier = modifier) {
        if (showSummary) {
            val chosen = units.firstOrNull { it.label in selected }
            SelectionSummary(chosen, chosen?.let(unitCaption))
            Spacer(modifier = Modifier.height(stepGap))
        }

        if (towers.size > 1) {
            StepLabel("Bloco")
            TowerSelector(
                towers = towers,
                selectedId = currentTower,
                onSelect = { id ->
                    towerId = id
                    floor = -1
                }
            )
            Spacer(modifier = Modifier.height(stepGap))
        }

        StepLabel("Andar")
        // Painel de elevador: todos os andares à vista, quebrando em linhas, sem rolagem escondida.
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            floors.forEach { value ->
                FloorButton(
                    label = if (value == GROUND_FLOOR) "T" else "$value",
                    spokenLabel = floorLabel(value),
                    selected = value == currentFloor,
                    size = if (compact) 40.dp else 44.dp,
                    onClick = {
                        towerId = currentTower
                        floor = value
                    }
                )
            }
        }

        Spacer(modifier = Modifier.height(stepGap))
        StepLabel(if (currentFloor == GROUND_FLOOR) "Apartamento · Térreo" else "Apartamento · ${currentFloor}º andar")
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            maxItemsInEachRow = tilesPerRow(floorUnits.size)
        ) {
            floorUnits.forEach { unit ->
                UnitTile(
                    number = unit.number,
                    caption = unitCaption(unit),
                    selected = unit.label in selected,
                    compact = compact,
                    onClick = { onUnitClick(unit) },
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
private fun StepLabel(text: String) {
    Eyebrow(text, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(bottom = 10.dp))
}

/** O apartamento escolhido, grande, como confirmação antes de seguir. */
@Composable
private fun SelectionSummary(unit: CondoUnit?, caption: String?) {
    val scheme = MaterialTheme.colorScheme
    val border by animateColorAsState(if (unit != null) scheme.tertiary else scheme.outlineVariant, label = "summaryBorder")
    Surface(
        shape = MaterialTheme.shapes.large,
        color = scheme.surfaceContainerLow,
        border = BorderStroke(1.dp, border),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp)) {
            if (unit == null) {
                Eyebrow("Apartamento", color = scheme.onSurfaceVariant)
                Spacer(modifier = Modifier.height(4.dp))
                Text("Escolha abaixo", style = MaterialTheme.typography.headlineSmall, color = scheme.outline)
            } else {
                Eyebrow("${unit.tower} · ${floorLabel(unit.floor)}${if (unit.floor == GROUND_FLOOR) "" else " andar"}")
                Spacer(modifier = Modifier.height(4.dp))
                Text("Apto ${unit.number}", style = MaterialTheme.typography.headlineMedium)
                caption?.let {
                    Text(it, style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant)
                }
            }
        }
    }
}

/** Controle segmentado; com muitos blocos, vira uma faixa que rola de lado. */
@Composable
private fun TowerSelector(towers: List<Pair<String, String>>, selectedId: String, onSelect: (String) -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val segmented = towers.size <= 4
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.medium)
            .background(scheme.surfaceContainer)
            .then(if (segmented) Modifier else Modifier.horizontalScroll(rememberScrollState()))
            .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        towers.forEach { (id, name) ->
            val selected = id == selectedId
            val background by animateColorAsState(if (selected) scheme.primary else scheme.surfaceContainer, label = "tower")
            Surface(
                onClick = { onSelect(id) },
                shape = MaterialTheme.shapes.small,
                color = background,
                contentColor = if (selected) scheme.onPrimary else scheme.onSurfaceVariant,
                modifier = if (segmented) Modifier.weight(1f) else Modifier
            ) {
                Box(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(name, style = MaterialTheme.typography.labelLarge, maxLines = 1)
                }
            }
        }
    }
}

@Composable
private fun FloorButton(label: String, spokenLabel: String, selected: Boolean, size: Dp, onClick: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Surface(
        onClick = onClick,
        shape = CircleShape,
        color = if (selected) scheme.primary else scheme.surfaceContainerLow,
        contentColor = if (selected) scheme.onPrimary else scheme.onSurface,
        // Anel champanhe no andar ativo, como o botão aceso de um elevador.
        border = BorderStroke(if (selected) 2.dp else 1.dp, if (selected) scheme.tertiary else scheme.outlineVariant),
        // Leitores de tela anunciam "Térreo" / "3º" em vez de "T" / "3".
        modifier = Modifier
            .size(size)
            .semantics { contentDescription = spokenLabel }
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(label, style = MaterialTheme.typography.titleSmall)
        }
    }
}

@Composable
private fun UnitTile(
    number: String,
    caption: String?,
    selected: Boolean,
    compact: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val scheme = MaterialTheme.colorScheme
    Surface(
        onClick = onClick,
        shape = MaterialTheme.shapes.medium,
        color = if (selected) scheme.primary else scheme.surfaceContainerLow,
        contentColor = if (selected) scheme.onPrimary else scheme.onSurface,
        border = if (selected) BorderStroke(1.5.dp, scheme.tertiary) else BorderStroke(1.dp, scheme.outlineVariant),
        modifier = modifier.height(
            when {
                caption == null -> if (compact) 44.dp else 52.dp
                compact -> 56.dp
                else -> 68.dp
            }
        )
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(number, style = MaterialTheme.typography.titleLarge, maxLines = 1)
            caption?.let {
                Text(
                    it,
                    style = MaterialTheme.typography.labelSmall,
                    color = if (selected) scheme.onPrimary.copy(alpha = 0.75f) else scheme.outline,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

/** Linhas que fecham certinho: 6 aptos viram 3 + 3, 10 viram 5 + 5. */
private fun tilesPerRow(count: Int): Int = when {
    count <= 4 -> count.coerceAtLeast(1)
    count <= 6 -> 3
    count <= 8 -> 4
    else -> 5
}

/** "Marina", "Paulo e Clara", "Ana, Beto e Caio"; `null` sem ninguém. */
fun List<User>?.firstNames(): String? {
    val names = this.orEmpty().map { it.name.substringBefore(" ") }.distinct()
    return when (names.size) {
        0 -> null
        1 -> names.first()
        else -> names.dropLast(1).joinToString() + " e " + names.last()
    }
}
