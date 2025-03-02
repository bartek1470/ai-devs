package pl.bartek.aidevs.db.resource.pdf

import pl.bartek.aidevs.db.pdf.BasePdfResourceTable

object PdfTextResourceTable : BasePdfResourceTable("pdf_text_resource") {
    val content = text("content").nullable()
    val originalContent = text("original_content")
}
