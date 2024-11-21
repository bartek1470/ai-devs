package pl.bartek.aidevs.vision

import org.springframework.ai.chat.client.ChatClient
import org.springframework.ai.chat.client.advisor.SimpleLoggerAdvisor
import org.springframework.ai.chat.messages.UserMessage
import org.springframework.ai.chat.model.ChatModel
import org.springframework.ai.chat.prompt.ChatOptions
import org.springframework.ai.model.Media
import org.springframework.ai.ollama.OllamaChatModel
import org.springframework.ai.ollama.api.OllamaOptions
import org.springframework.ai.openai.OpenAiChatModel
import org.springframework.ai.openai.OpenAiChatOptions
import org.springframework.ai.openai.api.OpenAiApi
import org.springframework.beans.factory.annotation.Value
import org.springframework.core.io.FileSystemResource
import org.springframework.http.MediaType
import org.springframework.stereotype.Service
import pl.bartek.aidevs.AiModelVendor
import pl.bartek.aidevs.TaskId
import java.nio.file.Files
import java.nio.file.Path
import java.util.stream.Collectors
import kotlin.io.path.exists
import kotlin.io.path.nameWithoutExtension
import kotlin.io.path.notExists

private const val IMAGE_DESCRIPTION_SUB_DIRECTORY = "image-description"

@Service
class VisionService(
    @Value("\${aidevs.cache-dir}") private val cacheDir: Path,
    aiModelVendor: AiModelVendor,
    openAiChatModel: OpenAiChatModel,
    ollamaChatModel: OllamaChatModel,
) {
    private val chatModel: ChatModel = if (aiModelVendor.isOllamaPreferred()) ollamaChatModel else openAiChatModel
    private val chatClient =
        ChatClient
            .builder(chatModel)
            .defaultAdvisors(SimpleLoggerAdvisor())
            .build()

    private val imageChatOptions: ChatOptions =
        if (aiModelVendor.isOllamaPreferred()) {
            OllamaOptions
                .builder()
                .withModel("llava:7b")
                .build()
        } else {
            OpenAiChatOptions
                .builder()
                .withModel(OpenAiApi.ChatModel.GPT_4_O)
                .build()
        }

    fun describeImage(
        file: ImageFileToView,
        taskId: TaskId,
        onPartialResponseReceived: (String) -> Unit = {},
    ): String {
        val imagePath = cacheDir.resolve(taskId.cacheFolderName()).resolve(file.filename)
        val imageDescriptionPath =
            imagePath.parent
                .resolve(
                    IMAGE_DESCRIPTION_SUB_DIRECTORY,
                ).resolve("${imagePath.nameWithoutExtension}.txt")

        if (imageDescriptionPath.exists()) {
            return Files.readString(imageDescriptionPath)
        }
        if (imagePath.notExists()) {
            Files.write(imagePath, file.image.contentAsByteArray)
        }

        Files.createDirectories(imageDescriptionPath.parent)
        val imageDescription =
            chatClient
                .prompt()
                .options(imageChatOptions)
                .messages(
                    UserMessage(
                        "Describe the image",
                        Media(MediaType.IMAGE_PNG, FileSystemResource(imagePath)),
                    ),
                ).stream()
                .content()
                .doOnNext(onPartialResponseReceived)
                .collect(Collectors.joining(""))
                .block() ?: throw IllegalStateException("Cannot get chat response")

        Files.write(imageDescriptionPath, imageDescription.toByteArray())

        return imageDescription
    }
}
