package com.example.condo_mini.ui.components

import android.graphics.BitmapFactory
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.condo_mini.data.AppContainer
import com.example.condo_mini.data.remote.brandLogo
import com.example.condo_mini.domain.model.Brand
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/** Fundo claro fixo: a logo da administradora aparece igual no tema claro e no escuro. */
private val LogoBackground = Color(0xFFF7F5F0)
private const val MAX_WIDE = 2.2f

/**
 * Marca da administradora: a logo (guardada no aparelho) ou, sem logo, as iniciais
 * sobre a cor dela. O mesmo bloco do painel web (`brandTile`). Logo larga deixa o
 * bloco mais largo (até 2,2× a altura) em vez de encolher dentro do quadrado.
 */
@Composable
fun BrandTile(brand: Brand, modifier: Modifier = Modifier, size: Dp = 42.dp) {
    val scheme = MaterialTheme.colorScheme
    val logo by produceState<Pair<File, Float>?>(null, brand.logoUrl) {
        val file = brand.logoUrl?.let { url -> AppContainer.apiClient?.brandLogo(url) } ?: return@produceState
        value = file to withContext(Dispatchers.IO) { aspectRatio(file) }
    }
    var failed by remember(logo) { mutableStateOf(false) }
    val current = logo?.takeIf { !failed }
    val brandColor = brand.color?.let { hex ->
        runCatching { Color(android.graphics.Color.parseColor(hex)) }.getOrNull()
    }
    val width = current?.let { (_, ratio) -> size * ratio.coerceIn(1f, MAX_WIDE) } ?: size
    Box(
        modifier = modifier
            .height(size)
            .width(width)
            .clip(MaterialTheme.shapes.medium)
            .background(if (current != null) LogoBackground else brandColor ?: scheme.primary)
            .border(1.dp, scheme.outlineVariant, MaterialTheme.shapes.medium),
        contentAlignment = Alignment.Center
    ) {
        if (current != null) {
            AsyncImage(
                model = current.first,
                contentDescription = brand.name,
                contentScale = ContentScale.Fit,
                onError = { failed = true },
                modifier = Modifier.padding(horizontal = size * 0.14f, vertical = size * 0.12f)
            )
        } else {
            Text(
                text = brand.initials,
                color = if (brandColor != null) Color.White else scheme.onPrimary,
                fontWeight = FontWeight.SemiBold,
                fontSize = (size.value * 0.34f).sp,
                letterSpacing = 0.5.sp
            )
        }
    }
}

/** Largura ÷ altura da imagem, sem decodificar os pixels; 1 se não der para ler. */
private fun aspectRatio(file: File): Float {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeFile(file.path, bounds)
    return if (bounds.outWidth > 0 && bounds.outHeight > 0) bounds.outWidth.toFloat() / bounds.outHeight else 1f
}
