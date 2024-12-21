package pl.bartek.aidevs.task0401

import com.fasterxml.jackson.annotation.JsonClassDescription
import com.fasterxml.jackson.annotation.JsonPropertyDescription

@JsonClassDescription("A request to describe an image located at the url")
data class DescribeImageRequest(
    @get:JsonPropertyDescription("The url to an image")
    val url: String,
)
