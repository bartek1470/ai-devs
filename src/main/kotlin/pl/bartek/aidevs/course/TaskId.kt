package pl.bartek.aidevs.course

enum class TaskId {
    TASK_0101,
    TASK_0102,
    TASK_0103,
    TASK_0104,
    TASK_0105,
    TASK_0201,
    TASK_0202,
    TASK_0203,
    TASK_0204,
    TASK_0205,
    TASK_0301,
    TASK_0302,
    TASK_0303,
    TASK_0304,
    TASK_0305,
    ;

    fun cacheFolderName(): String = name.substring("TASK_".length, name.length)
}
