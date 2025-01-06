package pl.bartek.aidevs.task0405.db

import org.jetbrains.exposed.dao.UUIDEntityClass
import org.jetbrains.exposed.dao.id.EntityID
import java.util.UUID

class PdfImageResource(
    id: EntityID<UUID>,
) : BasePdfResource(id, PdfImageResourceTable) {
    companion object : UUIDEntityClass<PdfImageResource>(PdfImageResourceTable)

    var extension by PdfImageResourceTable.extension
    var filePath by PdfImageResourceTable.filePath
    var filePathSmall by PdfImageResourceTable.filePathSmall
    var description by PdfImageResourceTable.description
}
