package pl.bartek.aidevs.task1

import org.springframework.ai.chat.client.ChatClient
import org.springframework.http.client.BufferingClientHttpRequestFactory
import org.springframework.http.client.SimpleClientHttpRequestFactory
import org.springframework.shell.command.CommandContext
import org.springframework.shell.command.annotation.Command
import org.springframework.web.client.RestClient
import org.springframework.web.client.body
import pl.bartek.aidevs.LoggingRestClientInterceptor
import pl.bartek.aidevs.task2.PatrollingRobotConversation
import pl.bartek.aidevs.task2.PatrollingRobotMessage

@Command(group = "task")
class Task2Command(
    private val chatClient: ChatClient,
) {
    private val restClient =
        RestClient
            .builder()
            .requestFactory(BufferingClientHttpRequestFactory(SimpleClientHttpRequestFactory()))
            .requestInterceptor(LoggingRestClientInterceptor())
            .baseUrl(ROBOT_SYSTEM_URL)
            .build()

    private var patrollingRobotConversation: PatrollingRobotConversation = PatrollingRobotConversation()

    @Command(command = ["task2"])
    fun run(ctx: CommandContext) {
        patrollingRobotConversation = PatrollingRobotConversation()
        val response = say("READY", ctx)
    }

    private fun say(message: String, ctx: CommandContext): String {
        patrollingRobotConversation.messages.add(message)
        ctx.terminal.writer().println("ISTOTA: $message")
        ctx.terminal.writer().flush()

        val responseMessage =
            restClient
                .post()
                .uri("/verify")
                .body(PatrollingRobotMessage(patrollingRobotConversation.messageId, message))
                .retrieve()
                .body<PatrollingRobotMessage>() ?: throw IllegalStateException("No response provided")

        if (patrollingRobotConversation.hasStarted().not()) {
            ctx.terminal.writer().println("(Message id: ${patrollingRobotConversation.messageId})")
            patrollingRobotConversation.messageId = responseMessage.messageId
        }
        patrollingRobotConversation.messages.add(responseMessage.text)
        ctx.terminal.writer().println("ROBOT: ${responseMessage.text}")
        ctx.terminal.writer().flush()

        return responseMessage.text
    }

    companion object {
        private const val ROBOT_SYSTEM_URL = "NEXT COMMITS IN ENVS"
    }
}
