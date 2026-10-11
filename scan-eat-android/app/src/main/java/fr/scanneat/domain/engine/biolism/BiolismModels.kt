package fr.scanneat.domain.engine.biolism

// ============================================================================
// BIOLISM MODELS — data classes for all computed results
// ============================================================================

enum class BiolismSex { MALE, FEMALE, NOT_SPECIFIED }
enum class BiolismBmiCategory { UNDERWEIGHT, NORMAL, OVERWEIGHT, OBESE }

data class BiolismProfile(
    val sex: BiolismSex = BiolismSex.NOT_SPECIFIED,
    val ageYears: Int = 0,
    val heightCm: Double = 0.0,
    val weightKg: Double = 0.0,
    val activityId: String = "sedentary",
    val ethnicityId: String = "caucasian",
    // Circumferences (cm) — optional; unlock Navy BF%, WHtR, WHR
    val waistCm: Double = 0.0,
    val hipCm: Double = 0.0,
    val neckCm: Double = 0.0,
    // Female reproductive cycle (day 1–28)
    val cycleDay: Int = 14,
) {
    val isValid get() = sex != BiolismSex.NOT_SPECIFIED && ageYears > 0 && heightCm > 0 && weightKg > 0
    val activityMeta get() = ACTIVITY_LEVELS.firstOrNull { it.id == activityId } ?: ACTIVITY_LEVELS[0]
    val ethnicMeta   get() = ETHNICITY_OPTIONS.firstOrNull { it.id == ethnicityId } ?: ETHNICITY_OPTIONS[0]
}

data class SubstrateResult(
    val fatFrac: Double,
    val carbFrac: Double,
    val protFrac: Double,
    val rq: Double,          // composite RQ
    val npRq: Double,        // non-protein RQ (input to Frayn model)
    val oxycaloric: Double,  // kcal/L O2 (Weir 1949)
)

data class OrganPct(
    val name: String,
    val pct: Double,
    val colorToken: String,  // Compose color token name
)

data class MetabolicResult(
    // Body composition
    val bmi: Double,
    val bmiClass: BiolismBmiCategory,
    val bfPct: Double,         // Deurenberg 1991
    val ffm: Double,           // fat-free mass (kg)
    val fm: Double,            // fat mass (kg)
    val bsa: Double,           // DuBois 1916 (m²)

    // Navy tape (nullable — requires circumferences)
    val navyBfPct: Double?,
    val navyFfm: Double?,
    val navyFm: Double?,

    // Ideal body weight (Devine/Robinson/Miller)
    val ibwDevine: Double,
    val ibwRobinson: Double,
    val ibwMiller: Double,
    val ibwMean: Double,

    // Visceral risk
    val whtr: Double?,         // waist-to-height ratio
    val whr: Double?,          // waist-to-hip ratio
    val bai: Double?,          // body adiposity index (Bergman 2011)

    // BMR (kcal/day)
    val bmrMsj: Double,        // Mifflin-St Jeor
    val bmrKm: Double,         // Katch-McArdle (lean-mass)
    val bmrDay: Double,        // consensus average

    // TDEE
    val tdeeDay: Double,
    val ketoSupprFactor: Double, // T3/adaptation suppression

    // Rates per second (suppressed)
    val kcalSec: Double,
    val watts: Double,

    // Gas exchange
    val vo2PerMin: Double,
    val vco2PerMin: Double,
    val vePerMin: Double,      // minute ventilation (L/min)

    // Substrate
    val sub: SubstrateResult,

    // Substrate oxidation rates (g/min)
    val fatOxGPerMin: Double,
    val carbOxGPerMin: Double,
    val protOxGPerMin: Double,
    val ffaFluxGPerMin: Double,

    // Ketosis metabolites
    val bhbMmolPerMin: Double,
    val ketoActivation: Double,
    val acCoaTotalMmolMin: Double,
    val acCoaFatMmolMin: Double,
    val acCoaCarbMmolMin: Double,
    val acCoaProtMmolMin: Double,
    val gngGPerHr: Double,
    val gngProtFrac: Double,

    // Physiology
    val hrEstimated: Double,    // Fick resting HR estimate
    val atpMmolPerMin: Double,
    val metWaterPerMin: Double, // g/min metabolic water
    val nExcrGPerDay: Double,   // urinary N2 g/day

    // Thermoregulation
    val heatRadW: Double,
    val heatConvW: Double,
    val heatCondW: Double,
    val heatEvapW: Double,
    val heatEvapSkinW: Double,
    val heatEvapRespW: Double,

    // Hydration
    val rwlMlPerHr: Double,        // respiratory water loss
    val tewlMlPerHr: Double,       // transepidermal water loss
    val iwlMlPerHr: Double,        // total insensible water loss
    val metWaterMlPerHr: Double,
    val netHydroBalMlPerHr: Double,

    // Organ heat distribution
    val organs: List<OrganPct>,

    // Macro targets
    val macroProtMinG: Double,
    val macroProtKcal: Double,
    val protGPerKgFfm: Double,
    val macroCarbMinG: Double,
    val macroCarbKcal: Double,
    val macroFatMinG: Double,
    val macroFatKcal: Double,
    val essentialFatMinG: Double,
    val macroFloorKcal: Double,
    val waterNeedL: Double,

    // Ethnicity meta (for display)
    val ethnicMeta: EthnicityOption,
)

data class HormoneReading(
    val value: Double,
    val unit: String,
    val refLow: Double,
    val refHigh: Double,
    val label: String,   // "Low" | "Low-Normal" | "Normal" | "High-Normal" | "High"
    val colorToken: String,
)

data class HormoneResult(
    val testosterone: HormoneReading,
    val estradiol: HormoneReading,
    val cortisol: HormoneReading,
    val insulin: HormoneReading,
    val leptin: HormoneReading,
    val ghrelin: HormoneReading,
    val glucagon: HormoneReading,
    val gh: HormoneReading,
    val igf1: HormoneReading,
    val fT3: HormoneReading,
    val dheaS: HormoneReading,
    val progesterone: HormoneReading?, // female only
)

enum class KetoPhase {
    GLYCOGEN_DEPLETION,
    TRANSITION,
    KETOSIS_ONSET,
    DEEP_KETOSIS,
    PROLONGED_FAST,
    KETO_ADAPTED,
    EXTENDED_STARVATION,
}

data class KetoPhaseInfo(
    val phase: KetoPhase,
    val label: String,
    val description: String,
    val progressPct: Double,  // 0–100 within this phase
    val colorToken: String,
    val estimatedKetoneMmol: String,
)

data class BiolismSession(
    val id: Long,
    val timestamp: String,
    val elapsedSec: Double,
    val kcalBurned: Double,
    val kcalPerMin: Double,
    val bmrDay: Double,
    val tdeeDay: Double,
    val activityLabel: String,
    val ketosis: Boolean,
    val startWeightKg: Double,
    val endWeightKg: Double,
    val fatFrac: Double,
    val fatLostKg: Double,
    // How long ketosis was actually toggled on during this session — distinct from
    // elapsedSec (the whole session's duration), since ketosis can be toggled on
    // partway through. Defaults to 0.0 for sessions saved before this field existed
    // (decodes cleanly from old JSON), so the Evolution tab's ketosis-average only
    // counts sessions that actually carry a real duration.
    val ketoElapsedSec: Double = 0.0,
)
