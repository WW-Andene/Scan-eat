package fr.scanneat.presentation.dashboard

import fr.scanneat.R
import fr.scanneat.presentation.diary.components.DiaryTab

/** Roadmap A11: dashboard cards the user can hide and reorder (the safety center is not one of them). */
internal enum class DashboardCard(val labelRes: Int, val module: DiaryTab? = null) {
    CALORIES(R.string.dash_card_calories),
    WEEKLY_SCORE(R.string.dash_card_weekly_score),
    MACROS(R.string.dash_card_macros),
    OTHER_TRACKERS(R.string.dash_card_other_trackers),
    MICRONUTRIENTS(R.string.dash_card_micronutrients),
    BUDGET(R.string.dash_card_budget),
    WEEKLY_BARS(R.string.dash_card_weekly_bars),
    BEST_WORST(R.string.dash_card_best_worst),
    MONTHLY(R.string.dash_card_monthly),
    WEEK_DELTA(R.string.dash_card_week_delta),
    INSIGHT(R.string.dash_card_insight, DiaryTab.WEIGHT),
    VALUE_SCORE(R.string.dash_card_value_score, DiaryTab.EXPENSES),
    DIVERSITY(R.string.dash_card_diversity),
    WEIGHT(R.string.dash_card_weight, DiaryTab.WEIGHT),
    EXPENSES(R.string.dash_card_expenses, DiaryTab.EXPENSES),
    GAPS(R.string.dash_card_gaps),
    CHRONIC_GAPS(R.string.dash_card_chronic_gaps),
    NEVER_LOGGED(R.string.dash_card_never_logged),
}

internal data class DashboardLayout(val order: List<DashboardCard>, val hidden: Set<DashboardCard>) {
    /** Cards to draw: user order, minus hidden ones and those of unfollowed modules (A4). */
    fun visible(hiddenModules: Set<DiaryTab>): List<DashboardCard> =
        order.filter { it !in hidden && (it.module == null || it.module !in hiddenModules) }

    fun moved(card: DashboardCard, by: Int): DashboardLayout {
        val i = order.indexOf(card); val j = (i + by).coerceIn(0, order.lastIndex)
        if (i < 0 || i == j) return this
        return copy(order = order.toMutableList().apply { add(j, removeAt(i)) })
    }

    fun toggled(card: DashboardCard) = copy(hidden = if (card in hidden) hidden - card else hidden + card)

    /** "NAME" for visible, "!NAME" for hidden, in order. */
    fun serialize(): String = order.joinToString(",") { if (it in hidden) "!${it.name}" else it.name }

    companion object {
        val DEFAULT = DashboardLayout(DashboardCard.entries, emptySet())

        /** Unknown names are dropped; cards missing from the stored value (added in a later version) are appended visible. */
        fun parse(csv: String): DashboardLayout {
            if (csv.isBlank()) return DEFAULT
            val order = mutableListOf<DashboardCard>(); val hidden = mutableSetOf<DashboardCard>()
            csv.split(",").map { it.trim() }.forEach { token ->
                val card = DashboardCard.entries.firstOrNull { it.name == token.removePrefix("!") } ?: return@forEach
                if (card in order) return@forEach
                order += card; if (token.startsWith("!")) hidden += card
            }
            order += DashboardCard.entries.filter { it !in order }
            return DashboardLayout(order, hidden)
        }
    }
}
