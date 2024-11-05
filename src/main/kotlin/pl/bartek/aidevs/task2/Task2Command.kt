package pl.bartek.aidevs.task2

import org.springframework.ai.chat.client.ChatClient
import org.springframework.boot.ansi.AnsiColor
import org.springframework.boot.ansi.AnsiOutput
import org.springframework.boot.ansi.AnsiStyle
import org.springframework.http.HttpStatusCode
import org.springframework.http.client.BufferingClientHttpRequestFactory
import org.springframework.http.client.SimpleClientHttpRequestFactory
import org.springframework.shell.command.CommandContext
import org.springframework.shell.command.annotation.Command
import org.springframework.web.client.RestClient
import org.springframework.web.client.body
import pl.bartek.aidevs.LoggingRestClientInterceptor

@Command(group = "task")
class Task2Command(
    private val chatClient: ChatClient,
) {
    private val restClient =
        RestClient
            .builder()
            .requestFactory(BufferingClientHttpRequestFactory(SimpleClientHttpRequestFactory()))
            .requestInterceptor(LoggingRestClientInterceptor())
            .defaultStatusHandler(HttpStatusCode::isError) { _, _ -> }
            .baseUrl(ROBOT_SYSTEM_URL)
            .build()

    private var patrollingRobotConversation: PatrollingRobotConversation = PatrollingRobotConversation()

    @Command(command = ["task2"])
    fun run(ctx: CommandContext) {
        patrollingRobotConversation = PatrollingRobotConversation()
        val robotQuestion = say("READY", ctx)
        val answer = generateAnswer(robotQuestion)
        val response = say(answer, ctx)
        if (response != "OK") {
            ctx.terminal.writer().println(
                AnsiOutput.toString(AnsiColor.RED, AnsiStyle.BOLD, "Autoryzacja nieudana", AnsiStyle.NORMAL, AnsiColor.DEFAULT),
            )
            ctx.terminal.writer().flush()
        } else {
            ctx.terminal.writer().println(
                AnsiOutput.toString(AnsiColor.GREEN, AnsiStyle.BOLD, "Pomyślna autoryzacja", AnsiStyle.NORMAL, AnsiColor.DEFAULT),
            )
            ctx.terminal.writer().flush()
        }
    }

    private fun generateAnswer(question: String): String {
        val content =
            chatClient
                .prompt()
                .system("")
                .user(question)
                .call()
                .content() ?: throw IllegalStateException("No content generated")
        return content
    }

    private fun say(
        message: String,
        ctx: CommandContext,
    ): String {
        patrollingRobotConversation.messages.add(message)
        ctx.terminal.writer().println(
            AnsiOutput.toString(AnsiColor.CYAN, AnsiStyle.BOLD, "ISTOTA: ", AnsiStyle.NORMAL, AnsiColor.DEFAULT, message),
        )
        ctx.terminal.writer().flush()

        val responseMessage =
            restClient
                .post()
                .uri("/verify")
                .body(PatrollingRobotMessage(messageId = patrollingRobotConversation.messageId, text = message))
                .retrieve()
                .body<PatrollingRobotMessage>() ?: throw IllegalStateException("No response provided")

        if (responseMessage.code != null) {
            throw IllegalStateException("Communication failed. Code from response: ${responseMessage.code}, message: '$responseMessage.message'")
        }

        if (patrollingRobotConversation.hasStarted().not()) {
            ctx.terminal.writer().println(
                AnsiOutput.toString(
                    AnsiColor.BRIGHT_BLACK,
                    "(Message id: ${patrollingRobotConversation.messageId})",
                    AnsiStyle.NORMAL,
                    AnsiColor.DEFAULT,
                ),
            )
            patrollingRobotConversation.messageId = responseMessage.messageId!!
        }
        patrollingRobotConversation.messages.add(responseMessage.text!!)
        ctx.terminal.writer().println(
            AnsiOutput.toString(
                AnsiColor.BRIGHT_MAGENTA,
                AnsiStyle.BOLD,
                "ROBOT: ",
                AnsiStyle.NORMAL,
                AnsiColor.DEFAULT,
                responseMessage.text,
            ),
        )
        ctx.terminal.writer().flush()

        return responseMessage.text
    }

    companion object {
        private const val ROBOT_SYSTEM_URL = "NEXT COMMITS IN ENVS"
    }
}
