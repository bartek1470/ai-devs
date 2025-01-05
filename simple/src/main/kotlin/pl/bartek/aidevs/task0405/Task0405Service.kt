package pl.bartek.aidevs.task0405

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.MapperFeature
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.dataformat.xml.XmlMapper
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import io.github.oshai.kotlinlogging.KotlinLogging
import io.qdrant.client.QdrantClient
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.apache.commons.codec.digest.DigestUtils
import org.apache.pdfbox.Loader
import org.apache.pdfbox.cos.COSName
import org.apache.pdfbox.pdmodel.PDDocument
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
import pl.bartek.aidevs.util.println
import pl.bartek.aidevs.util.resizeToFitSquare
import java.io.ByteArrayOutputStream
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.Executors
import javax.imageio.ImageIO
import kotlin.io.path.Path
import kotlin.io.path.absolute
import kotlin.io.path.exists
import kotlin.io.path.extension
import kotlin.io.path.nameWithoutExtension
import kotlin.io.path.notExists
import kotlin.io.path.relativeToOrSelf

private const val IMAGE_DIMENSIONS_THRESHOLD = 1024
private const val SMALL_IMAGE_SUFFIX = "_small"

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
        terminal.println("Downloading pdf file from $dataUrl")
        val pdfPath = restClient.downloadFile(dataUrl, apiKey, cacheDir)
        terminal.println("Obtaining PDF file from DB")
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
        terminal.println("Processing image resources from PDF")
        appendMissingImages(pdf)
        terminal.println("Creating documents")
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

        Executors.newFixedThreadPool(4).asCoroutineDispatcher().use { dispatcher ->

            val pdfImageList = runBlocking(dispatcher) { extractImages(pdf, pdfResourcesDir) }
            val missingImages =
                transaction {
                    pdf.refresh()
                    pdfImageList.filter { pdfImage ->
                        pdf.images.none { dbImage ->
                            pdfImage.extractedImage.hash == dbImage.hash
                        }
                    }
                }

            val newDescribedImages =
                runBlocking(dispatcher) {
                    missingImages.map { image -> describeImage(image) }.awaitAll()
                }
            transaction {
                pdf.refresh()
                newDescribedImages.forEach { describedImage ->
                    val filePath =
                        describedImage.dumpedImage.filePath
                            .absolute()
                            .relativeToOrSelf(cacheDir)
                            .toString()
                    val filePathSmall =
                        describedImage.dumpedImage.filePathSmall
                            ?.absolute()
                            ?.relativeToOrSelf(cacheDir)
                            ?.toString()
                    PdfImageResource.new {
                        pdfFile = pdf
                        pages = describedImage.dumpedImage.extractedImage.pages
                        name = describedImage.dumpedImage.extractedImage.name
                        extension = describedImage.dumpedImage.extractedImage.extension
                        hash = describedImage.dumpedImage.extractedImage.hash
                        this.filePath = filePath
                        this.filePathSmall = filePathSmall
                        this.description = describedImage.description
                    }
                }
            }
        }
    }

    private suspend fun describeImage(image: DumpedPdfImage): Deferred<DescribedImage> =
        coroutineScope {
            val resource = PathResource(image.filePathForDescribing)
            async {
                val description = generateImageDescription(resource).await()
                DescribedImage(image, description.trim())
            }
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

    private suspend fun generateImageDescription(image: Resource): Deferred<String> =
        coroutineScope {
            log.info { "Generating image description for image $image" }
            val media =
                buildList {
                    val mediaType =
                        MediaTypeFactory.getMediaType(image).orElseThrow {
                            throw UnsupportedOperationException("Unsupported media type for image $image")
                        }
                    add(Media(mediaType, image))
                }

            async(Dispatchers.IO) {
                chatService.sendToChat(
                    listOf(
                        UserMessage(
                            """
                            Describe what is in the image and read a text that might appear on the image, prioritizing the recognition of text written in the Polish language.
                            """.trimIndent(),
                            media,
                        ),
                    ),
                    chatOptions =
                        FunctionCallingOptions
                            .builder()
                            .temperature(0.0)
                            .model("moondream:1.8b")
                            .build(),
                )
            }
        }

    private fun extractImages(
        pdf: PdfFile,
        pdfResourcesDir: Path,
    ): List<DumpedPdfImage> {
        log.debug { "Extracting images from $pdf" }
        return runBlocking {
            Loader
                .loadPDF(pdf.filePath.toFile())
                .use { pdfDocument ->
                    pdfDocument.pages
                        .flatMapIndexed { pageIndex, page ->
                            log.debug { "Processing page $pageIndex" }
                            extractImagesWithHashes(page, pageIndex)
                        }.groupBy { it.hash }
                        .values
                        .map { imagesWithSameHash ->
                            val pages = imagesWithSameHash.flatMap { it.pages }.toSet()
                            imagesWithSameHash.first().copy(pages = pages)
                        }.map { extractedImage ->
                            dumpPdfImage(pdfDocument, extractedImage, pdfResourcesDir)
                        }
                }
        }
    }

    private suspend fun dumpPdfImage(
        pdfDocument: PDDocument,
        extractedImage: ExtractedPdfImage,
        pdfResourcesDir: Path,
    ): DumpedPdfImage {
        val imageXObject = (
            pdfDocument
                .getPage(extractedImage.pages.first())
                .resources
                .getXObject(COSName.getPDFName(extractedImage.name))
                .takeIf { it is PDImageXObject }
                ?.let { it as PDImageXObject }
                ?: throw IllegalStateException("${extractedImage.name} is not an image")
        )

        val filePath = pdfResourcesDir.resolve("${extractedImage.hash}.${extractedImage.extension}")
        if (filePath.notExists()) {
            withContext(Dispatchers.IO) {
                ImageIO.write(imageXObject.image, extractedImage.extension, filePath.toFile())
            }
        }

        val smallImagePath =
            if (imageXObject.image.width > IMAGE_DIMENSIONS_THRESHOLD ||
                imageXObject.image.height > IMAGE_DIMENSIONS_THRESHOLD
            ) {
                createSmallVersion(filePath, imageXObject)
            } else {
                null
            }
        return DumpedPdfImage(extractedImage, filePath, smallImagePath)
    }

    private fun extractImagesWithHashes(
        page: PDPage,
        pageIndex: Int,
    ): List<ExtractedPdfImage> =
        page.resources
            .xObjectNames
            .asSequence()
            .map { name -> name to page.resources.getXObject(name) }
            .filter { (_, xObject) -> xObject is PDImageXObject }
            .map { (name, xObject) -> name to xObject as PDImageXObject }
            .map { (name, imageXObject) ->
                val extension: String =
                    imageXObject.suffix
                        ?: throw IllegalStateException("Missing image extension for image $name on page $pageIndex")

                val hash =
                    ByteArrayOutputStream().use { outStream ->
                        ImageIO.write(imageXObject.image, extension, outStream)
                        DigestUtils.md5Hex(outStream.toByteArray())
                    }
                ExtractedPdfImage(
                    pages = setOf(pageIndex),
                    name = name.name,
                    extension = extension,
                    hash = hash,
                )
            }.toList()

    private suspend fun createSmallVersion(
        filePath: Path,
        imageXObject: PDImageXObject,
    ): Path {
        val smallImagePath =
            filePath.resolveSibling(
                "${filePath.nameWithoutExtension}$SMALL_IMAGE_SUFFIX.${filePath.extension}",
            )
        if (smallImagePath.exists()) {
            return smallImagePath
        }
        val resizedImage = imageXObject.image.resizeToFitSquare(IMAGE_DIMENSIONS_THRESHOLD)
        withContext(Dispatchers.IO) {
            ImageIO.write(resizedImage, filePath.extension, smallImagePath.toFile())
        }
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
