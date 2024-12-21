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
            return Availability.available()
        }
        return Availability.unavailable("profiles are invalid. Profiles should match: $profiles")
    }
}
