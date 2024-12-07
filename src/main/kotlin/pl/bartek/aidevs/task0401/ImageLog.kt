package pl.bartek.aidevs.task0401

import org.apache.commons.lang3.builder.ToStringBuilder
import org.apache.commons.lang3.builder.ToStringStyle
import org.springframework.web.util.UriComponentsBuilder
import java.net.URL
import kotlin.io.path.Path
import kotlin.io.path.extension
import kotlin.io.path.nameWithoutExtension

private const val SMALL_SUFFIX = "-small"

class ImageLog(
    val operation: ImageOperation,
    resultUrl: URL,
) {
    val resultFilename: String
    val resultUrl: URL
    val smallResultUrl: URL

    init {
        val original = UriComponentsBuilder.fromUri(resultUrl.toURI()).build()
        val originalBase =
            UriComponentsBuilder
                .fromUri(original.toUri())
                .replacePath(null)
                .pathSegment(*original.pathSegments.dropLast(1).toTypedArray())
                .build()
                .toUri()
        val originalFilename = Path(original.pathSegments.last())
        val resultFilename =
            if (originalFilename.nameWithoutExtension.endsWith(SMALL_SUFFIX)) {
                val smallResultFilename = originalFilename.toString()
                val filenameWithoutExtension = smallResultFilename.substring(0..smallResultFilename.indexOf(SMALL_SUFFIX))
                Path("$filenameWithoutExtension.${originalFilename.extension}")
            } else {
                originalFilename
            }

        this.resultFilename = resultFilename.fileName.toString()
        this.resultUrl = originalBase.resolve(this.resultFilename).toURL()
        smallResultUrl =
            originalBase.resolve("${originalFilename.nameWithoutExtension}$SMALL_SUFFIX.${originalFilename.extension}").toURL()
    }

    override fun toString(): String =
        ToStringBuilder(this, ToStringStyle.SIMPLE_STYLE)
            .append("operation", operation)
            .append("resultUrl", resultUrl)
            .toString()
}
