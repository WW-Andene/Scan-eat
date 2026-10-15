package fr.scanneat.data.repository.health

import fr.scanneat.data.local.prefs.UserPreferences
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Health Connect has no notion of app profiles: it holds the phone owner's
 * data. Reads and writes used to run for whichever profile was active, so a
 * child's profile imported the owner's weigh-ins, workouts and water, and its
 * own entries were written into the owner's Health Connect. Only the profile
 * chosen in Settings (default: the main profile) syncs now.
 */
@Singleton
class HealthConnectProfileGate @Inject constructor(private val prefs: UserPreferences) {
    suspend fun allows(profileId: String): Boolean = prefs.healthConnectProfileId.first() == profileId
}
