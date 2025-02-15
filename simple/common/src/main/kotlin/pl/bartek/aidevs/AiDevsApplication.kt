package pl.bartek.aidevs

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.boot.runApplication
import org.springframework.shell.command.annotation.CommandScan
import pl.bartek.aidevs.config.AiDevsProperties

@CommandScan
@EnableConfigurationProperties(AiDevsProperties::class)
@SpringBootApplication
class AiDevsApplication

fun main(args: Array<String>) {
    runApplication<AiDevsApplication>(*args)
}
