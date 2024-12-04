package pl.bartek.aidevs.course.api

data class AiDevsAnswerResponse(
    val code: Int,
    val message: String,
) {
    fun isSuccess() = code == 0

    fun isError() = code < 0
}
