package pl.bartek.aidevs.task0405

import com.fasterxml.jackson.core.JacksonException
import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.MapperFeature
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.dataformat.xml.XmlMapper
import com.fasterxml.jackson.module.kotlin.readValue
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import io.github.oshai.kotlinlogging.KotlinLogging
import io.qdrant.client.QdrantClient
import org.apache.commons.codec.digest.DigestUtils
import org.apache.pdfbox.Loader
import org.apache.pdfbox.pdmodel.PDPage
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject
import org.jetbrains.exposed.sql.transactions.transaction
import org.jline.terminal.Terminal
import org.springframework.ai.chat.messages.SystemMessage
import org.springframework.ai.chat.messages.UserMessage
import org.springframework.ai.document.Document
import org.springframework.ai.model.Media
import org.springframework.ai.model.function.FunctionCallingOptions
import org.springframework.ai.openai.api.OpenAiApi
import org.springframework.ai.reader.ExtractedTextFormatter
import org.springframework.ai.reader.pdf.PagePdfDocumentReader
import org.springframework.ai.reader.pdf.config.PdfDocumentReaderConfig
import org.springframework.ai.vectorstore.VectorStore
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Profile
import org.springframework.core.ParameterizedTypeReference
import org.springframework.core.io.FileSystemResource
import org.springframework.core.io.PathResource
import org.springframework.core.io.Resource
import org.springframework.http.MediaTypeFactory
import org.springframework.stereotype.Service
import org.springframework.web.client.RestClient
import pl.bartek.aidevs.ai.ChatService
import pl.bartek.aidevs.config.Profile.OPENAI
import pl.bartek.aidevs.config.Profile.QDRANT
import pl.bartek.aidevs.course.TaskId
import pl.bartek.aidevs.course.api.AiDevsApiClient
import pl.bartek.aidevs.task0405.db.PdfFile
import pl.bartek.aidevs.task0405.db.PdfFileTable
import pl.bartek.aidevs.task0405.db.PdfImageResource
import pl.bartek.aidevs.util.downloadFile
import pl.bartek.aidevs.util.resizeToFitSquare
import java.nio.file.Files
import java.nio.file.Path
import java.util.UUID
import javax.imageio.ImageIO
import kotlin.io.path.Path
import kotlin.io.path.absolute
import kotlin.io.path.exists
import kotlin.io.path.extension
import kotlin.io.path.nameWithoutExtension
import kotlin.io.path.notExists
import kotlin.io.path.relativeToOrSelf

private const val IMAGE_DIMENSIONS_THRESHOLD = 1024

@Profile(QDRANT, OPENAI)
@Service
class Task0405Service(
    @Value("\${aidevs.cache-dir}") cacheDir: String,
    @Value("\${aidevs.api-key}") private val apiKey: String,
    @Value("\${aidevs.task.0405.answer-url}") private val answerUrl: String,
    @Value("\${aidevs.task.0405.data-url}") private val dataUrl: String,
    @Value("\${aidevs.task.0405.questions-url}") private val questionsUrl: String,
    @Value("\${python.packages.path}") pythonPackagesPath: Path,
    @Value("\${spring.ai.vectorstore.qdrant.collection-name}") private val collectionName: String,
    objectMapper: ObjectMapper,
    private val restClient: RestClient,
    private val chatService: ChatService,
    private val aiDevsApiClient: AiDevsApiClient,
    private val vectorStore: VectorStore,
    private val qdrantClient: QdrantClient,
) {
    private val cacheDir = Path(cacheDir).resolve(TaskId.TASK_0405.cacheFolderName()).absolute()
    private val xmlMapper: ObjectMapper =
        XmlMapper
            .builder()
            .defaultUseWrapper(false)
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
            .configure(MapperFeature.ACCEPT_CASE_INSENSITIVE_PROPERTIES, true)
            .configure(SerializationFeature.INDENT_OUTPUT, true)
            .build()
            .registerKotlinModule()

    init {
        Files.createDirectories(this.cacheDir)
    }

    fun run(terminal: Terminal) {
        val pdfPath = restClient.downloadFile(dataUrl, apiKey, cacheDir)
        val pdf =
            transaction {
                PdfFile
                    .find {
                        PdfFileTable.filePath eq pdfPath
                    }.singleOrNull()
                    ?.also { log.info { "Found existing pdf file in db: ${it.id}" } }
                    ?: PdfFile
                        .new { filePath = pdfPath }
                        .also { log.info { "Created new pdf file in db: ${it.id}" } }
            }
        appendMissingImages(pdf)
        fetchDocuments(pdf)

//        prepareDocumentsInVectorStore(pdf)
//        val questions = fetchQuestions()
//        val pdfResourcesPath = pdf.resolveSibling(pdf.nameWithoutExtension)
//        val answers =
//            questions.mapValues { (key, question) ->
//                val results: List<Document> =
//                    vectorStore
//                        .similaritySearch(
//                            SearchRequest
//                                .builder()
//                                .query(question)
//                                .build(),
//                        )?.toList() ?: emptyList()
//
//                val pageNumbers =
//                    results
//                        .map { doc -> doc.metadata[PagePdfDocumentReader.METADATA_START_PAGE_NUMBER].toString() }
//                        .flatMap { it.split(",") }
//                        .map { it.toInt() }
//                        .toSortedSet()
//
//                val context: List<String> =
//                    pageNumbers.flatMap { page ->
//                        pdfStructure.resources
//                            .filter { it.pageNumbers.contains(page) }
//                            .map { pdfResource ->
//                                if (pdfResource.type.startsWith("image")) {
//                                    val imagePath = pdfResourcesPath.resolve("images").resolve(pdfResource.filename)
//                                    val imageDescription =
//                                        xmlMapper.readValue<ImageDescription>(imagePath.resolveSibling("${imagePath.fileName}.xml").toFile())
//                                    buildString {
//                                        append("Image description: ")
//                                        append(imageDescription.description)
//                                        imageDescription.text?.takeIf { it.isNotBlank() }?.also { append("\nText on the image: $it") }
//                                    }
//                                } else {
//                                    val textPath = pdfResourcesPath.resolve("text").resolve(pdfResource.filename)
//                                    Files.readString(textPath)
//                                }
//                            }
//                    }.distinct()
//
//                val context =
//                    pdfStructure.resources.mapNotNull { pdfResource ->
//                        if (pdfResource.type.startsWith("image")) {
//                            val imagePath = pdfResourcesPath.resolve("images").resolve(pdfResource.filename)
//                            val imageDescription =
//                                xmlMapper.readValue<ImageDescription>(
//                                    imagePath.resolveSibling("${imagePath.fileName}.xml").toFile(),
//                                )
//                            val imageText = imageDescription.text?.takeIf { it.isNotBlank() }
//                            if (imageText == null) {
//                                null
//                            } else {
//                                buildString {
//                                    append("Image description: ")
//                                    append(imageDescription.description)
//                                    append("\nText on the image: $imageText")
//                                }
//                            }
//                        } else {
//                            val textPath = pdfResourcesPath.resolve("text").resolve(pdfResource.filename)
//                            Files.readString(textPath)
//                        }
//                    }
//
//                val contextDocuments =
//                    context
//                        .mapIndexed { index, doc ->
//                            "# Context $index:\n${doc.trim()}"
//                        }.joinToString("\n\n")
//
//                terminal.println("Context:".ansiFormattedSecondaryInfoTitle())
//                terminal.println("$contextDocuments\n\n".ansiFormattedSecondaryInfo())
//
//                terminal.println("$key: $question".ansiFormattedHuman())
//                val aiAnswer =
//                    chatService.sendToChat(
//                        listOf(
//                            SystemMessage(
//                                """
//                                |Answer the user's question. Below you have user's notes providing context for the question.
//                                |Those notes are from user's notebook that is some kind of diary.
//                                |Answer shortly with only the data user has requested, e.g. if asking about a year, answer only with a year.
//                                |Provide an answer for the user's question in `result` XML tag.
//                                |The answer has to be translated to Polish language.
//                                |The context might don't have the information but you need to answer with the most possible one.
//                                |There might me some clues in the context like a number from a book.
//                                |When you have a date, think about the context you have found and see if it alerts the found date.
//                                |Also try to think about events mentioned in the text, e.g. when something was invented.
//                                |
//                                |Firstly, try to think about the most possible answer. Describe what you know from the context and facts above.
//                                |After that reason why you think this is most possible answer.
//                                |Lastly, provide an answer for user's question in a way specified above.
//                                |
//                                |# Example
//                                |Users asks about current year. Although year 2005 was mentioned, it is not clear if it is current year. More possible is year 2001 which is mentioned in the end of the note since it's a diary.
//                                |<result>
//                                |2001
//                                |</result>
//                                |
//                                |$contextDocuments
//                                """.trimMargin(),
//                            ),
//                            UserMessage(question),
//                        ),
//                        chatOptions =
//                            FunctionCallingOptions
//                                .builder()
//                                .temperature(0.3)
//                                .model(OpenAiApi.ChatModel.GPT_4_O.value)
//                                .build(),
//                        responseReceived = { terminal.print(it) },
//                    )
//                terminal.println()
//                aiAnswer.extractXmlRoot()?.trim()
//            }
//
//        val aiDevsAnswerResponse = aiDevsApiClient.sendAnswer(answerUrl, AiDevsAnswer(Task.NOTES, answers))
//        terminal.println(aiDevsAnswerResponse)
    }

//    private fun prepareDocumentsInVectorStore(pdf: Path) {
//        val documentCountInDb = qdrantClient.countAsync(collectionName, Duration.ofSeconds(5)).get()
//        if (documentCountInDb == 0L) {
//            val imageDocuments = addImages(pdf)
//            val textDocuments = createDocumentsFromText(pdf)
//            val allDocuments = textDocuments + imageDocuments
//
//            val pdfStructure =
//                allDocuments
//                    .map { doc ->
//                        val filename = doc.metadata[PagePdfDocumentReader.METADATA_FILE_NAME] as String
//                        val pages = doc.metadata[PagePdfDocumentReader.METADATA_START_PAGE_NUMBER].toString()
//                        val type = doc.metadata["type"]?.toString()
//                        PdfResource(filename, pages.split(",").map { it.toInt() }, type ?: "text")
//                    }.let {
//                        PdfStructure(it)
//                    }
// //            objectMapper.writeValue(pdf.resolveSibling(pdf.nameWithoutExtension).resolve("structure.json").toFile(), pdfStructure)
//
//            log.info { "Adding ${allDocuments.size} documents to vector store" }
//            vectorStore.add(allDocuments)
//        } else {
//            log.info { "Collection exists and is not empty. Skipping adding documents" }
//        }
//    }

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

        val documents: List<Document> = pdfReader.read()
        val pdfResourcesDir: Path = cacheDir.resolve(pdf.nameWithoutExtension).resolve("text")
        Files.createDirectories(pdfResourcesDir)
        val documentsWithCachePath = documents.map { pdfResourcesDir.resolve("${DigestUtils.md5Hex(it.text)}.txt") to it }
        documentsWithCachePath
            .filter { (cachePath, _) -> cachePath.notExists() }
            .forEach { (cachePath, doc) ->
                chatService.sendToChat(
                    listOf(
                        SystemMessage(
                            """
                            Clean provided user's text. Remove any extra spaces, new lines, etc.
                            Keep in mind that some of the words might have many whitespaces in between characters. You need to remove those to output a real Polish word.
                            There might be spelling mistakes, please correct them to match the context of the sentence.
                            Do not include any additional text or commentary.
                            """.trimIndent(),
                        ),
                        UserMessage(doc.text),
                    ),
                    chatOptions =
                        FunctionCallingOptions
                            .builder()
                            .temperature(0.0)
                            .model(OpenAiApi.ChatModel.GPT_4_O.value)
                            .build(),
                    cachePath = cachePath,
                )
            }

        return documentsWithCachePath.map { (cachePath, doc) ->
            doc
                .mutate()
                .metadata(PagePdfDocumentReader.METADATA_FILE_NAME, cachePath.fileName.toString())
                .metadata("original_text", doc.text!!)
                .text(Files.readString(cachePath))
                .build()
        }
    }

    fun appendMissingImages(pdf: PdfFile) {
        val pdfResourcesDir: Path = cacheDir.resolve(pdf.resourcesDir)
        Files.createDirectories(pdfResourcesDir)
        val imageResources = extractImages(pdf, pdfResourcesDir)
        // if an image file was deleted on a disk but wasn't in db, then it won't be processed
        val missingImageResources =
            transaction {
                pdf.refresh()
                imageResources.filter { extractedImage ->
                    pdf.images.none { dbImage ->
                        extractedImage.hash == dbImage.hash
                    }
                }
            }

        val describedImages = missingImageResources.map { extractedImage -> describeExtractedImage(extractedImage) }
        transaction {
            pdf.refresh()
            describedImages.forEach { describedImage ->
                val filePath =
                    describedImage.image.filePath
                        .absolute()
                        .relativeToOrSelf(cacheDir)
                        .toString()
                val filePathSmall =
                    describedImage.image.filePathSmall
                        ?.absolute()
                        ?.relativeToOrSelf(cacheDir)
                        ?.toString()
                PdfImageResource.new {
                    pdfFile = pdf
                    index = describedImage.image.indexes
                    pages = describedImage.image.pages
                    name = describedImage.image.name
                    extension = describedImage.image.extension
                    hash = describedImage.image.hash
                    this.filePath = filePath
                    this.filePathSmall = filePathSmall
                    this.description = describedImage.description
                    imageText = describedImage.text
                }
            }
        }
    }

    private fun describeExtractedImage(image: ExtractedPdfImage): DescribedExtractedImage {
        val resource = PathResource(image.filePathForDescribing)
        val (description, text) = generateImageDescription(resource)
        return DescribedExtractedImage(image, description.trim(), text?.trim())
    }

    private fun fetchDocuments(pdf: PdfFile): List<Document> {
        val imageResources =
            transaction {
                pdf.refresh()
                pdf.images
            }
        return emptyList()
//        imageResources.map {
//            Document()
//        }
//
//        resources.flatMap { imageResource ->
//            imageDescription
//                .let {
//                    val metadata =
//                        mutableMapOf<String, Any>()
//                            .apply {
//                                put("type", "image_description")
//                                put(
//                                    PagePdfDocumentReader.METADATA_START_PAGE_NUMBER,
//                                    imageResource.pageNumbers.joinToString(","),
//                                )
//                                put(PagePdfDocumentReader.METADATA_FILE_NAME, imageResource.image.fileName.toString())
//                            }.toMap()
//                    Document(it, metadata)
//                }.also { documents.add(it) }
//
//            imageText
//                ?.takeIf { it.isNotBlank() }
//                ?.let {
//                    val metadata =
//                        mutableMapOf<String, Any>()
//                            .apply {
//                                put("type", "image_text")
//                                put(
//                                    PagePdfDocumentReader.METADATA_START_PAGE_NUMBER,
//                                    imageResource.pageNumbers.joinToString(","),
//                                )
//                                put(PagePdfDocumentReader.METADATA_FILE_NAME, imageResource.image.fileName.toString())
//                            }.toMap()
//                    Document(it, metadata)
//                }?.also { documents.add(it) }
//
//            documents
//        }
    }

    private fun generateImageDescription(image: Resource): ImageDescription {
        log.info { "Generating image description for image $image" }
        val media =
            buildList {
                val mediaType =
                    MediaTypeFactory.getMediaType(image).orElseThrow {
                        throw UnsupportedOperationException("Unsupported media type for image $image")
                    }
                add(Media(mediaType, image))
            }

        val content =
            chatService.sendToChat(
                listOf(
                    UserMessage(
                        """
                        Create a XML with `description` and `text` tags inside `image` XML root element.
                        1. The `description` tag has to contain description what appears in the image. The description has to be translated to Polish language.
                        2. Next read the text in the image like you would be an OCR tool and include it in XML tag `text`. It's possible there would be no text in the image.
                            In `text` XML tag, include only text in the image and nothing else.
                        
                        DO NOT output anything else than the XML.
                        DO output only XML structure without any additional formatting, like markdown.
                        
                        Example:
                        <image>
                        <description>
                        To jest obraz notatki. Notatka posiada rysunek drzewa na marginesie, w prawym górnym rogu. Obok drzewa widnieje napis: "To było najstarsze drzewo w naszym mieście.". Tekst notatki: "Kiedy byłem dzieckiem, było tam duże, stare drzewo. Teraz zostało ono wycięte i powstaje tu blok."
                        <description>
                        <text>
                        To było najstarsze drzewo w naszym mieście.
                        Kiedy byłem dzieckiem, było tam duże, stare drzewo. Teraz zostało ono wycięte i powstaje tu blok.
                        </text>
                        </image>
                        """.trimIndent(),
                        media,
                    ),
                ),
                chatOptions =
                    FunctionCallingOptions
                        .builder()
                        .temperature(0.0)
                        .model(OpenAiApi.ChatModel.GPT_4_O.value)
                        .build(),
            )

        return try {
            xmlMapper.readValue<ImageDescription>(content)
        } catch (e: JacksonException) {
            log.error { "Cannot parse generated XML description of $image. The response:\n$content" }
            throw IllegalStateException("Cannot extract XML from AI response", e)
        }
    }

    private fun extractImages(
        pdf: PdfFile,
        pdfResourcesDir: Path,
    ): List<ExtractedPdfImage> {
        log.debug { "Extracting images from $pdf" }
        return Loader
            .loadPDF(pdf.filePath.toFile())
            .use { pdfDocument ->
                pdfDocument.pages
                    .mapIndexed { pageIndex, page ->
                        log.debug { "Processing page $pageIndex" }
                        extractImagesFromPage(page, pdfResourcesDir, pageIndex)
                    }.flatMap { it }
            }.groupBy { it.hash }
            .values
            .map { imagesWithSameHash ->
                imagesWithSameHash.reduce { acc, image ->
                    acc.copy(
                        indexes = acc.indexes + image.indexes,
                        pages = acc.pages + image.pages,
                        filePathSmall =
                            acc.filePathSmall ?: image.filePathSmall,
                    )
                }
            }.toList()
    }

    private fun extractImagesFromPage(
        page: PDPage,
        pdfResourcesDir: Path,
        pageIndex: Int,
    ) = page.resources
        .xObjectNames
        .asSequence()
        .map { name -> name to page.resources.getXObject(name) }
        .filter { (_, xObject) -> xObject is PDImageXObject }
        .map { (name, xObject) -> name to xObject as PDImageXObject }
        .mapIndexed { index, (name, imageXObject) ->
            val extension: String =
                imageXObject.suffix ?: throw IllegalStateException("Missing image extension for image $name on page $pageIndex")
            val filePath = pdfResourcesDir.resolve("${UUID.randomUUID()}.$extension")
            ImageIO.write(imageXObject.image, extension, filePath.toFile())

            val hash: String = Files.newInputStream(filePath).use { DigestUtils.md5Hex(it) }
            val fileHashNamePath = filePath.resolveSibling("$hash.$extension")
            if (fileHashNamePath.exists()) {
                log.debug { "File with hash $hash already exists. Deleting just extracted resource ${name.name} in path $filePath" }
                Files.delete(filePath)
                ExtractedPdfImage(
                    setOf(index),
                    setOf(pageIndex),
                    name.name,
                    extension,
                    hash,
                    fileHashNamePath,
                    null,
                )
            } else {
                Files.move(filePath, fileHashNamePath)
                val smallImagePath =
                    if (imageXObject.image.width > IMAGE_DIMENSIONS_THRESHOLD ||
                        imageXObject.image.height > IMAGE_DIMENSIONS_THRESHOLD
                    ) {
                        createSmallVersion(fileHashNamePath, imageXObject)
                    } else {
                        null
                    }
                ExtractedPdfImage(
                    setOf(index),
                    setOf(pageIndex),
                    name.name,
                    extension,
                    hash,
                    fileHashNamePath,
                    smallImagePath,
                )
            }
        }

    private fun createSmallVersion(
        fileHashNamePath: Path,
        imageXObject: PDImageXObject,
    ): Path {
        val smallImagePath =
            fileHashNamePath.resolveSibling(
                "${fileHashNamePath.nameWithoutExtension}$SMALL_IMAGE_SUFFIX.${fileHashNamePath.extension}",
            )
        val resizedImage = imageXObject.image.resizeToFitSquare(IMAGE_DIMENSIONS_THRESHOLD)
        ImageIO.write(resizedImage, fileHashNamePath.extension, smallImagePath.toFile())
        return smallImagePath
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
