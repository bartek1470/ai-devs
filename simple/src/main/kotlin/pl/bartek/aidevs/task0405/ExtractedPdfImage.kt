package pl.bartek.aidevs.task0405

data class ExtractedPdfImage(
    val pages: Set<Int>,
    val name: String,
    val extension: String,
    val hash: String,
)
