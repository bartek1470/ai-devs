package pl.bartek.aidevs.task0405.db

object PdfImageResourceTable : BasePdfResourceTable("pdf_image_resource") {
    val extension = text("extension")
    val filePath = text("filepath")
    val filePathSmall = text("filepath_small").nullable()
    val description = text("description")
}
