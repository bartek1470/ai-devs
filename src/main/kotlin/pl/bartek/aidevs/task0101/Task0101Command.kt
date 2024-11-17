package pl.bartek.aidevs.task0101

import org.jline.terminal.Terminal
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.shell.command.annotation.Command
import org.springframework.util.LinkedMultiValueMap
import org.springframework.web.client.RestClient
import org.springframework.web.util.UriComponentsBuilder
import pl.bartek.aidevs.AiModelVendor
import pl.bartek.aidevs.ansiFormattedAi
import pl.bartek.aidevs.ansiFormattedError
import pl.bartek.aidevs.ansiFormattedSuccess
import pl.bartek.aidevs.extractAiDevsFlag
import pl.bartek.aidevs.println
import pl.bartek.aidevs.removeExtraWhitespaces

@Command(
    group = "task",
    command = ["task"],
)
class Task0101Command(
    private val terminal: Terminal,
    @Value("\${aidevs.task.1.robot-system.url}") private val robotSystemUrl: String,
    @Value("\${aidevs.task.1.robot-system.username}") private val robotSystemUsername: String,
    @Value("\${aidevs.task.1.robot-system.password}") private val robotSystemPassword: String,
    aiModelVendor: AiModelVendor,
    private val restClient: RestClient,
) {
    private val chatClient = aiModelVendor.defaultChatClient()

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
                .fromHttpUrl(robotSystemUrl)
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
                .uri(robotSystemUrl)
                .accept(MediaType.ALL)
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body(
                    LinkedMultiValueMap<String, String>().apply {
                        add("username", robotSystemUsername)
                        add("password", robotSystemPassword)
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
        val robotSystemPage = Jsoup.connect(robotSystemUrl).get()
        val question = robotSystemPage.select("#human-question").text()
        terminal.println(question)
        return question
    }
}
