package pl.bartek.aidevs.vision

import org.springframework.core.io.Resource

data class ImageFileToView(
    val image: Resource,
    val filename: String = image.filename!!,
)
