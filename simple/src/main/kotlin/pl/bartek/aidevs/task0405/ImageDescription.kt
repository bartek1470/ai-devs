package pl.bartek.aidevs.task0405

import com.fasterxml.jackson.annotation.JsonRootName
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlCData

@JsonRootName("image")
data class ImageDescription(
    @JacksonXmlCData
    val description: String,
    @JacksonXmlCData
    val text: String?,
)
