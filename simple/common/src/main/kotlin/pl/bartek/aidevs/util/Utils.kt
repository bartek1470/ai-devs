package pl.bartek.aidevs.util

import io.github.oshai.kotlinlogging.KotlinLogging
import net.lingala.zip4j.ZipFile
import org.apache.commons.lang3.StringUtils
import org.jline.terminal.Terminal
import org.springframework.boot.ansi.AnsiBackground
import org.springframework.boot.ansi.AnsiColor
import org.springframework.boot.ansi.AnsiOutput
import org.springframework.boot.ansi.AnsiStyle
import org.springframework.http.MediaType
import org.springframework.web.client.RestClient
import org.springframework.web.util.UriComponentsBuilder
import pl.bartek.aidevs.course.api.AiDevsAnswerResponse
import java.awt.Image
import java.awt.image.BufferedImage
import java.nio.file.Files
import java.nio.file.Path
import java.util.Locale
import kotlin.math.min

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
    unzip(destinationPath, null)
}

fun Path.unzip(
    destinationPath: Path,
    password: CharArray?,
) {
    ZipFile(toFile(), password).extractAll(destinationPath.toAbsolutePath().toString())
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

fun String.ansiFormattedError(): String = ansiFormatted(style = AnsiStyle.BOLD, color = AnsiColor.BRIGHT_RED)

fun String.ansiFormattedSuccess(): String = ansiFormatted(style = AnsiStyle.BOLD, color = AnsiColor.BRIGHT_GREEN)

fun String.ansiFormattedAi(): String = ansiFormatted(style = AnsiStyle.BOLD, color = AnsiColor.BRIGHT_MAGENTA)

fun String.ansiFormattedHuman(): String = ansiFormatted(style = AnsiStyle.BOLD, color = AnsiColor.CYAN)

fun String.ansiFormattedSecondaryInfo(): String = ansiFormatted(color = AnsiColor.BRIGHT_BLACK)

fun String.ansiFormattedSecondaryInfoTitle(): String = ansiFormatted(style = AnsiStyle.BOLD, color = AnsiColor.BRIGHT_BLACK)

fun String.titleCase() =
    this.replaceFirstChar {
        if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString()
    }

fun Terminal.println(str: String = "") {
    log.debug { str }
    writer().println(str)
    flush()
}

fun Terminal.println(answerResponse: AiDevsAnswerResponse) {
    val (code, message) = answerResponse
    val messageToPrint = "$code, $message"
    if (answerResponse.isError()) {
        println(messageToPrint.ansiFormattedError())
        answerResponse.hint?.also { println(it.ansiFormattedError()) }
        answerResponse.debug?.also { println(it.ansiFormattedError()) }
    } else if (answerResponse.isSuccess()) {
        println(messageToPrint.ansiFormattedSuccess())
    } else {
        println(messageToPrint)
        log.warn { "Not supported answer type" }
    }
}

fun Terminal.print(str: String) {
    log.debug { str }
    writer().print(str)
    flush()
}

fun String.replaceNonBreakingSpaces(): String = this.replace("\u00a0", " ")

fun String.stripAccents(): String = StringUtils.stripAccents(this)

fun String.extractXmlRoot(xmlTagName: String = "result"): String? {
    val xmlStartTag = "<$xmlTagName>"
    val xmlEndTag = "</$xmlTagName>"
    val startIndex = indexOf(xmlStartTag, ignoreCase = true)
    val endIndex = indexOf(xmlEndTag, ignoreCase = true)
    return if (startIndex < 0 || endIndex < 0) null else substring(startIndex, endIndex + xmlEndTag.length)
}

fun String.executeCommand(vararg args: String): String {
    val process: Process = ProcessBuilder(this, *args).start()
    val exitCode = process.waitFor()

    if (exitCode == 0) {
        log.debug { "Success: $this ${args.joinToString(" ")}" }
        return process.inputReader().readText()
    }
    val error = process.errorReader().readText()
    val errorMessage = "Exit code $exitCode: $this ${args.joinToString(" ")}\n$error"
    log.error { errorMessage }
    throw IllegalStateException(errorMessage)
}

fun RestClient.downloadFile(
    url: String,
    apiKey: String? = null,
    directory: Path,
): Path {
    val uriComponents =
        UriComponentsBuilder
            .fromHttpUrl(url)
            .build()
    val filename = uriComponents.pathSegments[uriComponents.pathSegments.size - 1]!!
    val filePath = directory.resolve(filename)
    if (Files.exists(filePath)) {
        log.debug { "File already exists: ${filePath.toAbsolutePath()}. Skipping download" }
        return filePath
    }

    val body =
        get()
            .uri(uriComponents.toUriString(), apiKey)
            .headers { it.contentType = MediaType.APPLICATION_OCTET_STREAM }
            .retrieve()
            .body(ByteArray::class.java)!!
    Files.write(filePath, body)
    return filePath
}

fun BufferedImage.resizeToFitSquare(sideSize: Int): BufferedImage {
    val widthRatio = sideSize / width.toDouble()
    val heightRatio = sideSize / height.toDouble()
    val ratio = min(widthRatio, heightRatio)
    val resizedWidth = (width * ratio).toInt()
    val resizedHeight = (height * ratio).toInt()

    val resizedImage = getScaledInstance(resizedWidth, resizedHeight, Image.SCALE_SMOOTH)
    val resizedBufferedImage = BufferedImage(resizedWidth, resizedHeight, type)
    resizedBufferedImage.graphics.drawImage(resizedImage, 0, 0, null)
    return resizedBufferedImage
}

fun logCommandInfo(
    command: String,
    versionArgs: Array<String>? = null,
) {
    if (!log.isTraceEnabled()) {
        return
    }

    val whichProcess = ProcessBuilder("which", command).start()
    if (whichProcess.waitFor() != 0) {
        log.error { "Command '$command' not found" }
        return
    }

    val commandPath = String(whichProcess.inputStream.readAllBytes()).trim()
    log.trace { "Command '$command' located at $commandPath" }

    versionArgs?.let {
        ProcessBuilder(command, *versionArgs).start().apply {
            if (waitFor() == 0) {
                val version = String(inputStream.readAllBytes()).trim()
                log.trace { "'$command' version: $version" }
            } else {
                log.error { "Invalid version args for command '$command'" }
            }
        }
    }
}
