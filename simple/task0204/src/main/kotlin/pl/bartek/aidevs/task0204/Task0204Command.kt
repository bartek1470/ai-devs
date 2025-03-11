package pl.bartek.aidevs.task0204

import io.github.oshai.kotlinlogging.KotlinLogging
import org.jetbrains.exposed.sql.transactions.transaction
import org.jline.terminal.Terminal
import org.springframework.ai.chat.messages.SystemMessage
import org.springframework.ai.chat.messages.UserMessage
import org.springframework.ai.model.Media
import org.springframework.core.io.FileSystemResource
import org.springframework.http.MediaType
import org.springframework.shell.command.annotation.Command
import org.springframework.web.client.RestClient
import org.springframework.web.util.UriComponentsBuilder
import pl.bartek.aidevs.ai.ChatService
import pl.bartek.aidevs.ai.transcript.TranscriptService
import pl.bartek.aidevs.ai.transcript.TranscriptionRequest
import pl.bartek.aidevs.ai.transcript.WhisperLanguage
import pl.bartek.aidevs.config.AiDevsProperties
import pl.bartek.aidevs.course.TaskId
import pl.bartek.aidevs.course.api.AiDevsAnswer
import pl.bartek.aidevs.course.api.AiDevsApiClient
import pl.bartek.aidevs.course.api.Task
import pl.bartek.aidevs.db.audio.AudioResourceTable
import pl.bartek.aidevs.db.resource.BaseResource
import pl.bartek.aidevs.db.resource.audio.AudioResource
import pl.bartek.aidevs.db.resource.calculateContentHash
import pl.bartek.aidevs.db.resource.image.ImageResource
import pl.bartek.aidevs.db.resource.image.ImageResourceTable
import pl.bartek.aidevs.db.resource.text.TextResource
import pl.bartek.aidevs.db.resource.text.TextResourceTable
import pl.bartek.aidevs.util.ansiFormattedAi
import pl.bartek.aidevs.util.ansiFormattedSecondaryInfo
import pl.bartek.aidevs.util.ansiFormattedSecondaryInfoTitle
import pl.bartek.aidevs.util.print
import pl.bartek.aidevs.util.println
import pl.bartek.aidevs.util.resizeToFitSquare
import pl.bartek.aidevs.util.unzip
import java.nio.file.Files
import java.nio.file.Path
import javax.imageio.ImageIO
import kotlin.io.path.extension
import kotlin.io.path.name
import kotlin.io.path.nameWithoutExtension

@Command(
    group = "task",
    command = ["task"],
)
class Task0204Command(
    private val terminal: Terminal,
    private val aiDevsProperties: AiDevsProperties,
    private val aiDevsApiClient: AiDevsApiClient,
    private val restClient: RestClient,
    private val chatService: ChatService,
    private val transcriptService: TranscriptService,
) {
    private val cacheDir = aiDevsProperties.cacheDir.resolve(TaskId.TASK_0204.cacheFolderName())

    private val prompt =
        """
        Answer if the content is about captured people (not including brigade), presence of people nearby (not including brigade) or technical hardware issues (not software).
        Response has to be one word and nothing else, without any XML or markdown formatting. The word should be:
        * `${NoteContent.HARDWARE}` - for hardware issues
        * `${NoteContent.PRESENCE}` - for people presence
        * `${NoteContent.CAPTURE}` - for captured people
        * `${NoteContent.NONE}` - for none of above
        """.trimIndent()

    init {
        Files.createDirectories(this.cacheDir)
    }

    @Command(
        command = ["0204"],
        description = "https://bravecourses.circle.so/c/lekcje-programu-ai3-806660/s02e04-polaczenie-wielu-formatow",
    )
    fun run() {
        val factoryDataPath = fetchInputData()

        val resources =
            Files
                .list(
                    factoryDataPath,
                ).filter { Files.isRegularFile(it) }
                .filter { it.nameWithoutExtension.endsWith(RESIZED_SUFFIX).not() }
                .toList()
                .mapNotNull { file: Path -> obtainResource(file) }

        val notes =
            resources.map { resource ->
                when (resource) {
                    is TextResource -> Note(resource.name, resource.content)
                    is AudioResource -> Note(resource.name, resource.transcription)
                    is ImageResource -> Note(resource.name, resource.description)
                    else -> throw UnsupportedOperationException("Resource type not supported for this task")
                }
            }

        val groupedNotes =
            notes
                .groupBy { note ->
                    terminal.print("AI response to ${note.name}: ".ansiFormattedAi())
                    val content =
                        chatService.sendToChat(
                            listOf(
                                SystemMessage(prompt),
                                UserMessage(note.content),
                            ),
                        ) { terminal.print(it) }
                    terminal.println()
                    content
                }.mapKeys { NoteContent.valueOf(it.key.trim().uppercase()) }
                .mapValues { it.value.map { note -> note.name } }

        val answer =
            NotesContent(
                people = (groupedNotes[NoteContent.PRESENCE] ?: listOf()) + (groupedNotes[NoteContent.CAPTURE] ?: listOf()),
                hardware = groupedNotes[NoteContent.HARDWARE] ?: listOf(),
            )

        terminal.println("Summary:".ansiFormattedSecondaryInfoTitle())
        terminal.println("People:\n\t${answer.people.joinToString("\n\t")}".ansiFormattedSecondaryInfo())
        terminal.println("Hardware:\n\t${answer.hardware.joinToString("\n\t")}".ansiFormattedSecondaryInfo())
        terminal.println()

        val aiDevsAnswer = aiDevsApiClient.sendAnswer(aiDevsProperties.reportUrl, AiDevsAnswer(Task.KATEGORIE, answer))
        terminal.println(aiDevsAnswer)
    }

    private fun obtainResource(filePath: Path): BaseResource? {
        terminal.println("Obtaining ${filePath.name}...".ansiFormattedSecondaryInfoTitle())
        return when (filePath.extension) {
            "txt" -> {
                val hash = filePath.calculateContentHash()
                transaction {
                    TextResource
                        .find { TextResourceTable.hash eq hash }
                        .singleOrNull()
                } ?: let {
                    terminal.println("Creating a new text resource".ansiFormattedSecondaryInfo())
                    val content = Files.readString(filePath)
                    transaction {
                        TextResource.new {
                            this.hash = hash
                            this.name = filePath.name
                            this.content = content
                        }
                    }
                }
            }
            "mp3" -> {
                val hash = filePath.calculateContentHash()
                transaction {
                    AudioResource
                        .find { AudioResourceTable.hash eq hash }
                        .singleOrNull()
                } ?: let {
                    terminal.println("Creating a new audio resource".ansiFormattedSecondaryInfo())
                    val transcription =
                        transcriptService.transcribe(TranscriptionRequest(filePath, language = WhisperLanguage.ENGLISH))
                    transaction {
                        AudioResource.new {
                            this.hash = hash
                            this.name = filePath.name
                            path = filePath
                            this.transcription = transcription
                        }
                    }
                }
            }
            "png" -> {
                val hash = filePath.calculateContentHash()
                transaction {
                    ImageResource
                        .find { ImageResourceTable.hash eq hash }
                        .singleOrNull()
                } ?: let {
                    terminal.println("Creating a new image resource".ansiFormattedSecondaryInfo())
                    val bufferedImage =
                        ImageIO
                            .read(filePath.toFile())
                            .resizeToFitSquare(100)
                    val resizedImagePath = filePath.parent.resolve("${filePath.nameWithoutExtension}_$RESIZED_SUFFIX.${filePath.extension}")
                    ImageIO.write(bufferedImage, filePath.extension, resizedImagePath.toFile())
                    val description =
                        chatService.sendToChatWithImageSupport(
                            messages =
                                listOf(
                                    UserMessage(
                                        "Describe the image. Include any text that is written in the image",
                                        Media(MediaType.IMAGE_PNG, FileSystemResource(resizedImagePath)),
                                    ),
                                ),
                            streaming = false,
                        )
                    transaction {
                        ImageResource.new {
                            this.hash = hash
                            this.name = filePath.name
                            path = resizedImagePath
                            originalPath = filePath
                            this.description = description
                        }
                    }
                }
            }

            else -> {
                log.info { "$filePath is not supported resource for this task" }
                null
            }
        }
    }

    private fun fetchInputData(): Path {
        val uriComponents =
            UriComponentsBuilder
                .fromUri(
                    aiDevsProperties.task.task0204.dataUrl
                        .toURI(),
                ).build()
        val filename = uriComponents.pathSegments[uriComponents.pathSegments.size - 1]!!
        val zipFilePath = this.cacheDir.resolve(filename)
        val extractedZipPath = this.cacheDir.resolve(zipFilePath.nameWithoutExtension)
        if (Files.exists(extractedZipPath)) {
            terminal.println(
                "Input data already exists: ${extractedZipPath.toAbsolutePath()}. Skipping download".ansiFormattedSecondaryInfo(),
            )
            return extractedZipPath
        }

        terminal.println("Downloading input data from ${uriComponents.toUriString()}...".ansiFormattedSecondaryInfo())
        val body =
            restClient
                .get()
                .uri(uriComponents.toUriString())
                .headers { it.contentType = MediaType.APPLICATION_OCTET_STREAM }
                .retrieve()
                .body(ByteArray::class.java)!!
        Files.newOutputStream(zipFilePath).use {
            it.write(body)
        }
        zipFilePath.unzip(extractedZipPath)
        Files.delete(zipFilePath)
        return extractedZipPath
    }

    companion object {
        private val log = KotlinLogging.logger { }
        private const val RESIZED_SUFFIX = "resized"
    }
}
