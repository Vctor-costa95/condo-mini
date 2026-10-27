package com.example.condo_mini.data.fake

import com.example.condo_mini.domain.model.Notice
import com.example.condo_mini.domain.model.NoticeCategory
import com.example.condo_mini.domain.repository.NoticeRepository
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import java.time.LocalDateTime
import java.util.UUID

class FakeNoticeRepository : NoticeRepository {

    private val now = LocalDateTime.now()

    private val notices = MutableStateFlow(
        listOf(
            Notice(
                id = "n1",
                title = "Assembleia geral ordinária",
                body = "Convocamos todos os condôminos para a assembleia geral no dia 10, às 19h30, " +
                    "no salão de festas. Pauta: prestação de contas, previsão orçamentária e " +
                    "eleição do conselho fiscal.",
                category = NoticeCategory.MANAGER,
                author = "Síndico - Roberto Lima",
                publishedAt = now.minusHours(3),
                isPinned = true
            ),
            Notice(
                id = "n2",
                title = "Interrupção no fornecimento de água",
                body = "Na próxima quinta-feira, das 9h às 14h, o fornecimento de água será " +
                    "interrompido para limpeza das caixas d'água. Reserve água com antecedência.",
                category = NoticeCategory.IMPORTANT,
                author = "Administração",
                publishedAt = now.minusDays(1),
                isPinned = true
            ),
            Notice(
                id = "n3",
                title = "Manutenção do elevador social",
                body = "O elevador social do Bloco B passará por manutenção preventiva na " +
                    "segunda-feira, das 8h às 12h. Utilize o elevador de serviço.",
                category = NoticeCategory.MAINTENANCE,
                author = "Síndico - Roberto Lima",
                publishedAt = now.minusDays(2)
            ),
            Notice(
                id = "n4",
                title = "Festa junina do condomínio",
                body = "Venha participar da nossa festa junina no dia 22, a partir das 17h, na área " +
                    "da churrasqueira. Traga um prato típico!",
                category = NoticeCategory.EVENT,
                author = "Comissão de eventos",
                publishedAt = now.minusDays(5)
            ),
            Notice(
                id = "n5",
                title = "Novo horário da coleta seletiva",
                body = "A partir deste mês, a coleta de recicláveis acontece às terças e sextas. " +
                    "Separe o lixo corretamente e deposite nos contêineres identificados.",
                category = NoticeCategory.MANAGER,
                author = "Síndico - Roberto Lima",
                publishedAt = now.minusDays(9)
            )
        )
    )

    override fun observeNotices(): Flow<List<Notice>> = notices

    override suspend fun publish(
        title: String,
        body: String,
        category: NoticeCategory,
        pinned: Boolean,
        author: String
    ): Notice {
        delay(400)
        val notice = Notice(
            id = UUID.randomUUID().toString(),
            title = title.trim(),
            body = body.trim(),
            category = category,
            author = author,
            publishedAt = LocalDateTime.now(),
            isPinned = pinned
        )
        notices.update { listOf(notice) + it }
        return notice
    }

    override suspend fun delete(noticeId: String) {
        notices.update { list -> list.filterNot { it.id == noticeId } }
    }
}
