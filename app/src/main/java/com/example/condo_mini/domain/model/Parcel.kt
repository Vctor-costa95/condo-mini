package com.example.condo_mini.domain.model

import java.time.LocalDateTime

enum class ParcelStatus(val label: String) {
    WAITING("Aguardando retirada"),
    PICKED_UP("Retirada")
}

/** Encomenda recebida pela portaria. */
data class Parcel(
    val id: String,
    val description: String,
    val carrier: String,
    val receivedBy: String,
    val arrivedAt: LocalDateTime,
    val pickupCode: String,
    val status: ParcelStatus,
    val pickedUpAt: LocalDateTime? = null,
    /** Foto anexada pela portaria no recebimento (URL remota ou URI local). */
    val photoUrl: String? = null
)
