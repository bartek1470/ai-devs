package pl.bartek.aidevs.task0302

import io.github.oshai.kotlinlogging.KotlinLogging
import org.jline.terminal.Terminal
import org.springframework.ai.vectorstore.VectorStore
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean
import org.springframework.context.annotation.Profile
import org.springframework.http.MediaType
import org.springframework.shell.command.annotation.Command
import org.springframework.web.client.RestClient
import org.springframework.web.util.UriComponentsBuilder
import pl.bartek.aidevs.ai.ChatService
import pl.bartek.aidevs.course.TaskId
import pl.bartek.aidevs.courseapi.AiDevsApiClient
import pl.bartek.aidevs.util.println
import pl.bartek.aidevs.util.unzip
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.nameWithoutExtension

 @Profile("qdrant")
@Command(
    group = "task",
    command = ["task"],
)
class Task0302Command(
    private val terminal: Terminal,
    @Value("\${aidevs.cache-dir}") cacheDir: Path,
    @Value("\${aidevs.task.0302.data-url}") private val dataUrl: String,
    @Value("\${aidevs.task.0302.data-password}") private val dataPassword: CharArray,
    @Value("\${aidevs.task.0302.answer-url}") private val answerUrl: String,
    private val aiDevsApiClient: AiDevsApiClient,
    private val restClient: RestClient,
    private val chatService: ChatService,
) {
    private val cacheDir = cacheDir.resolve(TaskId.TASK_0302.cacheFolderName())

    init {
        Files.createDirectories(this.cacheDir)
    }

    @Command(
        command = ["0302"],
        description = "https://bravecourses.circle.so/c/lekcje-programu-ai3-806660/s03e02-wyszukiwanie-semantyczne",
    )
    fun run() {
        val inputDataPath = fetchInputData()
        terminal.println(inputDataPath.toString())
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

        if (Files.exists(extractedDataZipPath)) {
            log.info { "Input data already exists: ${extractedDataZipPath.toAbsolutePath()}. Skipping" }
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
        dataZipPath.unzip(extractedDataZipPath, dataPassword)
        Files.delete(dataZipPath)
        return extractedDataZipPath.resolve("do-not-share")
    }

    companion object {
        private val log = KotlinLogging.logger { }
    }
}
