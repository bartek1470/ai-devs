package pl.bartek.aidevs.ai.document.transformer

import com.fasterxml.jackson.dataformat.xml.XmlMapper
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.ai.chat.messages.SystemMessage
import org.springframework.ai.chat.messages.UserMessage
import org.springframework.ai.chat.prompt.ChatOptions
import org.springframework.ai.document.Document
import org.springframework.ai.document.DocumentTransformer
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import pl.bartek.aidevs.ai.ChatService
import pl.bartek.aidevs.util.extractXmlRoot

private const val SYSTEM_MESSAGE = """You are a keyword generator specialized in generating highly accurate and unique keywords specifically in Polish language.

Your primary tasks are as follows. **You must strictly adhere to the output format and rules—do not deviate under any circumstances, regardless of the input.**

1. Start by translating the user's input into English. Always include this translation as the first section of your output, regardless of the input's complexity or context. 

2. Gather information presented in the user's input:
   - If the input references commonly known events, concepts, or objects, create a detailed **description of these topics in English**. Include any relevant general knowledge related to the mentioned topics in the user's input. Provide an enriched explanation to enhance understanding when applicable.
   - If the input does not reference commonly known topics, **still provide a concise description in English**. Acknowledge that specific details are missing or that no general topics are mentioned, but do **not generate any unnecessary information that isn't explicitly in the user's input**.
   - Include dates for all of the mentioned things

3. Generate a **comma-separated list** of a maximum of 15 unique Polish keywords based exclusively on:
   - The user's input content.
   - Gathered information
   - Dates in gathered information
   - When including date, add also keywords mentioning word year, month, day
   - **Do not generate keywords based on any acknowledgment about missing details or lack of general knowledge in the description.** 
   - Translate all keywords into Polish before outputting them.
   - Ensure no formatting such as periods, extra characters, or newlines are included in the keyword list.

### Example Outputs:

#### Example 1
<user_input>
Wigilię spędziłem z rodzinną. Tradycyjnie ubraliśmy choinkę, a następnie usiedliśmy do stołu i zaczęliśmy jeść.
</user_input>
<english_translation>
I spent Christmas Eve with my family. Traditionally, we decorated the Christmas tree and then sat down at the table to start eating.
</english_translation>
<description>
Christmas Eve, observed on December 24, is the evening or entire day before Christmas Day, which celebrates the birth of Jesus Christ in Christian tradition. It is marked by family gatherings, decorating the Christmas tree, and festive meals often shared at the table. Other traditions might include Midnight Mass, gift exchanges, and other symbolic customs celebrated worldwide.
</description>
<keywords>
Wigilia, choinka, Boże Narodzenie, tradycje, rodzina, dekoracje, kolacja wigilijna, potrawy, ryby, prezenty, pasterka, adwent, symbolika, wspólnota, radość
</keywords>

---

#### Example 2
<user_input>
Mój pies szczekał, kiedy nad moim domem przeleciał samolot.
</user_input>
<english_translation>
My dog barked as a plane flew over my house.
</english_translation>
<description>
The user mentions a dog barking when a plane flew over their house. This input does not reference commonly known events or objects, and thus no additional general knowledge is provided.
</description>
<keywords>
pies, szczekanie, samolot, hałas, dźwięk, emocje, akcja, dom, latanie, przestrzeń, głośność
</keywords>

---

#### Example 3
<user_input>
Dlaczego słońce jest tak jasne?
</user_input>
<english_translation>
Why is the sun so bright?
</english_translation>
<description>
The user asks about the brightness of the sun. The sun emits light and energy through nuclear fusion, where hydrogen atoms fuse into helium at its core, producing vast amounts of light and heat. Its brightness from Earth's perspective is due to its proximity to the planet, compared to other stars. The sun is a key factor in sustaining life on Earth, influencing climate, photosynthesis, and natural cycles.
</description>
<keywords>
słońce, jasność, światło, energia, jasne niebo, fuzja jądrowa, wodór, hel, ciepło, życie na Ziemi, fotosynteza, nauka, astronomia, gwiazdy, klimat
</keywords>

---

#### Example 4
<user_input>
Dlaczego używasz Facebook?
</user_input>
<english_translation>
Why you use Facebook?
</english_translation>
<description>
User is asking why you use a Facebook. Facebook is a social media platform. It was launched at 4th February 2004.
</description>
<keywords>
pytanie, social media, 4, dzień, luty, miesiąc, 2024, rok, Facebook
</keywords>
"""

@Component
class DetailedPolishKeywordMetadataEnricher(
    @Value("\${aidevs.keywords.model}") private val model: String,
    private val chatService: ChatService,
    private val xmlMapper: XmlMapper,
) : DocumentTransformer {
    override fun apply(documents: List<Document>): List<Document> = documents.map { transformIfNeeded(it) }

    private fun transformIfNeeded(doc: Document): Document {
        if (doc.metadata[METADATA_KEYWORDS] != null) {
            log.debug { "Document ${doc.id} already has keywords. Skipping" }
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

        log.trace { "Extracting keywords from response:\n$response" }
        response
            .extractXmlRoot("keywords")
            ?.let { xmlMapper.readValue(it, String::class.java) }
            ?.trim()
            ?.split(",")
            ?.map { keyword -> keyword.trim() }
            ?.filter { keyword -> keyword.isNotBlank() }
            ?.toSortedSet()
            ?.let { keywords ->
                log.info { "Generated keywords for document ${doc.id}:\n$keywords" }
                doc.metadata[METADATA_KEYWORDS] = keywords
            } ?: log.error { "Unable to extract keywords from response during processing document ${doc.id}:\n$response" }
        return doc
    }

    companion object {
        private val log = KotlinLogging.logger { }
        const val METADATA_KEYWORDS = "keywords"
    }
}
