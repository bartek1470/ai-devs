package pl.bartek.aidevs.transcript

import java.nio.file.Path

data class FileToTranscribe(
    val path: Path,
    val language: WhisperLanguage? = null,
    val model: WhisperModel? = null,
)
