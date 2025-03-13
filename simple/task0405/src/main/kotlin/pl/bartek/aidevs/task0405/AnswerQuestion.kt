package pl.bartek.aidevs.task0405

import com.fasterxml.jackson.annotation.JsonClassDescription
import com.fasterxml.jackson.annotation.JsonPropertyDescription
import org.jline.terminal.Terminal
import org.springframework.ai.tool.ToolCallback
import org.springframework.ai.tool.function.FunctionToolCallback
import pl.bartek.aidevs.course.api.AiDevsAnswer
import pl.bartek.aidevs.course.api.AiDevsAnswerResponse
import pl.bartek.aidevs.course.api.AiDevsApiClient
import pl.bartek.aidevs.course.api.Task
import pl.bartek.aidevs.util.ansiFormattedAi
import pl.bartek.aidevs.util.ansiFormattedError
import pl.bartek.aidevs.util.ansiFormattedHuman
import pl.bartek.aidevs.util.ansiFormattedSuccess
import pl.bartek.aidevs.util.println
import java.net.URL
import java.util.Stack
import java.util.function.Function

@JsonClassDescription("A request to answer to the question")
data class AnswerQuestionRequest(
    @JsonPropertyDescription("An answer to the question")
    val answer: String,
)

class AnswerQuestion(
    questions: Map<String, String>,
    private val keywordsGenerator: (String) -> Set<String>,
    private val answerUrl: URL,
    private val aiDevsApiClient: AiDevsApiClient,
    private val terminal: Terminal,
) : Function<AnswerQuestionRequest, String> {
    private val answers: Map<String, String?> = questions.mapValues { null }.toMutableMap()
    private val remainingQuestions = Stack<Question>().apply { addAll(questions.toList().map { Question(it.first, it.second) }) }

    private var currentQuestion: Question? = this.remainingQuestions.pop()
    private var attempts = 0
    private var lastSuccessMessage: String? = null

    override fun apply(request: AnswerQuestionRequest): String {
        if (remainingQuestions.isEmpty()) {
            return "All questions already answered. RETURN the result to the user: `$lastSuccessMessage`"
        }

        val answer = request.answer
        val (key, question) = currentQuestion!!
        terminal.println("$key: $question".ansiFormattedHuman())
        terminal.println(answer.ansiFormattedAi())

        val answersToSend =
            answers.mapValues {
                if (it.value == null) {
                    "no answer"
                } else {
                    it.value!!
                }
            }
        val aiDevsAnswerResponse = aiDevsApiClient.sendAnswer(answerUrl, AiDevsAnswer(Task.NOTES, answersToSend))
        terminal.println("AI Devs:")
        terminal.println(aiDevsAnswerResponse)

        return respond(aiDevsAnswerResponse, key, answer)
    }

    private fun respond(
        aiDevsAnswerResponse: AiDevsAnswerResponse,
        key: String,
        answer: String,
    ) = if (!aiDevsAnswerResponse.message.contains(key) || aiDevsAnswerResponse.hint == null) {
        lastSuccessMessage = aiDevsAnswerResponse.message
        currentQuestion = takeUnless { remainingQuestions.isEmpty() }?.let { remainingQuestions.pop() }
        attempts = 0
        terminal.println("Correct answer! Answer: $answer".ansiFormattedSuccess())
        val nextInstruction =
            if (currentQuestion != null) {
                "Now you HAVE TO ANSWER THE QUESTION: ${currentQuestion!!.content}"
            } else {
                "You have answered all questions. RETURN the result to the user: $lastSuccessMessage"
            }
        "Correct answer!\n\n$nextInstruction"
    } else if (attempts >= MAX_ATTEMPTS) {
        terminal.println("Max tries reached. Aborting.".ansiFormattedError())
        throw IllegalStateException("Max tries reached")
    } else {
        """
        |"$answer" IS A WRONG ANSWER. DO NOT USE IT AGAIN. Try again answering the same question.
        |
        |QUESTION: ${currentQuestion!!.content}
        |HINT: ${aiDevsAnswerResponse.hint}
        |QUESTION KEYWORDS: ${keywordsGenerator(currentQuestion!!.content).joinToString(", ")}
        """.trimMargin()
    }

    companion object {
        private const val MAX_ATTEMPTS = 5

        fun createFunctionCallback(
            questions: Map<String, String>,
            keywordsGenerator: (String) -> Set<String>,
            answerUrl: URL,
            aiDevsApiClient: AiDevsApiClient,
            terminal: Terminal,
        ): ToolCallback {
            val tool = AnswerQuestion(questions, keywordsGenerator, answerUrl, aiDevsApiClient, terminal)
            return FunctionToolCallback
                .builder("answerQuestion", tool)
                .description("Try to answer the question")
                .build()
        }
    }
}
