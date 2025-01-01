package pl.bartek.aidevs.task0405

data class PdfStructure(
    val resources: List<PdfResource>,
)

data class PdfResource(
    val filename: String,
    val pageNumbers: List<Int>,
    val type: String,
)
