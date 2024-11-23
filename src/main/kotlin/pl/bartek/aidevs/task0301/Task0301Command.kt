package pl.bartek.aidevs.task0301

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.MapperFeature
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.dataformat.xml.XmlMapper
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import io.github.oshai.kotlinlogging.KotlinLogging
import org.jline.terminal.Terminal
import org.springframework.ai.chat.messages.SystemMessage
import org.springframework.ai.chat.messages.UserMessage
import org.springframework.ai.openai.OpenAiChatOptions
import org.springframework.ai.openai.api.OpenAiApi.ChatModel
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.MediaType
import org.springframework.shell.command.annotation.Command
import org.springframework.web.client.RestClient
import org.springframework.web.util.UriComponentsBuilder
import pl.bartek.aidevs.TaskId
import pl.bartek.aidevs.ansiFormattedAi
import pl.bartek.aidevs.courseapi.AiDevsAnswer
import pl.bartek.aidevs.courseapi.AiDevsApiClient
import pl.bartek.aidevs.courseapi.Task
import pl.bartek.aidevs.print
import pl.bartek.aidevs.println
import pl.bartek.aidevs.text.TextService
import pl.bartek.aidevs.unzip
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.extension
import kotlin.io.path.nameWithoutExtension
import kotlin.io.path.readText

@Command(
    group = "task",
    command = ["task"],
)
class Task0301Command(
    private val terminal: Terminal,
    @Value("\${aidevs.cache-dir}") cacheDir: Path,
    @Value("\${aidevs.task.0301.data-url}") private val dataUrl: String,
    @Value("\${aidevs.task.0301.answer-url}") private val answerUrl: String,
    private val aiDevsApiClient: AiDevsApiClient,
    private val restClient: RestClient,
    private val textService: TextService,
) {
    private val cacheDir = cacheDir.resolve(TaskId.TASK_0301.cacheFolderName())

    init {
        Files.createDirectories(this.cacheDir)
    }

    @Command(
        command = ["0301"],
        description = "https://bravecourses.circle.so/c/lekcje-programu-ai3-806660/s03e01-dokumenty",
    )
    fun run() {
        val path = fetchInputData()
        val facts =
            Files
                .list(path.resolve("facts"))
                .map { it.readText().trim() }
                .filter { it != "entry deleted" }
                .toList()
                .withIndex()
                .joinToString("\n\n") { "### Fact ${it.index}\n\n${it.value}" }
        val reports =
            Files
                .list(path)
                .filter { Files.isRegularFile(it) && it.extension == "txt" }
                .map { Pair(it, it.readText().trim()) }
                .toList()

        val prompt =
            """
            |# Task
            |
            |Generate a comma separated list of keywords for provided user's report and related context information.
            |
            |# Rules
            |
            |- translate keywords to Polish language
            |- the keywords can't use declination. Use only nominatives and infinitives
            |- keywords should include people names and information about them like professions, specializations, special abilities
            |- if a person has a special ability then think about profession for this special ability
            |- keywords should include presence of humans and other beings
            |
            |# Context information
            |
            |## Facts
            |
            |$facts
            |
            |# Your response
            |
            |In your response, include all steps below.
            |Firstly, analyze context information and decide if there's any related information to the user's report.
            |When you find the related context information, mention it and think about keywords for the found information.
            |Lastly, provide an answer with a list of keywords for the user's report and found context information. Example:
            |<result>
            |   sektor P2, Stefan, strażak, północ, notatka Kuba, stacja paliw, porwanie, woźny, zwierzęta, programista JavaScript
            |</result>
            """.trimMargin()

        val taskAnswer =
            reports
                .map { report ->
                    terminal.print("AI generated words for file ${report.first.fileName}: ".ansiFormattedAi())
                    val response =
                        textService.sendToChat(
                            listOf(
                                SystemMessage(prompt),
                                UserMessage(
                                    """
                                    |${report.first.fileName}:
                                    |${report.second}
                                    """.trimMargin(),
                                ),
                            ),
                            chatOptions = OpenAiChatOptions.builder().withModel(ChatModel.GPT_4_O).build(),
                        ) { terminal.print(it) }
                    terminal.println()
                    terminal.println()

                    val result =
                        response.substring(
                            response.indexOf("<result>"),
                            response.indexOf("</result>") + "</result>".length,
                        )

                    val xmlMapper: ObjectMapper =
                        XmlMapper
                            .builder()
                            .defaultUseWrapper(false)
                            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
                            .configure(MapperFeature.ACCEPT_CASE_INSENSITIVE_PROPERTIES, true)
                            .build()
                            .registerKotlinModule()

                    val resultXmlContent = xmlMapper.readValue(result, String::class.java)

                    Pair(report.first, resultXmlContent.trim())
                }.associate { it.first.fileName.toString() to it.second }

        val aiDevsAnswer = aiDevsApiClient.sendAnswer(answerUrl, AiDevsAnswer(Task.DOKUMENTY, taskAnswer))
        terminal.println(aiDevsAnswer)
    }

    private fun fetchInputData(): Path {
        val uriComponents =
            UriComponentsBuilder
                .fromHttpUrl(dataUrl)
                .build()
        val filename = uriComponents.pathSegments[uriComponents.pathSegments.size - 1]!!
        val zipFilePath = this.cacheDir.resolve(filename)
        val extractedZipPath = this.cacheDir.resolve(zipFilePath.nameWithoutExtension)
        if (Files.exists(extractedZipPath)) {
            log.info { "Input data already exists: ${extractedZipPath.toAbsolutePath()}. Skipping" }
            return extractedZipPath
        }

        val body =
            restClient
                .get()
                .uri(uriComponents.toUriString())
                .headers { it.contentType = MediaType.APPLICATION_OCTET_STREAM }
                .retrieve()
                .body(ByteArray::class.java)!!
        Files.newOutputStream(zipFilePath).use {
            it.write(body)
        }
        zipFilePath.unzip(extractedZipPath)
        Files.delete(zipFilePath)
        return extractedZipPath
    }

    companion object {
        private val log = KotlinLogging.logger { }
    }
}
