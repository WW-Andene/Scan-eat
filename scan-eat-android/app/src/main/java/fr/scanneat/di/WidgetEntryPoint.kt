package fr.scanneat.di

import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import fr.scanneat.data.local.prefs.UserPreferences
import fr.scanneat.data.repository.nutrition.ConsumptionRepository

/**
 * TodayWidget (Glance) isn't an Activity/Fragment/View/Service/BroadcastReceiver
 * Hilt already knows how to field-inject into via @AndroidEntryPoint — it's a plain
 * class instantiated by the Glance framework itself. EntryPointAccessors is the
 * standard way to reach Hilt-managed singletons from a context like that.
 */
@EntryPoint
@InstallIn(SingletonComponent::class)
interface WidgetEntryPoint {
    fun consumptionRepository(): ConsumptionRepository
    fun userPreferences(): UserPreferences
}

fun widgetEntryPoint(context: android.content.Context): WidgetEntryPoint =
    EntryPointAccessors.fromApplication(context.applicationContext, WidgetEntryPoint::class.java)
