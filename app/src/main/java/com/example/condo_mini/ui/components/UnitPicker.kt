package com.example.condo_mini.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.condo_mini.domain.model.CondoUnit
import com.example.condo_mini.domain.model.floorLabel

/**
 * Escolha de apartamento em três toques: bloco → andar → apartamento. Abre no bloco
 * e andar de [initialUnit] (ou no 1º andar do primeiro bloco).
 */
@Composable
fun UnitPicker(
    units: List<CondoUnit>,
    selected: Set<String>,
    onUnitClick: (CondoUnit) -> Unit,
    modifier: Modifier = Modifier,
    initialUnit: String? = selected.firstOrNull()
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

    Column(modifier = modifier) {
        if (towers.size > 1) {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(towers, key = { it.first }) { (id, name) ->
                    PillChip(
                        selected = currentTower == id,
                        onClick = {
                            towerId = id
                            floor = -1
                        }
                    ) {
                        Text(name, style = MaterialTheme.typography.labelLarge)
                    }
                }
            }
            Spacer(modifier = Modifier.height(10.dp))
        }
        Text("Andar", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(modifier = Modifier.height(6.dp))
        // Do térreo para cima, como se lê um painel de elevador.
        LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            items(floors) { value ->
                PillChip(
                    selected = currentFloor == value,
                    onClick = {
                        towerId = currentTower
                        floor = value
                    }
                ) {
                    Text(floorLabel(value), style = MaterialTheme.typography.labelLarge)
                }
            }
        }
        Spacer(modifier = Modifier.height(10.dp))
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            maxItemsInEachRow = chipsPerRow(floorUnits.size)
        ) {
            floorUnits.forEach { unit ->
                PillChip(
                    selected = unit.label in selected,
                    onClick = { onUnitClick(unit) },
                    modifier = Modifier.weight(1f)
                ) {
                    Text(unit.number, style = MaterialTheme.typography.labelLarge)
                }
            }
        }
    }
}

/** Linhas que fecham certinho: 6 aptos viram 3 + 3, 10 viram 5 + 5. */
private fun chipsPerRow(count: Int): Int = when {
    count <= 4 -> count.coerceAtLeast(1)
    count <= 6 -> 3
    count <= 8 -> 4
    else -> 5
}
