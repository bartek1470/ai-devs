package pl.bartek.aidevs.task0303

import org.springframework.web.client.RestClient
import org.springframework.web.client.body
import java.util.function.Function

class SendDbApiRequest(
    private val apiKey: String,
    private val apiUrl: String,
    private val restClient: RestClient,
) : Function<DbQuery, String> {
    override fun apply(dbQuery: DbQuery): String =
        restClient
            .post()
            .uri(apiUrl)
            .body(DbRequest(dbQuery.query, apiKey))
            .retrieve()
            .body<String>() ?: throw IllegalStateException("Failed to get response for query $dbQuery")
}
