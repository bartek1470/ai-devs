package pl.bartek.aidevs

import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.boot.ansi.AnsiBackground
import org.springframework.boot.ansi.AnsiColor
import org.springframework.boot.ansi.AnsiOutput
import org.springframework.boot.ansi.AnsiStyle
import java.nio.file.Files
import java.nio.file.Path
import java.util.Locale
import java.util.zip.ZipInputStream

private val log = KotlinLogging.logger {}
private val AI_DEVS_FLAG_REGEX = "\\{\\{FLG:(.*)}}".toRegex()

fun String.isAiDevsFlag(): Boolean = AI_DEVS_FLAG_REGEX.matches(this)

fun String.extractAiDevsFlag(): String? = AI_DEVS_FLAG_REGEX.find(this)?.value

fun String.removeExtraWhitespaces() =
    this
        .split("\n")
        .filter { it.isNotBlank() }
        .joinToString("\n") { it.trim() }

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

fun String.ansiFormatted(
    style: AnsiStyle = AnsiStyle.NORMAL,
    color: AnsiColor = AnsiColor.DEFAULT,
    background: AnsiBackground = AnsiBackground.DEFAULT,
): String =
    AnsiOutput.toString(
        style,
        color,
        background,
        this,
        AnsiStyle.NORMAL,
        AnsiColor.DEFAULT,
        AnsiBackground.DEFAULT,
    )

fun String.titleCase() =
    this.replaceFirstChar {
        if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString()
    }
