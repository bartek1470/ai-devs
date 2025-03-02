package pl.bartek.aidevs.db.resource.pdf

import pl.bartek.aidevs.db.pdf.BasePdfResourceTable

object PdfImageResourceTable : BasePdfResourceTable("pdf_image_resource") {
    val extension = text("extension")
    val filePath = text("filepath")
    val filePathSmall = text("filepath_small").nullable()
    val description = text("description")
}
