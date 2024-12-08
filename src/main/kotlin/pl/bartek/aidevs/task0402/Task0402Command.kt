package pl.bartek.aidevs.task0402

import org.jline.terminal.Terminal
import org.springframework.shell.command.annotation.Command
import org.springframework.shell.command.annotation.CommandAvailability
import org.springframework.shell.command.annotation.Option
import pl.bartek.aidevs.util.ansiFormattedError
import pl.bartek.aidevs.util.println

@Command(
    group = "task",
    command = ["task"],
)
class Task0402Command(
    private val terminal: Terminal,
    private val service: Task0402Service?,
) {
    @Command(
        command = ["0402"],
        description = "https://bravecourses.circle.so/c/lekcje-programu-ai3-806660/s04e02-przetwarzanie-tresci",
    )
    @CommandAvailability(provider = ["openAiProfilePresent"])
    fun run(
        @Option(required = true, shortNames = ['o']) operation: CommandOperation,
        @Option(required = false, shortNames = ['m']) model: String?,
    ) {
        if (operation == CommandOperation.VERIFY) {
            if (model.isNullOrBlank()) {
                terminal.println("Model is required to verify samples".ansiFormattedError())
                return
            }
            service?.verifySamples(model, terminal)
        } else if (operation == CommandOperation.START_FINE_TUNING) {
            service?.startFineTuning(terminal)
        } else {
            terminal.println("Not supported operation".ansiFormattedError())
        }
    }
}
