package pl.bartek.aidevs.task0304

data class AskApiResponse(
    val code: Int,
    val message: String,
) {
    fun isFailure() = code < 0
}
