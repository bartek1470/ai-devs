package pl.bartek.aidevs.task0205

import io.github.oshai.kotlinlogging.KotlinLogging
import org.jline.terminal.Terminal
import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import org.springframework.ai.chat.client.ChatClient
import org.springframework.ai.chat.client.advisor.SimpleLoggerAdvisor
import org.springframework.ai.chat.model.ChatModel
import org.springframework.ai.chat.prompt.ChatOptions
import org.springframework.ai.ollama.OllamaChatModel
import org.springframework.ai.ollama.api.OllamaOptions
import org.springframework.ai.openai.OpenAiChatModel
import org.springframework.ai.openai.OpenAiChatOptions
import org.springframework.ai.openai.api.OpenAiApi
import org.springframework.beans.factory.annotation.Value
import org.springframework.core.io.UrlResource
import org.springframework.shell.command.annotation.Command
import org.springframework.web.client.RestClient
import pl.bartek.aidevs.AiModelVendor
import pl.bartek.aidevs.TaskId
import pl.bartek.aidevs.ansiFormattedSecondaryInfo
import pl.bartek.aidevs.ansiFormattedSecondaryInfoTitle
import pl.bartek.aidevs.courseapi.AiDevsApiClient
import pl.bartek.aidevs.print
import pl.bartek.aidevs.println
import pl.bartek.aidevs.transcript.FileToTranscribe
import pl.bartek.aidevs.transcript.TranscriptService
import pl.bartek.aidevs.vision.ImageFileToView
import pl.bartek.aidevs.vision.VisionService
import java.nio.file.Files
import java.nio.file.Paths

@Command(
    group = "task",
    command = ["task"],
)
class Task0205Command(
    private val terminal: Terminal,
    @Value("\${aidevs.api-key}") private val apiKey: String,
    @Value("\${aidevs.task.0205.data-url}") private val dataUrl: String,
    @Value("\${aidevs.task.0205.answer-url}") private val answerUrl: String,
    @Value("\${aidevs.task.0205.article-url}") private val articleUrl: String,
    private val aiDevsApiClient: AiDevsApiClient,
    private val restClient: RestClient,
    private val transcriptService: TranscriptService,
    private val visionService: VisionService,
) {

    @Command(
        command = ["0205"],
        description = "https://bravecourses.circle.so/c/lekcje-programu-ai3-806660/s02e05-multimodalnosc-w-praktyce",
    )
    fun run() {
        val questions = fetchInputData()
        terminal.println("Pytania:".ansiFormattedSecondaryInfoTitle())
        terminal.println(questions.entries.joinToString("\n").ansiFormattedSecondaryInfo())
        val article = Jsoup.connect(articleUrl).get()

        val figureElements = article.body().select("figure")
        for (figure in figureElements) {
            val imageUrl =
                figure.selectFirst("img")?.attr("abs:src") ?: throw IllegalStateException("Unable to find figure image")
            val imageResource = UrlResource(imageUrl)
            val imageDescription =
                visionService.describeImage(
                    ImageFileToView(imageResource),
                    taskId = TaskId.TASK_0205,
                    onPartialResponseReceived = { terminal.print(it) },
                )
            val caption = figure.selectFirst("figcaption")?.text() ?: ""
            figure.replaceWith(
                Element("p").text(
                    """
                    IMAGE ${imageResource.filename} DESCRIPTION: $imageDescription
                    IMAGE ${imageResource.filename} CAPTION: $caption
                    """.trimIndent(),
                ),
            )
        }

        val audioElements = article.body().select("audio")
        for (audio in audioElements) {
            val audioUrl =
                audio.selectFirst("source")?.attr("abs:src") ?: throw IllegalStateException("Unable to find audio source")
            val audioResource = UrlResource(audioUrl)
            val recording = transcriptService.transcribe(FileToTranscribe(audioResource), TaskId.TASK_0205)
            audio.replaceWith(
                Element("p").text(
                    """
                    AUDIO ${audioResource.filename} TRANSCRIPTION: ${recording.transcript}
                    """.trimIndent(),
                ),
            )
        }

        println(article)
    }

    private fun fetchInputData(): Map<String, String> {
        val body =
            restClient
                .get()
                .uri(dataUrl, apiKey)
                .retrieve()
                .body(String::class.java) ?: throw IllegalStateException("Missing body")

        return body
            .split("\n")
            .filter { it.isNotBlank() }
            .map { it.split("=") }
            .associate { it[0] to it[1] }
    }

    companion object {
        private val log = KotlinLogging.logger { }
    }
}
