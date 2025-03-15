package pl.bartek.aidevs.task0205

import org.apache.commons.codec.digest.DigestUtils
import org.jetbrains.exposed.sql.transactions.transaction
import org.jline.terminal.Terminal
import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import org.springframework.ai.chat.messages.SystemMessage
import org.springframework.ai.chat.messages.UserMessage
import org.springframework.ai.model.Media
import org.springframework.core.io.UrlResource
import org.springframework.http.MediaType
import org.springframework.shell.command.annotation.Command
import org.springframework.web.client.RestClient
import pl.bartek.aidevs.ai.ChatService
import pl.bartek.aidevs.ai.transcript.TranscriptService
import pl.bartek.aidevs.ai.transcript.TranscriptionRequest
import pl.bartek.aidevs.ai.transcript.WhisperLanguage.POLISH
import pl.bartek.aidevs.config.AiDevsProperties
import pl.bartek.aidevs.course.TaskId
import pl.bartek.aidevs.course.api.AiDevsAnswer
import pl.bartek.aidevs.course.api.AiDevsApiClient
import pl.bartek.aidevs.course.api.Task
import pl.bartek.aidevs.db.audio.AudioResourceTable
import pl.bartek.aidevs.db.resource.audio.AudioResource
import pl.bartek.aidevs.util.ansiFormattedAi
import pl.bartek.aidevs.util.ansiFormattedSecondaryInfo
import pl.bartek.aidevs.util.ansiFormattedSecondaryInfoTitle
import pl.bartek.aidevs.util.print
import pl.bartek.aidevs.util.println
import pl.bartek.aidevs.util.removeExtraWhitespaces
import java.nio.file.Files

@Command(
    group = "task",
    command = ["task"],
)
class Task0205Command(
    private val terminal: Terminal,
    private val aiDevsProperties: AiDevsProperties,
    private val task0205Config: Task0205Config,
    private val aiDevsApiClient: AiDevsApiClient,
    private val restClient: RestClient,
    private val chatService: ChatService,
    private val transcriptService: TranscriptService,
) {
    private val cachePath = aiDevsProperties.cacheDir.resolve(TaskId.TASK_0205.cacheFolderName())

    init {
        Files.createDirectories(cachePath)
    }

    @Command(
        command = ["0205"],
        description = "https://bravecourses.circle.so/c/lekcje-programu-ai3-806660/s02e05-multimodalnosc-w-praktyce",
    )
    fun run() {
        val questions = fetchInputData()
        terminal.println("Pytania:".ansiFormattedSecondaryInfoTitle())
        terminal.println(questions.entries.joinToString("\n").ansiFormattedSecondaryInfo())
        val article = Jsoup.connect(task0205Config.articleUrl.toString()).get()

        val figureElements = article.body().select("figure")
        for (figure in figureElements) {
            val imageUrl =
                figure.selectFirst("img")?.attr("abs:src") ?: throw IllegalStateException("Unable to find figure image")
            val imageResource = UrlResource(imageUrl)
            val imageDescription =
                chatService.sendToChatWithImageSupport(
                    listOf(UserMessage("Describe the image", Media(MediaType.IMAGE_PNG, imageResource))),
                ) { terminal.print(it) }
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
            val audioResource = fetchAudioResource(audioUrl)
            audio.replaceWith(
                Element("p").text(
                    """
                    |AUDIO ${audioResource.path.fileName} TRANSCRIPTION:
                    |```
                    |${audioResource.transcription}
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
                    chatService.sendToChat(
                        listOf(
                            SystemMessage("Answer short and concisely to user's question about below article.\n$articleText"),
                            UserMessage(entry.value),
                        ),
                    ) { terminal.print(it) }
                terminal.println()
                response
            }

        val aiDevsAnswer = aiDevsApiClient.sendAnswer(aiDevsProperties.reportUrl, AiDevsAnswer(Task.ARXIV, answers))
        terminal.println(aiDevsAnswer)
    }

    private fun fetchAudioResource(audioUrl: String): AudioResource {
        val audioUrlResource = UrlResource(audioUrl)
        val content = audioUrlResource.contentAsByteArray
        val hash = DigestUtils.sha256Hex(content)

        return transaction { AudioResource.find { AudioResourceTable.hash eq hash }.firstOrNull() }
            ?: run {
                val filename = audioUrlResource.filename ?: "$hash.mp3"
                val audioPath = Files.write(cachePath.resolve(filename), content)
                val transcription =
                    transcriptService
                        .transcribe(
                            TranscriptionRequest(audioPath, language = POLISH),
                        ).removeExtraWhitespaces()
                        .trim()

                transaction {
                    AudioResource.new {
                        this.hash = hash
                        path = audioPath
                        this.transcription = transcription
                    }
                }
            }
    }

    private fun fetchInputData(): Map<String, String> {
        val body =
            restClient
                .get()
                .uri(task0205Config.dataUrl.toString(), aiDevsProperties.apiKey)
                .retrieve()
                .body(String::class.java) ?: throw IllegalStateException("Missing body")

        return body
            .split("\n")
            .filter { it.isNotBlank() }
            .map { it.split("=") }
            .associate { it[0] to it[1] }
    }
}
