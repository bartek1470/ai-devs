package pl.bartek.aidevs.task0405

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
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.transactions.transaction
import org.jline.terminal.Terminal
import org.springframework.ai.chat.messages.UserMessage
import org.springframework.ai.document.Document
import org.springframework.ai.model.Media
import org.springframework.ai.model.function.FunctionCallingOptions
import org.springframework.ai.reader.ExtractedTextFormatter
import org.springframework.ai.reader.pdf.PagePdfDocumentReader
import org.springframework.ai.reader.pdf.config.PdfDocumentReaderConfig
import org.springframework.ai.vectorstore.VectorStore
import org.springframework.beans.factory.annotation.Value
import org.springframework.core.ParameterizedTypeReference
import org.springframework.core.io.PathResource
import org.springframework.core.io.Resource
import org.springframework.http.MediaTypeFactory
import org.springframework.stereotype.Service
import org.springframework.web.client.RestClient
import pl.bartek.aidevs.ai.ChatService
import pl.bartek.aidevs.ai.document.transformer.DetailedPolishKeywordMetadataEnricher
import pl.bartek.aidevs.ai.document.transformer.TextCleanupTransformer
import pl.bartek.aidevs.ai.document.transformer.TitleEnricher
import pl.bartek.aidevs.course.TaskId
import pl.bartek.aidevs.course.api.AiDevsApiClient
import pl.bartek.aidevs.task0405.db.BasePdfResource
import pl.bartek.aidevs.task0405.db.PdfFile
import pl.bartek.aidevs.task0405.db.PdfFileTable
import pl.bartek.aidevs.task0405.db.PdfImageResource
import pl.bartek.aidevs.task0405.db.PdfImageResourceTable
import pl.bartek.aidevs.task0405.db.PdfTextResource
import pl.bartek.aidevs.task0405.db.PdfTextResourceTable
import pl.bartek.aidevs.util.downloadFile
import pl.bartek.aidevs.util.println
import pl.bartek.aidevs.util.resizeToFitSquare
import java.io.ByteArrayOutputStream
import java.nio.file.Files
import java.nio.file.Path
import java.util.UUID
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

// @Profile(QDRANT, OPENAI)
@Service
class Task0405Service(
    @Value("\${aidevs.cache-dir}") cacheDir: String,
    @Value("\${aidevs.api-key}") private val apiKey: String,
    @Value("\${aidevs.task.0405.answer-url}") private val answerUrl: String,
    @Value("\${aidevs.task.0405.data-url}") private val dataUrl: String,
    @Value("\${aidevs.task.0405.questions-url}") private val questionsUrl: String,
    @Value("\${spring.ai.vectorstore.qdrant.collection-name}") private val collectionName: String,
    private val restClient: RestClient,
    private val chatService: ChatService,
    private val aiDevsApiClient: AiDevsApiClient,
    private val vectorStore: VectorStore,
    private val qdrantClient: QdrantClient,
    private val detailedPolishKeywordMetadataEnricher: DetailedPolishKeywordMetadataEnricher,
    private val textCleanupTransformer: TextCleanupTransformer,
    private val titleEnricher: TitleEnricher,
) {
    private val cacheDir = Path(cacheDir).resolve(TaskId.TASK_0405.cacheFolderName()).absolute()

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
        terminal.println("Processing text resources from PDF")
        appendMissingText(pdf)
        terminal.println("Getting documents from DB")
        val documentsFromDb = fetchDocuments(pdf)
        terminal.println("Syncing keywords")
        val documents: List<Document> = detailedPolishKeywordMetadataEnricher.transform(documentsFromDb)
        syncKeywords(documents)

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

    private fun syncKeywords(documents: List<Document>) {
        transaction {
            val ids =
                documents
                    .filter { it.metadata[DetailedPolishKeywordMetadataEnricher.METADATA_KEYWORDS] != null }
                    .map { it.id }
                    .map { UUID.fromString(it) }

            val imageResources =
                PdfImageResource.find { (PdfImageResourceTable.id inList ids) and (PdfImageResourceTable.keywords eq null) }
            imageResources.forEach { resource ->
                val doc = findDocument(resource, documents)
                updateKeywordsInResource(resource, doc)
            }

            val textResources =
                PdfTextResource.find { (PdfTextResourceTable.id inList ids) and (PdfTextResourceTable.keywords eq null) }
            textResources.forEach { resource ->
                val doc = findDocument(resource, documents)
                updateKeywordsInResource(resource, doc)
            }
        }
    }

    private fun findDocument(
        resource: BasePdfResource,
        documents: List<Document>,
    ): Document =
        documents
            .single {
                it.id == resource.id.value.toString()
            }

    private fun updateKeywordsInResource(
        resource: BasePdfResource,
        document: Document,
    ) {
        document
            .metadata[DetailedPolishKeywordMetadataEnricher.METADATA_KEYWORDS]!!
            .takeIf { it is Set<*> }
            .let { it as Set<String> }
            .also { resource.keywords = it }
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

    fun appendMissingText(pdf: PdfFile) {
        log.debug { "Reading text from $pdf" }
        val pdfReader =
            PagePdfDocumentReader(
                PathResource(pdf.filePath),
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
        val textDocumentsFromPdf =
            documents
                .filter { it.text?.isNotBlank() ?: false }
                .also { documentsWithText -> warnIfSizeDiffers(documents, documentsWithText) }
        val missingDocuments =
            transaction {
                textDocumentsFromPdf.filter {
                    val hash: String = DigestUtils.md5Hex(it.text)
                    PdfTextResourceTable
                        .select(PdfTextResourceTable.id)
                        .where { PdfTextResourceTable.hash eq hash }
                        .count() == 0L
                }
            }
        val cleanDocuments: List<Pair<Document, String>> =
            textCleanupTransformer
                .transform(missingDocuments)
                .also { titleEnricher.transform(it) }
                .map {
                    val hash = DigestUtils.md5Hex(it.metadata[TextCleanupTransformer.METADATA_ORIGINAL_TEXT] as String? ?: it.text!!)
                    it to hash
                }
        transaction {
            pdf.refresh()
            cleanDocuments.forEach { (doc, hash) ->
                val name = doc.metadata[TitleEnricher.METADATA_TITLE] as String? ?: hash
                val pages = extractPages(doc)
                val (originalContent, content) =
                    doc.metadata[TextCleanupTransformer.METADATA_ORIGINAL_TEXT]
                        ?.let { it as String }
                        ?.let {
                            it to doc.text
                        } ?: (doc.text!! to null)
                PdfTextResource.new {
                    pdfFile = pdf
                    this.hash = hash
                    this.name = name
                    this.pages = pages
                    this.originalContent = originalContent
                    this.content = content
                }
            }
        }
    }

    private fun extractPages(doc: Document) =
        doc.metadata[PagePdfDocumentReader.METADATA_START_PAGE_NUMBER]
            ?.toString()
            ?.toInt()
            ?.let { startPage ->
                doc.metadata[PagePdfDocumentReader.METADATA_END_PAGE_NUMBER]
                    ?.toString()
                    ?.toInt()
                    ?.let { endPage -> (startPage..endPage).toSortedSet() }
                    ?: setOf(startPage.toString().toInt())
            } ?: setOf()

    private fun warnIfSizeDiffers(
        allDocuments: List<Document>,
        documentsWithText: List<Document>,
    ) {
        if (allDocuments.size != documentsWithText.size) {
            val pagesWithEmptyText =
                allDocuments
                    .asSequence()
                    .filter { doc -> doc.text == null }
                    .map { doc ->
                        val pages = extractPages(doc)
                        pages
                            .lastOrNull { it != pages.first() }
                            ?.let { "${pages.first()}-$it" } ?: pages.first().toString()
                    }.distinct()
                    .sorted()
                    .joinToString(", ")
            log.warn {
                "Some documents have no text. Skipped ${allDocuments.size - documentsWithText.size} documents. Pages with empty text: $pagesWithEmptyText"
            }
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

    private fun fetchDocuments(pdf: PdfFile): List<Document> =
        transaction {
            pdf.refresh()
            val imageDocuments =
                pdf.images.map {
                    Document(
                        it.id.value.toString(),
                        it.description,
                        buildMap {
                            put(PagePdfDocumentReader.METADATA_FILE_NAME, it.filePath)
                            put(PagePdfDocumentReader.METADATA_START_PAGE_NUMBER, it.pages)
                            if (it.keywords?.isNotEmpty() == true) {
                                put(DetailedPolishKeywordMetadataEnricher.METADATA_KEYWORDS, it.keywords)
                            }
                        },
                    )
                }
            val textDocuments =
                pdf.text.map {
                    Document(
                        it.id.value.toString(),
                        it.content ?: it.originalContent,
                        buildMap {
                            put(PagePdfDocumentReader.METADATA_FILE_NAME, pdf.filePath)
                            put(PagePdfDocumentReader.METADATA_START_PAGE_NUMBER, it.pages)
                            if (it.keywords?.isNotEmpty() == true) {
                                put(DetailedPolishKeywordMetadataEnricher.METADATA_KEYWORDS, it.keywords)
                            }
                        },
                    )
                }
            textDocuments + imageDocuments
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
                    streaming = false,
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
