package pl.bartek.aidevs.task0405.db

import org.jetbrains.exposed.dao.id.UUIDTable
import kotlin.io.path.Path
import kotlin.io.path.absolutePathString

object PdfFileTable : UUIDTable("pdf_file_table") {
    val filePath =
        text("file_path")
            .uniqueIndex()
            .transform(
                wrap = { Path(it) },
                unwrap = { it.absolutePathString() },
            )
}
