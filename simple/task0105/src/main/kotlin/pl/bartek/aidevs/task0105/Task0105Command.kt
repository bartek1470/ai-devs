package pl.bartek.aidevs.task0105

import org.jline.terminal.Terminal
import org.springframework.ai.chat.client.ChatClient
import org.springframework.ai.chat.messages.SystemMessage
import org.springframework.ai.chat.messages.UserMessage
import org.springframework.ai.chat.prompt.ChatOptions
import org.springframework.ai.chat.prompt.Prompt
import org.springframework.shell.command.annotation.Command
import org.springframework.web.client.RestClient
import pl.bartek.aidevs.config.AiDevsProperties
import pl.bartek.aidevs.course.api.AiDevsAnswer
import pl.bartek.aidevs.course.api.AiDevsApiClient
import pl.bartek.aidevs.course.api.Task
import pl.bartek.aidevs.util.ansiFormattedAi
import pl.bartek.aidevs.util.ansiFormattedSecondaryInfo
import pl.bartek.aidevs.util.print
import pl.bartek.aidevs.util.println
import java.util.stream.Collectors

@Command(
    group = "task",
    command = ["task"],
)
class Task0105Command(
    private val terminal: Terminal,
    private val aiDevsProperties: AiDevsProperties,
    private val task0105Config: Task0105Config,
    private val aiDevsApiClient: AiDevsApiClient,
    private val restClient: RestClient,
    private val chatClient: ChatClient,
) {
    @Command(command = ["0105"], description = "https://bravecourses.circle.so/c/lekcje-programu-ai3-806660/s01e05-produkcja")
    fun run() {
        val toBeConsored = fetchInputData()

        terminal.println("Original text:\n$toBeConsored".ansiFormattedSecondaryInfo())
        terminal.println()
        terminal.println("AI response:".ansiFormattedAi())

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
                        ChatOptions
                            .builder()
                            .temperature(.0)
                            .build(),
                    ),
                ).stream()
                .content()
                .doOnNext {
                    terminal.print(it)
                }.collect(Collectors.joining(""))
                .block() ?: throw IllegalStateException("Cannot get chat response")

        terminal.println()

        val answer = aiDevsApiClient.sendAnswer(aiDevsProperties.reportUrl, AiDevsAnswer(Task.CENZURA, response))
        terminal.println()
        terminal.println()
        terminal.println(answer)
    }

    private fun fetchInputData(): String =
        restClient
            .get()
            .uri(
                task0105Config.dataUrl.toString(),
                aiDevsProperties.apiKey,
            ).retrieve()
            .body(String::class.java) ?: throw IllegalStateException("Cannot get data to process")
}
