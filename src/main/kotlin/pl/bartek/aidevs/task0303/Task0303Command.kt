package pl.bartek.aidevs.task0303

import org.jline.terminal.Terminal
import org.springframework.shell.command.annotation.Command

@Command(
    group = "task",
    command = ["task"],
)
class Task0303Command(
    private val terminal: Terminal,
    private val task0303Service: Task0303Service?,
) {
    @Command(
        command = ["0303"],
        description = "https://bravecourses.circle.so/c/lekcje-programu-ai3-806660/s03e03-wyszukiwanie-hybrydowe",
    )
    fun run() {
        task0303Service?.run(terminal)
    }
}
