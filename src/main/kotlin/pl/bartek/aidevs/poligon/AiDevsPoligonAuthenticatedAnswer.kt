package pl.bartek.aidevs.poligon

import com.fasterxml.jackson.annotation.JsonProperty

data class AiDevsPoligonAuthenticatedAnswer<T>(
    val task: Task,
    val answer: T,
    @JsonProperty("apikey")
    val apiKey: String,
)
