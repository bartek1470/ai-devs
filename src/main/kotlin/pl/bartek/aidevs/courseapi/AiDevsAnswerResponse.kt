package pl.bartek.aidevs.courseapi

import org.jline.terminal.Terminal
import org.springframework.boot.ansi.AnsiColor.BRIGHT_GREEN
import org.springframework.boot.ansi.AnsiColor.BRIGHT_RED
import pl.bartek.aidevs.ansiFormatted

data class AiDevsAnswerResponse(
    val code: Int,
    val message: String,
) {

    fun println(terminal: Terminal){
        terminal.writer().println(
            "${code}, ${message}".ansiFormatted(color = if (isError()) BRIGHT_RED else BRIGHT_GREEN),
        )
        terminal.writer().flush()
    }

    fun isSuccess() = code == 0

    fun isError() = code < 0
}
