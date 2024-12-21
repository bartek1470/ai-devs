package pl.bartek.aidevs.task0403

import io.github.oshai.kotlinlogging.KotlinLogging
import org.jsoup.Jsoup
import org.springframework.web.util.UriComponentsBuilder
import java.nio.file.Files
import java.nio.file.Path
import java.time.Duration
import java.util.concurrent.atomic.AtomicInteger

class VisitSiteAction(
    private val baseUrl: String,
    private val cacheDir: Path,
    private val htmlToMarkdownConverter: (Path) -> String,
) : (VisitSiteRequest) -> String {
    private val counter = AtomicInteger()

    override fun invoke(request: VisitSiteRequest): String {
        val uriComponentsBuilder =
            if (request.url.startsWith("https://")) {
                if (!request.url.startsWith(baseUrl)) {
                    return "Only URLs starting with $baseUrl are allowed"
                }

                UriComponentsBuilder.fromHttpUrl(request.url)
            } else {
                UriComponentsBuilder.fromHttpUrl(baseUrl).path(request.url)
            }

        val url =
            uriComponentsBuilder
                .build(true)
                .toUri()
                .toURL()
        log.info { "Visiting $url" }

        val website = Jsoup.parse(url, Duration.ofSeconds(5).toMillis().toInt())
        val fileIndex = counter.getAndIncrement()
        val cacheFilePath = cacheDir.resolve("$fileIndex.html")
        Files.write(cacheFilePath, website.outerHtml().toByteArray())
        val websiteAsMarkdown = htmlToMarkdownConverter.invoke(cacheFilePath)
        log.trace { "Website markdown:\n$websiteAsMarkdown" }
        Files.write(cacheDir.resolve("$fileIndex.md"), websiteAsMarkdown.toByteArray())
        return websiteAsMarkdown
    }

    companion object {
        private val log = KotlinLogging.logger { }
    }
}
