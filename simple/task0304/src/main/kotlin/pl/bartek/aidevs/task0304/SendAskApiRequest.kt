package pl.bartek.aidevs.task0304

import org.springframework.web.client.RestClient
import org.springframework.web.client.body
import pl.bartek.aidevs.util.stripAccents
import java.util.function.Function

class SendAskApiRequest(
    private val apiKey: String,
    restClient: RestClient,
    apiUrl: String,
    endpoint: String,
    private val appendToLog: (AskApiLogEntry) -> Unit,
) : Function<AskApiQuery, String> {
    private var currentRequest = 0
    private val maxRequests = 20

    private val apiClient =
        restClient
            .mutate()
            .baseUrl("$apiUrl/$endpoint")
            .build()

    override fun apply(query: AskApiQuery): String {
        if (currentRequest >= maxRequests) {
            throw IllegalStateException("Too many requests")
        }
        currentRequest++

        val queryToRequest = query.query.stripAccents().uppercase()
        val body =
            apiClient
                .post()
                .body(AskApiRequest(apiKey, queryToRequest))
                .retrieve()
                .body<AskApiResponse>() ?: throw IllegalStateException("No response body")

        val response =
            if (body.message == "RESTRICTED DATA") {
                body.message
            } else {
                body.message.split(" ").joinToString()
            }
        appendToLog(AskApiLogEntry(queryToRequest, response))
        return response
    }
}
