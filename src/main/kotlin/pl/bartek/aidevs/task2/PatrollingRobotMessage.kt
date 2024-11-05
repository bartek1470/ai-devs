package pl.bartek.aidevs.task2

import com.fasterxml.jackson.annotation.JsonProperty

data class PatrollingRobotMessage(
    @JsonProperty("msgID")
    val messageId: String,
    val text: String,
)
