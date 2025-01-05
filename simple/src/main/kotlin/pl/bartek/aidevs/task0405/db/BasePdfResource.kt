package pl.bartek.aidevs.task0405.db

import org.jetbrains.exposed.dao.UUIDEntity
import org.jetbrains.exposed.dao.id.EntityID
import java.util.UUID

abstract class BasePdfResource(
    id: EntityID<UUID>,
    table: BasePdfResourceTable,
) : UUIDEntity(id) {
    var name by table.name
    var pages by table.pages
    var keywords: Set<String> by table.keywords
    var pdfFile by PdfFile referencedOn table.pdfFile

    override fun toString(): String = "BasePdfResource(name='$name', pdfFile=$pdfFile)"
}
