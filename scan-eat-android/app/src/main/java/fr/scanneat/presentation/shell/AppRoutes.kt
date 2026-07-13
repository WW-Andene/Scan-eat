package fr.scanneat.presentation.shell

/** String route constants for the app's single NavHost (see [AppNavGraph]). */
object AppRoutes {
    const val ONBOARDING    = "onboarding"
    const val SCAN          = "scan"
    const val DIARY         = "diary"
    const val DASHBOARD     = "dashboard"
    const val BIOLISM       = "biolism"
    const val SETTINGS      = "settings"
    const val RESULT        = "result/{scanId}"
    const val SCAN_PROFILE  = "scan_profile"
    const val RECIPES       = "recipes"
    const val TEMPLATES     = "templates"
    const val MEAL_PLAN     = "meal_plan"
    const val GROCERY       = "grocery"
    const val CUSTOM_FOODS  = "custom_foods"
    const val SCAN_HISTORY  = "scan_history"
    const val FAVORITES     = "favorites"
    const val CALENDAR      = "calendar"

    fun result(scanId: Long) = "result/$scanId"
}
