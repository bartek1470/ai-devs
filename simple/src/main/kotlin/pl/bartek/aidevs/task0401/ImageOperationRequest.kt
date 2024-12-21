package pl.bartek.aidevs.task0401

import com.fasterxml.jackson.annotation.JsonClassDescription
import com.fasterxml.jackson.annotation.JsonPropertyDescription

@JsonClassDescription("A request to apply the operation on an image specified by the filename")
data class ImageOperationRequest(
    @get:JsonPropertyDescription("The operation to apply on an image")
    val operation: ImageOperation,
    @get:JsonPropertyDescription("The filename of an image that would be processed")
    val filename: String?,
)
