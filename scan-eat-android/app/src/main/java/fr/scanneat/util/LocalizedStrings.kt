package fr.scanneat.util

import android.content.Context
import android.content.res.Configuration
import java.util.Locale

// Non-Compose contexts (WorkManager workers, Glance widgets) have no stringResource()
// and Context.getString() alone follows the device locale, not this app's own in-app
// language setting (Settings > Language, independent of device locale everywhere else
// this session) - a locale-overridden resources instance is the only way to respect
// it here too. Shared by ReminderWorker and TodayWidget instead of each keeping its
// own private copy.

fun localizedString(context: Context, lang: String, resId: Int): String {
    val config = Configuration(context.resources.configuration)
    config.setLocale(Locale(lang))
    return context.createConfigurationContext(config).resources.getString(resId)
}

fun localizedString(context: Context, lang: String, resId: Int, vararg args: Any): String {
    val config = Configuration(context.resources.configuration)
    config.setLocale(Locale(lang))
    return context.createConfigurationContext(config).resources.getString(resId, *args)
}

fun localizedQuantityString(context: Context, lang: String, resId: Int, quantity: Int, vararg args: Any): String {
    val config = Configuration(context.resources.configuration)
    config.setLocale(Locale(lang))
    return context.createConfigurationContext(config).resources.getQuantityString(resId, quantity, *args)
}
