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
        var response: ClientHttpResponse? = null
        var exception: Exception? = null
        try {
            response = execution.execute(request, body)
        } catch (ex: Exception) {
            exception = ex
        }

        val isBinaryRequest = request.headers.contentType?.isCompatibleWith(MediaType.APPLICATION_OCTET_STREAM) ?: false
        val requestBody = if (isBinaryRequest) "[BINARY]" else String(body)
        val isBinaryResponse = response?.headers?.contentType?.isCompatibleWith(MediaType.APPLICATION_OCTET_STREAM) ?: false
        val responseBody = if (isBinaryResponse) "[BINARY]" else response?.body?.readAllBytes()?.let { String(it) }
        log.debug {
            """
            |Request: ${request.method} ${request.uri}
            |${request.headers.toMultilineString()}
            |
            |$requestBody
            |
            |Response: ${response?.statusCode}
            |${response?.headers?.toMultilineString()}
            |
            |$responseBody
            """.trimMargin()
        }

        return response ?: throw exception!!
    }

    private fun HttpHeaders.toMultilineString(): String = this.toMap().entries.joinToString("\n")

    companion object {
        private val log = KotlinLogging.logger { }
    }
}
