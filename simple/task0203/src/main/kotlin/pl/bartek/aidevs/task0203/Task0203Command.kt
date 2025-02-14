package pl.bartek.aidevs.task0203

import org.jline.terminal.Terminal
import org.springframework.shell.command.annotation.Command
import org.springframework.shell.command.annotation.CommandAvailability

@Command(
    group = "task",
    command = ["task"],
)
class Task0203Command(
    private val terminal: Terminal,
    private val task0203Service: Task0203Service?,
) {
    @Command(
        command = ["0203"],
        description = "https://bravecourses.circle.so/c/lekcje-programu-ai3-806660/s02e03-generowanie-i-modyfikacja-obrazow",
    )
    @CommandAvailability(provider = ["openAiProfilePresent"])
    fun run() {
        task0203Service?.run(terminal)
    }
}
