package pl.bartek.aidevs.task0405

import java.nio.file.Path
import kotlin.io.path.extension
import kotlin.io.path.nameWithoutExtension

const val SMALL_IMAGE_SUFFIX = "_small"

data class ImageResource(
    val image: Path,
    val pageNumbers: Set<Int>,
) {
    fun smallImage(): Path = image.resolveSibling("${image.nameWithoutExtension}$SMALL_IMAGE_SUFFIX.${image.extension}")
}
