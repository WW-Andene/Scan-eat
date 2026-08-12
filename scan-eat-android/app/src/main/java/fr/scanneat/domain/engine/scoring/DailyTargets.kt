package fr.scanneat.domain.engine.scoring

import fr.scanneat.domain.model.*
import java.time.temporal.ChronoUnit

data class DailyTargets(
    // Energy + macros
    val kcal: Double,
    val satFatGMax: Double,
    val freeSugarsGMax: Double,
    val freeSugarsGIdeal: Double,
    val saltGMax: Double = 5.0,
    val proteinGTarget: Double,
    // 30% of kcal from fat (WHO/EFSA acceptable macronutrient range 20-35%,
    // midpoint) - unlike satFatGMax (saturated fat only), this was previously
    // entirely absent, so the Journal's fat total had no target to show
    // (unlike calories/protein/carbs, which all had one).
    val fatGTarget: Double,
    val fiberGTarget: Double = 25.0,        // EFSA DRV 2017
    // Micros — EFSA Population Reference Intakes (adults, sex-averaged where split)
    val ironMgTarget: Double = 11.0,        // EFSA 2015: 11 mg men / 16 mg menstruating women
    val calciumMgTarget: Double = 950.0,    // EFSA DRV 2015
    val vitDUgTarget: Double = 15.0,        // EFSA DRV 2016
    val b12UgTarget: Double = 4.0,          // EFSA DRV 2015
    val magnesiumMgTarget: Double = 350.0,  // EFSA DRV 2015 (adult men); 300 women
    val potassiumMgTarget: Double = 3500.0, // EFSA DRV 2016 AI
    val zincMgTarget: Double = 9.4,         // EFSA DRV 2014 (adult men)
    val vitCMgTarget: Double = 95.0,        // EFSA DRV 2013
    val vitAUgTarget: Double = 750.0,       // EFSA PRI 2015 (adult men); 650 women
    val b9UgTarget: Double = 330.0,         // EFSA DRV 2014 (folate, both sexes)
    // Diet-driven target overrides - null unless the selected diet actually
    // implies a daily total (most diets are ingredient-exclusion only, not
    // macro-budget diets, so this stays null for them).
    val carbsGDailyMax: Double? = null,
    // General carb target shown in the Journal/Dashboard for every diet (unlike
    // carbsGDailyMax, which is only a hard cap for macro-budget diets like keto).
    // Previously the Journal only ever showed a carbs target for keto users -
    // everyone else saw a bare total with no "/target" the way calories,
    // protein and fat all had, even though the same AMDR-derived number was
    // trivially available as "whatever's left of kcal after protein+fat".
    val carbsGTarget: Double,
)

/**
 * Standard ~0.45 kg/week (1 lb/week) deficit/surplus - a 3500 kcal/lb energy
 * equivalence spread over 7 days. Applied on top of TDEE so the kcal target
 * actually reflects the Goal the user picked in Profile, instead of always
 * showing maintenance calories regardless of Lose/Gain.
 */
private const val GOAL_KCAL_ADJUSTMENT = 500.0

/**
 * User-requested: trimester-adapted targets instead of the same flat
 * "pregnancy" health-condition caution the whole pregnancy - see
 * [Profile.pregnancyStartDate]'s own doc comment. Deliberately general,
 * widely-cited figures (IOM Dietary Reference Intakes 2005/2011, EFSA DRVs)
 * - a genuine per-person prenatal nutrition plan needs an obstetrician/
 * dietitian, not this app; every UI surface showing these MUST keep the
 * "consult a healthcare professional" disclaimer visible alongside them.
 *
 * Weeks-based, not a fixed day count per trimester - T1 ends at 13 weeks
 * (91 days), T2 at 27 weeks (189 days), matching standard obstetric
 * convention rather than splitting ~280 days into three even thirds.
 */
enum class PregnancyTrimester { FIRST, SECOND, THIRD }

fun pregnancyTrimester(startDate: java.time.LocalDate, today: java.time.LocalDate): PregnancyTrimester {
    val days = ChronoUnit.DAYS.between(startDate, today).coerceAtLeast(0)
    return when {
        days < 91  -> PregnancyTrimester.FIRST
        days < 189 -> PregnancyTrimester.SECOND
        else       -> PregnancyTrimester.THIRD
    }
}

/** Extra kcal/day on top of maintenance TDEE - IOM 2005 EER pregnancy
 *  increments, commonly rounded to +0 / +340 / +450. */
private fun pregnancyKcalBonus(trimester: PregnancyTrimester): Double = when (trimester) {
    PregnancyTrimester.FIRST  -> 0.0
    PregnancyTrimester.SECOND -> 340.0
    PregnancyTrimester.THIRD  -> 450.0
}

/** Extra protein g/day - IOM 2005 RDA bump (+25 g/day), applied from the
 *  second trimester on (first-trimester needs are close to non-pregnant
 *  baseline in most guidance). */
private fun pregnancyProteinBonus(trimester: PregnancyTrimester): Double =
    if (trimester == PregnancyTrimester.FIRST) 0.0 else 25.0

/** Same gating [dailyTargets] applies internally - UI call sites (Profile's
 *  trimester badge) read this instead of re-deriving the same "pregnancy" +
 *  date-present check by hand. */
fun currentPregnancyTrimester(p: Profile): PregnancyTrimester? =
    if ("pregnancy" in p.healthConditions) p.pregnancyStartDate?.let { pregnancyTrimester(it, java.time.LocalDate.now()) } else null

/**
 * [weightKgOverride] recomputes every weight-derived figure (BMR, protein
 * PRI target) as if the profile's body weight were this value instead of
 * [Profile.weightKg] - used to show what the day's macro targets would look
 * like at the user's stated goal weight, alongside the targets for their
 * current weight. Diet/health-condition/sex/age fields are untouched.
 */
fun dailyTargets(p: Profile, weightKgOverride: Double? = null): DailyTargets? {
    val effectiveP = weightKgOverride?.let { p.copy(weightKg = it) } ?: p
    val tdee = tdeeKcal(effectiveP) ?: return null
    // No floor previously existed on the LOSE-goal deficit. bmrMifflinStJeor
    // floors BMR at 500 kcal and the lowest PAL is 1.40 (SEDENTARY), so a
    // valid but extreme profile (very short/light/old, sedentary) can produce
    // tdee ~700 kcal - a LOSE goal then subtracted the flat 500 kcal
    // adjustment down to ~200 kcal/day, an order of magnitude below any
    // recognized safe-minimum-intake floor (dietetic bodies commonly cite
    // ~1200 kcal/day as the low end nobody should go under without medical
    // supervision), displayed as a legitimate target with every downstream
    // budget (sat fat, sugar, protein/fat/carb split) scaled proportionally
    // to that implausible number. 1200 is a conservative, commonly-cited
    // floor, not a per-profile clinical calculation - this app doesn't
    // attempt medical weight-loss supervision, so a hard floor here is the
    // right kind of guardrail rather than a fake precision figure.
    // 1200 kcal floor now applies to every goal, not just LOSE - a MAINTAIN
    // profile can hit the exact same implausible-TDEE edge case the LOSE
    // branch was patched for (BMR floored at 500, PAL 1.40 -> TDEE ~700),
    // and previously showed that figure as a legitimate maintenance target
    // with every downstream budget scaled to it.
    val goalAdjustedKcalBase = when (p.goal) {
        Goal.LOSE     -> (tdee - GOAL_KCAL_ADJUSTMENT).coerceAtLeast(1200.0)
        Goal.GAIN     -> tdee + GOAL_KCAL_ADJUSTMENT
        Goal.MAINTAIN -> tdee.coerceAtLeast(1200.0)
    }
    // Only when the profile has both explicitly opted into "pregnancy" as a
    // health condition AND entered a start date (ProfileScreen only shows the
    // date field once the former is true - see this function's own header
    // comment on why this stays gated rather than inferred). Deliberately
    // ignores Goal.LOSE's deficit/1200 floor above during pregnancy - an
    // intentional calorie deficit isn't a scenario this app should be
    // computing a "target" for at all here.
    val trimester = currentPregnancyTrimester(p)
    val goalAdjustedKcal = if (trimester != null) tdee + pregnancyKcalBonus(trimester) else goalAdjustedKcalBase
    // proteinTargetG(), not the bare proteinPriG() EFSA minimum - see that
    // function's own doc comment for why the Journal/Dashboard target needs
    // to be activity/goal-aware while the scan-result "% of EFSA PRI" callout
    // (ProteinAndBudgetAdjustments.kt) stays tied to the literal PRI figure.
    val pri  = (proteinTargetG(effectiveP) ?: 0.0) + (trimester?.let { pregnancyProteinBonus(it) } ?: 0.0)
    // Sex-specific iron: menstruating women 16 mg/day (EFSA 2015). Uses the
    // profile's own isMenstruating answer rather than inferring from age —
    // the app already asks this explicitly (ProfileScreen shows the checkbox
    // for any female profile), so a woman in the 13-50 range who says she
    // isn't currently menstruating (menopause, pregnancy, hormonal
    // contraception, amenorrhea) got the wrong 16 mg target from the age
    // heuristic alone, and the checkbox answer had no effect anywhere.
    // Pregnancy overrides both: 27 mg/day (IOM 2001 RDA, commonly cited),
    // higher than either non-pregnant figure for the whole pregnancy, not
    // just later trimesters - iron-deficiency risk rises from early
    // pregnancy on as blood volume expands.
    val ironTarget = if (trimester != null) 27.0 else if (p.sex == Sex.FEMALE && p.isMenstruating) 16.0 else 11.0
    // Sex-specific zinc: women 7.5 mg/day (EFSA 2014)
    val zincTarget = if (p.sex == Sex.FEMALE) 7.5 else 9.4
    // Older adults: higher vitD target 20 µg/day ≥75y. Value unchanged, but the
    // source citation was wrong - this is the IOM/NAM Dietary Reference Intakes
    // (2011) higher-elderly-RDA figure, not an EFSA one; EFSA's own 2016 DRV
    // opinion sets a single flat 15 µg/day for all adults with no age bump.
    val vitDTarget = if ((p.ageYears ?: 0) >= 75) 20.0 else 15.0
    // Sex-specific vitamin C: 110 mg/day men, 95 mg/day women (EFSA DRV 2013) -
    // same sex-adjustment pattern as ironTarget/zincTarget/magnesiumMgTarget
    // above/below; vitCMgTarget previously stayed a flat 95mg for everyone.
    val vitCTarget = if (p.sex == Sex.FEMALE) 95.0 else 110.0
    // Sex-specific vitamin A: 750 µg RE/day men, 650 µg RE/day women (EFSA PRI
    // 2015) - same sex-adjustment pattern as ironTarget/zincTarget above. Folate
    // has no sex split in the EFSA DRV (330 µg/day for all adults).
    val vitATarget = if (p.sex == Sex.FEMALE) 650.0 else 750.0

    // WHO guidance caps free sugars/salt harder for these conditions than the
    // general-population default - halved rather than a made-up clinical value,
    // since an exact per-condition target needs a dietitian, not this app.
    val sugarsCapFraction = if ("diabetes" in p.healthConditions) 0.05 else 0.10
    val saltCap = if ("hypertension" in p.healthConditions) 3.0 else 5.0

    // Diet-driven overrides - Volek & Phinney clinical ketosis range is 20-50g net
    // carbs/day; carnivore structurally has no plant fiber intake to target.
    val carbsMax = if (p.diet == DietKey.KETO) 30.0 else null
    val fiberTarget = if (p.diet == DietKey.CARNIVORE) 0.0 else 25.0
    val fatTarget = (0.30 * goalAdjustedKcal / 9.0)
    // Remaining calories after protein+fat, converted at 4 kcal/g - the
    // standard "carbs fill the rest of the budget" AMDR approach, so every
    // diet gets a real number here instead of only keto's hard cap.
    val generalCarbsTarget = ((goalAdjustedKcal - pri * 4.0 - fatTarget * 9.0) / 4.0).coerceAtLeast(0.0)

    return DailyTargets(
        kcal              = goalAdjustedKcal,
        satFatGMax        = (0.10 * goalAdjustedKcal / 9.0),
        freeSugarsGMax    = (sugarsCapFraction * goalAdjustedKcal / 4.0),
        freeSugarsGIdeal  = (0.05 * goalAdjustedKcal / 4.0),
        saltGMax          = saltCap,
        proteinGTarget    = pri,
        fatGTarget        = fatTarget,
        fiberGTarget      = fiberTarget,
        carbsGDailyMax    = carbsMax,
        carbsGTarget      = carbsMax ?: generalCarbsTarget,
        ironMgTarget      = ironTarget,
        calciumMgTarget   = 950.0,
        vitDUgTarget      = vitDTarget,
        b12UgTarget       = 4.0,
        magnesiumMgTarget = if (p.sex == Sex.FEMALE) 300.0 else 350.0,
        potassiumMgTarget = 3500.0,
        zincMgTarget      = zincTarget,
        vitCMgTarget      = vitCTarget,
        vitAUgTarget      = vitATarget,
        // Pregnancy: 600 µg DFE/day (IOM 2001 RDA, commonly cited) vs 330 µg
        // baseline - neural tube defect prevention is highest-stakes in the
        // very first weeks, so this stays flat across all three trimesters
        // rather than only bumping from a given week on.
        b9UgTarget        = if (trimester != null) 600.0 else 330.0,
    )
}

/**
 * Rescales every kcal-derived field in [targets] onto [rawKcal] (a raw TDEE/
 * maintenance estimate, e.g. Biolism's body-composition-aware
 * computeMetabolics().tdeeDay) - satFat/sugars/fat/carbs are all a fixed
 * fraction of the day's calorie budget, so swapping in a richer TDEE without
 * also rescaling them left macros that no longer summed to the kcal figure
 * shown next to them. proteinGTarget (per-kg body weight, not kcal) and the
 * diet-driven carbsGDailyMax hard cap (a fixed clinical ceiling, not a kcal
 * fraction) are deliberately left untouched.
 *
 * [goal] re-applies the same ±500 kcal Lose/Gain adjustment dailyTargets()
 * applies to its own TDEE estimate - [rawKcal] is a plain maintenance
 * estimate with no notion of the user's goal, so passing it straight into
 * `kcal` here previously discarded that adjustment entirely: a Lose-goal
 * user with a valid Biolism profile (auto-populated from Profile as soon as
 * sex/age/height/weight exist, no Biolism screen visit required) was shown
 * maintenance calories as their "target", not the deficit dailyTargets()
 * itself would have computed.
 *
 * [pregnancyTrimesterOverride] closes a real gap found after the trimester-
 * adapted pregnancy targets shipped: dailyTargets() deliberately skips the
 * LOSE-goal deficit/floor entirely during pregnancy (adds a kcal bonus
 * instead - see its own doc comment), but every one of this function's 4
 * call sites passed `profile.goal` straight through with no pregnancy
 * awareness, so a pregnant user with a LOSE goal still saved from before
 * pregnancy (nothing clears it automatically) saw a straight caloric-
 * deficit target on every Biolism/premium-linked screen (Dashboard, Diary,
 * Widget) - exactly what the pregnancy branch exists to prevent. Callers
 * pass `currentPregnancyTrimester(profile)` here instead of threading Goal
 * directly, so this function can apply the identical bonus-not-deficit rule
 * dailyTargets() already does.
 */
fun DailyTargets.withKcalOverride(rawKcal: Double, goal: Goal, pregnancyTrimesterOverride: PregnancyTrimester? = null): DailyTargets {
    if (rawKcal <= 0.0 || kcal <= 0.0) return this
    // Same 1200 kcal safe-minimum floor as dailyTargets()'s own floor (now
    // applied to every goal, not just LOSE - see that function's own updated
    // comment) - a rich Biolism TDEE for a very light/short profile is just
    // as capable of driving the post-deficit figure implausibly low, and
    // this function is the one place that number can reach the UI without
    // ever passing back through dailyTargets()'s own floor.
    val newKcal = if (pregnancyTrimesterOverride != null) {
        rawKcal + pregnancyKcalBonus(pregnancyTrimesterOverride)
    } else when (goal) {
        Goal.LOSE     -> (rawKcal - GOAL_KCAL_ADJUSTMENT).coerceAtLeast(1200.0)
        Goal.GAIN     -> rawKcal + GOAL_KCAL_ADJUSTMENT
        Goal.MAINTAIN -> rawKcal.coerceAtLeast(1200.0)
    }
    val ratio = newKcal / kcal
    val newFatTarget = fatGTarget * ratio
    val newCarbsTarget = if (carbsGDailyMax != null) carbsGTarget
        else ((newKcal - proteinGTarget * 4.0 - newFatTarget * 9.0) / 4.0).coerceAtLeast(0.0)
    return copy(
        kcal             = newKcal,
        satFatGMax       = satFatGMax * ratio,
        freeSugarsGMax   = freeSugarsGMax * ratio,
        freeSugarsGIdeal = freeSugarsGIdeal * ratio,
        fatGTarget       = newFatTarget,
        carbsGTarget     = newCarbsTarget,
    )
}
