package pl.bartek.aidevs.courseapi

import com.fasterxml.jackson.annotation.JsonProperty

data class AiDevsAuthenticatedAnswer<T>(
    val task: Task,
    val answer: T,
    @JsonProperty("apikey")
    val apiKey: String,
)
