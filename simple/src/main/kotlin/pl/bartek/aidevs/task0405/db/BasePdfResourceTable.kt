package pl.bartek.aidevs.task0405.db

import org.jetbrains.exposed.dao.id.UUIDTable
import pl.bartek.aidevs.db.NullableStringListToNullableSetTransformer
import pl.bartek.aidevs.db.StringListToIntSetTransformer

abstract class BasePdfResourceTable(
    name: String,
) : UUIDTable(name) {
    val name = text("name")
    val pages = text("pages").transform(StringListToIntSetTransformer)
    val pdfFile = reference("pdf_file", PdfFileTable)
    val hash = text("hash").uniqueIndex()
    val keywords = text("keywords").nullable().transform(NullableStringListToNullableSetTransformer)
}
