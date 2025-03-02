package pl.bartek.aidevs.ai.transcript

import java.nio.file.Path

data class TranscriptionRequest(
    val path: Path,
    val language: WhisperLanguage = WhisperLanguage.POLISH,
    val model: WhisperModel = WhisperModel.MEDIUM,
)
