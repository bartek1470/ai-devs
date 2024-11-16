package pl.bartek.aidevs.task0201

import java.nio.file.Path

data class Recording(
    val audioPath: Path,
    val transcriptPath: Path,
    val transcript: String,
)
