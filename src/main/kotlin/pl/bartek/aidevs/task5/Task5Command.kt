package pl.bartek.aidevs.task5

import org.springframework.ai.chat.messages.SystemMessage
import org.springframework.ai.chat.messages.UserMessage
import org.springframework.ai.chat.prompt.ChatOptions
import org.springframework.ai.chat.prompt.Prompt
import org.springframework.ai.model.function.FunctionCallingOptionsBuilder.PortableFunctionCallingOptions
import org.springframework.beans.factory.annotation.Value
import org.springframework.shell.command.CommandContext
import org.springframework.shell.command.annotation.Command
import org.springframework.web.client.RestClient
import pl.bartek.aidevs.AiModelVendor
import pl.bartek.aidevs.courseapi.AiDevsAnswer
import pl.bartek.aidevs.courseapi.AiDevsApiClient
import pl.bartek.aidevs.courseapi.Task
import java.util.stream.Collectors

@Command(group = "task")
class Task5Command(
    @Value("\${aidevs.api-key}") private val apiKey: String,
    @Value("\${aidevs.task.5.data-url}") private val dataUrl: String,
    @Value("\${aidevs.task.5.answer-url}") private val answerUrl: String,
    private val aiDevsApiClient: AiDevsApiClient,
    private val restClient: RestClient,
    aiModelVendor: AiModelVendor,
) {
    private val chatClient = aiModelVendor.defaultChatClient()
    private val chatOptions: ChatOptions =
        PortableFunctionCallingOptions
            .builder()
            .withTemperature(.0)
            .build()

    @Command(command = ["task5"])
    fun run(ctx: CommandContext) {
        val toBeConsored = fetchInputData()

        ctx.terminal.writer().println("Original text:\n$toBeConsored")
        ctx.terminal.writer().println()
        ctx.terminal.writer().println("AI response:")
        ctx.terminal.flush()

        val response =
            chatClient
                .prompt(
                    Prompt(
                        listOf(
                            SystemMessage(
                                """
                                User's text has 4 different information, in order:
                                1. Name with surname
                                2. City
                                3. Street with number
                                4. Age
                                
                                Replace each piece of information with a word "CENZURA". Follow below rules:
                                1. Don't use declension when inserting "CENZURA"
                                2. Surname and name always replace with one word "CENZURA"
                                3. Number with street always replace with one word "CENZURA"
                                4. Output has to contain only result and no other information
                                5. Don't paraphrase user's text
                                6. Don't adjust output to examples provided below
                                7. Don't change other words in user's text besides the information mentioned above
                                
                                First example:
                                <example>
                                Osoba podejrzana to Stanisław Kowalski. Adres: Białystok, ul. Mickiewicza 3. Wiek: 26 lat.
                                </example>
                                <result>
                                Osoba podejrzana to CENZURA. Adres: CENZURA, ul. CENZURA. Wiek: CENZURA lat.
                                </result>
                                
                                Second example:
                                <example>
                                Informacje o podejrzanym: Piotr Abramowicz. Mieszka w Lublinie przy ulicy Pięknej 10. Wiek: 32 lata.
                                </example>
                                <result>
                                Informacje o podejrzanym: CENZURA. Mieszka w CENZURA przy ulicy CENZURA. Wiek: CENZURA lata.
                                </result>
                                """.trimIndent(),
                            ),
                            UserMessage(toBeConsored),
                        ),
                        chatOptions,
                    ),
                ).stream()
                .content()
                .doOnNext {
                    ctx.terminal.writer().print(it)
                    ctx.terminal.flush()
                }.collect(Collectors.joining(""))
                .block() ?: throw IllegalStateException("Cannot get chat response")

        ctx.terminal.writer().println()
        ctx.terminal.writer().flush()

        val answer = aiDevsApiClient.sendAnswer(answerUrl, AiDevsAnswer(Task.CENZURA, response))
        ctx.terminal.writer().println()
        ctx.terminal.writer().println()
        ctx.terminal.writer().println("Centrala response:\n$answer")
        ctx.terminal.flush()
    }

    private fun fetchInputData(): String =
        restClient
            .get()
            .uri(dataUrl, apiKey)
            .retrieve()
            .body(String::class.java) ?: throw IllegalStateException("Cannot get data to process")
}
