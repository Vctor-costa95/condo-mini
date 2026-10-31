package com.example.condo_mini.data.fake

import android.net.Uri
import com.example.condo_mini.data.fake.ParcelPhotoGenerator.Kind
import com.example.condo_mini.domain.model.Parcel
import com.example.condo_mini.domain.model.ParcelStatus
import com.example.condo_mini.domain.repository.ParcelRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.time.LocalDateTime
import java.util.UUID
import kotlin.random.Random

/** Uma lista só, compartilhada: o que a portaria registra aparece na hora para o morador. */
class FakeParcelRepository(
    private val photos: ParcelPhotoGenerator
) : ParcelRepository {

    private val parcels = MutableStateFlow<List<Parcel>>(emptyList())
    private val seedLock = Mutex()
    private var seeded = false

    override fun observeParcels(condoId: String, unit: String): Flow<List<Parcel>> =
        parcels.onStart { ensureSeeded() }
            .map { list -> list.filter { it.condoId == condoId && it.unit == unit }.sortedByDescending { it.arrivedAt } }

    override fun observeAllParcels(condoId: String): Flow<List<Parcel>> =
        parcels.onStart { ensureSeeded() }
            .map { list -> list.filter { it.condoId == condoId }.sortedByDescending { it.arrivedAt } }

    override suspend fun register(
        condoId: String,
        unit: String,
        description: String,
        carrier: String,
        receivedBy: String,
        photoUrl: String?
    ): Parcel {
        delay(300)
        ensureSeeded()
        val parcel = Parcel(
            id = UUID.randomUUID().toString(),
            description = description,
            carrier = carrier,
            receivedBy = receivedBy,
            arrivedAt = LocalDateTime.now(),
            pickupCode = newCode(),
            status = ParcelStatus.WAITING,
            photoUrl = photoUrl,
            unit = unit,
            condoId = condoId
        )
        parcels.update { it + parcel }
        return parcel
    }

    override suspend fun deliver(parcelId: String, code: String, deliveredBy: String): Result<Parcel> {
        delay(250)
        val parcel = parcels.value.firstOrNull { it.id == parcelId }
            ?: return Result.failure(IllegalArgumentException("Encomenda não encontrada."))
        if (parcel.pickupCode != code.trim()) {
            return Result.failure(IllegalArgumentException("Código não confere. Peça ao morador para conferir no app."))
        }
        val delivered = parcel.copy(status = ParcelStatus.PICKED_UP, pickedUpAt = LocalDateTime.now(), deliveredBy = deliveredBy)
        parcels.update { list -> list.map { if (it.id == parcelId) delivered else it } }
        return Result.success(delivered)
    }

    /** 4 dígitos, sem repetir entre as que ainda aguardam retirada. */
    private fun newCode(): String {
        val inUse = parcels.value.filter { it.status == ParcelStatus.WAITING }.map { it.pickupCode }.toSet()
        return generateSequence { Random.nextInt(1000, 10000).toString() }.first { it !in inUse }
    }

    // As fotos de exemplo são desenhadas em disco: só na primeira leitura, fora da thread principal.
    private suspend fun ensureSeeded() = seedLock.withLock {
        if (seeded) return@withLock
        val seed = withContext(Dispatchers.IO) { seedParcels() }
        parcels.update { seed + it }
        seeded = true
    }

    private fun seedParcels(): List<Parcel> {
        val now = LocalDateTime.now()
        val condo = FakeDirectoryRepository.DEFAULT_CONDO_ID
        val home = FakeSessionRepository.DEFAULT_UNIT
        fun photo(id: String, kind: Kind, carrier: String, code: String) =
            Uri.fromFile(photos.photo(id, kind, carrier, code)).toString()

        return listOf(
            Parcel(
                "p1", "Caixa média", "Correios", "Porteiro José", now.minusHours(2), "4821",
                ParcelStatus.WAITING, photoUrl = photo("p1", Kind.BOX, "Correios", "4821"), unit = home, condoId = condo
            ),
            Parcel(
                "p2", "Envelope", "Mercado Livre", "Porteiro Marcos", now.minusDays(1), "1937",
                ParcelStatus.WAITING, photoUrl = photo("p2", Kind.ENVELOPE, "Mercado Livre", "1937"), unit = home, condoId = condo
            ),
            Parcel(
                "p3", "Caixa grande", "Amazon", "Porteiro José", now.minusDays(4), "7710",
                ParcelStatus.PICKED_UP, now.minusDays(3), photoUrl = photo("p3", Kind.BOX, "Amazon", "7710"),
                unit = home, condoId = condo, deliveredBy = "Porteiro José"
            ),
            // Sem foto: registros antigos ou quando o porteiro não anexou.
            Parcel(
                "p4", "Pacote", "Shopee", "Porteiro Marcos", now.minusDays(10), "2264",
                ParcelStatus.PICKED_UP, now.minusDays(10), unit = home, condoId = condo, deliveredBy = "Porteiro Marcos"
            ),
            Parcel(
                "p5", "Caixa de vinhos", "Evino", "Porteiro José", now.minusHours(5), "5306",
                ParcelStatus.WAITING, photoUrl = photo("p5", Kind.BOX, "Evino", "5306"),
                unit = "Apto 1402 - Bloco A", condoId = condo
            ),
            Parcel(
                "p6", "Envelope", "Correios", "Porteiro Antônio", now.minusHours(7), "3158",
                ParcelStatus.WAITING, photoUrl = photo("p6", Kind.ENVELOPE, "Correios", "3158"),
                unit = "Apto 302 - Bloco B", condoId = condo
            ),
            Parcel(
                "p7", "Caixa pequena", "Mercado Livre", "Porteiro Antônio", now.minusDays(2), "6642",
                ParcelStatus.WAITING, unit = "Apto 503 - Bloco B", condoId = condo
            )
        )
    }
}
