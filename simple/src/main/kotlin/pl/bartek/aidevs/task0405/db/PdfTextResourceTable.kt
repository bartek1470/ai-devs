package pl.bartek.aidevs.task0405.db

object PdfTextResourceTable : BasePdfResourceTable("pdf_text_resource") {
    val content = text("content").nullable()
    val originalContent = text("original_content")
}
