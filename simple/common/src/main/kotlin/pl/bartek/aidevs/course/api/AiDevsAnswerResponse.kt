package pl.bartek.aidevs.course.api

data class AiDevsAnswerResponse(
    val code: Int,
    val message: String,
    val hint: String? = null,
    val debug: String? = null,
) {
    fun isSuccess() = code == 0

    fun isError() = code < 0
}
