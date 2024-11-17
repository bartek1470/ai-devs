package pl.bartek.aidevs.task0204

import org.springframework.util.MimeType
import java.nio.file.Path

data class Note(
    val file: Path,
    val resourcePathToProcess: Path,
    val mimeType: MimeType,
)
