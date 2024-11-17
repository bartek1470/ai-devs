package pl.bartek.aidevs.task1

import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.ansi.AnsiColor
import org.springframework.boot.ansi.AnsiOutput
import org.springframework.boot.ansi.AnsiStyle
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.shell.command.CommandContext
import org.springframework.shell.command.annotation.Command
import org.springframework.util.LinkedMultiValueMap
import org.springframework.web.client.RestClient
import org.springframework.web.util.UriComponentsBuilder
import pl.bartek.aidevs.AiModelVendor
import pl.bartek.aidevs.extractAiDevsFlag
import pl.bartek.aidevs.removeExtraWhitespaces

@Command(group = "task")
class Task1Command(
    @Value("\${aidevs.task.1.robot-system.url}") private val robotSystemUrl: String,
    @Value("\${aidevs.task.1.robot-system.username}") private val robotSystemUsername: String,
    @Value("\${aidevs.task.1.robot-system.password}") private val robotSystemPassword: String,
    aiModelVendor: AiModelVendor,
    private val restClient: RestClient,
) {

    private val chatClient = aiModelVendor.defaultChatClient()

    @Command(command = ["task1"])
    fun run(ctx: CommandContext) {
        val question = findQuestion(ctx)
        val answer = askAiAboutAnswer(question, ctx)
        val response = loginToRobotsSystem(answer, ctx)

        if (response.statusCode.is3xxRedirection) {
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
                        ).body(""),
                    ctx,
                )
            printFlag(answerPage, ctx)
        } else {
            ctx.terminal.writer().println(
                AnsiOutput.toString(
                    AnsiColor.RED,
                    AnsiStyle.BOLD,
                    "Failed to login",
                    AnsiStyle.NORMAL,
                    AnsiColor.DEFAULT,
                ),
            )
            ctx.terminal.writer().flush()
        }
    }

    private fun redirect(
        response: ResponseEntity<String>,
        ctx: CommandContext,
    ): Document {
        val location = response.headers["location"]?.first()!!
        val newLink =
            UriComponentsBuilder
                .fromHttpUrl(robotSystemUrl)
                .pathSegment(location)
                .build()
                .toUriString()
        ctx.terminal.writer().println("Successful answer. Redirecting to '$newLink'...")
        ctx.terminal.flush()

        val answerPage = Jsoup.connect(newLink).get()
        ctx.terminal.writer().println("Answer page:")
        ctx.terminal
            .writer()
            .println(answerPage.wholeText().replace("\\s{2,}".toRegex(), "\n").replace("^\\s+".toRegex(), ""))
        ctx.terminal.flush()
        return answerPage
    }

    private fun printFlag(
        answerPage: Document,
        ctx: CommandContext,
    ) {
        val flag = answerPage.wholeText().extractAiDevsFlag()
        ctx.terminal.writer().println("The flag is: $flag")
        ctx.terminal.flush()
    }

    private fun loginToRobotsSystem(
        answer: String,
        ctx: CommandContext,
    ): ResponseEntity<String> {
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

        ctx.terminal.writer().println("Status: ${response.statusCode}")
        val body =
            if (response.headers.contentType?.isCompatibleWith(MediaType.TEXT_HTML) == true) {
                Jsoup
                    .parse(response.body!!)
                    .wholeText()
                    .removeExtraWhitespaces()
            } else {
                response.body!!
            }
        ctx.terminal.writer().println(body)
        ctx.terminal.flush()
        return response
    }

    private fun askAiAboutAnswer(
        question: String,
        ctx: CommandContext,
    ): String {
        val answer =
            chatClient
                .prompt()
                .system("You have to answer only with a year to the user's question")
                .user(question)
                .call()
                .content()!!
        ctx.terminal.writer().println("The answer is:\n$answer")
        ctx.terminal.writer().println()
        ctx.terminal.flush()
        return answer
    }

    private fun findQuestion(ctx: CommandContext): String {
        val robotSystemPage = Jsoup.connect(robotSystemUrl).get()
        val question = robotSystemPage.select("#human-question").text()
        ctx.terminal.writer().println(question)
        ctx.terminal.flush()
        return question
    }
}
