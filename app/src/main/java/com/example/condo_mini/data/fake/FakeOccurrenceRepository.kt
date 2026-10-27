package com.example.condo_mini.data.fake

import com.example.condo_mini.domain.model.Occurrence
import com.example.condo_mini.domain.model.OccurrenceCategory
import com.example.condo_mini.domain.model.OccurrenceMessage
import com.example.condo_mini.domain.model.OccurrenceStatus
import com.example.condo_mini.domain.repository.OccurrenceRepository
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import java.time.LocalDateTime
import java.util.UUID

class FakeOccurrenceRepository : OccurrenceRepository {

    private var nextProtocol = 1045
    private val now = LocalDateTime.now()

    // Os dois primeiros pertencem à unidade padrão do login de teste.
    private val occurrences = MutableStateFlow(
        listOf(
            Occurrence(
                id = "o1",
                protocol = "OC-1041",
                title = "Barulho após as 22h",
                description = "Som alto vindo do apartamento acima durante a madrugada de sábado.",
                category = OccurrenceCategory.NOISE,
                status = OccurrenceStatus.RESOLVED,
                createdAt = now.minusDays(12),
                unit = FakeSessionRepository.DEFAULT_UNIT,
                authorName = "Ana Clara",
                messages = listOf(
                    OccurrenceMessage(
                        "Síndico - Roberto Lima",
                        fromManagement = true,
                        text = "O morador foi notificado conforme o regimento interno.",
                        sentAt = now.minusDays(11)
                    )
                )
            ),
            Occurrence(
                id = "o2",
                protocol = "OC-1042",
                title = "Lâmpada queimada na garagem",
                description = "Lâmpada próxima à vaga 32 do subsolo está queimada.",
                category = OccurrenceCategory.MAINTENANCE,
                status = OccurrenceStatus.IN_REVIEW,
                createdAt = now.minusDays(2),
                unit = FakeSessionRepository.DEFAULT_UNIT,
                authorName = "Ana Clara"
            ),
            Occurrence(
                id = "o3",
                protocol = "OC-1043",
                title = "Vazamento no hall do Bloco B",
                description = "Água escorrendo pelo teto próximo ao elevador social do 3º andar.",
                category = OccurrenceCategory.MAINTENANCE,
                status = OccurrenceStatus.OPEN,
                createdAt = now.minusHours(5),
                unit = "Apto 302 - Bloco B",
                authorName = "Marina Costa"
            ),
            Occurrence(
                id = "o4",
                protocol = "OC-1044",
                title = "Carro na vaga de visitante",
                description = "Veículo prata estacionado há três dias na vaga de visitantes.",
                category = OccurrenceCategory.GARAGE,
                status = OccurrenceStatus.OPEN,
                createdAt = now.minusDays(1),
                unit = "Apto 104 - Bloco A",
                authorName = "Paulo Mendes"
            )
        )
    )

    override fun observeOccurrences(unit: String): Flow<List<Occurrence>> =
        occurrences.map { list -> list.filter { it.unit == unit }.sortedByDescending { it.createdAt } }

    override fun observeAllOccurrences(): Flow<List<Occurrence>> =
        occurrences.map { list -> list.sortedByDescending { it.createdAt } }

    override suspend fun register(
        title: String,
        description: String,
        category: OccurrenceCategory,
        unit: String,
        authorName: String,
        photoUrls: List<String>
    ): Occurrence {
        delay(400)
        val occurrence = Occurrence(
            id = UUID.randomUUID().toString(),
            protocol = "OC-${nextProtocol++}",
            title = title.trim(),
            description = description.trim(),
            category = category,
            status = OccurrenceStatus.OPEN,
            createdAt = LocalDateTime.now(),
            unit = unit,
            authorName = authorName,
            photoUrls = photoUrls
        )
        occurrences.update { it + occurrence }
        return occurrence
    }

    override suspend fun respond(
        occurrenceId: String,
        text: String,
        author: String,
        resolve: Boolean,
        photoUrls: List<String>
    ) {
        addMessage(occurrenceId, text, author, fromManagement = true, photoUrls) {
            if (resolve) OccurrenceStatus.RESOLVED else OccurrenceStatus.IN_REVIEW
        }
    }

    override suspend fun requestDetails(occurrenceId: String, text: String, author: String, photoUrls: List<String>) {
        addMessage(occurrenceId, text, author, fromManagement = true, photoUrls) { OccurrenceStatus.NEEDS_INFO }
    }

    override suspend fun replyAsResident(occurrenceId: String, text: String, author: String, photoUrls: List<String>) {
        // Complemento do morador devolve a ocorrência para análise.
        addMessage(occurrenceId, text, author, fromManagement = false, photoUrls) { OccurrenceStatus.IN_REVIEW }
    }

    private suspend fun addMessage(
        id: String,
        text: String,
        author: String,
        fromManagement: Boolean,
        photoUrls: List<String>,
        nextStatus: () -> OccurrenceStatus
    ) {
        delay(300)
        val message = OccurrenceMessage(author, fromManagement, text.trim(), LocalDateTime.now(), photoUrls)
        occurrences.update { list ->
            list.map {
                if (it.id == id) it.copy(messages = it.messages + message, status = nextStatus()) else it
            }
        }
    }
}
