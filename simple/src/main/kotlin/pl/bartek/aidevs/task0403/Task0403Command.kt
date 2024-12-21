package pl.bartek.aidevs.task0403

import org.jline.terminal.Terminal
import org.springframework.shell.command.annotation.Command

@Command(
    group = "task",
    command = ["task"],
)
class Task0403Command(
    private val terminal: Terminal,
    private val service: Task0403Service?,
) {
    @Command(
        command = ["0403"],
        description = "https://bravecourses.circle.so/c/lekcje-programu-ai3-806660/s04e03-zewnetrzne-zrodla-danych",
    )
    fun run() {
        service?.run(terminal)
    }
}
