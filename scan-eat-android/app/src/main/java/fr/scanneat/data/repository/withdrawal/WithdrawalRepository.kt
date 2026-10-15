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
import kotlinx.coroutines.flow.first
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
        if (plan == null) { p.remove(k("tob_plan", profileId)); p.remove(k("tob_log", profileId)); p.remove(k("tob_last", profileId)); p.remove(k("tob_last_prev", profileId)) }
        else p[k("tob_plan", profileId)] = planAdapter.toJson(TobaccoPlanJson(plan.startDate.toString(), plan.baselinePerDay, plan.packPriceEuros, plan.cigarettesPerPack, plan.targetPerDay))
    }

    fun tobaccoLog(profileId: String): Flow<Map<LocalDate, Int>> = data.map { p -> parseLog(p[k("tob_log", profileId)]) }.distinctUntilChanged()

    fun lastSmoke(profileId: String): Flow<LocalDateTime?> = data.map { p ->
        p[k("tob_last", profileId)]?.toLongOrNull()?.let { LocalDateTime.ofInstant(Instant.ofEpochMilli(it), ZoneId.systemDefault()) }
    }.distinctUntilChanged()

    /** +1 also stamps the last-cigarette time (the milestone clock); -1 undoes the latest +1,
     *  putting the clock back where it was (a mistaken tap no longer resets months of milestones). */
    suspend fun addCigarette(profileId: String, delta: Int, date: LocalDate = LocalDate.now()) = store.edit { p ->
        val log = parseLog(p[k("tob_log", profileId)]).toMutableMap()
        log[date] = ((log[date] ?: 0) + delta).coerceAtLeast(0)
        val cutoff = LocalDate.now().minusDays(LOG_DAYS)
        p[k("tob_log", profileId)] = log.filterKeys { !it.isBefore(cutoff) }.entries.joinToString(";") { "${it.key}=${it.value}" }
        val prevKey = k("tob_last_prev", profileId)
        if (delta > 0) {
            // Remember the clock before this cigarette ("" = none yet) so an undo can restore it.
            p[prevKey] = p[k("tob_last", profileId)] ?: ""
            p[k("tob_last", profileId)] = System.currentTimeMillis().toString()
        } else if (delta < 0) {
            // Only right after a +1: an older count correction leaves the clock alone.
            p[prevKey]?.let { prev ->
                if (prev.isEmpty()) p.remove(k("tob_last", profileId)) else p[k("tob_last", profileId)] = prev
                p.remove(prevKey)
            }
        }
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

    // ---- Caffeine reduction ----
    internal data class CaffeineTaperJson(val start: String, val startMg: Int, val targetMg: Int, val weeks: Int)
    private val cafAdapter = moshi.adapter(CaffeineTaperJson::class.java)

    fun caffeineTaper(profileId: String): Flow<fr.scanneat.domain.engine.withdrawal.CaffeineTaper?> = data.map { p ->
        p[k("caf_taper", profileId)]?.let { runCatching { cafAdapter.fromJson(it) }.getOrNull() }
            ?.let { j -> runCatching { fr.scanneat.domain.engine.withdrawal.CaffeineTaper(LocalDate.parse(j.start), j.startMg, j.targetMg, j.weeks) }.getOrNull() }
    }.distinctUntilChanged()

    suspend fun setCaffeineTaper(profileId: String, t: fr.scanneat.domain.engine.withdrawal.CaffeineTaper?) = store.edit { p ->
        if (t == null) p.remove(k("caf_taper", profileId))
        else p[k("caf_taper", profileId)] = cafAdapter.toJson(CaffeineTaperJson(t.startDate.toString(), t.startMg, t.targetMg, t.weeks))
    }

    // ---- Alcohol-free challenge ----
    fun alcoholChallenge(profileId: String): Flow<fr.scanneat.domain.engine.withdrawal.AlcoholChallenge?> = data.map { p ->
        p[k("alc_challenge", profileId)]?.split('|')?.takeIf { it.size == 2 }?.let { (d, n) ->
            runCatching { fr.scanneat.domain.engine.withdrawal.AlcoholChallenge(LocalDate.parse(d), n.toInt()) }.getOrNull()
        }
    }.distinctUntilChanged()

    suspend fun setAlcoholChallenge(profileId: String, c: fr.scanneat.domain.engine.withdrawal.AlcoholChallenge?) = store.edit { p ->
        if (c == null) p.remove(k("alc_challenge", profileId)) else p[k("alc_challenge", profileId)] = "${c.startDate}|${c.days}"
    }

    // ---- Backup ----
    suspend fun exportForBackup(profileId: String): fr.scanneat.data.backup.WithdrawalBackup? {
        val plan = tobaccoPlan(profileId).first()
        val log = tobaccoLog(profileId).first()
        val last = data.first()[k("tob_last", profileId)]?.toLongOrNull()
        val taper = taperPlan(profileId).first()
        val caf = caffeineTaper(profileId).first()
        val alc = alcoholChallenge(profileId).first()
        if (plan == null && log.isEmpty() && taper == null && caf == null && alc == null) return null
        return fr.scanneat.data.backup.WithdrawalBackup(
            tobacco = plan?.let { fr.scanneat.data.backup.TobaccoPlanBackup(it.startDate.toString(), it.baselinePerDay, it.packPriceEuros, it.cigarettesPerPack, it.targetPerDay) },
            tobaccoLog = log.mapKeys { it.key.toString() },
            lastSmokeMs = last,
            taper = taper?.let { t -> fr.scanneat.data.backup.TaperPlanBackup(t.medicationName, t.sortedSteps.map { fr.scanneat.data.backup.TaperStepBackup(it.startDate.toString(), it.dose) }, t.note) },
            caffeine = caf?.let { fr.scanneat.data.backup.CaffeineTaperBackup(it.startDate.toString(), it.startMg, it.targetMg, it.weeks) },
            alcoholChallengeStart = alc?.startDate?.toString(),
            alcoholChallengeDays = alc?.days,
        )
    }

    suspend fun importForBackup(profileId: String, b: fr.scanneat.data.backup.WithdrawalBackup) {
        val plan = b.tobacco?.let { t -> runCatching { TobaccoPlan(LocalDate.parse(t.startDate), t.baselinePerDay, t.packPriceEuros, t.cigarettesPerPack, t.targetPerDay) }.getOrNull() }
        setTobaccoPlan(profileId, plan)
        store.edit { p ->
            val log = b.tobaccoLog.mapNotNull { (d, n) -> runCatching { LocalDate.parse(d) to n.coerceAtLeast(0) }.getOrNull() }
            if (log.isEmpty()) p.remove(k("tob_log", profileId)) else p[k("tob_log", profileId)] = log.joinToString(";") { "${it.first}=${it.second}" }
            b.lastSmokeMs?.let { p[k("tob_last", profileId)] = it.toString() } ?: p.remove(k("tob_last", profileId))
        }
        setTaperPlan(profileId, b.taper?.let { t ->
            TaperPlan(t.medicationName, t.steps.mapNotNull { s -> runCatching { TaperStep(LocalDate.parse(s.startDate), s.dose) }.getOrNull() }, t.note)
        }?.takeIf { it.steps.isNotEmpty() })
        setAlcoholChallenge(profileId, b.alcoholChallengeStart?.let { d -> runCatching { fr.scanneat.domain.engine.withdrawal.AlcoholChallenge(LocalDate.parse(d), b.alcoholChallengeDays ?: 30) }.getOrNull() })
        setCaffeineTaper(profileId, b.caffeine?.let { c -> runCatching { fr.scanneat.domain.engine.withdrawal.CaffeineTaper(LocalDate.parse(c.startDate), c.startMg, c.targetMg, c.weeks) }.getOrNull() })
    }

    private companion object { const val LOG_DAYS = 400L }
}
