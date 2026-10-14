package fr.scanneat.notifications

import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import dagger.hilt.android.AndroidEntryPoint
import fr.scanneat.R
import fr.scanneat.data.local.prefs.UserPreferences
import fr.scanneat.data.repository.health.FastingRepository
import fr.scanneat.data.repository.health.HydrationRepository
import fr.scanneat.domain.engine.health.fastingSafety
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate

/**
 * Roadmap N5 (phone part): Quick Settings tiles - one swipe from anywhere,
 * without opening the app. A Wear OS companion is not built (needs a watch
 * module and Data Layer sync, untestable here).
 */
@AndroidEntryPoint
class WaterTileService : TileService() {
    @javax.inject.Inject lateinit var hydrationRepo: HydrationRepository
    @javax.inject.Inject lateinit var prefs: UserPreferences
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    override fun onStartListening() = refresh()

    override fun onClick() {
        scope.launch {
            withContext(Dispatchers.IO) { hydrationRepo.addGlass(LocalDate.now(), prefs.activeProfileId.first()) }
            refresh()
        }
    }

    private fun refresh() {
        scope.launch {
            val ml = withContext(Dispatchers.IO) { hydrationRepo.observe(LocalDate.now(), prefs.activeProfileId.first()).first() }
            qsTile?.apply {
                label = getString(R.string.tile_water)
                subtitleCompat(getString(R.string.tile_water_today, ml))
                state = Tile.STATE_INACTIVE
                updateTile()
            }
        }
    }

    override fun onDestroy() { scope.cancel(); super.onDestroy() }
}

@AndroidEntryPoint
class FastingTileService : TileService() {
    @javax.inject.Inject lateinit var fastingRepo: FastingRepository
    @javax.inject.Inject lateinit var prefs: UserPreferences
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    override fun onStartListening() = refresh()

    override fun onClick() {
        scope.launch {
            withContext(Dispatchers.IO) {
                val id = prefs.activeProfileId.first()
                if (fastingRepo.state(id).first() != null) fastingRepo.stop(id)
                // Roadmap C13: same profile check as the fasting screen.
                else if (!fastingSafety(prefs.profile.first()).blocked) fastingRepo.start(16, id)
            }
            refresh()
        }
    }

    private fun refresh() {
        scope.launch {
            val (active, blocked) = withContext(Dispatchers.IO) {
                val id = prefs.activeProfileId.first()
                (fastingRepo.state(id).first() != null) to fastingSafety(prefs.profile.first()).blocked
            }
            qsTile?.apply {
                label = getString(R.string.tile_fasting)
                subtitleCompat(getString(when { active -> R.string.tile_fasting_running; blocked -> R.string.tile_fasting_blocked; else -> R.string.tile_fasting_start }))
                state = when { active -> Tile.STATE_ACTIVE; blocked -> Tile.STATE_UNAVAILABLE; else -> Tile.STATE_INACTIVE }
                updateTile()
            }
        }
    }

    override fun onDestroy() { scope.cancel(); super.onDestroy() }
}

private fun Tile.subtitleCompat(text: String) {
    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) subtitle = text
}
