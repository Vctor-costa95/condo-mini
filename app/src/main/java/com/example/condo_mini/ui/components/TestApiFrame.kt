package com.example.condo_mini.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.example.condo_mini.BuildConfig

/** Âmbar da faixa: chama atenção sem parecer erro, nos dois temas. */
private val TestAmber = Color(0xFFE0A63A)
private val OnTestAmber = Color(0xFF2A1D05)

/**
 * App compilado para outra API (a local, nos testes): uma faixa âmbar no topo, em todas as
 * telas, com o endereço. Assim um pedido feito no emulador não se confunde com a produção.
 * Só existe quando `BuildConfig.USES_TEST_API`; o app publicado nunca mostra.
 */
@Composable
fun TestApiFrame(content: @Composable () -> Unit) {
    Column(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
            modifier = Modifier
                .fillMaxWidth()
                .background(TestAmber)
                .windowInsetsPadding(WindowInsets.statusBars)
                .padding(vertical = 4.dp)
        ) {
            Box(modifier = Modifier.size(6.dp).background(OnTestAmber, CircleShape))
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                "API de teste · ${BuildConfig.API_BASE_URL.substringAfter("://")}",
                style = MaterialTheme.typography.labelMedium,
                color = OnTestAmber,
                maxLines = 1
            )
        }
        // A faixa já ocupa a barra de status: as telas não somam esse espaço de novo.
        Box(modifier = Modifier.weight(1f).consumeWindowInsets(WindowInsets.statusBars)) {
            content()
        }
    }
}
