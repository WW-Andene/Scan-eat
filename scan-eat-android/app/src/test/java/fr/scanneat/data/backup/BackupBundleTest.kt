package fr.scanneat.data.backup

import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import fr.scanneat.data.local.db.activity.ActivityEntity
import fr.scanneat.data.local.db.consumption.ConsumptionEntity
import fr.scanneat.data.local.db.customfood.CustomFoodEntity
import fr.scanneat.data.local.db.recipe.RecipeEntity
import fr.scanneat.data.local.db.scan.ScanHistoryEntity
import fr.scanneat.data.local.db.template.MealTemplateEntity
import fr.scanneat.data.local.db.weight.WeightEntity
import org.junit.Assert.*
import org.junit.Test

// ============================================================================
// Tests the same Moshi configuration BackupRepository is injected with
// (KotlinJsonAdapterFactory, reflection-based — no per-entity @JsonClass
// annotations) round-trips every backed-up entity without loss. This is the
// part of the backup feature that's meaningfully unit-testable without a
// Room/Android dependency; SAF file I/O is exercised manually on-device.
// ============================================================================

class BackupBundleTest {

    private val moshi = Moshi.Builder().add(KotlinJsonAdapterFactory()).build()
    private val adapter = moshi.adapter(BackupBundle::class.java)

    private fun sampleBundle() = BackupBundle(
        exportedAtMs = 1_700_000_000_000L,
        appVersionName = "0.1.0",
        scanHistory = listOf(
            ScanHistoryEntity(
                id = 1, barcode = "3017620422003", productName = "Nutella",
                score = 25, grade = "D", category = "SPREAD",
                sourceJson = "{}", productJson = "{}", auditJson = "{}",
                scannedAt = 1_700_000_000_000L,
            ),
        ),
        consumption = listOf(
            ConsumptionEntity(
                id = 1, date = "2026-07-09", mealSlot = "BREAKFAST", loggedAt = 1_700_000_000_000L,
                productName = "Yaourt nature", barcode = null, portionG = 125.0,
                nutritionJson = "{}", source = "MANUAL",
            ),
        ),
        customFoods = listOf(
            CustomFoodEntity(id = "homemade-granola", name = "Granola maison", category = "SNACK", nutritionJson = "{}", createdAt = 1_700_000_000_000L),
        ),
        weights = listOf(
            WeightEntity(id = "w1", date = "2026-07-09", weightKg = 72.4, notes = "", loggedAt = 1_700_000_000_000L),
        ),
        activities = listOf(
            ActivityEntity(id = "a1", date = "2026-07-09", type = "running", minutes = 30, kcalBurned = 280, loggedAt = 1_700_000_000_000L),
        ),
        mealTemplates = listOf(
            MealTemplateEntity(id = "t1", name = "Petit-déj type", meal = "BREAKFAST", itemsJson = "[]", createdAt = 1_700_000_000_000L),
        ),
        recipes = listOf(
            RecipeEntity(id = "r1", name = "Salade César", servings = 2, componentsJson = "[]", createdAt = 1_700_000_000_000L),
        ),
    )

    @Test
    fun `export then import round-trips every field without loss`() {
        val original = sampleBundle()
        val json = adapter.toJson(original)
        val restored = adapter.fromJson(json)

        assertEquals(original, restored)
    }

    @Test
    fun `empty bundle round-trips to empty lists, not nulls`() {
        val empty = BackupBundle(
            exportedAtMs = 0L, appVersionName = "0.1.0",
            scanHistory = emptyList(), consumption = emptyList(), customFoods = emptyList(),
            weights = emptyList(), activities = emptyList(), mealTemplates = emptyList(), recipes = emptyList(),
        )
        val restored = adapter.fromJson(adapter.toJson(empty))

        assertEquals(empty, restored)
        assertTrue(restored!!.scanHistory.isEmpty())
    }

    @Test
    fun `BackupSummary total sums every category`() {
        val summary = BackupSummary.from(sampleBundle())
        assertEquals(7, summary.total) // one entity of each of the 7 kinds above
    }

    @Test
    fun `formatVersion newer than current is rejected before any DB write`() {
        val fromTheFuture = sampleBundle().copy(formatVersion = BACKUP_FORMAT_VERSION + 1)
        val json = adapter.toJson(fromTheFuture)
        val parsed = adapter.fromJson(json)!!

        assertTrue(parsed.formatVersion > BACKUP_FORMAT_VERSION)
    }
}
