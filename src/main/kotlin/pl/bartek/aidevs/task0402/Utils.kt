package pl.bartek.aidevs.task0402

import org.springframework.web.client.RestClient
import org.springframework.web.util.UriComponentsBuilder
import pl.bartek.aidevs.util.unzip
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.exists
import kotlin.io.path.nameWithoutExtension

fun fetchData(
    url: String,
    restClient: RestClient,
    cacheDir: Path,
): Path {
    val uriComponents = UriComponentsBuilder.fromHttpUrl(url).build()
    val filename = uriComponents.pathSegments.last()!!
    val filePath = cacheDir.resolve(filename)
    val unzippedFilePath = filePath.parent.resolve(filePath.nameWithoutExtension)
    if (unzippedFilePath.exists()) {
        return unzippedFilePath
    }

    val body =
        restClient
            .get()
            .uri(url)
            .retrieve()
            .body(ByteArray::class.java) ?: throw IllegalStateException("Unable to fetch data from URL: $url")

    Files.write(filePath, body)
    filePath.unzip(unzippedFilePath)
    return unzippedFilePath
}
