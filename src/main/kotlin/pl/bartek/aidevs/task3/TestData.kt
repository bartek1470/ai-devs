package pl.bartek.aidevs.task3

import com.fasterxml.jackson.annotation.JsonInclude

@JsonInclude(JsonInclude.Include.NON_NULL)
data class TestData(
    val question: String,
    val answer: String,
    val test: TestQuestion?,
)
