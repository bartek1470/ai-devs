package pl.bartek.aidevs.task0304

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.MapperFeature
import com.fasterxml.jackson.dataformat.xml.XmlMapper
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import io.github.oshai.kotlinlogging.KotlinLogging
import org.jline.terminal.Terminal
import org.springframework.ai.chat.messages.SystemMessage
import org.springframework.ai.chat.messages.UserMessage
import org.springframework.ai.model.function.DefaultFunctionCallbackBuilder
import org.springframework.ai.model.function.FunctionCallingOptionsBuilder
import org.springframework.ai.openai.api.OpenAiApi
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.ansi.AnsiColor.BRIGHT_YELLOW
import org.springframework.boot.ansi.AnsiColor.YELLOW
import org.springframework.boot.ansi.AnsiStyle.BOLD
import org.springframework.stereotype.Service
import org.springframework.web.client.RestClient
import pl.bartek.aidevs.ai.ChatService
import pl.bartek.aidevs.course.TaskId
import pl.bartek.aidevs.course.api.AiDevsAnswer
import pl.bartek.aidevs.course.api.AiDevsApiClient
import pl.bartek.aidevs.course.api.Task
import pl.bartek.aidevs.util.ansiFormatted
import pl.bartek.aidevs.util.ansiFormattedError
import pl.bartek.aidevs.util.extractXmlRoot
import pl.bartek.aidevs.util.print
import pl.bartek.aidevs.util.println
import pl.bartek.aidevs.util.replaceNonBreakingSpaces
import pl.bartek.aidevs.util.stripAccents
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.exists
import kotlin.io.path.writeText

@Service
class Task0304Service(
    @Value("\${aidevs.cache-dir}") cacheDir: Path,
    @Value("\${aidevs.task.0304.data-url}") private val dataUrl: String,
    @Value("\${aidevs.task.0304.answer-url}") private val answerUrl: String,
    @Value("\${aidevs.api-key}") private val apiKey: String,
    @Value("\${aidevs.task.0304.api-url}") private val apiUrl: String,
    private val aiDevsApiClient: AiDevsApiClient,
    private val restClient: RestClient,
    private val chatService: ChatService,
) {
    private val cacheDir = cacheDir.resolve(TaskId.TASK_0304.cacheFolderName())

    init {
        Files.createDirectories(this.cacheDir)
    }

    fun run(terminal: Terminal) {
        val note = fetchInputData().replaceNonBreakingSpaces()
        val apiLog = mutableListOf<AskApiLogEntry>()
        try {
            val aiAnswer =
                chatService.sendToChat(
                    listOf(
                        SystemMessage(
                            """
                            # Task
                            
                            Find in which city Barbara Zawadzka is currently located.
                            
                            # Details
                            
                            1. User will send you a note about Barbara Zawadzka.
                            2. The note is from the past, so the data inside is out of date, because you need to find current location.
                            3. The note's content is in Polish language.
                            4. The note also mentions other people and different places.
                            5. Barbara Zawadzka is NOT in Kraków city.
                            5. Barbara Zawadzka is NOT in Warszawa city.
                            5. Barbara Zawadzka is NOT in Lublin city.
                            
                            # Rules
                            
                            Each time you receive an answer from a tool, output it with a asked query in following format:
                            <tool>
                                <query>ASKED QUERY</query>
                                <answer>TOOL ANSWER</answer>
                            </tool>
                            
                            Under no circumstances, you are not allowed to ask the same query again.
                            The outputted query and answers should help you with further analysis.
                            
                            # How to complete the task
                            
                            1. Analyze the note and output that cities and people you can identify there.
                            2. Using provided tools find out who was coworker of Aleksander Ragowski and Barbara Zawadzka and output it.
                            3. Using provided tools find out who met Rafał Bomba and output it.
                            4. Investigate your analysis and findings to determine current location of Barbara Zawadzka or decide what next steps you need to take in order to get it and execute those.
                            6. Output current location of Barbara Zawadzka in Polish language in XML tag `result`
                            """.trimIndent(),
                        ),
                        UserMessage(note),
                    ),
                    listOf(
                        DefaultFunctionCallbackBuilder()
                            .description(
                                """
                                Get cities where a person with provided first name is located.
                                The response is in Polish language without declension and accents.
                                The response can contain multiple names separated with a ONE space.
                                """.trimIndent(),
                            ).function(
                                "people",
                                SendAskApiRequest(apiKey, restClient, apiUrl, "people") {
                                    apiLog.add(it)
                                    terminal.println()
                                    terminal.println("People".ansiFormatted(style = BOLD, color = BRIGHT_YELLOW))
                                    terminal.println(
                                        "${"Q:".ansiFormatted(
                                            color = YELLOW,
                                        )} ${it.query}\n${"A:".ansiFormatted(color = YELLOW)} ${it.response}",
                                    )
                                },
                            ).inputType(AskApiQuery::class.java)
                            .build(),
                        DefaultFunctionCallbackBuilder()
                            .description(
                                """
                                Get people which are located in a provided city.
                                The response is in Polish language without declension and accents.
                                The response can contain multiple places separated with a ONE space.
                                """.trimIndent(),
                            ).function(
                                "places",
                                SendAskApiRequest(apiKey, restClient, apiUrl, "places") {
                                    apiLog.add(it)
                                    terminal.println()
                                    terminal.println("Places".ansiFormatted(style = BOLD, color = BRIGHT_YELLOW))
                                    terminal.println(
                                        "${"Q:".ansiFormatted(
                                            color = YELLOW,
                                        )} ${it.query}\n${"A:".ansiFormatted(color = YELLOW)} ${it.response}",
                                    )
                                },
                            ).inputType(AskApiQuery::class.java)
                            .build(),
                    ),
                    chatOptions =
                        FunctionCallingOptionsBuilder.PortableFunctionCallingOptions
                            .builder()
                            .withModel(
//                                OpenAiApi.ChatModel.GPT_4_O_MINI.value,
                                OpenAiApi.ChatModel.GPT_4_O.value,
                            ).build(),
//                streaming = false,
                ) {
                    terminal.print(it)
                }
            terminal.println()

            val result =
                aiAnswer
                    .extractXmlRoot()
                    ?.let {
                        val mapper =
                            XmlMapper
                                .builder()
                                .defaultUseWrapper(false)
                                .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
                                .configure(MapperFeature.ACCEPT_CASE_INSENSITIVE_PROPERTIES, true)
                                .build()
                                .registerKotlinModule()
                        mapper.readValue(it, String::class.java)
                    }?.stripAccents()
                    ?.uppercase()
                    ?: "idk"
            val answer = aiDevsApiClient.sendAnswer(answerUrl, AiDevsAnswer(Task.LOOP, result))
            terminal.println(answer)
        } catch (ex: Exception) {
            val errorMessage = "Failed to determine location of Barbara Zawadzka."
            log.error(ex) { errorMessage }
            terminal.println()
            terminal.println(errorMessage.ansiFormattedError())
        }

//        terminal.println("API log:".ansiFormatted(style = BOLD, color = BRIGHT_YELLOW))
//        terminal.println(
//            "\t${apiLog.joinToString("\n\t") { "${"Q:".ansiFormatted(color = YELLOW)} ${ it.query }\n\tA: ${ it.response }" }}",
//        )
    }

    private fun fetchInputData(): String {
        val cachedDataPath = cacheDir.resolve("barbara.txt")
        if (cachedDataPath.exists()) {
            return Files.readString(cachedDataPath)
        }

        val text =
            restClient
                .get()
                .uri(dataUrl)
                .retrieve()
                .body(String::class.java)!!
        cachedDataPath.writeText(text)
        return text
    }

    companion object {
        private val log = KotlinLogging.logger { }
    }
}
