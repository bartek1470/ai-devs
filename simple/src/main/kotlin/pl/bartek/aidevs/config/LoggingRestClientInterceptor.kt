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

        val isBinaryRequest = request.headers.contentType?.isBinary() ?: false
        val requestBody = if (isBinaryRequest) "[BINARY]" else String(body)
        val isBinaryResponse = response?.headers?.contentType?.isBinary() ?: false
        val responseBody = if (isBinaryResponse) "[BINARY]" else response?.body?.readAllBytes()?.let { String(it) }
        log.debug {
            """
            |Request: ${request.method} ${request.uri}
            |$requestBody
            |
            |Response: ${response?.statusCode}
            |$responseBody
            """.trimMargin()
        }
        log.trace { "Request headers:\n${request.headers.toMultilineString()}" }
        log.trace { "Response headers:\n${response?.headers?.toMultilineString()}" }

        return response ?: throw exception!!
    }

    private fun HttpHeaders.toMultilineString(): String = this.toMap().entries.joinToString("\n")

    private fun MediaType.isBinary(): Boolean =
        isCompatibleWith(MediaType.APPLICATION_OCTET_STREAM) ||
            isCompatibleWith(MediaType.APPLICATION_PDF) ||
            isCompatibleWith(MediaType.IMAGE_GIF) ||
            isCompatibleWith(MediaType.IMAGE_PNG) ||
            isCompatibleWith(MediaType.IMAGE_JPEG)

    companion object {
        private val log = KotlinLogging.logger { }
    }
}
