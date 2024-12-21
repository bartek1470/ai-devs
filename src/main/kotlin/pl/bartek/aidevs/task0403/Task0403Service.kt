package pl.bartek.aidevs.task0403

import io.github.oshai.kotlinlogging.KotlinLogging
import org.jline.terminal.Terminal
import org.springframework.ai.chat.messages.SystemMessage
import org.springframework.ai.chat.messages.UserMessage
import org.springframework.ai.model.function.DefaultFunctionCallbackBuilder
import org.springframework.ai.model.function.FunctionCallingOptionsBuilder.PortableFunctionCallingOptions
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.ansi.AnsiColor.YELLOW
import org.springframework.boot.ansi.AnsiStyle.BOLD
import org.springframework.core.ParameterizedTypeReference
import org.springframework.stereotype.Service
import org.springframework.web.client.RestClient
import pl.bartek.aidevs.ai.ChatService
import pl.bartek.aidevs.course.TaskId
import pl.bartek.aidevs.course.api.AiDevsAnswer
import pl.bartek.aidevs.course.api.AiDevsApiClient
import pl.bartek.aidevs.course.api.Task
import pl.bartek.aidevs.util.ansiFormatted
import pl.bartek.aidevs.util.ansiFormattedAi
import pl.bartek.aidevs.util.executeCommand
import pl.bartek.aidevs.util.print
import pl.bartek.aidevs.util.println
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.Path
import kotlin.io.path.absolute

@Service
class Task0403Service(
    @Value("\${aidevs.cache-dir}") cacheDir: String,
    @Value("\${aidevs.api-key}") private val apiKey: String,
    @Value("\${aidevs.task.0403.answer-url}") private val answerUrl: String,
    @Value("\${aidevs.task.0403.base-url}") private val baseUrl: String,
    @Value("\${aidevs.task.0403.questions-url}") private val questionsUrl: String,
    @Value("\${python.packages.path}") pythonPackagesPath: Path,
    private val restClient: RestClient,
    private val chatService: ChatService,
    private val aiDevsApiClient: AiDevsApiClient,
) {
    private val cacheDir = Path(cacheDir).resolve(TaskId.TASK_0403.cacheFolderName()).absolute()
    private val markitdownExecPath = pythonPackagesPath.resolve("markitdown").toAbsolutePath().toString()

    init {
        Files.createDirectories(this.cacheDir)
    }

    fun run(terminal: Terminal) {
        val questions = fetchQuestions()
        val sitesTempDir = Files.createTempDirectory(this.cacheDir, "sites")
        val visitSiteAction =
            VisitSiteAction(
                baseUrl,
                sitesTempDir,
            ) { htmlPath -> markitdownExecPath.executeCommand(htmlPath.toString()) }

        val rootPageContent = visitSiteAction.invoke(VisitSiteRequest("/"))

        val answers =
            questions.mapValues { questionEntry ->
                val question = questionEntry.value
                terminal.print("Question:".ansiFormatted(style = BOLD, color = YELLOW))
                terminal.println(question)
                terminal.println("AI response:".ansiFormattedAi())
                val aiResponse =
                    chatService.sendToChat(
                        messages =
                            listOf(
                                SystemMessage(
                                    """
                                    Answer the user's question using information from a website you can browse using the `visitSite` function.
                                    If you cannot find a precise answer to a question then look up what links are available and visit the most relevant.
                                    Remember you can use all previously visited pages to visit another subpage.
                                    If you need to output a URL to a site, include also a protocol (e.g. `https://`).
                                    Keep your answer as short as possible. Try to answer with a single word.
                                    Don't use markdown formatting in your answer.
                                    Below markdown is a root page content that you need to use as a starting point of a search.
                                    
                                    $rootPageContent
                                    """.trimIndent(),
                                ),
                                UserMessage(question),
                            ),
                        functions =
                            listOf(
                                DefaultFunctionCallbackBuilder()
                                    .description("Visits a website specified by a relative URL and returns it in markdown format")
                                    .function(
                                        "visitSite",
                                        visitSiteAction,
                                    ).inputType(VisitSiteRequest::class.java)
                                    .build(),
                            ),
                        chatOptions =
                            PortableFunctionCallingOptions
                                .builder()
                                .withTemperature(0.0)
                                .build(),
                    ) { terminal.print(it) }
                terminal.println()
                aiResponse
            }

        val aiDevsAnswerResponse = aiDevsApiClient.sendAnswer(answerUrl, AiDevsAnswer(Task.SOFTO, answers))
        terminal.println()
        terminal.println(aiDevsAnswerResponse)
    }

    private fun fetchQuestions(): Map<String, String> =
        restClient
            .get()
            .uri(questionsUrl, apiKey)
            .retrieve()
            .body(object : ParameterizedTypeReference<Map<String, String>>() {}) ?: throw IllegalStateException("Missing body")

    companion object {
        private val log = KotlinLogging.logger { }
    }
}
