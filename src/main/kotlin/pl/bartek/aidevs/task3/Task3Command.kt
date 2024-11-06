package pl.bartek.aidevs.task3

import org.springframework.ai.chat.client.ChatClient
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.HttpStatusCode
import org.springframework.http.client.BufferingClientHttpRequestFactory
import org.springframework.http.client.SimpleClientHttpRequestFactory
import org.springframework.shell.command.CommandContext
import org.springframework.shell.command.annotation.Command
import org.springframework.web.client.RestClient
import pl.bartek.aidevs.LoggingRestClientInterceptor
import pl.bartek.aidevs.poligon.AiDevsPoligonAnswerResponse
import pl.bartek.aidevs.poligon.AiDevsPoligonApiClient
import pl.bartek.aidevs.poligon.AiDevsPoligonAuthenticatedAnswer
import pl.bartek.aidevs.poligon.Task

@Command(group = "task")
class Task3Command(
    @Value("\${aidevs.poligon.api-key}") private val apiKey: String,
    private val chatClient: ChatClient,
    private val poligonApiClient: AiDevsPoligonApiClient,
) {
    private val dataFileUrl = "NEXT COMMITS IN ENVS"
    private val restClient =
        RestClient
            .builder()
            .requestFactory(BufferingClientHttpRequestFactory(SimpleClientHttpRequestFactory()))
            .requestInterceptor(LoggingRestClientInterceptor())
            .defaultStatusHandler(HttpStatusCode::isError) { _, _ -> }
            .build()

    @Command(command = ["task3"])
    fun run(ctx: CommandContext) {
        val industrialRobotCalibrationFile =
            restClient
                .get()
                .uri(dataFileUrl)
                .retrieve()
                .body(IndustrialRobotCalibrationFile::class.java) ?: throw IllegalStateException("Cannot get file")

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
                        ctx.terminal.writer().println(it.question)
                        ctx.terminal.writer().flush()
                        val response =
                            chatClient
                                .prompt(it.question)
                                .call()
                                .content() ?: throw IllegalStateException("Cannot get answer")
                        ctx.terminal.writer().println(response)
                        ctx.terminal.writer().flush()

                        TestQuestion(it.question, response)
                    }

                TestData(testDataItem.question, answer.toString(), testQuestion)
            }

        val answer =
            restClient
                .post()
                .uri("NEXT COMMITS IN ENVS")
                .body(
                    AiDevsPoligonAuthenticatedAnswer(
                        Task.JSON,
                        industrialRobotCalibrationFile.copy(apiKey = apiKey, testData = newTestData),
                        apiKey,
                    ),
                ).retrieve()
                .body(AiDevsPoligonAnswerResponse::class.java)
        println(answer)
    }
}
