package pl.bartek.aidevs.task0203

import io.github.oshai.kotlinlogging.KotlinLogging
import org.jline.terminal.Terminal
import org.springframework.ai.image.ImagePrompt
import org.springframework.ai.openai.OpenAiImageModel
import org.springframework.ai.openai.OpenAiImageOptions
import org.springframework.ai.openai.api.OpenAiImageApi.ImageModel
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Service
import org.springframework.web.client.RestClient
import pl.bartek.aidevs.config.Profile.OPENAI
import pl.bartek.aidevs.courseapi.AiDevsAnswer
import pl.bartek.aidevs.courseapi.AiDevsApiClient
import pl.bartek.aidevs.courseapi.Task
import pl.bartek.aidevs.util.println

@Profile(OPENAI)
@Service
class Task0203Service(
    @Value("\${aidevs.api-key}") private val apiKey: String,
    @Value("\${aidevs.task.0203.data-url}") private val dataUrl: String,
    @Value("\${aidevs.task.0203.answer-url}") private val answerUrl: String,
    private val aiDevsApiClient: AiDevsApiClient,
    private val restClient: RestClient,
    private val openAiImageModel: OpenAiImageModel,
) {
    private val imageOptions =
        OpenAiImageOptions
            .builder()
            .withHeight(1024)
            .withWidth(1024)
            .withModel(ImageModel.DALL_E_3.value)
            .withResponseFormat("url")
            .build()

    fun run(terminal: Terminal) {
        val json = fetchInputData()
        terminal.println(json.description)

        val response = openAiImageModel.call(ImagePrompt(json.description, imageOptions))

        val url = response.result.output.url
        terminal.println(url)

        val answer = aiDevsApiClient.sendAnswer(answerUrl, AiDevsAnswer(Task.ROBOT_ID, url))
        terminal.println(answer)
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
