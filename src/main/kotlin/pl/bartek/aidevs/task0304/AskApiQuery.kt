package pl.bartek.aidevs.task0304

import com.fasterxml.jackson.annotation.JsonPropertyDescription

data class AskApiQuery(
    @get:JsonPropertyDescription("Accepts only ONE Polish word")
    val query: String,
)
