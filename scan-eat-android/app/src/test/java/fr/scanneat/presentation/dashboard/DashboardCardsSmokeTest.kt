package fr.scanneat.presentation.dashboard

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithText
import androidx.test.core.app.ApplicationProvider
import fr.scanneat.R
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.createComposeRule
import fr.scanneat.domain.engine.dashboard.DiversityLevel
import fr.scanneat.domain.engine.dashboard.FoodDiversityResult
import fr.scanneat.domain.engine.dashboard.WeekOverWeekDelta
import fr.scanneat.domain.engine.dashboard.monthlyRollup
import fr.scanneat.domain.engine.dashboard.weeklyRollup
import fr.scanneat.domain.engine.scoring.dailyTargets
import fr.scanneat.domain.model.ActivityLevel
import fr.scanneat.domain.model.ConsumedNutrition
import fr.scanneat.domain.model.Profile
import fr.scanneat.domain.model.Sex
import fr.scanneat.presentation.dashboard.cards.CalorieBalanceCard
import fr.scanneat.presentation.dashboard.cards.FoodDiversityCard
import fr.scanneat.presentation.dashboard.cards.MicronutrientCard
import fr.scanneat.presentation.dashboard.cards.MonthlyTrendCard
import fr.scanneat.presentation.dashboard.cards.NutrientBudgetCard
import fr.scanneat.presentation.dashboard.cards.TodayMacroCard
import fr.scanneat.presentation.dashboard.cards.WeekDeltaCard
import fr.scanneat.presentation.dashboard.cards.WeeklyBarsCard
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

// Render smoke tests (JVM, Robolectric): each stateless dashboard card must compose without
// throwing for empty, zero and extreme inputs - catching divide-by-zero, bad format arguments
// and missing resources that unit tests on the engines cannot see. Run in both app languages.

private val ZERO = ConsumedNutrition(0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0)
private val BIG = ConsumedNutrition(
    9_999.0, 999.0, 999.0, 999.0, 99.0, 999.0, 99.0, 99.0, 999.0, 9_999.0, 999.0, 999.0,
    magnesiumMg = 9_999.0, potassiumMg = 99_999.0, zincMg = 999.0, vitCMg = 9_999.0, vitAUg = 99_999.0, b9Ug = 9_999.0, caffeineMg = 9_999.0,
)
private val NORMAL = ConsumedNutrition(
    1_850.0, 90.0, 220.0, 65.0, 24.0, 48.0, 18.0, 5.2, 11.0, 800.0, 6.0, 3.5,
    magnesiumMg = 310.0, potassiumMg = 2_900.0, zincMg = 9.0, vitCMg = 80.0, vitAUg = 700.0, b9Ug = 300.0,
)
private val TARGETS = dailyTargets(Profile(sex = Sex.FEMALE, ageYears = 30, heightCm = 165.0, weightKg = 60.0, activityLevel = ActivityLevel.MODERATELY_ACTIVE))!!

abstract class DashboardCardsSmoke {
    @get:Rule val rule = createComposeRule()

    // The rule allows one setContent per test: host the card in observable state and swap it per case.
    private var current by mutableStateOf<@Composable () -> Unit>({})

    @Before fun host() {
        rule.setContent { current() }
    }

    private fun render(content: @Composable () -> Unit) {
        rule.runOnIdle { current = content }
        rule.waitForIdle()
    }

    @Test fun todayMacro() {
        for (t in listOf(ZERO, NORMAL, BIG)) for (tg in listOf(null, TARGETS)) render { TodayMacroCard(t, tg) }
    }

    @Test fun nutrientBudget() {
        for (t in listOf(ZERO, NORMAL, BIG)) render { NutrientBudgetCard(t, TARGETS) }
    }

    @Test fun micronutrients() {
        for (t in listOf(ZERO, NORMAL, BIG)) for (tg in listOf(null, TARGETS)) render { MicronutrientCard(t, tg) }
    }

    @Test fun calorieBalance() {
        for (net in listOf(-4_000.0, -50.0, 0.0, 200.0, 5_000.0)) for (streak in listOf(0, 1, 9_999)) {
            render { CalorieBalanceCard(CalorieBalance(kcalIn = 1_800.0, tdee = 2_000.0, tdeeFromBiolism = false, net = net), streak, longestStreak = streak) }
        }
    }

    @Test fun weekDelta() {
        for (k in listOf(-900.5, 0.0, 12.3, 1e6)) render { WeekDeltaCard(WeekOverWeekDelta(k, -k / 10, k / 5, 0.0)) }
    }

    @Test fun foodDiversity() {
        for (level in DiversityLevel.entries) for (n in listOf(0, 1, 7, 99)) render { FoodDiversityCard(FoodDiversityResult(n, level)) }
    }

    // Guards against vacuous passes: swapped-in content really composes, with the locale's own text.
    @Test fun swappedContentIsActuallyRendered() {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        render { FoodDiversityCard(FoodDiversityResult(15, DiversityLevel.GOOD)) }
        rule.onNodeWithText(ctx.getString(R.string.dashboard_diversity_good), substring = true).assertIsDisplayed()
        render { FoodDiversityCard(FoodDiversityResult(2, DiversityLevel.LOW)) }
        rule.onNodeWithText(ctx.getString(R.string.dashboard_diversity_low), substring = true).assertIsDisplayed()
    }

    @Test fun rollupCardsWithNoData() {
        for (lang in listOf("fr", "en")) for (tg in listOf(null, TARGETS)) {
            render { WeeklyBarsCard(weeklyRollup(emptyList()), tg, lang) }
            render { MonthlyTrendCard(monthlyRollup(emptyList()), tg, lang) }
        }
    }
}

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "fr")
class DashboardCardsSmokeFrenchTest : DashboardCardsSmoke()

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "en")
class DashboardCardsSmokeEnglishTest : DashboardCardsSmoke()
