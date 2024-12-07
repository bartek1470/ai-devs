package pl.bartek.aidevs.task0305

import org.jline.terminal.Terminal
import org.springframework.shell.command.annotation.Command

@Command(
    group = "task",
    command = ["task"],
)
class Task0305Command(
    private val terminal: Terminal,
    private val service: Task0305Service?,
) {
    @Command(
        command = ["0305"],
        description = "https://bravecourses.circle.so/c/lekcje-programu-ai3-806660/s03e05-bazy-grafowe",
    )
    fun run() {
        service?.run(terminal)
    }
}
