package pl.bartek.aidevs.util

import java.nio.file.Path
import kotlin.io.path.nameWithoutExtension

data class ReadFile(
    val name: String,
    val content: String,
) {
    constructor(path: Path, content: String) : this(path.nameWithoutExtension, content)
}
