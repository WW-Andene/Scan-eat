package fr.scanneat.data.repository.withdrawal

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.squareup.moshi.Moshi
import dagger.hilt.android.qualifiers.ApplicationContext
import fr.scanneat.data.local.prefs.SecureFieldCipher
import fr.scanneat.domain.engine.withdrawal.TaperPlan
import fr.scanneat.domain.engine.withdrawal.TaperStep
import fr.scanneat.domain.engine.withdrawal.TobaccoPlan
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import java.io.IOException
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Singleton

private val Context.withdrawalDataStore by preferencesDataStore(name = "withdrawal")

/** Tobacco plan + daily log, and the prescribed medication taper plan, per profile. */
@Singleton
class WithdrawalRepository @Inject constructor(
    @ApplicationContext context: Context,
    moshi: Moshi,
) {
    private val store = context.withdrawalDataStore
    private val data: Flow<Preferences> = store.data.catch { e -> if (e is IOException) emit(emptyPreferences()) else throw e }

    private fun k(name: String, profileId: String) = stringPreferencesKey("${name}_$profileId")

    // ---- Tobacco ----
    internal data class TobaccoPlanJson(val start: String, val base: Int, val price: Double, val pack: Int, val target: Int?)
    private val planAdapter = moshi.adapter(TobaccoPlanJson::class.java)

    fun tobaccoPlan(profileId: String): Flow<TobaccoPlan?> = data.map { p ->
        p[k("tob_plan", profileId)]?.let { runCatching { planAdapter.fromJson(it) }.getOrNull() }?.let {
            TobaccoPlan(LocalDate.parse(it.start), it.base, it.price, it.pack, it.target)
        }
    }.distinctUntilChanged()

    suspend fun setTobaccoPlan(profileId: String, plan: TobaccoPlan?) = store.edit { p ->
        if (plan == null) { p.remove(k("tob_plan", profileId)); p.remove(k("tob_log", profileId)); p.remove(k("tob_last", profileId)) }
        else p[k("tob_plan", profileId)] = planAdapter.toJson(TobaccoPlanJson(plan.startDate.toString(), plan.baselinePerDay, plan.packPriceEuros, plan.cigarettesPerPack, plan.targetPerDay))
    }

    fun tobaccoLog(profileId: String): Flow<Map<LocalDate, Int>> = data.map { p -> parseLog(p[k("tob_log", profileId)]) }.distinctUntilChanged()

    fun lastSmoke(profileId: String): Flow<LocalDateTime?> = data.map { p ->
        p[k("tob_last", profileId)]?.toLongOrNull()?.let { LocalDateTime.ofInstant(Instant.ofEpochMilli(it), ZoneId.systemDefault()) }
    }.distinctUntilChanged()

    /** +1 also stamps the last-cigarette time (the milestone clock); -1 only corrects the count. */
    suspend fun addCigarette(profileId: String, delta: Int, date: LocalDate = LocalDate.now()) = store.edit { p ->
        val log = parseLog(p[k("tob_log", profileId)]).toMutableMap()
        log[date] = ((log[date] ?: 0) + delta).coerceAtLeast(0)
        val cutoff = LocalDate.now().minusDays(LOG_DAYS)
        p[k("tob_log", profileId)] = log.filterKeys { !it.isBefore(cutoff) }.entries.joinToString(";") { "${it.key}=${it.value}" }
        if (delta > 0) p[k("tob_last", profileId)] = System.currentTimeMillis().toString()
    }

    private fun parseLog(raw: String?): Map<LocalDate, Int> = raw.orEmpty().split(';').mapNotNull { e ->
        val (d, n) = e.split('=').takeIf { it.size == 2 } ?: return@mapNotNull null
        runCatching { LocalDate.parse(d) to n.toInt() }.getOrNull()
    }.toMap()

    // ---- Prescribed taper (encrypted at rest: the medication name is health data) ----
    internal data class StepJson(val start: String, val dose: String)
    internal data class TaperJson(val name: String, val steps: List<StepJson>, val note: String)
    private val taperAdapter = moshi.adapter(TaperJson::class.java)

    fun taperPlan(profileId: String): Flow<TaperPlan?> = data.map { p ->
        p[k("taper", profileId)]?.let { SecureFieldCipher.decryptOrNull(it) }
            ?.let { runCatching { taperAdapter.fromJson(it) }.getOrNull() }
            ?.let { j -> TaperPlan(j.name, j.steps.mapNotNull { s -> runCatching { TaperStep(LocalDate.parse(s.start), s.dose) }.getOrNull() }, j.note) }
    }.distinctUntilChanged()

    suspend fun setTaperPlan(profileId: String, plan: TaperPlan?) = store.edit { p ->
        if (plan == null) p.remove(k("taper", profileId))
        else p[k("taper", profileId)] = SecureFieldCipher.encrypt(taperAdapter.toJson(
            TaperJson(plan.medicationName, plan.sortedSteps.map { StepJson(it.startDate.toString(), it.dose) }, plan.note)))
    }

    private companion object { const val LOG_DAYS = 400L }
}
