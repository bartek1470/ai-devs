package pl.bartek.aidevs.task0405

import java.nio.file.Path

data class ExtractedPdfImage(
    val indexes: Set<Int>,
    val pages: Set<Int>,
    val name: String,
    val extension: String,
    val hash: String,
    val filePath: Path,
    val filePathSmall: Path?,
)
