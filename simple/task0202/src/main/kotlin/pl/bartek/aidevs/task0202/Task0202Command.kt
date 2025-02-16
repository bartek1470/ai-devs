package pl.bartek.aidevs.task0202

import org.jline.terminal.Terminal
import org.springframework.ai.chat.client.ChatClient
import org.springframework.ai.chat.messages.UserMessage
import org.springframework.ai.chat.prompt.Prompt
import org.springframework.ai.model.Media
import org.springframework.boot.ansi.AnsiColor.YELLOW
import org.springframework.boot.ansi.AnsiStyle.BOLD
import org.springframework.core.io.FileSystemResource
import org.springframework.http.MediaType
import org.springframework.shell.command.annotation.Command
import pl.bartek.aidevs.config.AiDevsProperties
import pl.bartek.aidevs.course.TaskId
import pl.bartek.aidevs.util.ansiFormatted
import pl.bartek.aidevs.util.ansiFormattedAi
import pl.bartek.aidevs.util.print
import pl.bartek.aidevs.util.println
import java.nio.file.Files
import java.util.stream.Collectors

@Command(
    group = "task",
    command = ["task"],
)
class Task0202Command(
    private val terminal: Terminal,
    aiDevsProperties: AiDevsProperties,
    private val chatClient: ChatClient,
) {
    private val cacheDir = aiDevsProperties.cacheDir.resolve(TaskId.TASK_0202.cacheFolderName())

    init {
        Files.createDirectories(this.cacheDir)
        // cache dir needs to contain 4 images with a different map
        // those are manually cropped from an image of a file received before the course
    }

    @Command(
        command = ["0202"],
        description = "https://bravecourses.circle.so/c/lekcje-programu-ai3-806660/s02e02-rozumienie-obrazu-i-wideo",
    )
    fun run() {
        val mapDescriptions =
            (1..4).map { mapNumber ->
                terminal.println("AI response map $mapNumber:".ansiFormattedAi())

                val response =
                    chatClient
                        .prompt(
                            Prompt(
                                listOf(
                                    UserMessage(
                                        """
                                        Image represents a location from a city. Please describe what can you see on the map.
                                        Include all details like:
                                        - street names
                                        - points of interests
                                        - any important infrastructure like junction or traffic circle, etc.
                                        
                                        Section for each detail description start with markdown header `##`.
                                        Last section should be your thoughts about the location.
                                        """.trimIndent(),
                                        Media(MediaType.IMAGE_JPEG, FileSystemResource(cacheDir.resolve("$mapNumber.jpeg"))),
                                    ),
                                ),
                            ),
                        ).stream()
                        .content()
                        .doOnNext { terminal.print(it) }
                        .collect(Collectors.joining(""))
                        .block() ?: throw IllegalStateException("Cannot get chat response")

                terminal.println()
                terminal.println()
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
                .doOnNext { terminal.print(it) }
                .collect(Collectors.joining(""))
                .block() ?: throw IllegalStateException("Cannot get chat response")

        terminal.println()

        val finalResponseLines = finalResponse.split("\n")
        val answer =
            finalResponseLines
                .drop(finalResponseLines.size - 4)
                .groupingBy { it.trim() }
                .eachCount()
                .maxBy { it.value }
                .key

        val flag = "{{FLG:${answer.uppercase()}}}"
        terminal.println("Try input flag:\n$flag".ansiFormatted(style = BOLD, color = YELLOW))
    }
}
