package fr.scanneat.presentation.activity

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import fr.scanneat.R
import fr.scanneat.data.repository.health.ActivityType

// internal (not private) so CalendarScreen's day-detail panel can reuse the same
// localized labels instead of falling back to ActivityType.labelFr.
@Composable
internal fun typeLabels(): Map<ActivityType, String> = mapOf(
    ActivityType.WALKING_BRISK to stringResource(R.string.activity_type_walking),
    ActivityType.RUNNING to stringResource(R.string.activity_type_running),
    ActivityType.CYCLING to stringResource(R.string.activity_type_cycling),
    ActivityType.SWIMMING to stringResource(R.string.activity_type_swimming),
    ActivityType.STRENGTH to stringResource(R.string.activity_type_strength),
    ActivityType.YOGA to stringResource(R.string.activity_type_yoga),
    ActivityType.HIIT to stringResource(R.string.activity_type_hiit),
    ActivityType.OTHER to stringResource(R.string.activity_type_other),
)

@Composable
internal fun subTypeLabels(): Map<String, String> = mapOf(
    "bench_press" to stringResource(R.string.activity_subtype_bench_press),
    "squat" to stringResource(R.string.activity_subtype_squat),
    "deadlift" to stringResource(R.string.activity_subtype_deadlift),
    "biceps_curl" to stringResource(R.string.activity_subtype_biceps_curl),
    "freestyle" to stringResource(R.string.activity_subtype_freestyle),
    "breaststroke" to stringResource(R.string.activity_subtype_breaststroke),
    "butterfly" to stringResource(R.string.activity_subtype_butterfly),
    "trail" to stringResource(R.string.activity_subtype_trail),
    "sprint" to stringResource(R.string.activity_subtype_sprint),
    "interval" to stringResource(R.string.activity_subtype_interval),
    "road" to stringResource(R.string.activity_subtype_road),
    "mountain" to stringResource(R.string.activity_subtype_mountain),
    "indoor" to stringResource(R.string.activity_subtype_indoor),
)
