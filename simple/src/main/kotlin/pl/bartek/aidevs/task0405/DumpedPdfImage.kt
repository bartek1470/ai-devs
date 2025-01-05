package pl.bartek.aidevs.task0405

import java.nio.file.Path

data class DumpedPdfImage(
    val extractedImage: ExtractedPdfImage,
    val filePath: Path,
    val filePathSmall: Path?,
) {
    val filePathForDescribing: Path
        get() {
            return filePathSmall ?: filePath
        }
}
