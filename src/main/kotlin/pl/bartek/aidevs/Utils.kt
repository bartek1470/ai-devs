package pl.bartek.aidevs

import org.jsoup.nodes.Document

private val AI_DEVS_FLAG_REGEX = "\\{\\{FLG:(.*)}}".toRegex()

fun String.isAiDevsFlag(): Boolean = AI_DEVS_FLAG_REGEX.matches(this)

fun String.extractAiDevsFlag(): String? = AI_DEVS_FLAG_REGEX.find(this)?.value

fun Document.minimalizedWholeText() = wholeText().split("\n").filter { it.isNotBlank() }.joinToString("\n") { it.trim() }
