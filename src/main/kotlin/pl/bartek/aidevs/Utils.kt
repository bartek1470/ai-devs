package pl.bartek.aidevs

private val AI_DEVS_FLAG_REGEX = "\\{\\{FLG:(.*)}}".toRegex()

fun String.isAiDevsFlag(): Boolean = AI_DEVS_FLAG_REGEX.matches(this)

fun String.extractAiDevsFlag(): String? = AI_DEVS_FLAG_REGEX.find(this)?.value
