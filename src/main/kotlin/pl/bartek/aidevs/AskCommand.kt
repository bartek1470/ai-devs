package pl.bartek.aidevs

import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.ai.chat.client.ChatClient
import org.springframework.shell.command.CommandContext
import org.springframework.shell.command.annotation.Command
import org.springframework.shell.command.annotation.Option

@Command(group = "general")
class AskCommand(
    private val chatClient: ChatClient,
) {
    @Command(description = "Sends question to AI and outputs the response")
    fun ask(
        @Option(required = true, description = "The question to send") question: String,
        ctx: CommandContext,
    ) {
        log.debug { "Sending question: $question" }
        val response =
            chatClient
                .prompt()
                .user(question)
                .call()
                .content()

        log.trace { "Got response: $response" }
        ctx.terminal.writer().println(response)
        ctx.terminal.writer().flush()
    }

    companion object {
        private val log = KotlinLogging.logger {}
    }
}
