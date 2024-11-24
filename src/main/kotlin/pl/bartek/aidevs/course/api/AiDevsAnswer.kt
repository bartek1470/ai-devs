package pl.bartek.aidevs.courseapi

data class AiDevsAnswer<T>(
    val task: Task,
    val answer: T,
)
