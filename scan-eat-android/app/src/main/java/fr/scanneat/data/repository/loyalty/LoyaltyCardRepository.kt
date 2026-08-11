package fr.scanneat.data.repository.loyalty

import android.content.Context
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import com.squareup.moshi.JsonClass
import com.squareup.moshi.Moshi
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import java.io.IOException
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

// ============================================================================
// LOYALTY CARD REPOSITORY — user-requested: keep store loyalty cards (e.g.
// Carrefour) on hand so the physical card isn't needed at checkout. [code] is
// captured either by scanning the card's own barcode (reusing the app's
// existing MLKit barcode reader, same as a product scan) or typed manually
// when a card has no scannable barcode - no OCR/card-recognition model of any
// kind, this only stores and re-displays whatever code the barcode reader (or
// the user) already read.
// ============================================================================

@JsonClass(generateAdapter = true)
data class LoyaltyCard(val id: String, val storeName: String, val code: String)

private val Context.loyaltyCardDataStore by preferencesDataStore(name = "loyalty_cards")

private fun cardsKey(profileId: String) =
    stringPreferencesKey(if (profileId == "default") "loyalty_cards" else "loyalty_cards_${profileId}")

@Singleton
class LoyaltyCardRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val moshi: Moshi,
) {
    private val store = context.loyaltyCardDataStore
    private val listType = com.squareup.moshi.Types.newParameterizedType(List::class.java, LoyaltyCard::class.java)
    private val adapter = moshi.adapter<List<LoyaltyCard>>(listType)

    private val storeData: Flow<Preferences> = store.data.catch { e ->
        if (e is IOException) emit(emptyPreferences()) else throw e
    }

    fun cards(profileId: String = "default"): Flow<List<LoyaltyCard>> =
        storeData.map { parse(it[cardsKey(profileId)]) }.distinctUntilChanged()

    suspend fun add(storeName: String, code: String, profileId: String = "default") {
        if (storeName.isBlank() || code.isBlank()) return
        store.edit { prefs ->
            val k = cardsKey(profileId)
            val current = parse(prefs[k]).toMutableList()
            current.add(LoyaltyCard(UUID.randomUUID().toString(), storeName.trim(), code.trim()))
            prefs[k] = serialize(current)
        }
    }

    suspend fun remove(id: String, profileId: String = "default") {
        store.edit { prefs ->
            val k = cardsKey(profileId)
            prefs[k] = serialize(parse(prefs[k]).filterNot { it.id == id })
        }
    }

    private fun parse(raw: String?): List<LoyaltyCard> {
        if (raw.isNullOrEmpty()) return emptyList()
        return runCatching { adapter.fromJson(raw) }.getOrNull() ?: emptyList()
    }

    private fun serialize(list: List<LoyaltyCard>): String = adapter.toJson(list)
}
