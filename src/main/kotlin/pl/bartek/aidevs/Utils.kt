package pl.bartek.aidevs

import io.github.oshai.kotlinlogging.KotlinLogging
import net.lingala.zip4j.ZipFile
import org.apache.commons.codec.binary.Base32
import org.jline.terminal.Terminal
import org.springframework.boot.ansi.AnsiBackground
import org.springframework.boot.ansi.AnsiColor
import org.springframework.boot.ansi.AnsiOutput
import org.springframework.boot.ansi.AnsiStyle
import pl.bartek.aidevs.courseapi.AiDevsAnswerResponse
import java.nio.file.Files
import java.nio.file.Path
import java.security.MessageDigest
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
    unzip(destinationPath, null)
}

fun Path.unzip(destinationPath: Path, password: CharArray?) {
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
    writer().println(str)
    flush()
}

fun Terminal.println(answerResponse: AiDevsAnswerResponse) {
    val (code, message) = answerResponse
    val messageToPrint = "$code, $message"
    if (answerResponse.isError()) {
        println(messageToPrint.ansiFormattedError())
    } else if (answerResponse.isSuccess()) {
        println(messageToPrint.ansiFormattedSuccess())
    } else {
        println(messageToPrint)
        log.warn { "Not supported answer type" }
    }
}

fun Terminal.print(str: String) {
    writer().print(str)
    flush()
}

fun String.sha256Base32(): String {
    val digest = MessageDigest.getInstance("SHA-256")
    val hash = digest.digest(encodeToByteArray())
    return Base32().encodeAsString(hash)
}
