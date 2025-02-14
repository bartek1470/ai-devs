package pl.bartek.task0404

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.MapperFeature
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.dataformat.xml.XmlMapper
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.ai.chat.client.ChatClient
import org.springframework.ai.chat.client.advisor.SimpleLoggerAdvisor
import org.springframework.ai.chat.messages.SystemMessage
import org.springframework.ai.chat.messages.UserMessage
import org.springframework.ai.chat.model.ChatModel
import org.springframework.ai.model.function.FunctionCallingOptionsBuilder.PortableFunctionCallingOptions
import org.springframework.ai.openai.api.OpenAiApi
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.HttpStatusCode
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.client.RestClient
import org.springframework.web.client.toEntity

@RequestMapping("/map")
@RestController
class MapController(
    chatModel: ChatModel,
    @Value("\${aidevs.api-key}") private val apiKey: String,
    @Value("\${aidevs.hq-url}") private val hqUrl: String,
    @Value("\${aidevs.task.0404.server-url}") private val serverUrl: String,
) {
    private val chatClient =
        ChatClient
            .builder(chatModel)
            .defaultAdvisors(SimpleLoggerAdvisor())
            .build()

    private val restClient =
        RestClient
            .builder()
            .baseUrl(hqUrl)
            .defaultStatusHandler(HttpStatusCode::isError) { _, _ -> }
            .build()

    val xmlMapper: ObjectMapper =
        XmlMapper
            .builder()
            .defaultUseWrapper(false)
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
            .configure(MapperFeature.ACCEPT_CASE_INSENSITIVE_PROPERTIES, true)
            .build()
            .registerKotlinModule()

    @PostMapping("/start-drone")
    fun startDrone() {
        log.info { "Received request to start drone" }
        val responseSpec =
            restClient
                .post()
                .uri("/report")
                .body(
                    """
                    {
                        "apikey":"$apiKey",
                        "answer":"$serverUrl/map/move",
                        "task":"webhook"
                    }
                    """.trimIndent(),
                ).retrieve()
                .toEntity<String>()
        log.info { "Got start drone response: ${responseSpec.statusCode}\n${responseSpec.body}" }
    }

    @PostMapping("/move")
    fun moveDrone(
        @RequestBody request: MapMoveRequest,
    ): MapMoveResponse {
        log.info { "Received request to move drone: ${request.instruction}" }
        val response =
            chatClient
                .prompt()
                .messages(
                    SystemMessage(
                        """
                        1. The map is 4 rows and 4 columns grid.
                        2. Each cell is a unique, continuous area representing one feature only.
                        
                        # Map Layout
                        
                        - **Row 1:**
                          - **Row 1 Cell 1:** Marker
                          - **Row 1 Cell 2:** Grass
                          - **Row 1 Cell 3:** One tree
                          - **Row 1 Cell 4:** House

                        - **Row 2:**
                          - **Row 2 Cell 1:** Grass
                          - **Row 2 Cell 2:** Windmill
                          - **Row 2 Cell 3:** Grass
                          - **Row 2 Cell 4:** Grass

                        - **Row 3:**
                          - **Row 3 Cell 1:** Grass
                          - **Row 3 Cell 2:** Grass
                          - **Row 3 Cell 3:** Rocks
                          - **Row 3 Cell 4:** Two trees

                        - **Row 4:**
                          - **Row 4 Cell 1:** Mountains
                          - **Row 4 Cell 2:** Mountains
                          - **Row 4 Cell 3:** Car
                          - **Row 4 Cell 4:** Cave
                        
                        # Map visualization
                        
                        Marker    | Grass     | One tree | House
                        Grass     | Windmill  | Grass    | Grass
                        Grass     | Grass     | Rocks    | Two trees
                        Mountains | Mountains | Car      | Cave
                        
                        # Task
                        
                        You need to detect from a user's message to which cell a drone operator (the user) wants to move the drone and tell him what object is in the destination cell.
                        The drone is always starting from a row 1 cell 1 which contains a marker object.
                        The drone operator can cancel instructions in his message, so you need to firstly think what are the final instructions from the drone operator.
                        The final answer has to be specified in `result` XML tag and it has to be the object from the cell translated to Polish language.

                        # Example
                        
                        Drone operator: Okay, let's start. Move right then down... Or no, no. Let's go like this. First go down and then right. This should be okay. What do you see?
                        AI: The drone operator wants to move one cell right and then one cell down. Then he cancel his instructions and tell to go one cell down, then one cell right. It means the final directions are down, right.
                        <result>
                        Młyn
                        </result>

                        # Example
                        
                        Drone operator: Go all the way down and then twice right.
                        AI: The drone operator wants to move to the end of the map down and then two cells right. The map is four by four cells size, so the final directions are down, down, down, right, right
                        <result>
                        Samochód
                        </result>
                        """.trimIndent(),
                    ),
                    UserMessage(request.instruction),
                ).options(
                    PortableFunctionCallingOptions
                        .builder()
                        .withModel(OpenAiApi.ChatModel.GPT_4.value)
                        .withTemperature(0.45)
                        .build(),
                ).call()
                .content() ?: throw IllegalStateException("No chat response")
        log.info { "Response: $response" }
        val result = response.extractXmlRoot() ?: throw IllegalStateException("No XML root in response")
        val finalResult = xmlMapper.readValue(result, String::class.java)
        log.info { "Final result: $finalResult" }
        return MapMoveResponse(finalResult)
    }

    private fun String.extractXmlRoot(xmlTagName: String = "result"): String? {
        val xmlStartTag = "<$xmlTagName>"
        val xmlEndTag = "</$xmlTagName>"
        val startIndex = indexOf(xmlStartTag, ignoreCase = true)
        val endIndex = indexOf(xmlEndTag, ignoreCase = true) + xmlEndTag.length
        return if (startIndex < 0 || endIndex < 0) null else substring(startIndex, endIndex)
    }

    companion object {
        private val log = KotlinLogging.logger { }
    }
}
