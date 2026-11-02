package com.example.condo_mini.data.remote

import android.content.Context
import com.example.condo_mini.data.fake.demoDocumentText
import com.example.condo_mini.data.pdf.BillPdfGenerator
import com.example.condo_mini.data.pdf.DocumentPdfGenerator
import com.example.condo_mini.domain.model.Bill
import com.example.condo_mini.domain.model.CondoDocument
import com.example.condo_mini.domain.model.DocumentCategory
import com.example.condo_mini.domain.model.Occurrence
import com.example.condo_mini.domain.model.OccurrenceCategory
import com.example.condo_mini.domain.model.Parcel
import com.example.condo_mini.domain.model.UnitDocument
import com.example.condo_mini.domain.model.User
import com.example.condo_mini.domain.repository.BillRepository
import com.example.condo_mini.domain.repository.DocumentRepository
import com.example.condo_mini.domain.repository.OccurrenceRepository
import com.example.condo_mini.domain.repository.ParcelRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.time.YearMonth

/**
 * Fotos escolhidas no aparelho (content:// ou file://) sobem para a API antes do
 * registro; as que já são da API seguem como estão.
 */
private suspend fun ApiClient.uploadPhotos(urls: List<String>, condoId: String): List<String> =
    urls.map { url ->
        if (url.startsWith("http")) url
        else JSONObject(upload("files", url, mapOf("condoId" to condoId))).getString("url")
    }

/** PDFs baixados da API ficam no cache, um arquivo por id. */
private fun Context.pdfCache(folder: String, id: String): File = File(cacheDir, "api-pdfs/$folder/$id.pdf")

class ApiBillRepository(
    private val api: ApiClient,
    private val context: Context,
    private val pdfGenerator: BillPdfGenerator,
    private val condoId: CondoProvider
) : BillRepository {

    override fun observeBills(unit: String): Flow<List<Bill>> =
        api.live { api.get("bills", mapOf("condoId" to condoId(), "unit" to unit)).list { it.toBill() } }

    override suspend fun getBill(billId: String, unit: String): Bill? =
        runCatching { api.get("bills/$billId").obj { it.toBill() } }.getOrNull()

    /** O PDF enviado pela administração; sem ele, um de demonstração gerado no aparelho. */
    override suspend fun billPdf(bill: Bill, payer: User): File {
        val target = context.pdfCache("bills", bill.id)
        val downloaded = runCatching { api.download(api.url("bills/${bill.id}/pdf"), target) }.getOrDefault(false)
        return if (downloaded) target else withContext(Dispatchers.IO) { pdfGenerator.generate(bill, payer) }
    }

    override fun observeUnitDocuments(): Flow<List<UnitDocument>> =
        api.live { api.get("unit-documents", mapOf("condoId" to condoId())).list { it.toUnitDocument() } }

    override suspend fun uploadUnitDocument(
        condoId: String,
        unit: String,
        reference: YearMonth,
        sourceUri: String
    ): Result<UnitDocument> = runCatching {
        api.upload(
            "unit-documents", sourceUri,
            mapOf("condoId" to condoId, "unit" to unit, "reference" to reference.toString())
        ).obj { it.toUnitDocument() }
    }

    override suspend fun deleteUnitDocument(documentId: String) = api.quietly {
        api.send("DELETE", "unit-documents/$documentId")
    }
}

class ApiParcelRepository(private val api: ApiClient) : ParcelRepository {

    override fun observeParcels(condoId: String, unit: String): Flow<List<Parcel>> =
        api.live { api.get("parcels", mapOf("condoId" to condoId, "unit" to unit)).list { it.toParcel() } }

    override fun observeAllParcels(condoId: String): Flow<List<Parcel>> =
        api.live { api.get("parcels", mapOf("condoId" to condoId)).list { it.toParcel() } }

    // O servidor registra quem recebeu pelo login; [receivedBy] fica para os dados de demonstração.
    override suspend fun register(
        condoId: String,
        unit: String,
        description: String,
        carrier: String,
        receivedBy: String,
        photoUrl: String?
    ): Parcel {
        val photo = photoUrl?.let { api.uploadPhotos(listOf(it), condoId).first() }
        return api.send(
            "POST", "parcels",
            JSONObject()
                .put("condoId", condoId)
                .put("unit", unit)
                .put("description", description)
                .put("carrier", carrier)
                .put("photoUrl", photo ?: JSONObject.NULL)
        ).obj { it.toParcel() }
    }

    override suspend fun deliver(parcelId: String, code: String, deliveredBy: String): Result<Parcel> = runCatching {
        api.send("POST", "parcels/$parcelId/deliver", JSONObject().put("code", code.trim())).obj { it.toParcel() }
    }
}

class ApiOccurrenceRepository(private val api: ApiClient, private val condoId: CondoProvider) : OccurrenceRepository {

    override fun observeOccurrences(unit: String): Flow<List<Occurrence>> =
        api.live { api.get("occurrences", mapOf("condoId" to condoId(), "unit" to unit)).list { it.toOccurrence() } }

    override fun observeAllOccurrences(): Flow<List<Occurrence>> =
        api.live { api.get("occurrences", mapOf("condoId" to condoId())).list { it.toOccurrence() } }

    override suspend fun register(
        title: String,
        description: String,
        category: OccurrenceCategory,
        unit: String,
        authorName: String,
        photoUrls: List<String>
    ): Occurrence {
        val condo = condoId()
        return api.send(
            "POST", "occurrences",
            JSONObject()
                .put("condoId", condo)
                .put("title", title.trim())
                .put("description", description.trim())
                .put("category", category.name)
                .put("unit", unit)
                .put("photoUrls", JSONArray(api.uploadPhotos(photoUrls, condo)))
        ).obj { it.toOccurrence() }
    }

    override suspend fun respond(
        occurrenceId: String,
        text: String,
        author: String,
        resolve: Boolean,
        photoUrls: List<String>
    ) = message(occurrenceId, "respond", text, photoUrls) { put("resolve", resolve) }

    override suspend fun requestDetails(occurrenceId: String, text: String, author: String, photoUrls: List<String>) =
        message(occurrenceId, "request-details", text, photoUrls)

    override suspend fun replyAsResident(occurrenceId: String, text: String, author: String, photoUrls: List<String>) =
        message(occurrenceId, "reply", text, photoUrls)

    // O autor da mensagem vem do login.
    private suspend fun message(
        occurrenceId: String,
        action: String,
        text: String,
        photoUrls: List<String>,
        extra: JSONObject.() -> Unit = {}
    ) = api.quietly {
        val body = JSONObject()
            .put("text", text.trim())
            .put("photoUrls", JSONArray(api.uploadPhotos(photoUrls, condoId())))
            .apply(extra)
        api.send("POST", "occurrences/$occurrenceId/$action", body)
    }
}

class ApiDocumentRepository(
    private val api: ApiClient,
    private val context: Context,
    private val demoGenerator: DocumentPdfGenerator
) : DocumentRepository {

    override fun observeDocuments(condoId: String): Flow<List<CondoDocument>> =
        api.live { api.get("documents", mapOf("condoId" to condoId)).list { it.toDocument() } }

    override suspend fun getDocument(documentId: String): CondoDocument? =
        runCatching { api.get("documents/$documentId").obj { it.toDocument() } }.getOrNull()

    /** Baixa o PDF publicado; os documentos de demonstração (sem URL) são gerados no aparelho. */
    override suspend fun documentFile(document: CondoDocument): File {
        if (document.filePath.isBlank()) {
            return withContext(Dispatchers.IO) { demoGenerator.generate(document, demoDocumentText(document)) }
        }
        val target = context.pdfCache("documents", document.id)
        if (!target.exists() && !api.download(document.filePath.toHttpUrl(), target)) {
            throw ApiException("Documento não encontrado.", 404)
        }
        return target
    }

    // O servidor assina com o nome de quem está logado; [uploadedBy] fica para os dados de demonstração.
    override suspend fun upload(
        condoId: String,
        title: String,
        category: DocumentCategory,
        sourceUri: String,
        uploadedBy: String
    ): Result<CondoDocument> = runCatching {
        api.upload(
            "documents", sourceUri,
            mapOf("condoId" to condoId, "title" to title.trim(), "category" to category.name)
        ).obj { it.toDocument() }
    }

    override suspend fun delete(documentId: String) = api.quietly {
        api.send("DELETE", "documents/$documentId")
    }
}
