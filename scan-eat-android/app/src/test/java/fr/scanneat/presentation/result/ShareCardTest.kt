package fr.scanneat.presentation.result

import fr.scanneat.domain.model.Grade
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Roadmap A9: the card renders for long names and a blocked verdict without throwing. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ShareCardTest {
    @Test fun `renders a 1080 square card`() {
        val bmp = renderShareCard(ShareCardData(
            productName = "Biscuits fourrés au chocolat et aux noisettes, recette extra-croustillante, paquet familial de 12",
            score = 23, grade = Grade.E, personalLine = "Contient : Arachides · Traces possibles : Fruits à coque (allergie sévère)",
            personalBlocked = true, sourceLine = "Données : Open Food Facts", footer = "Noté par Scan'eat",
        ))
        assertEquals(1080, bmp.width); assertEquals(1080, bmp.height)
    }
}
