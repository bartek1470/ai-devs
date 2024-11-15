package pl.bartek.aidevs

import io.github.oshai.kotlinlogging.KotlinLogging
import org.jsoup.nodes.Document
import java.nio.file.Files
import java.nio.file.Path
import java.util.zip.ZipInputStream

private val log = KotlinLogging.logger {}
private val AI_DEVS_FLAG_REGEX = "\\{\\{FLG:(.*)}}".toRegex()

fun String.isAiDevsFlag(): Boolean = AI_DEVS_FLAG_REGEX.matches(this)

fun String.extractAiDevsFlag(): String? = AI_DEVS_FLAG_REGEX.find(this)?.value

fun Document.minimalizedWholeText() = wholeText().split("\n").filter { it.isNotBlank() }.joinToString("\n") { it.trim() }

fun Path.unzip(destinationPath: Path) {
    Files.createDirectories(destinationPath)

    Files.newInputStream(this).use { inputStream ->
        ZipInputStream(inputStream).use { zipInputStream ->
            var entry = zipInputStream.nextEntry
            while (entry != null) {
                val entryPath = destinationPath.resolve(entry.name)
                log.debug { "Extracting ${entryPath.toAbsolutePath()}" }
                if (entry.isDirectory) {
                    Files.createDirectories(entryPath)
                } else {
                    Files.createDirectories(entryPath.parent)
                    Files.newOutputStream(entryPath).use { outputStream ->
                        val content = zipInputStream.readAllBytes()
                        outputStream.write(content)
                    }
                }
                zipInputStream.closeEntry()
                entry = zipInputStream.nextEntry
            }
        }
    }
}
