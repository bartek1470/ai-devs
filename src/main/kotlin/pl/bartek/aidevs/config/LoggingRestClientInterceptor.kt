package pl.bartek.aidevs.config

import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpRequest
import org.springframework.http.MediaType
import org.springframework.http.client.ClientHttpRequestExecution
import org.springframework.http.client.ClientHttpRequestInterceptor
import org.springframework.http.client.ClientHttpResponse
import java.io.IOException

class LoggingRestClientInterceptor : ClientHttpRequestInterceptor {
    @Throws(IOException::class)
    override fun intercept(
        request: HttpRequest,
        body: ByteArray,
        execution: ClientHttpRequestExecution,
    ): ClientHttpResponse {
        log.debug { "REQUEST: ${request.method} ${request.uri}" }
        log.debug { "HEADERS:\n${request.headers.toMultilineString()}" }
        log.debug { "BODY:\n${String(body)}" }

        val response: ClientHttpResponse = execution.execute(request, body)

        log.debug { "RESPONSE FROM ${request.method} ${request.uri} -> ${response.statusCode}" }
        log.debug { "HEADERS:\n${response.headers.toMultilineString()}" }

        if (request.headers.contentType?.isCompatibleWith(MediaType.APPLICATION_OCTET_STREAM) != true) {
            log.debug { "BODY:\n${String(response.body.readAllBytes())}" }
        } else {
            log.debug { "BODY is binary" }
        }

        return response
    }

    private fun HttpHeaders.toMultilineString(): String = this.toMap().entries.joinToString("\n")

    companion object {
        private val log = KotlinLogging.logger { }
    }
}
