package pl.bartek.aidevs.task0405

import io.github.oshai.kotlinlogging.KotlinLogging
import io.qdrant.client.QdrantClient
import org.apache.pdfbox.Loader
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject
import org.jline.terminal.Terminal
import org.springframework.ai.chat.messages.UserMessage
import org.springframework.ai.document.Document
import org.springframework.ai.model.Media
import org.springframework.ai.reader.ExtractedTextFormatter
import org.springframework.ai.reader.pdf.PagePdfDocumentReader
import org.springframework.ai.reader.pdf.config.PdfDocumentReaderConfig
import org.springframework.ai.vectorstore.VectorStore
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
import java.time.Duration
import java.util.UUID
import javax.imageio.ImageIO
import kotlin.io.path.Path
import kotlin.io.path.absolute
import kotlin.io.path.absolutePathString
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
    @Value("\${spring.ai.vectorstore.qdrant.collection-name}") private val collectionName: String,
    private val restClient: RestClient,
    private val chatService: ChatService,
    private val aiDevsApiClient: AiDevsApiClient,
    private val vectorStore: VectorStore,
    private val qdrantClient: QdrantClient,
) {
    private val cacheDir = Path(cacheDir).resolve(TaskId.TASK_0405.cacheFolderName()).absolute()
    private val markitdownExecPath = pythonPackagesPath.resolve("markitdown").toAbsolutePath().toString()

    init {
        Files.createDirectories(this.cacheDir)
    }

    fun run(terminal: Terminal) {
        val questions = fetchQuestions()
        val pdf = restClient.downloadFile(dataUrl, apiKey, cacheDir)
        prepareDocumentsInVectorStore(pdf)
    }

    private fun prepareDocumentsInVectorStore(pdf: Path) {
        val documentCountInDb = qdrantClient.countAsync(collectionName, Duration.ofSeconds(5)).get()
        if (documentCountInDb == 0L) {
            val imageDocuments = createDocumentsFromEmbeddedImages(pdf)
            val textDocuments = createDocumentsFromText(pdf)
            val allDocuments = textDocuments + imageDocuments
            log.info { "Adding ${allDocuments.size} documents to vector store" }
            vectorStore.add(allDocuments)
        } else {
            log.info { "Collection exists and is not empty. Skipping adding documents" }
        }
    }

    fun createDocumentsFromText(pdf: Path): List<Document> {
        log.debug { "Reading text from $pdf" }
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
        log.debug { "Creating documents from embedded images in $pdf" }
        val pdfResourcesDir: Path = cacheDir.resolve(pdf.nameWithoutExtension)
        Files.createDirectories(pdfResourcesDir)
        val imageResources = extractImagesFromPdf(pdf, pdfResourcesDir)
        val uniqueResources = removeDuplicates(imageResources, pdfResourcesDir)
        reduceSizeIfNeeded(uniqueResources)
        return toDocuments(uniqueResources)
    }

    private fun toDocuments(uniqueResources: List<ImageResource>): List<Document> =
        uniqueResources.map { imageResource ->
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

            val descriptionPath: Path = generateImageDescription(imageResource, media)
            val description: String = Files.readString(descriptionPath)

            val metadata =
                mutableMapOf<String, Any>()
                    .apply {
                        put("type", "image_description")
                        put("page_number", imageResource.pageNumbers.joinToString(","))
                        put(PagePdfDocumentReader.METADATA_FILE_NAME, imageResource.image.fileName.toString())
                    }.toMap()
            Document(description, metadata)
        }

    private fun generateImageDescription(
        imageResource: ImageResource,
        media: List<Media>,
    ): Path {
        val imageDescriptionPath: Path =
            imageResource.image.resolveSibling("${imageResource.image.fileName}.txt")
        return if (Files.exists(imageDescriptionPath)) {
            log.info { "Image description exist. Image ${imageResource.image}" }
            imageDescriptionPath
        } else {
            log.info { "Generating image description for image ${imageResource.image}" }
            val content =
                chatService.sendToChat(
                    listOf(
                        UserMessage(
                            """
                            Describe the image. In the description include the text in the image.
                            
                            Example:
                            This is an image of a note.
                            The note has a drawing of a tree in the margin in right upper corner. Next to the tree there's a text: "This was the oldest tree in our town.".
                            The main content of a note has a text:
                            "When I was a child there was a big old tree. Now it has been cut and a block of flats is being built here."
                            """.trimIndent(),
                            media,
                        ),
                    ),
                )
            Files.write(imageDescriptionPath, content.toByteArray(Charsets.UTF_8))
        }
    }

    private fun reduceSizeIfNeeded(uniqueResources: List<ImageResource>) {
        uniqueResources
            .filter { Files.size(it.image) >= IMAGE_FILE_SIZE_THRESHOLD }
            .forEach {
                val smallImagePath = it.image.resolveSibling(it.smallImage())
                val originalBufferedImage = it.image.inputStream().use(ImageIO::read)
                val smallWidth = originalBufferedImage.width / 2
                val smallHeight = originalBufferedImage.height / 2
                val smallImage = originalBufferedImage.getScaledInstance(smallWidth, smallHeight, Image.SCALE_SMOOTH)
                val smallBufferedImage = BufferedImage(smallWidth, smallHeight, BufferedImage.TYPE_INT_RGB)
                smallBufferedImage.graphics.drawImage(smallImage, 0, 0, null)
                ImageIO.write(smallBufferedImage, it.image.extension, smallImagePath.toFile())
            }
    }

    private fun removeDuplicates(
        imageResources: List<ImageResource>,
        pdfResourcesDir: Path,
    ): List<ImageResource> {
        val uniqueByFileContent =
            imageResources.map { imageResource ->
                val sameImage = findSameImage(pdfResourcesDir, imageResource.image)
                if (sameImage != null) {
                    Files.delete(imageResource.image)
                    ImageResource(sameImage, imageResource.pageNumbers)
                } else {
                    imageResource
                }
            }

        val uniquePaths = uniqueByFileContent.map { it.image }.toSet()

        return uniquePaths.map { imagePath ->
            val allPageNumbers =
                uniqueByFileContent
                    .filter { imageResource -> imageResource.image == imagePath }
                    .map { it.pageNumbers }
                    .flatten()
                    .toSet()
            ImageResource(imagePath, allPageNumbers)
        }
    }

    private fun extractImagesFromPdf(
        pdf: Path,
        pdfResourcesDir: Path,
    ): List<ImageResource> {
        log.debug { "Extracting images from $pdf" }
        return Loader.loadPDF(pdf.toFile()).use { pdfDocument ->
            pdfDocument.pages
                .mapIndexed { index, page ->
                    log.debug { "Processing page $index" }
                    page.resources
                        .xObjectNames
                        .asSequence()
                        .map { name -> page.resources.getXObject(name) }
                        .filterIsInstance(PDImageXObject::class.java)
                        .map { imageXObject ->
                            val extension: String = imageXObject.suffix ?: "jpg"
                            val filePath = pdfResourcesDir.resolve("${UUID.randomUUID()}.$extension")
                            ImageIO.write(imageXObject.image, extension, filePath.toFile())
                            filePath
                        }.map { image ->
                            val sameImage = findSameImage(pdfResourcesDir, image)
                            if (sameImage != null) {
                                log.debug { "Image $image is duplicate of $sameImage. Deleting $image" }
                                Files.delete(image)
                                sameImage
                            } else {
                                image
                            }
                        }.map { ImageResource(it, setOf(index)) }
                }.flatMap { it }
        }
    }

    private fun findSameImage(
        pdfResourcesDir: Path,
        imagePath: Path,
    ): Path? =
        Files
            .newDirectoryStream(pdfResourcesDir) {
                val isNotSmallImage = !it.nameWithoutExtension.endsWith(SMALL_IMAGE_SUFFIX)
                val isNotItself = imagePath.absolutePathString() != it.absolutePathString()
                val isNotTextFile = it.extension != "txt"
                it.isRegularFile() &&
                    isNotItself &&
                    isNotSmallImage &&
                    isNotTextFile
            }.singleOrNull {
                val areEqual = Files.mismatch(it, imagePath) == -1L
                areEqual
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
