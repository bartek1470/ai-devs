package pl.bartek.aidevs.poligon

data class AiDevsPoligonAnswerResponse(
    val code: Int,
    val message: String,
) {
    fun isSuccess() = code == 0

    fun isError() = code < 0
}
