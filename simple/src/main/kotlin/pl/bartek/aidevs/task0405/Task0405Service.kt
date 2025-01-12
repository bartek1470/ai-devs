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
import org.jetbrains.exposed.sql.transactions.transaction
import org.jline.terminal.Terminal
import org.springframework.ai.chat.messages.SystemMessage
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
import org.springframework.util.SimpleIdGenerator
import org.springframework.web.client.RestClient
import pl.bartek.aidevs.ai.ChatService
import pl.bartek.aidevs.ai.document.transformer.DetailedPolishKeywordMetadataEnricher
import pl.bartek.aidevs.ai.document.transformer.TextCleanupTransformer
import pl.bartek.aidevs.ai.document.transformer.TitleEnricher
import pl.bartek.aidevs.ai.document.transformer.hasKeywords
import pl.bartek.aidevs.ai.document.transformer.keywords
import pl.bartek.aidevs.course.TaskId
import pl.bartek.aidevs.course.api.AiDevsApiClient
import pl.bartek.aidevs.db.keywords.Keywords
import pl.bartek.aidevs.db.keywords.KeywordsTable
import pl.bartek.aidevs.db.pdf.PdfFile
import pl.bartek.aidevs.db.pdf.PdfFileTable
import pl.bartek.aidevs.db.pdf.PdfImageResource
import pl.bartek.aidevs.db.pdf.PdfTextResource
import pl.bartek.aidevs.db.pdf.PdfTextResourceTable
import pl.bartek.aidevs.util.ansiFormattedAi
import pl.bartek.aidevs.util.ansiFormattedSecondaryInfo
import pl.bartek.aidevs.util.ansiFormattedSecondaryInfoTitle
import pl.bartek.aidevs.util.downloadFile
import pl.bartek.aidevs.util.print
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
import kotlin.time.Duration.Companion.seconds
import kotlin.time.toJavaDuration

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
    @Value("\${aidevs.image-description.model}") private val imageDescriptionModel: String,
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
    private val idGenerator = SimpleIdGenerator()

    init {
        Files.createDirectories(this.cacheDir)
    }

    fun run(terminal: Terminal) {
        terminal.println("Downloading pdf file from $dataUrl".ansiFormattedSecondaryInfo())
        val pdfPath = restClient.downloadFile(dataUrl, apiKey, cacheDir)

        terminal.println("Obtaining PDF file from DB".ansiFormattedSecondaryInfo())
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

        terminal.println("Processing image resources from PDF".ansiFormattedSecondaryInfo())
        appendMissingImages(pdf)

        terminal.println("Processing text resources from PDF".ansiFormattedSecondaryInfo())
        appendMissingText(pdf)

        terminal.println("Fetching documents".ansiFormattedSecondaryInfo())
        val documents = fetchDocuments(pdf)

        terminal.println("Adding documents to vector store".ansiFormattedSecondaryInfo())
        addDocumentsIfEmptyVectorStoreCollection(documents)

        terminal.println("Fetching questions".ansiFormattedSecondaryInfo())
        val questions = fetchQuestions()
        terminal.println("Following questions will be asked".ansiFormattedSecondaryInfoTitle())
        terminal.println("\t${questions.entries.joinToString("\n\t")}".ansiFormattedSecondaryInfo())

        val aiAnswer =
            chatService.sendToChat(
                listOf(
                    // TODO [bartek1470] modify the prompt to utilize tools
                    SystemMessage(
                        """
                        |Answer the user's question based on the provided context. Utilize the diary notes or other documents given in the context to deduce the most probable and concise answer. The final response should be translated into Polish and output in a specified XML format.
                        |You have few attempts to provide a correct answer. If you failed previously to answer the question, it will appear in PREVIOUS TRIES section, along with hint to help you answer the question.
                        |NEVER use the same answer from PREVIOUS TRIES. It was incorrect and using it again is nonsense.
                        |
                        |# CONTEXT PROCESSING RULES:
                        |1. FIRSTLY ANALYZE all context and extract potential clues (including dates, events, hints).
                        |2. FIND OUT all possible dates for keywords or specific events found in text.
                        |3. IF THE INFORMATION IS AMBIGUOUS, deduce the most logical answer based on reasoning (e.g., decide between conflicting dates).
                        |4. IF THE CONTEXT IS MISSING RELEVANT DATA, infer a reasonable answer using general knowledge, provided it stays plausible and matches diary intent.
                        |5. ANALYZE what has just happened in context and OUTPUT the year of the event.
                        |
                        |# OUTPUT RULES:
                        |1. REASON through the answer explicitly before presenting the final result.
                        |2. ENCLOSE your final output inside the `<result>` XML tag.
                        |3. TRANSLATE the final answer into Polish with ACCURATE DIACRITICS and grammar.
                        |4. KEEP THE RESPONSE CONCISE and only include the requested information.
                        |5. WHEN NUMBER, DATE, OR AN EVENT is asked for, provide related logical facts from context to support your reasoning.
                        |
                        |# OUTPUT FORMAT STRUCTURE:
                        |- Reasoning explaining deduction.
                        |- Final response in XML:
                        |  <result>
                        |  [translated answer]
                        |  </result>
                        |
                        |# EXAMPLES
                        |Users asks about current year when diary mentions years 2001 and 2005. Reasoning: 2001 is referenced last and fits diary tone.
                        |<result>
                        |2001
                        |</result>
                        """.trimMargin(),
                    ),
                ),
                functions =
                    listOf(
                        AnswerQuestion.createFunctionCallback(questions, { generateKeywords(it) }, answerUrl, aiDevsApiClient, terminal),
                        FetchContext.createFunctionCallback({ generateKeywords(it) }, vectorStore, terminal),
                    ),
                responseReceived = { terminal.print(it) },
            )

        terminal.println(aiAnswer.ansiFormattedAi())
    }

    private fun generateKeywords(question: String): Set<String> =
        transaction {
            Keywords
                .find { KeywordsTable.id eq Keywords.calculateHash(question) }
                .singleOrNull()
                ?: createKeywords(question)
        }.keywords

    private fun createKeywords(question: String): Keywords {
        val generatedKeywords =
            detailedPolishKeywordMetadataEnricher
                .transform(listOf(Document(idGenerator.generateId().toString(), question, mapOf())))
                .flatMap { it.keywords() }
                .toSortedSet()

        return Keywords.new(Keywords.calculateHash(question)) {
            keywords = generatedKeywords
        }
    }

    /**
     * Converts metadata values to match types supported by [org.springframework.ai.vectorstore.qdrant.QdrantValueFactory.value]
     */
    private fun transformMetadataToQdrantSupportedTypes(documents: List<Document>): List<Document> =
        documents.map { doc ->
            val convertedMetadata =
                doc.metadata.mapValues { (_, value) ->
                    if (value is Set<*>) {
                        value.toTypedArray()
                    } else if (value is Path) {
                        value.toString()
                    } else {
                        value
                    }
                }
            doc
                .mutate()
                .metadata(convertedMetadata)
                .build()
        }

    private fun addDocumentsIfEmptyVectorStoreCollection(documents: List<Document>) {
        val documentCountInDb = qdrantClient.countAsync(collectionName, 5.seconds.toJavaDuration()).get()
        if (documentCountInDb != 0L) {
            log.info { "Vector store collection already has documents. There are $documentCountInDb documents. Skipping adding documents." }
            return
        }

        val qdrantDocs = transformMetadataToQdrantSupportedTypes(documents)
        vectorStore.add(qdrantDocs)
    }

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
                    val hash =
                        DigestUtils.md5Hex(
                            it.metadata[TextCleanupTransformer.METADATA_ORIGINAL_TEXT] as String? ?: it.text!!,
                        )
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

    private fun fetchDocuments(pdf: PdfFile): List<Document> {
        val imageDocuments: List<Document> =
            transaction {
                pdf.refresh()
                pdf.images.map { image ->
                    val keywords =
                        Keywords
                            .find {
                                KeywordsTable.id eq Keywords.calculateHash(image.description)
                            }.singleOrNull()
                            ?.keywords
                    Document(
                        image.id.value.toString(),
                        image.description,
                        buildMap {
                            put(PagePdfDocumentReader.METADATA_FILE_NAME, image.filePath)
                            put(PagePdfDocumentReader.METADATA_START_PAGE_NUMBER, image.pages)
                            put(TitleEnricher.METADATA_TITLE, image.name)
                            keywords?.run { put(DetailedPolishKeywordMetadataEnricher.METADATA_KEYWORDS, this) }
                        },
                    )
                }
            }.let { detailedPolishKeywordMetadataEnricher.transform(it) }

        val textDocuments: List<Document> =
            transaction {
                pdf.refresh()
                pdf.text.map { textResource ->
                    val keywords =
                        Keywords
                            .find {
                                KeywordsTable.id eq
                                    Keywords.calculateHash(
                                        textResource.content ?: textResource.originalContent,
                                    )
                            }.singleOrNull()
                            ?.keywords
                    Document(
                        textResource.id.value.toString(),
                        textResource.content ?: textResource.originalContent,
                        buildMap {
                            put(PagePdfDocumentReader.METADATA_FILE_NAME, pdf.filePath)
                            put(PagePdfDocumentReader.METADATA_START_PAGE_NUMBER, textResource.pages)
                            put(TitleEnricher.METADATA_TITLE, textResource.name)
                            if (textResource.content != null) {
                                put(TextCleanupTransformer.METADATA_ORIGINAL_TEXT, textResource.originalContent)
                            }
                            keywords?.run { put(DetailedPolishKeywordMetadataEnricher.METADATA_KEYWORDS, this) }
                        },
                    )
                }
            }.let { detailedPolishKeywordMetadataEnricher.transform(it) }

        val keywords =
            (textDocuments + imageDocuments)
                .filter { it.hasKeywords() }
                .map { doc -> Keywords.calculateHash(doc.text!!) to doc.keywords() }
        transaction {
            keywords.forEach { (hash, keywords) ->
                Keywords.new(hash) { this.keywords = keywords }
            }
        }
        return textDocuments + imageDocuments
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
                            .model(imageDescriptionModel)
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
            .body(object : ParameterizedTypeReference<Map<String, String>>() {})
            ?: throw IllegalStateException("Missing body")

    companion object {
        private val log = KotlinLogging.logger { }
    }
}
