package pl.bartek.aidevs.task0405

data class DescribedExtractedImage(
    val image: ExtractedPdfImage,
    val description: String,
    val text: String?,
)
