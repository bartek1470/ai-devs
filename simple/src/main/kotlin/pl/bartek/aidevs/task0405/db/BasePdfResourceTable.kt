package pl.bartek.aidevs.task0405.db

import org.jetbrains.exposed.dao.id.UUIDTable

abstract class BasePdfResourceTable(
    name: String,
) : UUIDTable(name) {
    val name = text("name")
    val pages = text("pages").transform(StringListToIntSetTransformer)
    val indexes = text("indexes").transform(StringListToIntSetTransformer)
    val pdfFile = reference("pdf_file", PdfFileTable)
}
