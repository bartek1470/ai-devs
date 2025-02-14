package pl.bartek.aidevs.task0102

import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.annotation.JsonProperty

@JsonInclude(JsonInclude.Include.NON_NULL)
data class PatrollingRobotMessage(
    val code: Int? = null,
    @JsonProperty("msgID")
    val messageId: String? = null,
    val text: String? = null,
    val message: String? = null,
)
