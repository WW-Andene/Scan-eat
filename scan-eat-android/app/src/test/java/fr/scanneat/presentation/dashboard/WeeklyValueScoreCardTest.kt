package fr.scanneat.presentation.dashboard

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import fr.scanneat.domain.engine.expense.ValueScore
import fr.scanneat.presentation.dashboard.cards.WeeklyValueScoreCard
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

// JVM Compose UI test (Robolectric, no device): renders the real card with the real string
// resources and checks the singular/plural wording fixed in the plurals conversion.
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "fr")
class WeeklyValueScoreCardFrenchTest {
    @get:Rule val rule = createComposeRule()

    @Test
    fun `french singular and plural`() {
        rule.setContent { WeeklyValueScoreCard(mapOf(ValueScore.GREAT to 1, ValueScore.POOR to 1)) }
        rule.onNodeWithText("1 bonne affaire").assertIsDisplayed()
        rule.onNodeWithText("1 achat cher").assertIsDisplayed()
    }

    @Test
    fun `french plural counts good and great together`() {
        rule.setContent { WeeklyValueScoreCard(mapOf(ValueScore.GREAT to 2, ValueScore.GOOD to 1, ValueScore.POOR to 4)) }
        rule.onNodeWithText("3 bonnes affaires").assertIsDisplayed()
        rule.onNodeWithText("4 achats chers").assertIsDisplayed()
    }
}

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "en")
class WeeklyValueScoreCardEnglishTest {
    @get:Rule val rule = createComposeRule()

    @Test
    fun `english singular and plural`() {
        rule.setContent { WeeklyValueScoreCard(mapOf(ValueScore.GOOD to 1, ValueScore.POOR to 3)) }
        rule.onNodeWithText("1 great buy").assertIsDisplayed()
        rule.onNodeWithText("3 pricey buys").assertIsDisplayed()
    }
}
