package pl.bartek.aidevs.ai.document.transformer

import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.ai.chat.messages.SystemMessage
import org.springframework.ai.chat.messages.UserMessage
import org.springframework.ai.chat.prompt.ChatOptions
import org.springframework.ai.document.Document
import org.springframework.ai.document.DocumentTransformer
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import pl.bartek.aidevs.ai.ChatService
import pl.bartek.aidevs.ai.document.transformer.TextCleanupTransformer.Companion.METADATA_ORIGINAL_TEXT

private const val SYSTEM_MESSAGE = """Clean the provided user's text in Polish. Follow the steps below strictly:
1. REMOVE unnecessary new lines and all extra spaces, including spaces between characters in individual words.
2. CORRECT any spelling mistakes to ensure valid Polish words matching the sentence context.
3. REPLACE words with the correct Polish spellings, using proper Polish diacritics (e.g., ą, ć, ę, etc.).
4. HANDLE mixed or nonstandard whitespaces such as non-breaking spaces, ensuring they are removed or normalized to a single space.
5. DO NOT add any commentary, explanations, or information outside the cleaned text.
6. DO NOT include any text about being not able to achieve your task.

The output must be a single corrected Polish sentence or text block, properly spaced and grammatically valid. Retain the context and meaning of the original text but ensure all corrections strictly conform to Polish language spelling and grammar.

Examples:

Input: "Dz isiaj   jest    pięk   ny    dzień "
Output: "Dzisiaj jest piękny dzień"

Input: "To   je st  zd an ie z   ła  ńcu   ch em literówek"
Output: "To jest zdanie z łańcuchem literówek"

Input: "Ko t   ma  spad zor nione pióra   ."
Output: "Kot ma spadzornione pióra."
"""

fun Document.originalText(): String =
    metadata[METADATA_ORIGINAL_TEXT]?.toString() ?: throw IllegalStateException("Invalid document. Missing original text")

@Component
class TextCleanupTransformer(
    @Value("\${aidevs.text-cleanup.model}") private val model: String,
    private val chatService: ChatService,
) : DocumentTransformer {
    override fun apply(documents: List<Document>): List<Document> = documents.map { transformIfNeeded(it) }

    private fun transformIfNeeded(doc: Document): Document {
        if (doc.metadata[METADATA_ORIGINAL_TEXT] != null) {
            log.trace { "Document ${doc.id} already cleaned up" }
            return doc
        }

        log.info { "Processing document ${doc.id} text:\n${doc.text}" }
        val response =
            chatService.sendToChat(
                listOf(SystemMessage(SYSTEM_MESSAGE), UserMessage(doc.text)),
                chatOptions =
                    ChatOptions
                        .builder()
                        .model(model)
                        .build(),
                streaming = false,
            )
        log.info { "Cleaned up text of document ${doc.id}:\n$response" }
        return doc
            .mutate()
            .metadata(METADATA_ORIGINAL_TEXT, doc.text!!)
            .text(response)
            .build()
    }

    companion object {
        private val log = KotlinLogging.logger { }
        const val METADATA_ORIGINAL_TEXT = "original_text"
    }
}
