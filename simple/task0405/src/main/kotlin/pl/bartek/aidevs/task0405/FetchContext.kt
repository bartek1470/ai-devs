package pl.bartek.aidevs.task0405

import com.fasterxml.jackson.annotation.JsonClassDescription
import com.fasterxml.jackson.annotation.JsonPropertyDescription
import org.jline.terminal.Terminal
import org.springframework.ai.document.Document
import org.springframework.ai.tool.ToolCallback
import org.springframework.ai.tool.function.FunctionToolCallback
import org.springframework.ai.vectorstore.SearchRequest
import org.springframework.ai.vectorstore.VectorStore
import org.springframework.ai.vectorstore.filter.FilterExpressionBuilder
import pl.bartek.aidevs.ai.document.transformer.keywords
import pl.bartek.aidevs.ai.document.transformer.title
import pl.bartek.aidevs.util.ansiFormattedSecondaryInfo
import pl.bartek.aidevs.util.println

@JsonClassDescription("A request to search for a context for provided question")
data class FetchContextRequest(
    @JsonPropertyDescription("A question to fetch context for")
    val question: String,
)

class FetchContext(
    private val keywordsGenerator: (String) -> Set<String>,
    private val vectorStore: VectorStore,
    private val terminal: Terminal,
) : (FetchContextRequest) -> String {
    private val filterBuilder = FilterExpressionBuilder()

    override fun invoke(request: FetchContextRequest): String {
        val question = request.question
        val keywords = keywordsGenerator(question)
        val similarDocuments = fetchSimilarDocuments(question, keywords)

        val documentsContext =
            similarDocuments.joinToString("\n\n") { doc ->
                """
                |## Document "${doc.title()}"
                |${doc.text}
                |
                |Keywords: ${doc.keywords().joinToString(", ")}
                """.trimMargin()
            }
        return "# Context for question \"$question\"\n$documentsContext"
    }

    private fun fetchSimilarDocuments(
        question: String,
        questionKeywords: Set<String>,
    ): List<Document> {
        val similarDocuments =
            vectorStore.similaritySearch(
                SearchRequest
                    .builder()
                    .query(question)
                    .filterExpression(filterBuilder.`in`("keywords", questionKeywords).build())
                    .similarityThreshold(0.8)
                    .build(),
            ) ?: listOf()

        terminal.println("Found ${similarDocuments.size} similar documents.".ansiFormattedSecondaryInfo())
        return similarDocuments
    }

    companion object {
        fun createFunctionCallback(
            keywordsGenerator: (String) -> Set<String>,
            vectorStore: VectorStore,
            terminal: Terminal,
        ): ToolCallback {
            val tool = FetchContext(keywordsGenerator, vectorStore, terminal)
            return FunctionToolCallback
                .builder("fetchContext", tool)
                .description("Fetches context information about the given question")
                .inputType(FetchContextRequest::class.java)
                .build()
        }
    }
}
