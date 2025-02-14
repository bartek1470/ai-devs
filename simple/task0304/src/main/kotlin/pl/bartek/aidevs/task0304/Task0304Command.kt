package pl.bartek.aidevs.task0304

import org.jline.terminal.Terminal
import org.springframework.shell.command.annotation.Command

@Command(
    group = "task",
    command = ["task"],
)
class Task0304Command(
    private val terminal: Terminal,
    private val service: Task0304Service?,
) {
    @Command(
        command = ["0304"],
        description = "https://bravecourses.circle.so/c/lekcje-programu-ai3-806660/s03e04-zrodla-danych",
    )
    fun run() {
        service?.run(terminal)
    }
}
