package pl.bartek.aidevs.task0202

import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.ai.chat.client.ChatClient
import org.springframework.ai.model.Media
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.ansi.AnsiColor.BRIGHT_GREEN
import org.springframework.boot.ansi.AnsiColor.BRIGHT_MAGENTA
import org.springframework.boot.ansi.AnsiColor.BRIGHT_RED
import org.springframework.boot.ansi.AnsiStyle.BOLD
import org.springframework.core.io.FileSystemResource
import org.springframework.http.MediaType
import org.springframework.shell.command.CommandContext
import org.springframework.shell.command.annotation.Command
import pl.bartek.aidevs.ansiFormatted
import pl.bartek.aidevs.courseapi.AiDevsApiClient
import java.nio.file.Files
import java.nio.file.Paths
import java.util.stream.Collectors

@Command(group = "task")
class Task0202Command(
    private val aiDevsApiClient: AiDevsApiClient,
    private val chatClient: ChatClient,
    @Value("\${aidevs.cache-dir}") cacheDir: String,
) {
    private val cacheDir = Paths.get(cacheDir, "02_02")

    init {
        Files.createDirectories(this.cacheDir)
        // cache dir needs to contain 4 images with a different map
        // those are manually cropped from an image of a file received before the course
    }

    @Command(command = ["task0202"])
    fun run(ctx: CommandContext) {
        val mapDescriptions =
            (1..4).map { mapNumber ->
                ctx.terminal
                    .writer()
                    .println("AI response map $mapNumber:".ansiFormatted(style = BOLD, color = BRIGHT_MAGENTA))
                ctx.terminal.writer().flush()

                val response =
                    chatClient
                        .prompt()
                        .user {
                            it
                                .text(
                                    """
                                    Image represents a location from a city. Please describe what can you see on the map.
                                    Include all details like:
                                    - street names
                                    - points of interests
                                    - any important infrastructure like junction or traffic circle, etc.
                                    
                                    Section for each detail description start with markdown header `##`.
                                    Last section should be your thoughts about the location.
                                    """.trimIndent(),
                                ).media(Media(MediaType.IMAGE_JPEG, FileSystemResource(cacheDir.resolve("$mapNumber.jpeg"))))
                        }.stream()
                        .content()
                        .doOnNext {
                            ctx.terminal.writer().print(it)
                            ctx.terminal.flush()
                        }.collect(Collectors.joining(""))
                        .block() ?: throw IllegalStateException("Cannot get chat response")

                ctx.terminal.writer().println()
                ctx.terminal.writer().println()
                ctx.terminal.writer().flush()
                return@map response
            }

        val descriptions =
            mapDescriptions
                .withIndex()
                .joinToString("\n\n") { "# Description ${it.index + 1}:\n${it.value}" }
        val finalResponse =
            chatClient
                .prompt()
                .user(
                    """
                    |For each description below, find out about what city it is. One of descriptions is about different city than three others.
                    |Three descriptions are describing a city where garners and strongholds exist.
                    |Before giving an answer, think about the best answer and explain why you think it is a correct city for a given description.
                    |Lastly, output city names in Polish language without any markdown formatting. Each city name should be in a new line.
                    |Example:
                    |```
                    |Poznań
                    |Warszawa
                    |Poznań
                    |Poznań
                    |```
                    |
                    |$descriptions
                    """.trimMargin(),
                ).stream()
                .content()
                .doOnNext {
                    ctx.terminal.writer().print(it)
                    ctx.terminal.flush()
                }.collect(Collectors.joining(""))
                .block() ?: throw IllegalStateException("Cannot get chat response")

        ctx.terminal.writer().println()
        ctx.terminal.writer().flush()

        val finalResponseLines = finalResponse.split("\n")
        val answer =
            finalResponseLines
                .drop(finalResponseLines.size - 4)
                .groupingBy { it.trim() }
                .eachCount()
                .maxBy { it.value }
                .key

        val flag = "{{FLG:${answer.uppercase()}}}"
        ctx.terminal.writer().println("Flag:".ansiFormatted(color = BRIGHT_GREEN, style = BOLD))
        ctx.terminal.writer().println(flag)
        ctx.terminal.writer().flush()
    }

    companion object {
        private val log = KotlinLogging.logger { }
    }
}
