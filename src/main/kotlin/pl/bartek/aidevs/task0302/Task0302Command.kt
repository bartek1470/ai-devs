package pl.bartek.aidevs.task0302

import org.jline.terminal.Terminal
import org.springframework.shell.command.annotation.Command
import org.springframework.shell.command.annotation.CommandAvailability

@Command(
    group = "task",
    command = ["task"],
)
class Task0302Command(
    private val terminal: Terminal,
    private val service: Task0302Service?,
) {
    @Command(
        command = ["0302"],
        description = "https://bravecourses.circle.so/c/lekcje-programu-ai3-806660/s03e02-wyszukiwanie-semantyczne",
    )
    @CommandAvailability(provider = ["qdrantProfilePresent"])
    fun run() {
        service?.run(terminal)
    }
}
