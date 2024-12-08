package pl.bartek.aidevs.ai.openai.api.model

import com.fasterxml.jackson.annotation.JsonProperty

data class FileResponse(
    val id: String,
    @JsonProperty("object")
    val obj: String,
    val bytes: Long,
    @JsonProperty("created_at")
    val createdAt: Long,
    val filename: String,
    val purpose: String,
)
