package fr.scanneat.di

import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import fr.scanneat.data.local.prefs.UserPreferences
import fr.scanneat.data.repository.biolism.BiolismRepository
import fr.scanneat.data.repository.health.HydrationRepository
import fr.scanneat.data.repository.health.MedicationRepository
import fr.scanneat.data.repository.nutrition.ConsumptionRepository
import fr.scanneat.data.repository.scan.ScanRepository

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
    fun hydrationRepository(): HydrationRepository
    fun biolismRepository(): BiolismRepository
    fun medicationRepository(): MedicationRepository
    // User-requested: ScanWidget (see that file's own header) needs the last
    // scanned product's name/score/grade.
    fun scanRepository(): ScanRepository
}

fun widgetEntryPoint(context: android.content.Context): WidgetEntryPoint =
    EntryPointAccessors.fromApplication(context.applicationContext, WidgetEntryPoint::class.java)
