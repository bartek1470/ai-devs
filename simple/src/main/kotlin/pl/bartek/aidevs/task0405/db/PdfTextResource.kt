package pl.bartek.aidevs.task0405.db

import org.jetbrains.exposed.dao.UUIDEntityClass
import org.jetbrains.exposed.dao.id.EntityID
import java.util.UUID

class PdfTextResource(
    id: EntityID<UUID>,
) : BasePdfResource(id, PdfTextResourceTable) {
    companion object : UUIDEntityClass<PdfTextResource>(PdfTextResourceTable)

    var content by PdfTextResourceTable.content
    var originalContent by PdfTextResourceTable.originalContent
}
