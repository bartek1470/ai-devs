package pl.bartek.aidevs.task5

import org.springframework.ai.autoconfigure.ollama.OllamaChatProperties
import org.springframework.ai.chat.client.ChatClient
import org.springframework.ai.chat.client.advisor.SimpleLoggerAdvisor
import org.springframework.ai.ollama.OllamaChatModel
import org.springframework.ai.ollama.api.OllamaApi
import org.springframework.ai.ollama.api.OllamaOptions
import org.springframework.shell.command.CommandContext
import org.springframework.shell.command.annotation.Command
import pl.bartek.aidevs.poligon.AiDevsPoligonAnswer
import pl.bartek.aidevs.poligon.AiDevsPoligonApiClient
import pl.bartek.aidevs.poligon.Task
import java.util.stream.Collectors

@Command(group = "task")
class Task5Command(
    private val apiClient: AiDevsPoligonApiClient,
    ollamaApi: OllamaApi,
    ollamaChatProperties: OllamaChatProperties,
) {
    private val chatModel =
        OllamaChatModel(
            ollamaApi,
            OllamaOptions
                .fromOptions(ollamaChatProperties.options)
                .withTemperature(.0),
        )

    private val chatClient =
        ChatClient
            .builder(chatModel)
            .defaultAdvisors(SimpleLoggerAdvisor())
            .build()

    @Command(command = ["task5"])
    fun run(ctx: CommandContext) {
        val toBeConsored = apiClient.fetchTask5Data()

        ctx.terminal.writer().println("Original text:\n$toBeConsored")
        ctx.terminal.writer().println()
        ctx.terminal.writer().println("AI response:")
        ctx.terminal.flush()

        val response =
            chatClient
                .prompt()
                .system(
                    """User's text has 4 different information, in order:
                        |1. Name with surname
                        |2. City
                        |3. Street with number
                        |4. Age
                        |
                        |Replace each piece of information with a word "CENZURA". Follow below rules:
                        |1. Don't use declension when inserting "CENZURA"
                        |2. Surname and name always replace with one word "CENZURA"
                        |3. Number with street always replace with one word "CENZURA"
                        |4. Output has to contain only result and no other information
                        |5. Don't paraphrase user's text
                        |6. Don't adjust output to examples provided below
                        |7. Don't change other words in user's text besides the information mentioned above
                        |
                        |First example:
                        |<example>
                        |Osoba podejrzana to Stanisław Kowalski. Adres: Białystok, ul. Mickiewicza 3. Wiek: 26 lat.
                        |</example>
                        |<result>
                        |Osoba podejrzana to CENZURA. Adres: CENZURA, ul. CENZURA. Wiek: CENZURA lat.
                        |</result>
                        |
                        |Second example:
                        |<example>
                        |Informacje o podejrzanym: Piotr Abramowicz. Mieszka w Lublinie przy ulicy Pięknej 10. Wiek: 32 lata.
                        |</example>
                        |<result>
                        |Informacje o podejrzanym: CENZURA. Mieszka w CENZURA przy ulicy CENZURA. Wiek: CENZURA lata.
                        |</result>
                    """.trimMargin(),
                ).user(toBeConsored)
                .stream()
                .content()
                .doOnNext {
                    ctx.terminal.writer().print(it)
                    ctx.terminal.flush()
                }.collect(Collectors.joining(""))
                .block() ?: throw IllegalStateException("Cannot get chat response")

        ctx.terminal.writer().println()
        ctx.terminal.writer().flush()

        val answer =
            apiClient.sendAnswer("NEXT COMMITS IN ENVS", AiDevsPoligonAnswer(Task.CENZURA, response))
        ctx.terminal.writer().println()
        ctx.terminal.writer().println()
        ctx.terminal.writer().println("Centrala response:\n$answer")
        ctx.terminal.flush()
    }
}
