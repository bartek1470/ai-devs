package pl.bartek.aidevs.task1

import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.springframework.ai.chat.client.ChatClient
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

@Command(group = "task")
class Task1Command(
    private val chatClient: ChatClient,
) {
    private val restClient =
        RestClient
            .builder()
            .build()

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
                        .headers(HttpHeaders(LinkedMultiValueMap<String, String>().apply { add("location", "/firmware") }))
                        .body(""),
                    ctx,
                )
            printFlag(answerPage, ctx)
        } else {
            ctx.terminal.writer().println(
                AnsiOutput.toString(AnsiColor.RED, AnsiStyle.BOLD, "Failed to login", AnsiStyle.NORMAL, AnsiColor.DEFAULT),
            )
            ctx.terminal.writer().flush()
        }
    }

    private fun redirect(
        response: ResponseEntity<String>,
        ctx: CommandContext,
    ): Document {
        val location = response.headers["location"]?.first()!!
        val newLink = "$ROBOT_SYSTEM_URL$location"
        ctx.terminal.writer().println("Successful answer. Redirecting to '$newLink'...")
        ctx.terminal.flush()

        val answerPage = Jsoup.connect(newLink).get()
        ctx.terminal.writer().println("Answer page:")
        ctx.terminal.writer().println(answerPage.wholeText().replace("\\s{2,}".toRegex(), "\n").replace("^\\s+".toRegex(), ""))
        ctx.terminal.flush()
        return answerPage
    }

    private fun printFlag(
        answerPage: Document,
        ctx: CommandContext,
    ) {
        val flag = "\\{\\{FLG:(.*)}}".toRegex().find(answerPage.wholeText())?.value
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
                .uri(ROBOT_SYSTEM_URL)
                .accept(MediaType.ALL)
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body(
                    LinkedMultiValueMap<String, String>().apply {
                        add("username", "NEXT COMMITS IN ENVS")
                        add("password", "NEXT COMMITS IN ENVS")
                        add("answer", answer)
                    },
                ).retrieve()
                .toEntity(String::class.java)

        ctx.terminal.writer().println(response.statusCode)
        ctx.terminal.writer().println(
            response.headers
                .toSingleValueMap()
                .entries
                .joinToString("\n"),
        )
        ctx.terminal.writer().println()
        ctx.terminal.writer().println(response.body)
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
        val robotSystemPage = Jsoup.connect(ROBOT_SYSTEM_URL).get()
        val question = robotSystemPage.select("#human-question").text()
        ctx.terminal.writer().println(question)
        ctx.terminal.flush()
        return question
    }

    companion object {
        private const val ROBOT_SYSTEM_URL = "NEXT COMMITS IN ENVS"
    }
}
