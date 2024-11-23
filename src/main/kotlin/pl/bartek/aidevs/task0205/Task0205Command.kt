package pl.bartek.aidevs.task0205

import io.github.oshai.kotlinlogging.KotlinLogging
import org.jline.terminal.Terminal
import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import org.springframework.ai.chat.messages.SystemMessage
import org.springframework.ai.chat.messages.UserMessage
import org.springframework.beans.factory.annotation.Value
import org.springframework.core.io.UrlResource
import org.springframework.shell.command.annotation.Command
import org.springframework.web.client.RestClient
import pl.bartek.aidevs.TaskId
import pl.bartek.aidevs.ansiFormattedAi
import pl.bartek.aidevs.ansiFormattedSecondaryInfo
import pl.bartek.aidevs.ansiFormattedSecondaryInfoTitle
import pl.bartek.aidevs.courseapi.AiDevsAnswer
import pl.bartek.aidevs.courseapi.AiDevsApiClient
import pl.bartek.aidevs.courseapi.Task
import pl.bartek.aidevs.print
import pl.bartek.aidevs.println
import pl.bartek.aidevs.text.TextService
import pl.bartek.aidevs.transcript.FileToTranscribe
import pl.bartek.aidevs.transcript.TranscriptService
import pl.bartek.aidevs.transcript.WhisperLanguage.POLISH
import pl.bartek.aidevs.vision.ImageFileToView
import pl.bartek.aidevs.vision.VisionService

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
    private val textService: TextService,
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
                    |IMAGE ${imageResource.filename} DESCRIPTION:
                    |```
                    |${imageDescription.trim()}
                    |```
                    |IMAGE ${imageResource.filename} CAPTION: `${caption.trim()}`
                    """.trimMargin(),
                ),
            )
        }

        val audioElements = article.body().select("audio")
        for (audio in audioElements) {
            val audioUrl =
                audio.selectFirst("source")?.attr("abs:src") ?: throw IllegalStateException("Unable to find audio source")
            val audioResource = UrlResource(audioUrl)
            val recording = transcriptService.transcribe(FileToTranscribe(audioResource, language = POLISH), TaskId.TASK_0205)
            audio.replaceWith(
                Element("p").text(
                    """
                    |AUDIO ${audioResource.filename} TRANSCRIPTION:
                    |```
                    |${recording.transcript.trim()}
                    |```
                    """.trimMargin(),
                ),
            )
        }

        article.selectFirst(".chicago-bibliography")?.previousElementSibling()?.remove()
        article.selectFirst(".chicago-bibliography")?.remove()
        val articleText =
            article
                .wholeText()
                .trim()
                .split("\n")
                .joinToString("\n") { it.trim() }

        terminal.println("Article:".ansiFormattedSecondaryInfoTitle())
        terminal.println(articleText.ansiFormattedSecondaryInfo())

        val answers =
            questions.mapValues { entry ->
                terminal.print("AI response to ${entry.key}, ${entry.value}: ".ansiFormattedAi())
                val response =
                    textService.sendToChat(
                        listOf(
                            SystemMessage("Answer short and concisely to user's question about below article.\n$articleText"),
                            UserMessage(entry.value),
                        ),
                    ) { terminal.print(it) }
                terminal.println()
                response
            }

        val aiDevsAnswer = aiDevsApiClient.sendAnswer(answerUrl, AiDevsAnswer(Task.ARXIV, answers))
        terminal.println(aiDevsAnswer)
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