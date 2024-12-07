package pl.bartek.aidevs.task0401

import org.jline.terminal.Terminal
import org.springframework.shell.command.annotation.Command

@Command(
    group = "task",
    command = ["task"],
)
class Task0401Command(
    private val terminal: Terminal,
    private val service: Task0401Service?,
) {
    @Command(
        command = ["0401"],
        description = "https://bravecourses.circle.so/c/lekcje-programu-ai3-806660/s04e01-interfejs",
    )
    fun run() {
        service?.run(terminal)
    }
}
