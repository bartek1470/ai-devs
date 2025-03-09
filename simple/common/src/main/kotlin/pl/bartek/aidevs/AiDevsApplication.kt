package pl.bartek.aidevs

import org.springframework.boot.SpringApplication
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.shell.command.annotation.CommandScan
import pl.bartek.aidevs.config.AiDevsProperties

@CommandScan
@EnableConfigurationProperties(AiDevsProperties::class)
@SpringBootApplication
class AiDevsApplication

fun main(args: Array<String>) {
    SpringApplication(AiDevsApplication::class.java)
        .apply {
            setAdditionalProfiles("task")
        }.run(*args)
}
