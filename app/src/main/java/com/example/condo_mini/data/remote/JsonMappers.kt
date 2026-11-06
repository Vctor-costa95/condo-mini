package com.example.condo_mini.data.remote

import com.example.condo_mini.domain.model.Bill
import com.example.condo_mini.domain.model.BookingMode
import com.example.condo_mini.domain.model.BillStatus
import com.example.condo_mini.domain.model.CommonArea
import com.example.condo_mini.domain.model.CondoDocument
import com.example.condo_mini.domain.model.Condominium
import com.example.condo_mini.domain.model.DocumentCategory
import com.example.condo_mini.domain.model.Notice
import com.example.condo_mini.domain.model.NoticeCategory
import com.example.condo_mini.domain.model.Occurrence
import com.example.condo_mini.domain.model.OccurrenceCategory
import com.example.condo_mini.domain.model.OccurrenceMessage
import com.example.condo_mini.domain.model.OccurrenceStatus
import com.example.condo_mini.domain.model.Parcel
import com.example.condo_mini.domain.model.ParcelStatus
import com.example.condo_mini.domain.model.RepairKind
import com.example.condo_mini.domain.model.Reservation
import com.example.condo_mini.domain.model.ReservationPeriod
import com.example.condo_mini.domain.model.ReservationStatus
import com.example.condo_mini.domain.model.ServiceBooking
import com.example.condo_mini.domain.model.ServiceType
import com.example.condo_mini.domain.model.Tower
import com.example.condo_mini.domain.model.UnitDocument
import com.example.condo_mini.domain.model.User
import com.example.condo_mini.domain.model.UserRole
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.YearMonth
import java.time.ZoneId

// Conversão entre o JSON da API e os modelos do app. Datas vêm em ISO
// ("2026-11-02", "2026-11-02T10:30:00", "2026-11") e enums pelo nome.

/** Texto do campo, ou null quando ausente ou JSON null (optString devolveria "null"). */
internal fun JSONObject.str(key: String): String? = if (isNull(key)) null else getString(key)

internal fun JSONObject.text(key: String): String = str(key).orEmpty()

internal fun JSONArray.objects(): List<JSONObject> = List(length()) { getJSONObject(it) }

internal fun JSONArray.strings(): List<String> = List(length()) { getString(it) }

internal fun <T> String.list(map: (JSONObject) -> T): List<T> = JSONArray(this).objects().map(map)

internal fun <T> String.obj(map: (JSONObject) -> T): T = map(JSONObject(this))

/** A API grava no horário de Brasília; o app mostra no fuso do aparelho. */
private val SERVER_ZONE: ZoneId = ZoneId.of("America/Sao_Paulo")

private fun String.toDeviceTime(): LocalDateTime =
    LocalDateTime.parse(this).atZone(SERVER_ZONE).withZoneSameInstant(ZoneId.systemDefault()).toLocalDateTime()

private fun JSONObject.dateTime(key: String): LocalDateTime = getString(key).toDeviceTime()

private fun JSONObject.dateTimeOrNull(key: String): LocalDateTime? = str(key)?.toDeviceTime()

internal fun JSONObject.toUser(): User {
    val units = getJSONArray("units").strings()
    return User(
        id = getString("id"),
        name = getString("name"),
        unit = text("unit"),
        role = UserRole.valueOf(getString("role")),
        email = text("email"),
        phone = text("phone"),
        condoId = text("condoId"),
        units = units,
        login = text("login")
    )
}

/** Mesmo formato que a API devolve, para guardar a sessão no aparelho. */
internal fun User.toJson(): JSONObject = JSONObject()
    .put("id", id)
    .put("name", name)
    .put("unit", unit)
    .put("units", JSONArray(units))
    .put("role", role.name)
    .put("email", email)
    .put("phone", phone)
    .put("condoId", condoId)
    .put("login", login)

internal fun JSONObject.toCondo(): Condominium = Condominium(
    id = getString("id"),
    name = getString("name"),
    address = text("address"),
    towers = getJSONArray("towers").objects().map { t ->
        val overrides = t.getJSONObject("floorOverrides")
        Tower(
            id = t.getString("id"),
            name = t.getString("name"),
            floors = t.getInt("floors"),
            unitsPerFloor = t.getInt("unitsPerFloor"),
            floorOverrides = overrides.keys().asSequence().associate { it.toInt() to overrides.getInt(it) }
        )
    }
)

internal fun Condominium.toJson(): JSONObject = JSONObject()
    .put("id", id)
    .put("name", name)
    .put("address", address)
    .put("towers", JSONArray(towers.map { t ->
        JSONObject()
            .put("id", t.id)
            .put("name", t.name)
            .put("floors", t.floors)
            .put("unitsPerFloor", t.unitsPerFloor)
            .put("floorOverrides", JSONObject(t.floorOverrides.mapKeys { it.key.toString() }))
    }))

internal fun JSONObject.toNotice(): Notice = Notice(
    id = getString("id"),
    title = getString("title"),
    body = getString("body"),
    category = NoticeCategory.valueOf(getString("category")),
    author = getString("author"),
    publishedAt = dateTime("publishedAt"),
    isPinned = getBoolean("isPinned")
)

internal fun JSONObject.toArea(): CommonArea = CommonArea(
    id = getString("id"),
    name = getString("name"),
    description = text("description"),
    capacity = getInt("capacity"),
    fee = getDouble("fee"),
    rules = text("rules"),
    iconKey = text("iconKey"),
    closedReason = str("closedReason"),
    condoId = text("condoId"),
    // Modo que este app ainda não conhece vira turno, em vez de derrubar a lista de espaços.
    bookingMode = BookingMode.entries.firstOrNull { it.name == optString("bookingMode") } ?: BookingMode.PERIOD,
    opensAt = optInt("opensAt", 8),
    closesAt = optInt("closesAt", 22),
    maxHours = optInt("maxHours", 2)
)

internal fun CommonArea.toJson(): JSONObject = JSONObject()
    .put("id", id)
    .put("condoId", condoId)
    .put("name", name)
    .put("description", description)
    .put("capacity", capacity)
    .put("fee", fee)
    .put("rules", rules)
    .put("iconKey", iconKey)
    .put("closedReason", closedReason ?: JSONObject.NULL)
    .put("bookingMode", bookingMode.name)
    .put("opensAt", opensAt)
    .put("closesAt", closesAt)
    .put("maxHours", maxHours)

internal fun JSONObject.toReservation(): Reservation {
    val period = str("period")?.let(ReservationPeriod::valueOf)
    return Reservation(
        id = getString("id"),
        area = getJSONObject("area").toArea(),
        date = LocalDate.parse(getString("date")),
        period = period,
        unit = getString("unit"),
        status = ReservationStatus.valueOf(getString("status")),
        residentName = text("residentName"),
        rejectionReason = str("rejectionReason"),
        startHour = optInt("startHour", period?.startHour ?: 0),
        endHour = optInt("endHour", period?.endHour ?: 0)
    )
}

internal fun JSONObject.toBooking(): ServiceBooking = ServiceBooking(
    id = getString("id"),
    type = ServiceType.valueOf(getString("type")),
    date = LocalDate.parse(getString("date")),
    period = ReservationPeriod.valueOf(getString("period")),
    unit = getString("unit"),
    residentName = text("residentName"),
    condoId = text("condoId"),
    status = ReservationStatus.valueOf(getString("status")),
    repairKind = str("repairKind")?.let(RepairKind::valueOf),
    provider = text("provider"),
    rejectionReason = str("rejectionReason")
)

internal fun JSONObject.toBill(): Bill = Bill(
    id = getString("id"),
    reference = YearMonth.parse(getString("reference")),
    amount = getDouble("amount"),
    dueDate = LocalDate.parse(getString("dueDate")),
    status = BillStatus.valueOf(getString("status")),
    barcode = getString("barcode"),
    paidAt = str("paidAt")?.let(LocalDate::parse)
)

/** [UnitDocument.filePath] guarda a URL do PDF na API. */
internal fun JSONObject.toUnitDocument(): UnitDocument = UnitDocument(
    id = getString("id"),
    unit = getString("unit"),
    reference = YearMonth.parse(getString("reference")),
    fileName = getString("fileName"),
    filePath = getString("url"),
    sizeBytes = getLong("sizeBytes"),
    uploadedAt = dateTime("uploadedAt"),
    condoId = text("condoId")
)

internal fun JSONObject.toParcel(): Parcel = Parcel(
    id = getString("id"),
    description = getString("description"),
    carrier = text("carrier"),
    receivedBy = text("receivedBy"),
    arrivedAt = dateTime("arrivedAt"),
    pickupCode = text("pickupCode"),
    status = ParcelStatus.valueOf(getString("status")),
    pickedUpAt = dateTimeOrNull("pickedUpAt"),
    photoUrl = str("photoUrl"),
    unit = text("unit"),
    condoId = text("condoId"),
    deliveredBy = str("deliveredBy")
)

internal fun JSONObject.toOccurrence(): Occurrence = Occurrence(
    id = getString("id"),
    protocol = getString("protocol"),
    title = getString("title"),
    description = getString("description"),
    category = OccurrenceCategory.valueOf(getString("category")),
    status = OccurrenceStatus.valueOf(getString("status")),
    createdAt = dateTime("createdAt"),
    unit = getString("unit"),
    authorName = text("authorName"),
    photoUrls = getJSONArray("photoUrls").strings(),
    messages = getJSONArray("messages").objects().map { m ->
        OccurrenceMessage(
            author = m.getString("author"),
            fromManagement = m.getBoolean("fromManagement"),
            text = m.getString("text"),
            sentAt = m.dateTime("sentAt"),
            photoUrls = m.getJSONArray("photoUrls").strings()
        )
    }
)

/** [CondoDocument.filePath] guarda a URL do PDF; vazio = documento de demonstração. */
internal fun JSONObject.toDocument(): CondoDocument = CondoDocument(
    id = getString("id"),
    condoId = getString("condoId"),
    title = getString("title"),
    category = DocumentCategory.valueOf(getString("category")),
    fileName = getString("fileName"),
    filePath = text("url"),
    sizeBytes = getLong("sizeBytes"),
    uploadedAt = dateTime("uploadedAt"),
    uploadedBy = text("uploadedBy")
)
