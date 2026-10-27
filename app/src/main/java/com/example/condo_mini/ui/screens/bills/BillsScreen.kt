package com.example.condo_mini.ui.screens.bills

import android.os.Build
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.PictureAsPdf
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.condo_mini.domain.model.Bill
import com.example.condo_mini.domain.model.BillStatus
import com.example.condo_mini.ui.components.CondoCard
import com.example.condo_mini.ui.components.Eyebrow
import com.example.condo_mini.ui.components.HeroCard
import com.example.condo_mini.ui.components.LoadingState
import com.example.condo_mini.ui.components.SectionHeader
import com.example.condo_mini.ui.components.StatusChip
import com.example.condo_mini.ui.components.StatusTone
import com.example.condo_mini.ui.theme.CondoTheme
import com.example.condo_mini.ui.util.formatDate
import com.example.condo_mini.ui.util.formatMonth
import com.example.condo_mini.ui.util.toBrl
import kotlinx.coroutines.launch

@Suppress("DEPRECATION") // LocalClipboardManager: API síncrona simples para texto.
@Composable
fun BillsRoute(
    snackbarHostState: SnackbarHostState,
    onOpenPdf: (Bill) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: BillsViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val clipboard = LocalClipboardManager.current
    val scope = rememberCoroutineScope()

    BillsScreen(
        uiState = uiState,
        onCopyBarcode = { bill ->
            clipboard.setText(AnnotatedString(bill.barcode))
            // A partir do Android 13 o sistema já confirma a cópia.
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
                scope.launch { snackbarHostState.showSnackbar("Linha digitável copiada") }
            }
        },
        onOpenPdf = onOpenPdf,
        modifier = modifier
    )
}

@Composable
fun BillsScreen(
    uiState: BillsUiState,
    onCopyBarcode: (Bill) -> Unit,
    onOpenPdf: (Bill) -> Unit,
    modifier: Modifier = Modifier
) {
    if (uiState.isLoading) {
        LoadingState(modifier)
        return
    }
    LazyColumn(
        modifier = modifier,
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item { SummaryCard(uiState) }
        if (uiState.pending.isNotEmpty()) {
            item { SectionHeader("A pagar", modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp)) }
            items(uiState.pending, key = { it.id }) { BillCard(it, onCopyBarcode, onOpenPdf) }
        }
        if (uiState.paid.isNotEmpty()) {
            item { SectionHeader("Histórico", modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp)) }
            items(uiState.paid, key = { it.id }) { BillCard(it, onCopyBarcode, onOpenPdf) }
        }
    }
}

@Composable
private fun SummaryCard(uiState: BillsUiState) {
    val condo = CondoTheme.colors
    HeroCard {
        Eyebrow("Total em aberto", color = condo.accent)
        Spacer(modifier = Modifier.height(10.dp))
        Text(
            uiState.totalPending.toBrl(),
            style = MaterialTheme.typography.displaySmall,
            color = condo.onHero
        )
        Spacer(modifier = Modifier.height(20.dp))
        HorizontalDivider(color = condo.onHeroMuted.copy(alpha = 0.2f))
        Spacer(modifier = Modifier.height(16.dp))
        Row {
            SummaryMetric(
                label = "Próximo vencimento",
                value = uiState.nextDue?.dueDate?.formatDate() ?: "—",
                modifier = Modifier.weight(1f)
            )
            SummaryMetric(
                label = "Em atraso",
                value = when (uiState.overdueCount) {
                    0 -> "Nenhum"
                    1 -> "1 boleto"
                    else -> "${uiState.overdueCount} boletos"
                },
                highlight = uiState.overdueCount > 0,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun SummaryMetric(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    highlight: Boolean = false
) {
    val condo = CondoTheme.colors
    Column(modifier = modifier) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = condo.onHeroMuted)
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            value,
            style = MaterialTheme.typography.titleMedium,
            color = if (highlight) condo.accent else condo.onHero
        )
    }
}

@Composable
private fun BillCard(
    bill: Bill,
    onCopyBarcode: (Bill) -> Unit,
    onOpenPdf: (Bill) -> Unit
) {
    var expanded by rememberSaveable(bill.id) { mutableStateOf(false) }
    val isPaid = bill.status == BillStatus.PAID

    CondoCard(
        onClick = { expanded = !expanded },
        modifier = Modifier.animateContentSize()
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(bill.reference.formatMonth(), style = MaterialTheme.typography.titleLarge)
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = if (isPaid) {
                        "Pago em ${bill.paidAt?.formatDate().orEmpty()}"
                    } else {
                        "Vence em ${bill.dueDate.formatDate()}"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    bill.amount.toBrl(),
                    style = MaterialTheme.typography.titleMedium,
                    color = if (isPaid) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(6.dp))
                StatusChip(bill.status.label, bill.status.tone())
            }
        }
        if (expanded && !isPaid) {
            Spacer(modifier = Modifier.height(18.dp))
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(MaterialTheme.shapes.medium)
                    .background(MaterialTheme.colorScheme.surfaceContainer)
                    .padding(16.dp)
            ) {
                Eyebrow("Linha digitável", color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    bill.barcode,
                    style = MaterialTheme.typography.bodyMedium,
                    fontFamily = FontFamily.Monospace
                )
            }
        }
        if (expanded) {
            Spacer(modifier = Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (!isPaid) {
                    BillAction(
                        text = "Copiar código",
                        icon = Icons.Outlined.ContentCopy,
                        onClick = { onCopyBarcode(bill) },
                        modifier = Modifier.weight(1f)
                    )
                }
                BillAction(
                    text = if (isPaid) "Ver boleto em PDF" else "Ver PDF",
                    icon = Icons.Outlined.PictureAsPdf,
                    onClick = { onOpenPdf(bill) },
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
private fun BillAction(
    text: String,
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    OutlinedButton(
        onClick = onClick,
        shape = MaterialTheme.shapes.medium,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary),
        contentPadding = PaddingValues(horizontal = 12.dp),
        modifier = modifier.height(48.dp)
    ) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary)
        Spacer(modifier = Modifier.width(8.dp))
        Text(text, color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelLarge)
    }
}

private fun BillStatus.tone(): StatusTone = when (this) {
    BillStatus.OPEN -> StatusTone.WARNING
    BillStatus.OVERDUE -> StatusTone.ERROR
    BillStatus.PAID -> StatusTone.SUCCESS
}
