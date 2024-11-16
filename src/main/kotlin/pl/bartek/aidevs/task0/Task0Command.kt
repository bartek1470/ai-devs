package pl.bartek.aidevs.task0

import org.jsoup.Jsoup
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.HttpHeaders.CONTENT_TYPE
import org.springframework.http.MediaType.TEXT_PLAIN_VALUE
import org.springframework.shell.command.CommandContext
import org.springframework.shell.command.annotation.Command
import org.springframework.web.client.RestClient
import org.springframework.web.client.body
import pl.bartek.aidevs.courseapi.AiDevsAnswer
import pl.bartek.aidevs.courseapi.AiDevsApiClient
import pl.bartek.aidevs.courseapi.Task
import pl.bartek.aidevs.removeExtraWhitespaces

@Command(group = "task")
class Task0Command(
    private val apiClient: AiDevsApiClient,
    private val restClient: RestClient,
    @Value("\${aidevs.task.0.data-url}") private val dataUrl: String,
    @Value("\${aidevs.task.0.answer-url}") private val answerUrl: String,
) {
    @Command(command = ["task0"])
    fun run(ctx: CommandContext) {
        val data = fetchInputData()
        val response = apiClient.sendAnswerReceiveText(answerUrl, AiDevsAnswer(Task.POLIGON, data))
        val text = Jsoup.parse(response).wholeText().removeExtraWhitespaces()
        ctx.terminal.writer().println(text)
        ctx.terminal.writer().flush()
    }

    private fun fetchInputData(): List<String> {
        val responseSpec =
            restClient
                .get()
                .uri(dataUrl)
                .header(CONTENT_TYPE, TEXT_PLAIN_VALUE)
                .retrieve()
        val body = responseSpec.body<String>() ?: throw IllegalStateException("Missing response body")
        return body.trim().split("\n")
    }
}
