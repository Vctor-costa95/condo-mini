package com.example.condo_mini.data.fake

import com.example.condo_mini.data.pdf.CondoDocumentStore
import com.example.condo_mini.data.pdf.DocumentPdfGenerator
import com.example.condo_mini.domain.model.CondoDocument
import com.example.condo_mini.domain.model.DocumentCategory
import com.example.condo_mini.domain.repository.DocumentRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withContext
import java.io.File
import java.time.LocalDateTime
import java.util.UUID

/**
 * Documentos de exemplo em memória. Os PDFs enviados pelo síndico ficam em disco;
 * os de exemplo são gerados na primeira abertura.
 */
class FakeDocumentRepository(
    private val store: CondoDocumentStore,
    private val demoGenerator: DocumentPdfGenerator
) : DocumentRepository {

    private val documents = MutableStateFlow(seed())

    override fun observeDocuments(condoId: String): Flow<List<CondoDocument>> =
        documents.map { list -> list.filter { it.condoId == condoId }.sortedByDescending { it.uploadedAt } }

    override suspend fun getDocument(documentId: String): CondoDocument? =
        documents.value.firstOrNull { it.id == documentId }

    override suspend fun documentFile(document: CondoDocument): File = withContext(Dispatchers.IO) {
        document.filePath.takeIf { it.isNotBlank() }
            ?.let { File(it) }
            ?.takeIf { it.exists() }
            ?: demoGenerator.generate(document, demoText(document))
    }

    override suspend fun upload(
        condoId: String,
        title: String,
        category: DocumentCategory,
        sourceUri: String,
        uploadedBy: String
    ): Result<CondoDocument> = withContext(Dispatchers.IO) {
        runCatching {
            val id = UUID.randomUUID().toString()
            val stored = store.save(condoId, id, sourceUri)
            val document = CondoDocument(
                id = id,
                condoId = condoId,
                title = title.trim(),
                category = category,
                fileName = stored.displayName,
                filePath = stored.file.absolutePath,
                sizeBytes = stored.file.length(),
                uploadedAt = LocalDateTime.now(),
                uploadedBy = uploadedBy
            )
            documents.update { it + document }
            document
        }
    }

    override suspend fun delete(documentId: String) {
        val document = documents.value.firstOrNull { it.id == documentId } ?: return
        withContext(Dispatchers.IO) { store.delete(document.filePath) }
        documents.update { list -> list.filterNot { it.id == documentId } }
    }

    private fun seed(): List<CondoDocument> {
        val now = LocalDateTime.now()
        fun demo(id: String, condoId: String, title: String, category: DocumentCategory, daysAgo: Long, sizeKb: Long) =
            CondoDocument(
                id = id,
                condoId = condoId,
                title = title,
                category = category,
                fileName = "$id.pdf",
                filePath = "",
                sizeBytes = sizeKb * 1024,
                uploadedAt = now.minusDays(daysAgo),
                uploadedBy = "Síndico - Carlos Andrade"
            )
        val c1 = FakeDirectoryRepository.DEFAULT_CONDO_ID
        return listOf(
            demo("doc-regimento", c1, "Regimento interno", DocumentCategory.BYLAWS, 420, 312),
            demo("doc-convencao", c1, "Convenção do condomínio", DocumentCategory.CONVENTION, 900, 540),
            demo("doc-ata-ago", c1, "Ata da AGO · prestação de contas", DocumentCategory.MINUTES, 45, 128),
            demo("doc-ata-age", c1, "Ata da AGE · reforma da fachada", DocumentCategory.MINUTES, 120, 96),
            demo("doc-regimento-c2", "c2", "Regimento interno", DocumentCategory.BYLAWS, 300, 280)
        )
    }

    private fun demoText(document: CondoDocument): List<String> = when (document.category) {
        DocumentCategory.BYLAWS -> listOf(
            "Capítulo I · Das disposições gerais",
            "Art. 1º Este regimento disciplina o uso das áreas comuns e privativas do condomínio, " +
                "complementando a convenção, e obriga todos os condôminos, moradores, visitantes e prestadores de serviço.",
            "Capítulo II · Do silêncio",
            "Art. 2º Entre 22h e 8h, é vedada a produção de ruídos que perturbem o sossego dos demais moradores, " +
                "incluindo obras, música em volume elevado e arrastar de móveis.",
            "Capítulo III · Das áreas comuns",
            "Art. 3º O salão de festas e a churrasqueira devem ser reservados pelo aplicativo, com antecedência " +
                "mínima de 48 horas, e entregues limpos até as 10h do dia seguinte.",
            "Art. 4º A piscina funciona das 8h às 22h. Crianças menores de 12 anos devem estar acompanhadas de um responsável.",
            "Capítulo IV · Das mudanças e obras",
            "Art. 5º Mudanças e obras devem ser comunicadas à administração e acontecem de segunda a sábado, das 8h às 18h."
        )
        DocumentCategory.CONVENTION -> listOf(
            "Convenção de condomínio registrada no Cartório de Registro de Imóveis.",
            "Cláusula 1ª O condomínio é composto pelas unidades autônomas e pelas partes de uso comum descritas no memorial de incorporação.",
            "Cláusula 2ª As despesas ordinárias são rateadas na proporção da fração ideal de cada unidade.",
            "Cláusula 3ª O síndico é eleito em assembleia geral ordinária, com mandato de dois anos, permitida a reeleição."
        )
        DocumentCategory.MINUTES -> listOf(
            "Aos ${document.uploadedAt.toLocalDate().minusDays(3).dayOfMonth} dias do mês, em segunda chamada, " +
                "reuniram-se os condôminos no salão de festas, sob a presidência do síndico.",
            "Ordem do dia: ${document.title.substringAfter("·").trim()}.",
            "Após a apresentação e o debate, a proposta foi colocada em votação e aprovada pela maioria dos presentes.",
            "Nada mais havendo a tratar, foi encerrada a assembleia, lavrando-se esta ata, que segue assinada pelo presidente e pelo secretário."
        )
        DocumentCategory.OTHER -> listOf("Documento publicado pela administração do condomínio.")
    }
}
