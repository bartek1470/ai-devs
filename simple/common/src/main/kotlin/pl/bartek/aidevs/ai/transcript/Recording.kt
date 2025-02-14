package pl.bartek.aidevs.ai.transcript

import org.springframework.core.io.Resource
import java.nio.file.Files
import java.nio.file.Path

data class Recording(
    val originalResource: Resource,
    val audioPath: Path,
    val transcriptPath: Path,
) {
    val transcript: String
        get() = Files.readString(transcriptPath)
}
