package pl.bartek.aidevs.transcript

import org.springframework.core.io.Resource

data class FileToTranscribe(
    val audio: Resource,
    val filename: String = audio.filename!!,
    val language: WhisperLanguage? = null,
    val model: WhisperModel? = null,
)
