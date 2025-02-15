package pl.bartek.aidevs.taskpoligon

import org.jline.terminal.Terminal
import org.jsoup.Jsoup
import org.springframework.http.HttpHeaders.CONTENT_TYPE
import org.springframework.http.MediaType.TEXT_PLAIN_VALUE
import org.springframework.shell.command.annotation.Command
import org.springframework.web.client.RestClient
import org.springframework.web.client.body
import pl.bartek.aidevs.config.AiDevsProperties
import pl.bartek.aidevs.course.api.AiDevsAnswer
import pl.bartek.aidevs.course.api.AiDevsApiClient
import pl.bartek.aidevs.course.api.Task
import pl.bartek.aidevs.util.println
import pl.bartek.aidevs.util.removeExtraWhitespaces

@Command(
    group = "task",
    command = ["task"],
)
class TaskPoligonCommand(
    private val terminal: Terminal,
    private val aiDevsProperties: AiDevsProperties,
    private val aiDevsApiClient: AiDevsApiClient,
    private val restClient: RestClient,
) {
    @Command(command = ["poligon"], description = "https://bravecourses.circle.so/c/prework-ai3/s00e01-generatywna-sztuczna-inteligencja")
    fun run() {
        val data = fetchInputData()
        val response = aiDevsApiClient.sendAnswerReceiveText(aiDevsProperties.task.poligon.answerUrl, AiDevsAnswer(Task.POLIGON, data))
        val text = Jsoup.parse(response).wholeText().removeExtraWhitespaces()
        terminal.println(text)
    }

    private fun fetchInputData(): List<String> {
        val responseSpec =
            restClient
                .get()
                .uri(
                    aiDevsProperties.task.poligon.dataUrl
                        .toURI(),
                ).header(CONTENT_TYPE, TEXT_PLAIN_VALUE)
                .retrieve()
        val body = responseSpec.body<String>() ?: throw IllegalStateException("Missing response body")
        return body.trim().split("\n")
    }
}
