package pl.bartek.aidevs.task0103

import org.jline.terminal.Terminal
import org.springframework.ai.chat.client.ChatClient
import org.springframework.shell.command.annotation.Command
import org.springframework.web.client.RestClient
import pl.bartek.aidevs.config.AiDevsProperties
import pl.bartek.aidevs.course.api.AiDevsAnswer
import pl.bartek.aidevs.course.api.AiDevsApiClient
import pl.bartek.aidevs.course.api.Task
import pl.bartek.aidevs.util.println

@Command(
    group = "task",
    command = ["task"],
)
class Task0103Command(
    private val terminal: Terminal,
    private val aiDevsProperties: AiDevsProperties,
    private val chatClient: ChatClient,
    private val aiDevsApiClient: AiDevsApiClient,
    private val restClient: RestClient,
) {
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
                aiDevsProperties.reportUrl,
                AiDevsAnswer(
                    Task.JSON,
                    industrialRobotCalibrationFile.copy(apiKey = aiDevsProperties.apiKey, testData = newTestData),
                ),
            )
        terminal.println(answer)
    }

    private fun fetchInputData(): IndustrialRobotCalibrationFile =
        restClient
            .get()
            .uri(
                aiDevsProperties.task.task0103.dataUrl
                    .toString(),
                aiDevsProperties.apiKey,
            ).retrieve()
            .body(IndustrialRobotCalibrationFile::class.java) ?: throw IllegalStateException("Cannot get data to process")
}
