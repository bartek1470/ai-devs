package pl.bartek.aidevs.task0201

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.MapperFeature
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.dataformat.xml.XmlMapper
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import io.github.oshai.kotlinlogging.KotlinLogging
import org.apache.commons.codec.digest.DigestUtils
import org.jetbrains.exposed.sql.transactions.transaction
import org.jline.terminal.Terminal
import org.springframework.ai.chat.messages.SystemMessage
import org.springframework.ai.chat.messages.UserMessage
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
import pl.bartek.aidevs.db.resource.audio.AudioResource
import pl.bartek.aidevs.util.ansiFormattedAi
import pl.bartek.aidevs.util.ansiFormattedError
import pl.bartek.aidevs.util.ansiFormattedSecondaryInfo
import pl.bartek.aidevs.util.ansiFormattedSecondaryInfoTitle
import pl.bartek.aidevs.util.print
import pl.bartek.aidevs.util.println
import pl.bartek.aidevs.util.removeExtraWhitespaces
import pl.bartek.aidevs.util.titleCase
import pl.bartek.aidevs.util.unzip
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.extension
import kotlin.io.path.nameWithoutExtension

@Command(
    group = "task",
    command = ["task"],
)
class Task0201Command(
    private val terminal: Terminal,
    private val aiDevsProperties: AiDevsProperties,
    private val task0201Config: Task0201Config,
    private val aiDevsApiClient: AiDevsApiClient,
    private val restClient: RestClient,
    private val chatService: ChatService,
    private val transcriptService: TranscriptService,
) {
    private val cacheDir = aiDevsProperties.cacheDir.resolve(TaskId.TASK_0201.cacheFolderName())

    init {
        Files.createDirectories(this.cacheDir)
    }

    @Command(
        command = ["0201"],
        description = "https://bravecourses.circle.so/c/lekcje-programu-ai3-806660/s02e01-audio-i-interfejs-glosowy",
    )
    fun run() {
        val recordingsDirectory = fetchInputData()
        val recordingPaths: List<Path> = Files.list(recordingsDirectory).filter { it.extension == "m4a" }.toList()
        val recordings = fetchAudioResources(recordingPaths)

        val systemPrompt =
            """
            You are given transcript of different people recordings that have something in common with professor Andrzej Maj.
            The transcripts are in Polish language.
            Each recording is preceded with a name of the person and is divided with a new line before next recording, like below:
            ```
            Igor:
            Myślę że tak jest.
            
            Patrycja:
            To nie było tak.
            ```
            
            You need to investigate those transcripts and detect a city, an university and a faculty where professor Maj was giving lectures.
            During investigation keep in mind that person named Rafal had the closest relations with profesor Andrzej Maj, so most likely information from his transcript is the most genuine.
            Transcripts can contain lies or misleading information about someone else which you need to ignore and focus only on information about profesor Andrzej Maj.
            
            After investigation, you need to think about a street name where the faculty of the university in the city is located.
            Keep in mind that transcripts contain only clues about the street name and not an actual street name.
            The name of the university or its faculty doesn't have to appear as a whole name, so you might concatenate some data to get the exact name of the faculty.
            The street name of the faculty of the university in the city is common knowledge you should already know without transcripts.
            Your output should have a form of below XML:
            <result>
                <city>the city</city>
                <university>the university</university>
                <faculty>the faculty</faculty>
                <streetName>the street name</streetName>
            </result>
            
            An example XML result:
            <result>
                <city>Tokio</city>
                <university>Politechnika Bartosza</university>
                <faculty>Wydział Biologiczny</faculty>
                <streetName>Wesoła</streetName>
            </result>
            
            The output can't contain anything other than XML. Skip any markdown formatting
            """.trimIndent()

        val recordingsWithName =
            recordings.map { audio ->
                """
                |${audio.path.nameWithoutExtension.titleCase()}:
                |${audio.transcription}
                """.trimMargin()
            }
        val userPrompt =
            """
            |${recordingsWithName.joinToString("\n\n")}
            """.trimMargin()

        terminal.println("System prompt:".ansiFormattedSecondaryInfoTitle())
        terminal.println(systemPrompt.ansiFormattedSecondaryInfo())
        terminal.println("User prompt:".ansiFormattedSecondaryInfoTitle())
        terminal.println(userPrompt.ansiFormattedSecondaryInfo())
        terminal.print("AI: ".ansiFormattedAi())
        val response =
            chatService.sendToChat(
                messages = listOf(SystemMessage(systemPrompt), UserMessage(userPrompt)),
            ) { terminal.print(it) }

        terminal.println()

        val aiResponse = extractAnswer(response)
        val aiDevsAnswerResponse = aiDevsApiClient.sendAnswer(aiDevsProperties.reportUrl, AiDevsAnswer(Task.MP3, aiResponse.streetName))
        terminal.println(aiDevsAnswerResponse)
    }

    private fun fetchAudioResources(recordingPaths: List<Path>): List<AudioResource> {
        val pathsWithHash =
            recordingPaths
                .map { Pair(it, DigestUtils.sha256Hex(Files.readAllBytes(it))) }

        val existingAudioResources =
            transaction {
                pathsWithHash.mapNotNull { (_, hash) ->
                    AudioResource
                        .find { AudioResourceTable.hash eq hash }
                        .firstOrNull()
                }
            }

        val newAudioResources =
            pathsWithHash
                .filter { existingAudioResources.none { it.path == it.path } }
                .map { (path, hash) ->
                    terminal.println("Transcribing $path".ansiFormattedSecondaryInfo())
                    val transcription = transcriptService.transcribe(TranscriptionRequest(path, language = WhisperLanguage.POLISH))
                    transaction {
                        AudioResource.new {
                            this.name = path.nameWithoutExtension.titleCase()
                            this.path = path
                            this.hash = hash
                            this.transcription = transcription.removeExtraWhitespaces().trim()
                        }
                    }
                }
        return existingAudioResources + newAudioResources
    }

    private fun extractAnswer(response: String): AiResponse {
        val responseStartIndex = response.indexOf("<result>")
        val responseEndTag = "</result>"
        val responseEndIndex = response.indexOf(responseEndTag)
        if (responseStartIndex < 0 || responseEndIndex < 0 || responseStartIndex < responseEndIndex) {
            terminal.println("Final AI answer not found".ansiFormattedError())
            throw IllegalStateException("Final AI answer not found")
        }
        val xml =
            response.substring(response.indexOf("<result>"), response.indexOf("</result>") + "</result>".length)
        val xmlMapper: ObjectMapper =
            XmlMapper
                .builder()
                .defaultUseWrapper(false)
                .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
                .configure(MapperFeature.ACCEPT_CASE_INSENSITIVE_PROPERTIES, true)
                .build()
                .registerKotlinModule()

        return xmlMapper.readValue(xml, AiResponse::class.java)
    }

    private fun fetchInputData(): Path {
        val uriComponents = UriComponentsBuilder.fromUri(task0201Config.dataUrl.toURI()).build()
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
    }
}
