package pl.bartek.aidevs.config

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.core.env.Environment
import org.springframework.core.env.Profiles
import org.springframework.shell.AvailabilityProvider
import pl.bartek.aidevs.config.Profile.OPENAI
import pl.bartek.aidevs.config.Profile.QDRANT

@Configuration
class CommandAvailabilityConfig(
    private val environment: Environment,
) {
    @Bean
    fun openAiProfilePresent(): AvailabilityProvider = AcceptsProfilesAvailabilityProvider(environment, Profiles.of(OPENAI))

    @Bean
    fun qdrantProfilePresent(): AvailabilityProvider = AcceptsProfilesAvailabilityProvider(environment, Profiles.of(QDRANT))
}
