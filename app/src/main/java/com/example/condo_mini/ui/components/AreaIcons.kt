package com.example.condo_mini.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Celebration
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.Laptop
import androidx.compose.material.icons.filled.LocalLaundryService
import androidx.compose.material.icons.filled.MeetingRoom
import androidx.compose.material.icons.filled.OutdoorGrill
import androidx.compose.material.icons.filled.Park
import androidx.compose.material.icons.filled.PedalBike
import androidx.compose.material.icons.filled.Pets
import androidx.compose.material.icons.filled.Pool
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material.icons.filled.SelfImprovement
import androidx.compose.material.icons.filled.Spa
import androidx.compose.material.icons.filled.SportsEsports
import androidx.compose.material.icons.filled.SportsSoccer
import androidx.compose.material.icons.filled.SportsTennis
import androidx.compose.material.icons.filled.Theaters
import androidx.compose.material.icons.filled.Toys
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.condo_mini.domain.model.CommonArea
import com.example.condo_mini.ui.theme.ChampagneLight
import com.example.condo_mini.ui.theme.Ivory
import com.example.condo_mini.ui.util.toBrl

/**
 * Ícones que o administrador pode atribuir aos espaços. Cada um traz o par de tons
 * escuros da capa; [key] é o valor salvo em [CommonArea.iconKey] (e no backend).
 */
enum class AreaIcon(
    val key: String,
    val label: String,
    val icon: ImageVector,
    val start: Color,
    val end: Color
) {
    PARTY("party", "Festas", Icons.Default.Celebration, Color(0xFF3B3226), Color(0xFF1C1813)),
    GRILL("grill", "Churrasco", Icons.Default.OutdoorGrill, Color(0xFF4A2F24), Color(0xFF1F1511)),
    GOURMET("gourmet", "Gourmet", Icons.Default.Restaurant, Color(0xFF2C3A33), Color(0xFF131A16)),
    COURT("court", "Quadra", Icons.Default.SportsSoccer, Color(0xFF243247), Color(0xFF111824)),
    MEETING("meeting", "Reuniões", Icons.Default.MeetingRoom, Color(0xFF34303F), Color(0xFF16141C)),
    POOL("pool", "Piscina", Icons.Default.Pool, Color(0xFF1F3A44), Color(0xFF0F1B20)),
    GYM("gym", "Academia", Icons.Default.FitnessCenter, Color(0xFF33363B), Color(0xFF15171A)),
    SPA("spa", "Spa e sauna", Icons.Default.Spa, Color(0xFF3A2E3A), Color(0xFF1A141A)),
    TENNIS("tennis", "Tênis", Icons.Default.SportsTennis, Color(0xFF2F3D2A), Color(0xFF151C12)),
    KIDS("kids", "Infantil", Icons.Default.Toys, Color(0xFF44352A), Color(0xFF1E1712)),
    GAMES("games", "Jogos", Icons.Default.SportsEsports, Color(0xFF2A2F45), Color(0xFF12141F)),
    CINEMA("cinema", "Cinema", Icons.Default.Theaters, Color(0xFF3D2328), Color(0xFF1B1012)),
    COWORK("cowork", "Coworking", Icons.Default.Laptop, Color(0xFF263038), Color(0xFF11161A)),
    WELLNESS("wellness", "Bem-estar", Icons.Default.SelfImprovement, Color(0xFF37322A), Color(0xFF191612)),
    GARDEN("garden", "Jardim", Icons.Default.Park, Color(0xFF293A2C), Color(0xFF121A13)),
    PETS("pets", "Pet place", Icons.Default.Pets, Color(0xFF3F3527), Color(0xFF1C1711)),
    LAUNDRY("laundry", "Lavanderia", Icons.Default.LocalLaundryService, Color(0xFF2B3440), Color(0xFF131820)),
    BIKES("bikes", "Bicicletário", Icons.Default.PedalBike, Color(0xFF33302B), Color(0xFF171512));

    companion object {
        fun fromKey(key: String): AreaIcon = entries.firstOrNull { it.key == key } ?: MEETING
    }
}

val CommonArea.areaIcon: AreaIcon get() = AreaIcon.fromKey(iconKey)

/**
 * Capa do espaço como o morador vê na aba Reservas. Interditado, perde a seta,
 * escurece e mostra o motivo no lugar da descrição.
 */
@Composable
fun AreaCoverCard(
    area: CommonArea,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null
) {
    val visual = area.areaIcon
    val content: @Composable () -> Unit = {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(148.dp)
                .background(Brush.linearGradient(listOf(visual.start, visual.end)))
        ) {
            Icon(
                imageVector = visual.icon,
                contentDescription = null,
                tint = Color.White.copy(alpha = if (area.isClosed) 0.05f else 0.09f),
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .offset(x = 24.dp)
                    .size(150.dp)
            )
            Column(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(22.dp)
            ) {
                if (area.isClosed) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Default.Block,
                            contentDescription = null,
                            tint = ChampagneLight,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Eyebrow(text = "Interditado", color = ChampagneLight)
                    }
                } else {
                    Eyebrow(
                        text = listOfNotNull(
                            area.capacity.takeIf { it > 0 }?.let { "Até $it pessoas" },
                            if (area.fee > 0) area.fee.toBrl() else "Cortesia"
                        ).joinToString(" · "),
                        color = Ivory.copy(alpha = 0.75f)
                    )
                }
                Spacer(modifier = Modifier.height(6.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        area.name.ifBlank { "Nome do espaço" },
                        style = MaterialTheme.typography.headlineMedium,
                        color = Ivory.copy(alpha = if (area.isClosed) 0.7f else 1f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    if (!area.isClosed && onClick != null) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowForward,
                            contentDescription = null,
                            tint = Ivory.copy(alpha = 0.85f),
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
                Text(
                    area.closedReason ?: area.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = Ivory.copy(alpha = 0.7f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
    if (onClick != null) {
        Surface(
            onClick = onClick,
            shape = MaterialTheme.shapes.extraLarge,
            color = visual.end,
            modifier = modifier.fillMaxWidth(),
            content = content
        )
    } else {
        Surface(
            shape = MaterialTheme.shapes.extraLarge,
            color = visual.end,
            modifier = modifier.fillMaxWidth(),
            content = content
        )
    }
}
