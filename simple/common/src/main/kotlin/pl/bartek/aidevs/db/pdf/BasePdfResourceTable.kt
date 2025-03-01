package pl.bartek.aidevs.db.pdf

import org.jetbrains.exposed.dao.id.UUIDTable
import pl.bartek.aidevs.db.transformers.StringListToIntSetTransformer

abstract class BasePdfResourceTable(
    name: String,
) : UUIDTable(name) {
    val name = text("name")
    val pages = text("pages").transform(StringListToIntSetTransformer)
    val pdfFile = reference("pdf_file", PdfFileTable)
    val hash = text("hash").uniqueIndex()
}
