package pl.bartek.aidevs.db.pdf

object PdfTextResourceTable : BasePdfResourceTable("pdf_text_resource") {
    val content = text("content").nullable()
    val originalContent = text("original_content")
}
