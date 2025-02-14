package pl.bartek.aidevs.task0403

import com.fasterxml.jackson.annotation.JsonClassDescription
import com.fasterxml.jackson.annotation.JsonPropertyDescription

@JsonClassDescription("Information needed to visit a website")
data class VisitSiteRequest(
    @JsonPropertyDescription(
        "An absolute or relative URL to the website. If absolute URL, it has to start with the base path configured in the tool",
    )
    val url: String,
)
