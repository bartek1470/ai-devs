package pl.bartek.aidevs.task0405

import io.github.oshai.kotlinlogging.KotlinLogging
import org.apache.pdfbox.Loader
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject
import org.jline.terminal.Terminal
import org.springframework.ai.chat.messages.UserMessage
import org.springframework.ai.document.Document
import org.springframework.ai.model.Media
import org.springframework.ai.reader.ExtractedTextFormatter
import org.springframework.ai.reader.pdf.PagePdfDocumentReader
import org.springframework.ai.reader.pdf.config.PdfDocumentReaderConfig
import org.springframework.beans.factory.annotation.Value
import org.springframework.core.ParameterizedTypeReference
import org.springframework.core.io.FileSystemResource
import org.springframework.http.MediaType
import org.springframework.http.MediaTypeFactory
import org.springframework.stereotype.Service
import org.springframework.web.client.RestClient
import pl.bartek.aidevs.ai.ChatService
import pl.bartek.aidevs.course.TaskId
import pl.bartek.aidevs.course.api.AiDevsApiClient
import pl.bartek.aidevs.util.downloadFile
import java.awt.Image
import java.awt.image.BufferedImage
import java.nio.file.Files
import java.nio.file.Path
import javax.imageio.ImageIO
import kotlin.io.path.Path
import kotlin.io.path.absolute
import kotlin.io.path.exists
import kotlin.io.path.extension
import kotlin.io.path.inputStream
import kotlin.io.path.isRegularFile
import kotlin.io.path.nameWithoutExtension

private const val IMAGE_FILE_SIZE_THRESHOLD = 1 * 1024 * 1024

@Service
class Task0405Service(
    @Value("\${aidevs.cache-dir}") cacheDir: String,
    @Value("\${aidevs.api-key}") private val apiKey: String,
    @Value("\${aidevs.task.0405.answer-url}") private val answerUrl: String,
    @Value("\${aidevs.task.0405.data-url}") private val dataUrl: String,
    @Value("\${aidevs.task.0405.questions-url}") private val questionsUrl: String,
    @Value("\${python.packages.path}") pythonPackagesPath: Path,
    private val restClient: RestClient,
    private val chatService: ChatService,
    private val aiDevsApiClient: AiDevsApiClient,
) {
    private val cacheDir = Path(cacheDir).resolve(TaskId.TASK_0405.cacheFolderName()).absolute()
    private val markitdownExecPath = pythonPackagesPath.resolve("markitdown").toAbsolutePath().toString()

    init {
        Files.createDirectories(this.cacheDir)
    }

    fun run(terminal: Terminal) {
        val questions = fetchQuestions()
        val pdf = restClient.downloadFile(dataUrl, apiKey, cacheDir)
//        val docs = getDocsFromPdf(pdf)
//        terminal.println(docs.toString())
        val imageDocuments = createDocumentsFromEmbeddedImages(pdf)
        println(imageDocuments)
    }

    fun getDocsFromPdf(pdf: Path): List<Document> {
        val pdfReader =
            PagePdfDocumentReader(
                FileSystemResource(pdf),
                PdfDocumentReaderConfig
                    .builder()
                    .withPageTopMargin(0)
                    .withPageExtractedTextFormatter(
                        ExtractedTextFormatter
                            .builder()
                            .withNumberOfTopTextLinesToDelete(0)
                            .build(),
                    ).withPagesPerDocument(1)
                    .build(),
            )

        return pdfReader.read()
    }

    fun createDocumentsFromEmbeddedImages(pdf: Path): List<Document> {
        val pdfResourcesDir = cacheDir.resolve(pdf.nameWithoutExtension)
        Files.createDirectories(pdfResourcesDir)

        Loader.loadPDF(pdf.toFile()).use { pdfDocument ->
            val imageResources =
                pdfDocument.pages
                    .mapIndexed { index, page ->
                        page.resources
                            .xObjectNames
                            .asSequence()
                            .map { name -> name to page.resources.getXObject(name) }
                            .filter { (_, xObject) -> xObject is PDImageXObject }
                            .map { it.first to (it.second as PDImageXObject) }
                            .map { (cosName, imageXObject) ->
                                val extension: String = imageXObject.suffix ?: "jpg"
                                val filePath = pdfResourcesDir.resolve("${cosName.name}.$extension")
                                ImageIO.write(imageXObject.image, extension, filePath.toFile())
                                filePath
                            }.map { ImageResource(index, it) }
                    }.flatMap { it }

            val uniqueResources =
                imageResources.flatMap { imageResource ->
                    val alreadyExists =
                        Files
                            .newDirectoryStream(pdfResourcesDir) {
                                val isSmallImage = it.nameWithoutExtension.endsWith(SMALL_IMAGE_SUFFIX)
                                val isNotItself = imageResource.image != it
                                it.isRegularFile() &&
                                    isNotItself &&
                                    isSmallImage
                            }.any { Files.mismatch(it, imageResource.image) == -1L }
                    if (alreadyExists) {
                        Files.delete(imageResource.image)
                        listOf()
                    } else {
                        listOf(imageResource)
                    }
                }

            uniqueResources
                .filter { Files.size(it.image) >= IMAGE_FILE_SIZE_THRESHOLD }
                .forEach {
                    val originalBufferedImage = it.image.inputStream().use(ImageIO::read)
                    val smallImagePath = it.image.resolveSibling(it.smallImage())
                    val smallWidth = originalBufferedImage.width / 2
                    val smallHeight = originalBufferedImage.height / 2
                    val smallImage = originalBufferedImage.getScaledInstance(smallWidth, smallHeight, Image.SCALE_SMOOTH)
                    val smallBufferedImage = BufferedImage(smallWidth, smallHeight, BufferedImage.TYPE_INT_RGB)
                    smallBufferedImage.graphics.drawImage(smallImage, 0, 0, null)
                    ImageIO.write(smallBufferedImage, it.image.extension, smallImagePath.toFile())
                }

            uniqueResources.map { imageResource ->
                val imageDescriptionPath =
                    imageResource.image.resolveSibling("${imageResource.image.fileName}.txt")
                val resource =
                    if (imageResource.smallImage().exists()) {
                        FileSystemResource(imageResource.smallImage())
                    } else {
                        FileSystemResource(imageResource.image)
                    }
                val media =
                    mutableListOf<Media>()
                        .apply {
                            val mediaType =
                                MediaTypeFactory
                                    .getMediaType(resource)
                                    .orElseGet { MediaType.IMAGE_JPEG }
                            add(Media(mediaType, resource))
                        }.toList()

                val description: String =
                    if (Files.exists(imageDescriptionPath)) {
                        Files.readString(imageDescriptionPath)
                    } else {
                        val content =
                            chatService.sendToChat(
                                listOf(
                                    UserMessage(
                                        "Describe the image and include text that is in this image",
                                        media,
                                    ),
                                ),
                            )
                        Files.write(imageDescriptionPath, content.toByteArray(Charsets.UTF_8))
                        content
                    }

                val metadata =
                    mutableMapOf<String, Any>()
                        .apply {
                            put(PagePdfDocumentReader.METADATA_START_PAGE_NUMBER, imageResource.pageNumber)
                            put(PagePdfDocumentReader.METADATA_FILE_NAME, resource.filename)
                        }.toMap()
                Document(description, media, metadata)
            }
        }
        return listOf()
    }

    private fun fetchQuestions(): Map<String, String> =
        restClient
            .get()
            .uri(questionsUrl, apiKey)
            .retrieve()
            .body(object : ParameterizedTypeReference<Map<String, String>>() {}) ?: throw IllegalStateException("Missing body")

    companion object {
        private val log = KotlinLogging.logger { }
    }
}
