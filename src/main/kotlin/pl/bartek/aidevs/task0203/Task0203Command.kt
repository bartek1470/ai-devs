package pl.bartek.aidevs.task0203

import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.ai.image.ImagePrompt
import org.springframework.ai.openai.OpenAiImageModel
import org.springframework.ai.openai.OpenAiImageOptions
import org.springframework.ai.openai.api.OpenAiImageApi.ImageModel
import org.springframework.beans.factory.annotation.Value
import org.springframework.shell.command.CommandContext
import org.springframework.shell.command.annotation.Command
import org.springframework.web.client.RestClient
import pl.bartek.aidevs.courseapi.AiDevsAnswer
import pl.bartek.aidevs.courseapi.AiDevsApiClient
import pl.bartek.aidevs.courseapi.Task

@Command(group = "task")
class Task0203Command(
    private val aiDevsApiClient: AiDevsApiClient,
    private val restClient: RestClient,
    private val openAiImageModel: OpenAiImageModel,
    @Value("\${aidevs.api-key}") private val apiKey: String,
    @Value("\${aidevs.task.0203.data-url}") private val dataUrl: String,
    @Value("\${aidevs.task.0203.answer-url}") private val answerUrl: String,
) {
    @Command(command = ["task0203"])
    fun run(ctx: CommandContext) {
        val json = fetchInputData()
        ctx.terminal.writer().println(json.description)
        ctx.terminal.writer().flush()

        val response = openAiImageModel.call(
            ImagePrompt(
                json.description,
                OpenAiImageOptions
                    .builder()
                    .withHeight(1024)
                    .withWidth(1024)
                    .withModel(ImageModel.DALL_E_3.value)
                    .withResponseFormat("url")
                    .build(),
            ),
        )

        val url = response.result.output.url
        ctx.terminal.writer().println(url)
        ctx.terminal.writer().flush()

        val answer = aiDevsApiClient.sendAnswer(answerUrl, AiDevsAnswer(Task.ROBOT_ID, url))
        answer.println(ctx.terminal)
    }

    private fun fetchInputData() =
        restClient
            .post()
            .uri(dataUrl, apiKey)
            .retrieve()
            .body(TaskData::class.java) ?: throw IllegalStateException("Cannot get input data")

    companion object {
        private val log = KotlinLogging.logger { }
    }
}
