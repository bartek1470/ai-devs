package pl.bartek.aidevs.task0401

import org.springframework.core.io.UrlResource
import org.springframework.web.util.UriComponentsBuilder
import java.net.MalformedURLException
import java.net.URI
import java.net.URL
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.Path
import kotlin.io.path.extension
import kotlin.io.path.nameWithoutExtension
import kotlin.io.path.notExists

private const val SMALL_SUFFIX = "-small"

data class ImageUrl(
    val filename: String,
    val url: URL,
    val urlSmall: URL,
) {
    fun ensurePersisted(
        directory: Path,
        small: Boolean = true,
    ): Path {
        val imagePath = directory.resolve(filename)
        if (imagePath.notExists()) {
            val resource = UrlResource(if (small) urlSmall else url)
            Files.write(imagePath, resource.contentAsByteArray)
        }
        return imagePath
    }

    companion object {
        @Throws(IllegalArgumentException::class)
        fun parse(fileUrlStr: String): ImageUrl {
            val normalizedUrl = fileUrlStr.trim().replace("\\s".toRegex(), "")
            validate(normalizedUrl)

            val original = UriComponentsBuilder.fromHttpUrl(normalizedUrl).build()
            val originalFilename = Path(original.pathSegments.last())
            val normalizedFilename =
                if (originalFilename.nameWithoutExtension.endsWith(SMALL_SUFFIX)) {
                    val smallResultFilename = originalFilename.toString()
                    val filenameWithoutExtension = smallResultFilename.substring(0..smallResultFilename.indexOf(SMALL_SUFFIX))
                    Path("$filenameWithoutExtension.${originalFilename.extension}")
                } else {
                    originalFilename
                }

            val filename = normalizedFilename.fileName.toString()
            val url = original.toUri().resolve(filename).toURL()
            val urlSmall =
                original.toUri().resolve("${originalFilename.nameWithoutExtension}$SMALL_SUFFIX.${originalFilename.extension}").toURL()

            return ImageUrl(filename, url, urlSmall)
        }

        private fun validate(fileUrlStr: String) {
            val fileUri: URI =
                try {
                    URI.create(fileUrlStr)
                } catch (ex: IllegalArgumentException) {
                    throw IllegalArgumentException("ERROR: Not a valid filename. The given filename: $fileUrlStr", ex)
                }

            try {
                fileUri.toURL()
            } catch (ex: MalformedURLException) {
                throw IllegalArgumentException("ERROR: Not a valid filename. The given filename: $fileUrlStr", ex)
            } catch (ex: IllegalArgumentException) {
                throw IllegalArgumentException(
                    "ERROR: Given filename is not absolute filename. The given filename: $fileUrlStr",
                    ex,
                )
            }
        }
    }
}
