package pl.bartek.aidevs.task0204

import io.github.oshai.kotlinlogging.KotlinLogging
import org.jline.terminal.Terminal
import org.springframework.ai.chat.client.ChatClient
import org.springframework.ai.chat.client.advisor.SimpleLoggerAdvisor
import org.springframework.ai.chat.messages.UserMessage
import org.springframework.ai.chat.model.ChatModel
import org.springframework.ai.chat.prompt.ChatOptions
import org.springframework.ai.model.Media
import org.springframework.ai.ollama.OllamaChatModel
import org.springframework.ai.ollama.api.OllamaOptions
import org.springframework.ai.openai.OpenAiChatModel
import org.springframework.ai.openai.OpenAiChatOptions
import org.springframework.ai.openai.api.OpenAiApi
import org.springframework.beans.factory.annotation.Value
import org.springframework.core.io.FileSystemResource
import org.springframework.core.io.Resource
import org.springframework.http.MediaType
import org.springframework.shell.command.annotation.Command
import org.springframework.web.client.RestClient
import org.springframework.web.util.UriComponentsBuilder
import pl.bartek.aidevs.AiModelVendor
import pl.bartek.aidevs.TaskId
import pl.bartek.aidevs.ansiFormattedAi
import pl.bartek.aidevs.ansiFormattedSecondaryInfo
import pl.bartek.aidevs.ansiFormattedSecondaryInfoTitle
import pl.bartek.aidevs.courseapi.AiDevsAnswer
import pl.bartek.aidevs.courseapi.AiDevsApiClient
import pl.bartek.aidevs.courseapi.Task
import pl.bartek.aidevs.print
import pl.bartek.aidevs.println
import pl.bartek.aidevs.transcript.FileToTranscribe
import pl.bartek.aidevs.transcript.TranscriptService
import pl.bartek.aidevs.transcript.WhisperLanguage
import pl.bartek.aidevs.unzip
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.util.stream.Collectors
import java.util.stream.Stream
import kotlin.io.path.extension
import kotlin.io.path.nameWithoutExtension

@Command(
    group = "task",
    command = ["task"],
)
class Task0204Command(
    private val terminal: Terminal,
    @Value("\${aidevs.cache-dir}") cacheDir: String,
    @Value("\${aidevs.task.0204.data-url}") private val dataUrl: String,
    @Value("\${aidevs.task.0204.answer-url}") private val answerUrl: String,
    private val aiDevsApiClient: AiDevsApiClient,
    private val restClient: RestClient,
    openAiChatModel: OpenAiChatModel,
    ollamaChatModel: OllamaChatModel,
    aiModelVendor: AiModelVendor,
    private val transcriptService: TranscriptService,
) {
    private val cacheDir = Paths.get(cacheDir, TaskId.TASK_0204.cacheFolderName())

    private val chatModel: ChatModel = if (aiModelVendor.isOllamaPreferred()) ollamaChatModel else openAiChatModel
    private val chatClient =
        ChatClient
            .builder(chatModel)
            .defaultAdvisors(SimpleLoggerAdvisor())
            .build()

    private val textChatOptions: ChatOptions =
        if (aiModelVendor.isOllamaPreferred()) {
            OllamaOptions
                .builder()
                .withModel("llama3.2:3b")
                .build()
        } else {
            OpenAiChatOptions
                .builder()
                .withModel(OpenAiApi.ChatModel.GPT_4_O)
                .build()
        }

    private val imageChatOptions: ChatOptions =
        if (aiModelVendor.isOllamaPreferred()) {
            OllamaOptions
                .builder()
                .withModel("llava:7b")
                .build()
        } else {
            textChatOptions
        }

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
//        val filesToProcess = setOf(
//            "2024-11-12_report-10-sektor-C1.mp3",
//            "2024-11-12_report-11-sektor-C2.mp3",
//            "2024-11-12_report-12-sektor_A1.mp3",
//        )
        val preparedNotes =
            Files
                .list(factoryDataPath)
                .filter { Files.isRegularFile(it) }
                .flatMap { file: Path ->
                    prepareFiles(file)
                }.toList()
//                .filter { filesToProcess.contains(it.file.fileName.toString()) }
                .sortedBy { it.file.fileName }
                .toList()
        val notes =
            preparedNotes
                .groupBy { note ->
                    terminal.print("AI response to ${note.file.fileName}: ".ansiFormattedAi())
                    val content =
                        if (note.mimeType == MediaType.IMAGE_PNG) {
                            processImage(FileSystemResource(note.resourcePathToProcess))
                        } else {
                            processText(FileSystemResource(note.resourcePathToProcess))
                        }
                    terminal.println()
                    content
                }.mapKeys { NoteContent.valueOf(it.key.trim().uppercase()) }
                .mapValues { it.value.map { note -> note.file.fileName.toString() } }

        val answer =
            NotesContent(
                people = (notes[NoteContent.PRESENCE] ?: listOf()) + (notes[NoteContent.CAPTURE] ?: listOf()),
                hardware = notes[NoteContent.HARDWARE] ?: listOf(),
            )

        terminal.println("Summary:".ansiFormattedSecondaryInfoTitle())
        terminal.println("People:\n\t${answer.people.joinToString("\n\t")}".ansiFormattedSecondaryInfo())
        terminal.println("Hardware:\n\t${answer.hardware.joinToString("\n\t")}".ansiFormattedSecondaryInfo())
        terminal.println()

        val aiDevsAnswer = aiDevsApiClient.sendAnswer(answerUrl, AiDevsAnswer(Task.KATEGORIE, answer))
        terminal.println(aiDevsAnswer)
    }

    private fun processImage(imageResource: Resource): String =
        chatClient
            .prompt()
            .options(imageChatOptions)
            .messages(
                UserMessage(
                    "",
                    Media(MediaType.IMAGE_PNG, imageResource),
                ),
            ).system(prompt)
            .stream()
            .content()
            .doOnNext { terminal.print(it) }
            .collect(Collectors.joining(""))
            .block() ?: throw IllegalStateException("Cannot get chat response")

    private fun processText(textResource: Resource): String =
        chatClient
            .prompt()
            .options(textChatOptions)
            .system(prompt)
            .messages(UserMessage(textResource.getContentAsString(StandardCharsets.UTF_8)))
            .stream()
            .content()
            .doOnNext { terminal.print(it) }
            .collect(Collectors.joining(""))
            .block() ?: throw IllegalStateException("Cannot get chat response")

    private fun prepareFiles(file: Path): Stream<Note> =
        when (file.extension) {
            "txt" -> Stream.of(Note(file, file, MediaType.TEXT_PLAIN))
            "mp3" -> {
                val recording =
                    transcriptService.transcribe(
                        FileToTranscribe(FileSystemResource(file), language = WhisperLanguage.ENGLISH),
                        TaskId.TASK_0204,
                    )
                Stream.of(Note(file, recording.transcriptPath, MediaType.TEXT_PLAIN))
            }
            "png" -> Stream.of(Note(file, file, MediaType.IMAGE_PNG))
            else -> Stream.empty()
        }

    private fun fetchInputData(): Path {
        val uriComponents =
            UriComponentsBuilder
                .fromHttpUrl(dataUrl)
                .build()
        val filename = uriComponents.pathSegments[uriComponents.pathSegments.size - 1]!!
        val zipFilePath = this.cacheDir.resolve(filename)
        val extractedZipPath = this.cacheDir.resolve(zipFilePath.nameWithoutExtension)
        if (Files.exists(extractedZipPath)) {
            log.info { "Input data already exists: ${extractedZipPath.toAbsolutePath()}. Skipping" }
            return extractedZipPath
        }

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
    }
}
