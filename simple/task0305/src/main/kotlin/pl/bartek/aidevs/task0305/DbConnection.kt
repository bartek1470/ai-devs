package pl.bartek.aidevs.task0305

import com.fasterxml.jackson.annotation.JsonProperty

data class DbConnection(
    @JsonProperty("user1_id")
    val user1Id: Int,
    @JsonProperty("user2_id")
    val user2Id: Int,
)
