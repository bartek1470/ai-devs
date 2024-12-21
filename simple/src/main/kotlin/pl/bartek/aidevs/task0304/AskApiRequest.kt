package pl.bartek.aidevs.task0304

import com.fasterxml.jackson.annotation.JsonProperty

data class AskApiRequest(
    @get:JsonProperty("apikey")
    val apiKey: String,
    val query: String,
)
