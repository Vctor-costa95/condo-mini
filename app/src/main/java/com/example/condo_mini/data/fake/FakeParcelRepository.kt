package com.example.condo_mini.data.fake

import android.net.Uri
import com.example.condo_mini.data.fake.ParcelPhotoGenerator.Kind
import com.example.condo_mini.domain.model.Parcel
import com.example.condo_mini.domain.model.ParcelStatus
import com.example.condo_mini.domain.repository.ParcelRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import java.time.LocalDateTime

class FakeParcelRepository(
    private val photos: ParcelPhotoGenerator
) : ParcelRepository {

    override fun observeParcels(unit: String): Flow<List<Parcel>> = flow {
        val now = LocalDateTime.now()
        fun photo(id: String, kind: Kind, carrier: String, code: String) =
            Uri.fromFile(photos.photo(id, kind, carrier, code)).toString()

        // Os exemplos completos são da unidade padrão; as demais têm uma encomenda própria,
        // para a troca de apartamento mostrar dados diferentes.
        if (unit != FakeSessionRepository.DEFAULT_UNIT) {
            emit(
                listOf(
                    Parcel(
                        "p5", "Caixa de vinhos", "Evino", "Porteiro José", now.minusHours(5), "5306",
                        ParcelStatus.WAITING, photoUrl = photo("p5", Kind.BOX, "Evino", "5306")
                    )
                )
            )
            return@flow
        }

        emit(
            listOf(
                Parcel(
                    "p1", "Caixa média", "Correios", "Porteiro José", now.minusHours(2), "4821",
                    ParcelStatus.WAITING, photoUrl = photo("p1", Kind.BOX, "Correios", "4821")
                ),
                Parcel(
                    "p2", "Envelope", "Mercado Livre", "Porteiro Marcos", now.minusDays(1), "1937",
                    ParcelStatus.WAITING, photoUrl = photo("p2", Kind.ENVELOPE, "Mercado Livre", "1937")
                ),
                Parcel(
                    "p3", "Caixa grande", "Amazon", "Porteiro José", now.minusDays(4), "7710",
                    ParcelStatus.PICKED_UP, now.minusDays(3), photoUrl = photo("p3", Kind.BOX, "Amazon", "7710")
                ),
                // Sem foto: registros antigos ou quando o porteiro não anexou.
                Parcel("p4", "Pacote", "Shopee", "Porteiro Marcos", now.minusDays(10), "2264", ParcelStatus.PICKED_UP, now.minusDays(10))
            )
        )
    }.flowOn(Dispatchers.IO)
}
