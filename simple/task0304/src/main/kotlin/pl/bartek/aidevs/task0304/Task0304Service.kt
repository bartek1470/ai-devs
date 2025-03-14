package pl.bartek.aidevs.task0304

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.MapperFeature
import com.fasterxml.jackson.dataformat.xml.XmlMapper
import com.fasterxml.jackson.module.kotlin.readValue
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import org.jline.terminal.Terminal
import org.springframework.ai.chat.messages.UserMessage
import org.springframework.ai.chat.prompt.ChatOptions
import org.springframework.ai.tool.function.FunctionToolCallback
import org.springframework.boot.ansi.AnsiColor.BRIGHT_YELLOW
import org.springframework.boot.ansi.AnsiColor.YELLOW
import org.springframework.boot.ansi.AnsiStyle.BOLD
import org.springframework.stereotype.Service
import org.springframework.web.client.RestClient
import pl.bartek.aidevs.ai.ChatService
import pl.bartek.aidevs.config.AiDevsProperties
import pl.bartek.aidevs.course.TaskId
import pl.bartek.aidevs.course.api.AiDevsAnswer
import pl.bartek.aidevs.course.api.AiDevsApiClient
import pl.bartek.aidevs.course.api.Task
import pl.bartek.aidevs.util.ansiFormatted
import pl.bartek.aidevs.util.ansiFormattedAi
import pl.bartek.aidevs.util.ansiFormattedSecondaryInfo
import pl.bartek.aidevs.util.ansiFormattedSecondaryInfoTitle
import pl.bartek.aidevs.util.extractXmlRoot
import pl.bartek.aidevs.util.println
import pl.bartek.aidevs.util.replaceNonBreakingSpaces
import pl.bartek.aidevs.util.stripAccents
import java.nio.file.Files
import kotlin.io.path.exists
import kotlin.io.path.writeText

@Service
class Task0304Service(
    private val aiDevsProperties: AiDevsProperties,
    private val task0304Config: Task0304Config,
    private val aiDevsApiClient: AiDevsApiClient,
    private val restClient: RestClient,
    private val chatService: ChatService,
) {
    private val cacheDir = aiDevsProperties.cacheDir.resolve(TaskId.TASK_0304.cacheFolderName())

    private val xmlMapper =
        XmlMapper
            .builder()
            .defaultUseWrapper(false)
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
            .configure(MapperFeature.ACCEPT_CASE_INSENSITIVE_PROPERTIES, true)
            .build()
            .registerKotlinModule()

    init {
        Files.createDirectories(this.cacheDir)
    }

    fun run(terminal: Terminal) {
        val summaryWithLists = summarizeInputData()
        val summary = summaryWithLists.substring(0, summaryWithLists.indexOf("<")).trim()
        val people =
            summaryWithLists
                .extractXmlRoot("people")!!
                .let { xmlMapper.readValue<String>(it) }
                .trim()
                .split("\n")
                .map { it.trim() }
        val places =
            summaryWithLists
                .extractXmlRoot("places")!!
                .let { xmlMapper.readValue<String>(it) }
                .trim()
                .split("\n")
                .map { it.trim() }

        terminal.println("Note summary:".ansiFormattedSecondaryInfoTitle())
        terminal.println(summary.ansiFormattedSecondaryInfo())
        terminal.println("People:".ansiFormattedSecondaryInfoTitle())
        terminal.println(people.joinToString(", ").ansiFormattedSecondaryInfo())
        terminal.println("Places:".ansiFormattedSecondaryInfoTitle())
        terminal.println(places.joinToString(", ").ansiFormattedSecondaryInfo())

        val aiAnswer =
            chatService.sendToChat(
                listOf(
                    UserMessage(
                        """
                        |# Summary of note from the past
                        |$summary
                        |
                        |# People
                        |$people
                        |
                        |# Places
                        |$places
                        |
                        |Treat the note summary, list of people and list of places as a starting point to find Barbara Zawadzka current location.
                        |If provided tools returns BARBARA and the city appeared in the above data then it means it was a past location and you still need to do the searching.
                        |Ask about every possible person and place from above data and tool responses.
                        |List the possible locations in `result` XML tag like this:
                        |<result>
                        |Kołobrzeg
                        |Szczecin
                        |</result>
                        """.trimMargin(),
                    ),
                ),
                listOf(
                    FunctionToolCallback
                        .builder(
                            "people",
                            SendAskApiRequest(
                                aiDevsProperties.apiKey,
                                restClient,
                                task0304Config.apiUrl.toString(),
                                "people",
                            ) {
                                terminal.println()
                                terminal.println("People".ansiFormatted(style = BOLD, color = BRIGHT_YELLOW))
                                terminal.println(
                                    "${
                                        "Q:".ansiFormatted(
                                            color = YELLOW,
                                        )
                                    } ${it.query}\n${"A:".ansiFormatted(color = YELLOW)} ${it.response}",
                                )
                            },
                        ).description(
                            """
                            Get cities where a person with provided first name is currently located. Accepts only one Polish word without declension and accents. The response can contain multiple names.
                            """.trimIndent(),
                        ).inputType(AskApiQuery::class.java)
                        .build(),
                    FunctionToolCallback
                        .builder(
                            "places",
                            SendAskApiRequest(
                                aiDevsProperties.apiKey,
                                restClient,
                                task0304Config.apiUrl.toString(),
                                "places",
                            ) {
                                terminal.println()
                                terminal.println("Places".ansiFormatted(style = BOLD, color = BRIGHT_YELLOW))
                                terminal.println(
                                    "${
                                        "Q:".ansiFormatted(
                                            color = YELLOW,
                                        )
                                    } ${it.query}\n${"A:".ansiFormatted(color = YELLOW)} ${it.response}",
                                )
                            },
                        ).description(
                            """
                            Get people which is currently located in a queried city. Accepts only one Polish word without declension and accents. The response can contain multiple places.
                            """.trimIndent(),
                        ).inputType(AskApiQuery::class.java)
                        .build(),
                ),
                chatOptions =
                    ChatOptions
                        .builder()
                        .temperature(0.5)
                        .build(),
            )

        terminal.println("AI:".ansiFormattedAi())
        terminal.println(aiAnswer)

        val multipleAnswers =
            aiAnswer
                .extractXmlRoot()
                ?.let {
                    xmlMapper.readValue(it, String::class.java)
                }?.stripAccents()
                ?.uppercase()
                ?: "idk"
        val resultList = multipleAnswers.split("\n").filter { it.isNotBlank() }.map { it.trim() }

        resultList.forEach { result ->
            val answer = aiDevsApiClient.sendAnswer(aiDevsProperties.reportUrl, AiDevsAnswer(Task.LOOP, result))
            terminal.println(answer)
        }
    }

    private fun summarizeInputData(): String {
        val cachedSummaryPath = cacheDir.resolve("summaryWithLists.txt")
        if (cachedSummaryPath.exists()) {
            return Files.readString(cachedSummaryPath)
        }

        val note = fetchInputData().replaceNonBreakingSpaces()
        val summaryWithLists =
            chatService.sendToChat(
                listOf(
                    UserMessage(
                        """
                        Note about Barbara Zawadzka:
                        $note
                        
                        Above is a note about Barbara Zawadzka. The note is from the past. Summarize it and in the end list all people and places appearing in that note.
                        In the summary focus on people and places and omit information that is not relevant to traveling or meeting people.
                        People list should include only names. Places list should be in Polish language.
                        Example result:
                        
                        Barbara Zawadzka is a woman. She was living in Warsaw. She met Aleksander Kowalski.
                        <people>
                        Barbara Zawadzka
                        Aleksander Kowalski
                        </people>
                        <places>
                        Paryż
                        </places>
                        """.trimIndent(),
                    ),
                ),
                chatOptions =
                    ChatOptions
                        .builder()
                        .temperature(0.0)
                        .build(),
            )

        Files.writeString(cachedSummaryPath, summaryWithLists)
        return summaryWithLists
    }

    private fun fetchInputData(): String {
        val cachedDataPath = cacheDir.resolve("barbara.txt")
        if (cachedDataPath.exists()) {
            return Files.readString(cachedDataPath)
        }

        val text =
            restClient
                .get()
                .uri(task0304Config.dataUrl.toURI())
                .retrieve()
                .body(String::class.java)!!
        cachedDataPath.writeText(text)
        return text
    }
}
