package pl.bartek.aidevs.task0405

import org.jline.terminal.Terminal
import org.springframework.shell.command.annotation.Command
import org.springframework.shell.command.annotation.CommandAvailability

@Command(
    group = "task",
    command = ["task"],
)
class Task0405Command(
    private val terminal: Terminal,
    private val service: Task0405Service?,
) {
    @CommandAvailability(provider = ["qdrantProfilePresent"])
    @Command(
        command = ["0405"],
        description = "https://bravecourses.circle.so/c/lekcje-programu-ai3-806660/s04e05-mechaniki-obslugi-narzedzi",
    )
    fun run() {
        service?.run(terminal)
    }
}
