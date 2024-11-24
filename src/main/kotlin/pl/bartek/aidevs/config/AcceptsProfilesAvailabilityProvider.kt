package pl.bartek.aidevs.config

import org.springframework.core.env.Environment
import org.springframework.core.env.Profiles
import org.springframework.shell.Availability
import org.springframework.shell.AvailabilityProvider

class AcceptsProfilesAvailabilityProvider(
    private val environment: Environment,
    private val profiles: Profiles,
) : AvailabilityProvider {
    override fun get(): Availability {
        if (environment.acceptsProfiles(profiles)) {
            Availability.available()
        }
        return Availability.unavailable("Invalid profiles. Profiles should match: $profiles")
    }
}
