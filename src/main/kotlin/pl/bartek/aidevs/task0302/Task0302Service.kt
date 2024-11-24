package pl.bartek.aidevs.task0302

import io.github.oshai.kotlinlogging.KotlinLogging
import io.qdrant.client.QdrantClient
import org.jline.terminal.Terminal
import org.springframework.ai.document.Document
import org.springframework.ai.vectorstore.SearchRequest
import org.springframework.ai.vectorstore.VectorStore
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Profile
import org.springframework.http.MediaType
import org.springframework.stereotype.Service
import org.springframework.web.client.RestClient
import org.springframework.web.util.UriComponentsBuilder
import pl.bartek.aidevs.ai.ChatService
import pl.bartek.aidevs.config.Profile.QDRANT
import pl.bartek.aidevs.course.TaskId
import pl.bartek.aidevs.courseapi.AiDevsAnswer
import pl.bartek.aidevs.courseapi.AiDevsApiClient
import pl.bartek.aidevs.courseapi.Task
import pl.bartek.aidevs.util.ReadFile
import pl.bartek.aidevs.util.println
import pl.bartek.aidevs.util.unzip
import java.nio.file.Files
import java.nio.file.Path
import java.time.Duration
import kotlin.io.path.listDirectoryEntries
import kotlin.io.path.nameWithoutExtension

private const val DATE_METADATA_KEY = "report_date"

@Profile(QDRANT)
@Service
class Task0302Service(
    @Value("\${aidevs.cache-dir}") cacheDir: Path,
    @Value("\${aidevs.task.0302.data-url}") private val dataUrl: String,
    @Value("\${aidevs.task.0302.data-password}") private val dataPassword: CharArray,
    @Value("\${aidevs.task.0302.answer-url}") private val answerUrl: String,
    @Value("\${spring.ai.vectorstore.qdrant.collection-name}") private val collectionName: String,
    private val aiDevsApiClient: AiDevsApiClient,
    private val restClient: RestClient,
    private val chatService: ChatService,
    private val vectorStore: VectorStore,
    private val qdrantClient: QdrantClient,
) {
    private val cacheDir = cacheDir.resolve(TaskId.TASK_0302.cacheFolderName())

    init {
        Files.createDirectories(this.cacheDir)
    }

    fun run(terminal: Terminal) {
        val documentCountInDb = qdrantClient.countAsync(collectionName, Duration.ofSeconds(5)).get()

        if (documentCountInDb == 0L) {
            val inputDataPath = fetchInputData()
            val documents =
                inputDataPath
                    .listDirectoryEntries("*.txt")
                    .map { ReadFile(it, Files.readString(it)) }
                    .map {
                        Document(it.content, mapOf(DATE_METADATA_KEY to it.name.replace("_", "-")))
                    }
            vectorStore.add(documents)
        }

        val result =
            vectorStore.similaritySearch(
                SearchRequest.query("W raporcie, z którego dnia znajduje się wzmianka o kradzieży prototypu broni?").withTopK(1),
            )

        terminal.println(result.toString())
        val date = result[0].metadata[DATE_METADATA_KEY].toString()

        val answer = aiDevsApiClient.sendAnswer(answerUrl, AiDevsAnswer(Task.WEKTORY, date))
        terminal.println(answer)
    }

    private fun fetchInputData(): Path {
        val uriComponents =
            UriComponentsBuilder
                .fromHttpUrl(dataUrl)
                .build()
        val filename = uriComponents.pathSegments[uriComponents.pathSegments.size - 1]!!
        val zipFilePath = this.cacheDir.resolve(filename)
        val extractedZipPath = this.cacheDir.resolve(zipFilePath.nameWithoutExtension)

        val dataDir = "weapons_tests"
        val dataZipPath = this.cacheDir.resolve(zipFilePath.nameWithoutExtension).resolve("$dataDir.zip")
        val extractedDataZipPath = this.cacheDir.resolve(zipFilePath.nameWithoutExtension).resolve(dataDir)
        val inputDataPath = extractedDataZipPath.resolve("do-not-share")

        if (Files.exists(inputDataPath)) {
            log.info { "Input data already exists: ${inputDataPath.toAbsolutePath()}. Skipping" }
            return inputDataPath
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
        dataZipPath.unzip(extractedDataZipPath, dataPassword)
        Files.delete(dataZipPath)
        return inputDataPath
    }

    companion object {
        private val log = KotlinLogging.logger { }
    }
}
