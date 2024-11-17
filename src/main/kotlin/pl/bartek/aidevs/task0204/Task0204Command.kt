package pl.bartek.aidevs.task0204

import io.github.oshai.kotlinlogging.KotlinLogging
import org.jline.terminal.Terminal
import org.springframework.ai.chat.client.ChatClient
import org.springframework.ai.chat.client.advisor.SimpleLoggerAdvisor
import org.springframework.ai.chat.messages.UserMessage
import org.springframework.ai.chat.model.ChatModel
import org.springframework.ai.chat.prompt.ChatOptions
import org.springframework.ai.chat.prompt.Prompt
import org.springframework.ai.model.Media
import org.springframework.ai.ollama.OllamaChatModel
import org.springframework.ai.ollama.api.OllamaOptions
import org.springframework.ai.openai.OpenAiChatModel
import org.springframework.ai.openai.OpenAiChatOptions
import org.springframework.ai.openai.api.OpenAiApi
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.ansi.AnsiColor.BRIGHT_MAGENTA
import org.springframework.boot.ansi.AnsiStyle.BOLD
import org.springframework.core.io.FileSystemResource
import org.springframework.core.io.Resource
import org.springframework.http.MediaType
import org.springframework.http.MediaType.IMAGE_PNG_VALUE
import org.springframework.http.MediaType.TEXT_PLAIN_VALUE
import org.springframework.shell.command.CommandContext
import org.springframework.shell.command.annotation.Command
import org.springframework.web.client.RestClient
import org.springframework.web.util.UriComponentsBuilder
import pl.bartek.aidevs.AiModelVendor
import pl.bartek.aidevs.ansiFormatted
import pl.bartek.aidevs.courseapi.AiDevsApiClient
import pl.bartek.aidevs.task0201.Recording
import pl.bartek.aidevs.unzip
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.util.stream.Collectors
import java.util.stream.Stream
import kotlin.io.path.extension
import kotlin.io.path.nameWithoutExtension

@Command(group = "task")
class Task0204Command(
    private val aiDevsApiClient: AiDevsApiClient,
    private val restClient: RestClient,
    openAiChatModel: OpenAiChatModel,
    ollamaChatModel: OllamaChatModel,
    aiModelVendor: AiModelVendor,
    @Value("\${aidevs.cache-dir}") cacheDir: String,
    @Value("\${aidevs.task.0204.data-url}") private val dataUrl: String,
    @Value("\${aidevs.task.0204.answer-url}") private val answerUrl: String,
) {
    private val cacheDir = Paths.get(cacheDir, "02_04")

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
                .withModel(OpenAiApi.ChatModel.GPT_4_O_MINI)
                .build()
        }

    private val audioChatOptions: ChatOptions = textChatOptions

    private val imageChatOptions: ChatOptions =
        if (aiModelVendor.isOllamaPreferred()) {
            OllamaOptions
                .builder()
                .withModel("llava:7b")
                .build()
        } else {
            textChatOptions
        }

    init {
        Files.createDirectories(this.cacheDir)
    }

    @Command(command = ["task0204"])
    fun run(ctx: CommandContext) {
        val factoryDataPath = fetchInputData()
        val resourcesToProcess =
            Files
                .list(factoryDataPath)
                .filter { Files.isRegularFile(it) }
//            .flatMap { file: Path ->
// //                prepareContent(file, factoryDataPath, ctx)
//            }
                .flatMap { file: Path ->
                    mapToResourceWithType(file)
                }.toList()
                .groupBy { it.first }
                .mapValues { it.value.map { file -> file.second } }

        val result =
            resourcesToProcess.entries.flatMap { entry ->
                when (entry.key) {
                    TEXT_PLAIN_VALUE ->
                        listOf()
//                        entry.value.map {
//                            ctx.terminal.writer().println(
//                                "AI response to ${it.filename}:".ansiFormatted(
//                                    style = BOLD,
//                                    color = BRIGHT_MAGENTA,
//                                ),
//                            )
//                            ctx.terminal.writer().flush()
//                            processText(it, ctx.terminal)
//                            ctx.terminal.writer().println()
//                            ctx.terminal.writer().println()
//                            ctx.terminal.writer().flush()
//                        }
                    "audio" ->
                        listOf()
//                        entry.value.map {
//                            ctx.terminal.writer().println(
//                                "AI response to ${it.filename}:".ansiFormatted(
//                                    style = BOLD,
//                                    color = BRIGHT_MAGENTA,
//                                ),
//                            )
//                            ctx.terminal.writer().flush()
//                            processAudio(it, ctx.terminal)
//                            ctx.terminal.writer().println()
//                            ctx.terminal.writer().println()
//                            ctx.terminal.writer().flush()
//                        }
                    IMAGE_PNG_VALUE ->
                        entry.value.map {
                            ctx.terminal.writer().println(
                                "AI response to ${it.filename}:".ansiFormatted(
                                    style = BOLD,
                                    color = BRIGHT_MAGENTA,
                                ),
                            )
                            ctx.terminal.writer().flush()
                            processImage(it, ctx.terminal)
                            ctx.terminal.writer().println()
                            ctx.terminal.writer().println()
                            ctx.terminal.writer().flush()
                        }
                    else -> throw UnsupportedOperationException("Invalid file type ${entry.key}")
                }
            }
    }

    private fun processImage(
        imageResource: Resource,
        terminal: Terminal,
    ): String =
        chatClient
            .prompt(
                Prompt(
                    listOf(
                        UserMessage(
                            """
                            Find any information that is referring to:
                            - captured people
                            - presence of people
                            - hardware issues
                            
                            Skip information about software. Focus only on those 3 things mentioned above.
                            Your response should be in format as below, where TYPE is one of: HARDWARE_ISSUES, PEOPLE_PRESENCE, CAPTURED_PEOPLE.
                            <TYPE>
                            """.trimIndent(),
                            Media(MediaType.IMAGE_JPEG, imageResource),
                        ),
                    ),
                    textChatOptions,
                ),
            ).stream()
            .content()
            .doOnNext {
                terminal.writer().print(it)
                terminal.flush()
            }.collect(Collectors.joining(""))
            .block() ?: throw IllegalStateException("Cannot get chat response")

    private fun processAudio(
        audioResource: Resource,
        terminal: Terminal,
    ): String =
        chatClient
            .prompt(
                Prompt(
                    listOf(
                        UserMessage(
                            """
                            Find any information that is referring to:
                            - captured people
                            - presence of people
                            - hardware issues
                            
                            Skip information about software. Focus only on those 3 things mentioned above.
                            Your response should be in format as below, where TYPE is one of: HARDWARE_ISSUES, PEOPLE_PRESENCE, CAPTURED_PEOPLE.
                            <TYPE>
                            """.trimIndent(),
                            Media(MediaType.APPLICATION_OCTET_STREAM, audioResource),
                        ),
                    ),
                    audioChatOptions,
                ),
            ).stream()
            .content()
            .doOnNext {
                terminal.writer().print(it)
                terminal.flush()
            }.collect(Collectors.joining(""))
            .block() ?: throw IllegalStateException("Cannot get chat response")

    private fun processText(
        textResource: Resource,
        terminal: Terminal,
    ): String =
        chatClient
            .prompt(
                Prompt(
                    listOf(
                        UserMessage(
                            """
                            Find any information that is referring to:
                            - captured people
                            - presence of people
                            - hardware issues
                            
                            Skip information about software. Focus only on those 3 things mentioned above.
                            Your response should be in format as below, where TYPE is one of: HARDWARE_ISSUES, PEOPLE_PRESENCE, CAPTURED_PEOPLE.
                            <TYPE>
                            """.trimIndent(),
                            Media(MediaType.TEXT_PLAIN, textResource),
                        ),
                    ),
                    textChatOptions,
                ),
            ).stream()
            .content()
            .doOnNext {
                terminal.writer().print(it)
                terminal.flush()
            }.collect(Collectors.joining(""))
            .block() ?: throw IllegalStateException("Cannot get chat response")

    private fun mapToResourceWithType(file: Path): Stream<Pair<String, Resource>> =
        when (file.extension) {
            "txt" -> Stream.of(Pair(TEXT_PLAIN_VALUE, FileSystemResource(file)))
            "mp3" -> Stream.of(Pair("audio", FileSystemResource(file)))
            "png" -> Stream.of(Pair(IMAGE_PNG_VALUE, FileSystemResource(file)))
            else -> Stream.empty()
        }

    private fun prepareContent(
        file: Path,
        factoryDataPath: Path,
        ctx: CommandContext,
    ): Stream<NoteContent> =
        when (file.extension) {
            "txt" -> Stream.of(NoteContent(file, Files.readString(file)))
            "mp3" -> {
                val transcriptPath = factoryDataPath.resolve("${file.nameWithoutExtension}.txt")
                val content =
                    if (Files.exists(transcriptPath)) {
                        Files.readString(file)
                    } else {
                        transcribe(cacheDir, file)[0].transcript
                    }!!
                Stream.of(NoteContent(file, content))
            }

            "png" -> Stream.of(NoteContent(file, describeImage(file, ctx.terminal)))
            else -> Stream.empty()
        }

    private fun describeImage(
        imagePath: Path,
        terminal: Terminal,
    ): String {
        terminal
            .writer()
            .println("AI response to $imagePath:".ansiFormatted(style = BOLD, color = BRIGHT_MAGENTA))
        terminal.writer().flush()
        return chatClient
            .prompt(
                Prompt(
                    listOf(
                        UserMessage(
                            """
                            Describe the image
                            """.trimIndent(),
                            Media(MediaType.IMAGE_JPEG, FileSystemResource(cacheDir.resolve(imagePath))),
                        ),
                    ),
                    imageChatOptions,
                ),
            ).stream()
            .content()
            .doOnNext {
                terminal.writer().print(it)
                terminal.flush()
            }.collect(Collectors.joining(""))
            .block() ?: throw IllegalStateException("Cannot get chat response")
    }

    private fun transcribe(
        outputPath: Path,
        vararg files: Path,
    ): List<Recording> =
        files.map { recordingPath ->
            val transcriptPath = outputPath.resolve("${recordingPath.nameWithoutExtension}.txt")

            if (Files.notExists(transcriptPath)) {
                createTranscript(transcriptPath, outputPath)
            }
            val transcript = Files.readString(transcriptPath)
            Recording(recordingPath, transcriptPath, transcript)
        }

    private fun createTranscript(
        file: Path,
        outputPath: Path,
    ) {
        // TODO [bartek1470] second option -> OpenAiAudioTranscriptionModel - via OpenAI API

        try {
            log.info { "Transcribing $file" }
            val process =
                ProcessBuilder(
                    "whisper",
                    "--task",
                    "transcribe",
                    "--model",
                    "medium",
                    "--language",
                    "Polish",
                    "--output_format",
                    "txt",
                    "--output_dir",
                    outputPath.toAbsolutePath().toString(),
                    file.toAbsolutePath().toString(),
                ).redirectOutput(ProcessBuilder.Redirect.INHERIT).redirectError(ProcessBuilder.Redirect.INHERIT).start()

            val exitCode = process.waitFor()

            if (exitCode == 0) {
                log.debug { "Successfully processed $file" }
            } else {
                log.error { "Error processing $file with exit code $exitCode" }
            }
        } catch (e: Exception) {
            log.error(e) { "Failed to execute whisper command for $file" }
            throw IllegalStateException("Failed to transcribe: $file", e)
        }
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
