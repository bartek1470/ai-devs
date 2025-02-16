package pl.bartek.aidevs.task0303

import io.github.oshai.kotlinlogging.KotlinLogging
import org.jline.terminal.Terminal
import org.springframework.ai.chat.messages.SystemMessage
import org.springframework.ai.chat.messages.UserMessage
import org.springframework.ai.model.function.DefaultFunctionCallbackBuilder
import org.springframework.stereotype.Service
import org.springframework.web.client.RestClient
import pl.bartek.aidevs.ai.ChatService
import pl.bartek.aidevs.config.AiDevsProperties
import pl.bartek.aidevs.course.TaskId
import pl.bartek.aidevs.course.api.AiDevsAnswer
import pl.bartek.aidevs.course.api.AiDevsApiClient
import pl.bartek.aidevs.course.api.Task
import pl.bartek.aidevs.util.ansiFormattedError
import pl.bartek.aidevs.util.print
import pl.bartek.aidevs.util.println
import java.nio.file.Files

@Service
class Task0303Service(
    private val aiDevsProperties: AiDevsProperties,
    private val restClient: RestClient,
    private val aiDevsApiClient: AiDevsApiClient,
    private val chatService: ChatService,
) {
    private val cacheDir = aiDevsProperties.cacheDir.resolve(TaskId.TASK_0303.cacheFolderName())

    init {
        Files.createDirectories(this.cacheDir)
    }

    fun run(terminal: Terminal) {
        val response =
            chatService.sendToChat(
                listOf(
                    SystemMessage(
                        """
                        # Task
                        
                        You can use `sendDbApiRequest` tool to browse a database. The tool accepts:
                        - `show tables` - see available tables
                        - `show create table TABLE_NAME` - see a table structure
                        - an SQL query to query the data
                        
                        # `sendDbApiRequest` tool rules
                        
                        -  returns a json response
                        - `error` field contains an information about the request. If the value is `OK` then request was successful, otherwise it's a description of an error.
                        
                        Your result should be only a comma separated list and nothing else. Skip any markdown formatting.
                        """.trimIndent(),
                    ),
                    UserMessage(
                        """
                        Które aktywne datacenter (DC_ID) są zarządzane przez pracowników, którzy są na urlopie (is_active=0)
                        """.trimIndent(),
                    ),
                ),
                listOf(
                    DefaultFunctionCallbackBuilder()
                        .function(
                            "sendDbApiRequest",
                            SendDbApiRequest(
                                aiDevsProperties.apiKey,
                                aiDevsProperties.task.task0303.apiUrl
                                    .toString(),
                                restClient,
                            ),
                        ).description("Execute database query")
                        .inputType(SendDbApiRequest::class.java)
                        .build(),
                ),
            ) { terminal.print(it) }
        terminal.println()

        val ids =
            response
                .split(",")
                .map { it.trim() }
                .filter { it.isNotBlank() }
        val areNumbers = ids.all { it.matches("\\d+".toRegex()) }
        if (ids.isEmpty() || !areNumbers) {
            terminal.println("Invalid response".ansiFormattedError())
            return
        }

        val datacenterIds =
            ids.map { it.toLong() }
        val answer = aiDevsApiClient.sendAnswer(aiDevsProperties.reportUrl, AiDevsAnswer(Task.DATABASE, datacenterIds))
        terminal.println(answer)
    }

    companion object {
        private val log = KotlinLogging.logger { }
    }
}
