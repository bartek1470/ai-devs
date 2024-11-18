package pl.bartek.aidevs.task0103

import org.jline.terminal.Terminal
import org.springframework.beans.factory.annotation.Value
import org.springframework.shell.command.annotation.Command
import org.springframework.web.client.RestClient
import pl.bartek.aidevs.AiModelVendor
import pl.bartek.aidevs.courseapi.AiDevsAnswer
import pl.bartek.aidevs.courseapi.AiDevsApiClient
import pl.bartek.aidevs.courseapi.Task
import pl.bartek.aidevs.println

@Command(
    group = "task",
    command = ["task"],
)
class Task0103Command(
    private val terminal: Terminal,
    @Value("\${aidevs.api-key}") private val apiKey: String,
    @Value("\${aidevs.task.0103.data-url}") private val dataUrl: String,
    @Value("\${aidevs.task.0103.answer-url}") private val answerUrl: String,
    aiModelVendor: AiModelVendor,
    private val aiDevsApiClient: AiDevsApiClient,
    private val restClient: RestClient,
) {
    private val chatClient = aiModelVendor.defaultChatClient()

    @Command(
        command = ["0103"],
        description = "https://bravecourses.circle.so/c/lekcje-programu-ai3-806660/s01e03-limity-duzych-modeli-jezykowych-i-api",
    )
    fun run() {
        val industrialRobotCalibrationFile = fetchInputData()
        val newTestData =
            industrialRobotCalibrationFile.testData.map { testDataItem ->
                val answer =
                    testDataItem.question
                        .split("\\+".toRegex())
                        .map { it.trim() }
                        .map { it.toInt() }
                        .reduce(Int::plus)

                val testQuestion =
                    testDataItem.test?.let {
                        terminal.println(it.question)
                        terminal.flush()
                        val response =
                            chatClient
                                .prompt(it.question)
                                .call()
                                .content() ?: throw IllegalStateException("Cannot get answer")
                        terminal.println(response)
                        terminal.flush()

                        TestQuestion(it.question, response)
                    }

                TestData(testDataItem.question, answer.toString(), testQuestion)
            }

        val answer =
            aiDevsApiClient.sendAnswer(
                answerUrl,
                AiDevsAnswer(
                    Task.JSON,
                    industrialRobotCalibrationFile.copy(apiKey = apiKey, testData = newTestData),
                ),
            )
        terminal.println(answer)
    }

    private fun fetchInputData(): IndustrialRobotCalibrationFile =
        restClient
            .get()
            .uri(dataUrl, apiKey)
            .retrieve()
            .body(IndustrialRobotCalibrationFile::class.java) ?: throw IllegalStateException("Cannot get data to process")
}
