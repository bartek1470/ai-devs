package pl.bartek.aidevs.course.api

data class AiDevsAnswer<T>(
    val task: Task,
    val answer: T,
)
