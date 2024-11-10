package pl.bartek.aidevs.task0

import org.springframework.shell.command.annotation.Command
import pl.bartek.aidevs.poligon.AiDevsPoligonAnswer
import pl.bartek.aidevs.poligon.AiDevsPoligonAnswerResponse
import pl.bartek.aidevs.poligon.AiDevsPoligonApiClient
import pl.bartek.aidevs.poligon.Task

@Command(group = "task")
class Task0Command(
    private val apiClient: AiDevsPoligonApiClient,
) {
    @Command(command = ["task0"])
    fun run(): AiDevsPoligonAnswerResponse {
        val data = apiClient.fetchTask0Data()
        val response = apiClient.sendAnswer("NEXT COMMITS IN ENVS", AiDevsPoligonAnswer(Task.POLIGON, data))
        return response
    }
}
