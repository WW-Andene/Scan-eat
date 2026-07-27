package fr.scanneat.data.repository.health

import android.content.Context
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.ExerciseSessionRecord
import androidx.health.connect.client.records.HydrationRecord
import androidx.health.connect.client.records.NutritionRecord
import androidx.health.connect.client.records.TotalCaloriesBurnedRecord
import androidx.health.connect.client.records.WeightRecord
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import javax.inject.Inject
import javax.inject.Singleton

// ============================================================================
// HEALTH CONNECT REPOSITORY — platform health-data sync for weight.
//
// Scan'eat previously had zero integration with any platform health store —
// weight logged in-app stayed siloed here, invisible to Health Connect and
// any other app reading from it (or writing to it: a smart scale's own app,
// for instance). This wraps the minimal read/write surface for WeightRecord;
// WeightRepository calls writeWeight() after a successful local log() when
// the user has opted in and granted permission (see SettingsScreen).
//
// Health Connect ships as a separate app on API < 34 (bundled into the OS
// from API 34+), so availability must be checked before touching the client
// — HealthConnectClient.getOrCreate() throws if it isn't present.
// ============================================================================

enum class HealthConnectAvailability { AVAILABLE, NOT_INSTALLED, UNSUPPORTED }

@Singleton
class HealthConnectRepository @Inject constructor(
    // Widened from private to internal so the weight/hydration/activity/nutrition
    // sync extension functions (extracted verbatim into their own sibling files -
    // HealthConnectWeightExt.kt, HealthConnectHydrationExt.kt,
    // HealthConnectActivityExt.kt, HealthConnectNutritionExt.kt) can still reach
    // it - same purely-structural split ScanRepository already went through for
    // ScanOffLookup/ScanServerClient.
    @ApplicationContext internal val context: Context,
) {
    companion object {
        // Write and read are two separate subsets (not one combined set), same
        // reasoning as activity's own split just below: a user who granted only
        // the read half (or only the write half) - a plausible privacy choice,
        // e.g. "let it log my weigh-ins out, but don't let it read anyone else's
        // weight data in" - must not lose the other half's functionality just
        // because this pair happens to ship together. writeWeight()/deleteWeight()
        // check only the write subset; readWeights() checks only the read subset.
        internal val weightWritePermissions = setOf(HealthPermission.getWritePermission(WeightRecord::class))
        internal val weightReadPermissions = setOf(HealthPermission.getReadPermission(WeightRecord::class))
        // Hydration is write-only (see writeHydrationDelta) - Health Connect's
        // HydrationRecord models a volume over a start/end interval, but this
        // app stores intake as a single mutable running total per day, so
        // reading external records back and merging them risks double-
        // counting on every re-read rather than being a safe idempotent
        // import the way readExternalWeights() is for WeightRepository.
        internal val hydrationPermissions = setOf(HealthPermission.getWritePermission(HydrationRecord::class))
        // Activity now reads back too (see readExternalActivity) - unlike
        // hydration, ActivityEntity is a genuine multi-entry-per-day table
        // with a dedup key (externalSourceId, added alongside this), so a
        // safe idempotent import is possible the same way it already is for
        // weight. There's still no stored Health-Connect-record id to delete
        // later (ActivityRepository.delete() only ever gets the local row's
        // own id), so a deleted in-app activity still leaves its HC mirror
        // behind - that part of the original write-only limitation stands.
        //
        // Write and read are two separate subsets (not one combined set) for
        // the same reason weight/hydration/activity writes each check their
        // own subset via hasPermission() below rather than the full
        // PERMISSIONS set: a user who granted only the write half (or only
        // the read half) must not lose the other half's functionality just
        // because this pair happens to ship together.
        internal val activityWritePermissions = setOf(
            HealthPermission.getWritePermission(ExerciseSessionRecord::class),
            HealthPermission.getWritePermission(TotalCaloriesBurnedRecord::class),
        )
        internal val activityReadPermissions = setOf(
            HealthPermission.getReadPermission(ExerciseSessionRecord::class),
            HealthPermission.getReadPermission(TotalCaloriesBurnedRecord::class),
        )
        // Nutrition is write-only, same accepted limitation as hydration - a DiaryEntry
        // maps to one NutritionRecord per log, but there's no stored HC record id to
        // delete later (ConsumptionRepository.delete()/update() only ever get the
        // local row's own id), so editing/deleting an entry in-app leaves its HC
        // mirror behind.
        internal val nutritionPermissions = setOf(HealthPermission.getWritePermission(NutritionRecord::class))

        /** Requested together up front (single system permission dialog) - see [hasPermission] for why writes each check only their own subset instead of this combined set. */
        val PERMISSIONS: Set<String> = weightWritePermissions + weightReadPermissions + hydrationPermissions + activityWritePermissions + activityReadPermissions + nutritionPermissions

        internal const val TAG = "HealthConnectRepository"
    }

    fun availability(): HealthConnectAvailability = when (HealthConnectClient.getSdkStatus(context)) {
        HealthConnectClient.SDK_UNAVAILABLE -> HealthConnectAvailability.UNSUPPORTED
        HealthConnectClient.SDK_UNAVAILABLE_PROVIDER_UPDATE_REQUIRED -> HealthConnectAvailability.NOT_INSTALLED
        else -> HealthConnectAvailability.AVAILABLE
    }

    internal fun client(): HealthConnectClient = HealthConnectClient.getOrCreate(context)

    /**
     * True only when every permission Scan'eat ever asks for is granted — used for the Settings
     * screen's single "connected" status, not to gate individual writes (see [hasPermission] for
     * that). Guarded like every other function below: a revoked-permission or provider-missing
     * exception here must never propagate past this repository, since 4 different callers
     * (Recipes/MealPlan/Result/Diary ViewModels) invoke Health Connect functions with no guard
     * of their own, after their local Room write has already committed.
     */
    suspend fun hasPermissions(): Boolean = try {
        hasPermission(PERMISSIONS)
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        android.util.Log.w(TAG, "hasPermissions failed", e)
        false
    }

    /**
     * Checks only the specific permission(s) a given sync feature needs.
     * hasPermissions() (all-or-nothing against the full PERMISSIONS set) used
     * to gate every write path, including writeWeight()/readWeights() - so
     * adding hydration/activity's own write permissions to that same shared
     * set meant a user who had weight sync working (and never touched the
     * newer hydration/activity permissions, e.g. by revoking just one of them
     * in system settings) would silently lose weight sync too, the moment
     * PERMISSIONS stopped being a subset of what's actually granted. Each
     * write function now checks only what it personally needs.
     */
    internal suspend fun hasPermission(required: Set<String>): Boolean {
        if (availability() != HealthConnectAvailability.AVAILABLE) return false
        return client().permissionController.getGrantedPermissions().containsAll(required)
    }

    // writeWeight/readWeights/readExternalWeights/deleteWeight now live in
    // HealthConnectWeightExt.kt (see that file) - extracted verbatim as extension
    // functions on HealthConnectRepository, same cohesive "weight sync" concern
    // ScanRepository's own split pulled ScanOffLookup/ScanServerClient out for.

    // writeHydrationDelta now lives in HealthConnectHydrationExt.kt (see that file) -
    // extracted verbatim, same "hydration sync" concern.

    // writeActivity/readExternalActivity/exerciseTypeFor/activityTypeFor/
    // ExternalActivitySession now live in HealthConnectActivityExt.kt (see that
    // file) - extracted verbatim, same "activity/exercise sync" concern.

    // writeNutrition/mealTypeFor now live in HealthConnectNutritionExt.kt (see
    // that file) - extracted verbatim, same "nutrition sync" concern.
}
