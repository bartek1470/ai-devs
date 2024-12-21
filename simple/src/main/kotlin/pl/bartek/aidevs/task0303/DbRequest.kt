package pl.bartek.aidevs.task0303

import com.fasterxml.jackson.annotation.JsonProperty
import pl.bartek.aidevs.course.api.Task

data class DbRequest(
    val query: String,
    @JsonProperty("apikey")
    val apiKey: String,
    val task: String = Task.DATABASE.taskName,
)
