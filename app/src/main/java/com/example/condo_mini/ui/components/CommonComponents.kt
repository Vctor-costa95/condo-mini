package com.example.condo_mini.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextFieldColors
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.condo_mini.ui.theme.CondoTheme

/** Tom visual de um status (aberto, pago, vencido...). */
enum class StatusTone { NEUTRAL, INFO, SUCCESS, WARNING, ERROR, ACCENT }

@Composable
private fun StatusTone.colors(): Pair<Color, Color> {
    val scheme = MaterialTheme.colorScheme
    val condo = CondoTheme.colors
    return when (this) {
        StatusTone.NEUTRAL -> scheme.surfaceContainer to scheme.onSurfaceVariant
        StatusTone.INFO -> condo.infoContainer to condo.info
        StatusTone.SUCCESS -> condo.successContainer to condo.success
        StatusTone.WARNING -> condo.warningContainer to condo.warning
        StatusTone.ERROR -> scheme.errorContainer to scheme.error
        StatusTone.ACCENT -> scheme.tertiaryContainer to scheme.onTertiaryContainer
    }
}

/** Selo discreto com ponto colorido, no estilo de etiquetas de hotelaria. */
@Composable
fun StatusChip(
    text: String,
    tone: StatusTone,
    modifier: Modifier = Modifier
) {
    val (container, content) = tone.colors()
    Row(
        modifier = modifier
            .clip(CircleShape)
            .background(container)
            .padding(horizontal = 10.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(6.dp)
                .clip(CircleShape)
                .background(content)
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(text = text, style = MaterialTheme.typography.labelMedium, color = content)
    }
}

/** Rótulo em caixa alta com espaçamento largo, usado acima de títulos. */
@Composable
fun Eyebrow(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.tertiary
) {
    Text(
        text = text.uppercase(),
        style = MaterialTheme.typography.labelSmall,
        color = color,
        modifier = modifier
    )
}

@Composable
fun SectionHeader(
    text: String,
    modifier: Modifier = Modifier,
    trailing: String? = null
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(top = 12.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Eyebrow(text = text, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
        trailing?.let {
            Text(it, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.outline)
        }
    }
}

/** Cartão padrão: superfície clara com borda fina, sem sombra. */
@Composable
fun CondoCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    contentPadding: PaddingValues = PaddingValues(20.dp),
    content: @Composable ColumnScope.() -> Unit
) {
    val shape = MaterialTheme.shapes.large
    val border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    val color = MaterialTheme.colorScheme.surfaceContainerLow
    if (onClick != null) {
        Surface(onClick = onClick, modifier = modifier.fillMaxWidth(), shape = shape, color = color, border = border) {
            Column(modifier = Modifier.padding(contentPadding), content = content)
        }
    } else {
        Surface(modifier = modifier.fillMaxWidth(), shape = shape, color = color, border = border) {
            Column(modifier = Modifier.padding(contentPadding), content = content)
        }
    }
}

/** Cartão de destaque escuro com gradiente e filete champanhe. */
@Composable
fun HeroCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    val condo = CondoTheme.colors
    val shape = MaterialTheme.shapes.extraLarge
    // Filete champanhe translúcido na borda: acabamento sem virar "efeito".
    val border = BorderStroke(1.dp, condo.accent.copy(alpha = 0.35f))
    val body: @Composable () -> Unit = {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(Brush.linearGradient(listOf(condo.heroStart, condo.heroEnd)))
                .padding(24.dp),
            content = content
        )
    }
    if (onClick != null) {
        Surface(onClick = onClick, modifier = modifier.fillMaxWidth(), shape = shape, color = condo.heroEnd, border = border, content = body)
    } else {
        Surface(modifier = modifier.fillMaxWidth(), shape = shape, color = condo.heroEnd, border = border, content = body)
    }
}

/** Ícone em quadrado arredondado de fundo neutro. */
@Composable
fun IconBadge(
    icon: ImageVector,
    modifier: Modifier = Modifier,
    size: Dp = 44.dp,
    container: Color = MaterialTheme.colorScheme.surfaceContainer,
    tint: Color = MaterialTheme.colorScheme.onSurface
) {
    Box(
        modifier = modifier
            .size(size)
            .clip(MaterialTheme.shapes.medium)
            .background(container),
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(size * 0.48f))
    }
}

/** Seletor em pílula. Selecionado = tinta cheia; normal = só contorno. */
@Composable
fun PillChip(
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    content: @Composable () -> Unit
) {
    val scheme = MaterialTheme.colorScheme
    Surface(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier,
        shape = MaterialTheme.shapes.medium,
        color = if (selected) scheme.primary else scheme.surfaceContainerLow,
        contentColor = when {
            selected -> scheme.onPrimary
            enabled -> scheme.onSurface
            else -> scheme.outline
        },
        border = if (selected) null else BorderStroke(1.dp, scheme.outlineVariant)
    ) {
        Box(modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp), contentAlignment = Alignment.Center) {
            content()
        }
    }
}

@Composable
fun PrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    loading: Boolean = false,
    trailingIcon: ImageVector? = null
) {
    Button(
        onClick = onClick,
        enabled = enabled && !loading,
        shape = MaterialTheme.shapes.medium,
        colors = ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary
        ),
        contentPadding = PaddingValues(horizontal = 24.dp),
        modifier = modifier
            .fillMaxWidth()
            .height(56.dp)
    ) {
        if (loading) {
            CircularProgressIndicator(
                modifier = Modifier.size(20.dp),
                strokeWidth = 2.dp,
                color = MaterialTheme.colorScheme.onPrimary
            )
        } else {
            Text(text, style = MaterialTheme.typography.labelLarge)
            trailingIcon?.let {
                Spacer(modifier = Modifier.width(10.dp))
                Icon(it, contentDescription = null, modifier = Modifier.size(18.dp))
            }
        }
    }
}

/** Avatar com iniciais e anel champanhe. */
@Composable
fun InitialsAvatar(
    name: String,
    modifier: Modifier = Modifier,
    size: Dp = 40.dp
) {
    val scheme = MaterialTheme.colorScheme
    Box(
        modifier = modifier
            .size(size)
            .border(1.5.dp, scheme.tertiary, CircleShape)
            .padding(3.dp)
            .clip(CircleShape)
            .background(scheme.primary),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = name.initials(),
            color = scheme.onPrimary,
            style = if (size >= 64.dp) MaterialTheme.typography.headlineMedium else MaterialTheme.typography.titleSmall,
            textAlign = TextAlign.Center
        )
    }
}

fun String.initials(): String =
    trim().split(" ").filter { it.isNotBlank() }.take(2).joinToString("") { it.first().uppercase() }

@Composable
fun condoTextFieldColors(): TextFieldColors {
    val scheme = MaterialTheme.colorScheme
    return OutlinedTextFieldDefaults.colors(
        focusedBorderColor = scheme.primary,
        unfocusedBorderColor = scheme.outlineVariant,
        focusedContainerColor = scheme.surfaceContainerLow,
        unfocusedContainerColor = scheme.surfaceContainerLow,
        focusedLabelColor = scheme.primary,
        cursorColor = scheme.tertiary
    )
}

@Composable
fun EmptyState(
    icon: ImageVector,
    title: String,
    message: String,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(40.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        IconBadge(
            icon = icon,
            size = 72.dp,
            container = MaterialTheme.colorScheme.tertiaryContainer,
            tint = MaterialTheme.colorScheme.tertiary
        )
        Spacer(modifier = Modifier.height(20.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.headlineSmall,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
fun LoadingState(modifier: Modifier = Modifier) {
    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator(color = MaterialTheme.colorScheme.tertiary, strokeWidth = 2.dp)
    }
}
