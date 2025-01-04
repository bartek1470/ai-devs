package pl.bartek.aidevs.task0405.db

import org.jetbrains.exposed.dao.EntityClass
import org.jetbrains.exposed.dao.UUIDEntity
import org.jetbrains.exposed.dao.id.EntityID
import java.nio.file.Path
import java.util.UUID
import kotlin.io.path.nameWithoutExtension

class PdfFile(
    id: EntityID<UUID>,
) : UUIDEntity(id) {
    companion object : EntityClass<UUID, PdfFile>(PdfFileTable)

    var filePath by PdfFileTable.filePath
    val text by PdfTextResource referrersOn PdfTextResourceTable.pdfFile
    val images by PdfImageResource referrersOn PdfImageResourceTable.pdfFile

    val resourcesDir: Path
        get() {
            return filePath.resolveSibling(filePath.fileName.nameWithoutExtension)
        }
}
