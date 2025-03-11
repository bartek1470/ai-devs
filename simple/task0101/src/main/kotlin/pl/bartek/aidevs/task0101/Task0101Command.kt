package pl.bartek.aidevs.task0101

import org.jline.terminal.Terminal
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.springframework.ai.chat.client.ChatClient
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.shell.command.annotation.Command
import org.springframework.util.LinkedMultiValueMap
import org.springframework.web.client.RestClient
import org.springframework.web.util.UriComponentsBuilder
import pl.bartek.aidevs.config.AiDevsProperties
import pl.bartek.aidevs.util.ansiFormattedAi
import pl.bartek.aidevs.util.ansiFormattedError
import pl.bartek.aidevs.util.ansiFormattedSuccess
import pl.bartek.aidevs.util.extractAiDevsFlag
import pl.bartek.aidevs.util.println
import pl.bartek.aidevs.util.removeExtraWhitespaces

@Command(
    group = "task",
    command = ["task"],
)
class Task0101Command(
    private val terminal: Terminal,
    private val aiDevsProperties: AiDevsProperties,
    private val task0101Config: Task0101Config,
    private val restClient: RestClient,
    private val chatClient: ChatClient,
) {
    @Command(
        command = ["0101"],
        description = "https://bravecourses.circle.so/c/lekcje-programu-ai3-806660/s01e01-interakcja-z-duzym-modelem-jezykowym",
    )
    fun run() {
        val question = findQuestion()
        val answer = askAiAboutAnswer(question)
        val response = loginToRobotsSystem(answer)

        if (response.statusCode.is3xxRedirection) {
            terminal.println("Successful answer".ansiFormattedSuccess())
            val answerPage =
                redirect(
                    ResponseEntity
                        .status(200)
                        .headers(
                            HttpHeaders(
                                LinkedMultiValueMap<String, String>().apply {
                                    add(
                                        "location",
                                        "/firmware",
                                    )
                                },
                            ),
                        ).body(null),
                )
            printFlag(answerPage)
        } else {
            terminal.println("Failed to login".ansiFormattedError())
        }
    }

    private fun redirect(response: ResponseEntity<String>): Document {
        val location = response.headers["location"]?.first()!!
        val newLink =
            UriComponentsBuilder
                .fromHttpUrl(task0101Config.url)
                .pathSegment(location)
                .build()
                .toUriString()
        terminal.println("Redirecting to '$newLink'...")

        val answerPage = Jsoup.connect(newLink).get()
        val answerPageLink = answerPage.wholeText().replace("\\s{2,}".toRegex(), "\n").replace("^\\s+".toRegex(), "")
        terminal.println("Answer page: $answerPageLink")
        return answerPage
    }

    private fun printFlag(answerPage: Document) {
        val flag = answerPage.wholeText().extractAiDevsFlag()
        terminal.println("The flag is: $flag".ansiFormattedSuccess())
    }

    private fun loginToRobotsSystem(answer: String): ResponseEntity<String> {
        val response =
            restClient
                .post()
                .uri(task0101Config.url)
                .accept(MediaType.ALL)
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body(
                    LinkedMultiValueMap<String, String>().apply {
                        add("username", task0101Config.username)
                        add("password", task0101Config.password)
                        add("answer", answer)
                    },
                ).retrieve()
                .toEntity(String::class.java)

        terminal.println("Status: ${response.statusCode}")
        val body =
            if (response.headers.contentType?.isCompatibleWith(MediaType.TEXT_HTML) == true) {
                Jsoup
                    .parse(response.body!!)
                    .wholeText()
                    .removeExtraWhitespaces()
            } else {
                response.body!!
            }
        terminal.println(body)
        return response
    }

    private fun askAiAboutAnswer(question: String): String {
        val answer =
            chatClient
                .prompt()
                .system("You have to answer only with a year to the user's question")
                .user(question)
                .call()
                .content()!!
        terminal.println("AI response:\n$answer".ansiFormattedAi())
        terminal.println()
        return answer
    }

    private fun findQuestion(): String {
        val robotSystemPage = Jsoup.connect(task0101Config.url).get()
        val question = robotSystemPage.select("#human-question").text()
        terminal.println(question)
        return question
    }
}
