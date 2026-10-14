package fr.scanneat.presentation.sources

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.core.app.ApplicationProvider
import android.content.Context
import fr.scanneat.R
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Roadmap A1/N2: the sources screen composes and shows its methodology card. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "fr")
class SourcesScreenSmokeTest {
    @get:Rule val rule = createComposeRule()

    @Test fun `renders methodology and every source has its texts`() {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        rule.setContent { SourcesScreen(onBack = {}) }
        rule.onNodeWithText(ctx.getString(R.string.sources_method_title)).assertExists()
        DATA_SOURCES.forEach {
            assert(ctx.getString(it.nameRes).isNotBlank() && ctx.getString(it.usageRes).isNotBlank())
            assert(it.url.startsWith("https://"))
        }
    }
}
