package fr.scanneat.presentation.shell

/** String route constants for the app's single NavHost (see [AppNavGraph]). */
object AppRoutes {
    const val ONBOARDING    = "onboarding"
    const val SCAN          = "scan"
    const val DIARY         = "diary"
    const val DASHBOARD     = "dashboard"
    const val BIOLISM       = "biolism"
    const val SETTINGS      = "settings"
    // fresh=true only from a just-completed scan (ScanScreen.onResultReady) - see
    // ResultViewModel's use of it to gate ComparisonRepository.arm()/compare().
    // Every other entry point (History/Favorites/Dashboard's "top scanned" tile)
    // routes here too, to view an old entry, and defaults to fresh=false.
    const val RESULT        = "result/{scanId}?fresh={fresh}"
    const val SCAN_PROFILE  = "scan_profile"
    const val RECIPES       = "recipes"
    const val TEMPLATES     = "templates"
    const val MEAL_PLAN     = "meal_plan"
    const val GROCERY       = "grocery"
    const val CUSTOM_FOODS  = "custom_foods"
    const val SCAN_HISTORY  = "scan_history"
    const val FAVORITES     = "favorites"
    const val CALENDAR      = "calendar"
    const val REMINDERS     = "reminders"

    fun result(scanId: Long, fresh: Boolean = false) = "result/$scanId?fresh=$fresh"
}
