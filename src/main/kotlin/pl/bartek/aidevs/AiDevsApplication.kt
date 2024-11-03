package pl.bartek.aidevs

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import org.springframework.shell.command.annotation.CommandScan

@CommandScan
@SpringBootApplication
class AiDevsApplication

fun main(args: Array<String>) {
    runApplication<AiDevsApplication>(*args)
}
