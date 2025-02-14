package pl.bartek.aidevs.task0305

import com.fasterxml.jackson.annotation.JsonProperty
import java.time.LocalDate

data class DbUser(
    val id: Int,
    val username: String,
    @JsonProperty("access_level")
    val accessLevel: String? = null,
    @JsonProperty("is_active")
    val isActive: Int,
    @JsonProperty("lastlog")
    val lastLog: LocalDate? = null,
)
