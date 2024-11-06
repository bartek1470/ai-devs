package pl.bartek.aidevs.task3

import com.fasterxml.jackson.annotation.JsonProperty

data class TestQuestion(
    @JsonProperty("q")
    val question: String,
    @JsonProperty("a")
    val answer: String,
)
