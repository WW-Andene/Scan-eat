package fr.scanneat.domain.engine.biolism

// ============================================================================
// BIOLISM CONSTANTS — port of Biolism/src/App.jsx module-scope constants
// Sources preserved inline where relevant.
// ============================================================================

const val GLYCOGEN_KCAL   = 1900.0   // total glycogen energy reserve (kcal) — Cahill 1966
const val GLYCOGEN_G      = 475.0    // grams of glycogen
const val WATER_PER_GLYC  = 3.0     // g water released per g glycogen (stored ratio)

data class ActivityLevel(
    val id: String,
    val label: String,
    val note: String,
    val mult: Double,
    // FR translations, added alongside (not replacing) `label`/`note` above to
    // avoid touching every UI call site blind without a compiler available -
    // callers can switch to `activityLabel(lang)`/`activityNote(lang)` below
    // as they're migrated to be locale-aware.
    val labelFr: String = label,
    val noteFr: String = note,
)

fun ActivityLevel.label(lang: String): String = if (lang == "en") label else labelFr
fun ActivityLevel.note(lang: String): String = if (lang == "en") note else noteFr

// Deliberately separate from PersonalScoreEngine's ACTIVITY_PAL (FAO/WHO/UNU
// 2004 multipliers used for daily nutrition targets) - this is the classic
// Harris-Benedict activity-multiplier set the ported Biolism metabolic model
// was built against. Same label wording, different multipliers by design:
// don't "fix" one to match the other without re-deriving BiolismEngine's math.
val ACTIVITY_LEVELS = listOf(
    ActivityLevel("sedentary",  "Sedentary",          "desk job, little or no exercise",              1.200,
        labelFr = "Sédentaire",          noteFr = "travail de bureau, peu ou pas d'exercice"),
    ActivityLevel("light",      "Lightly Active",      "light exercise 1–3 days per week",             1.375,
        labelFr = "Légèrement actif",     noteFr = "exercice léger 1 à 3 jours par semaine"),
    ActivityLevel("moderate",   "Moderately Active",   "moderate exercise 3–5 days per week",          1.550,
        labelFr = "Modérément actif",     noteFr = "exercice modéré 3 à 5 jours par semaine"),
    ActivityLevel("very",       "Very Active",         "hard exercise 6–7 days per week",              1.725,
        labelFr = "Très actif",           noteFr = "exercice intense 6 à 7 jours par semaine"),
    ActivityLevel("extra",      "Extra Active",        "very hard exercise + physical job",             1.900,
        labelFr = "Extrêmement actif",    noteFr = "exercice très intense + travail physique"),
)

data class EthnicityOption(
    val id: String,
    val label: String,
    val bmiOffset: Double,           // effective BMI units to subtract before Deurenberg BF%
    val bmiOverweight: Double,       // WHO / regional action threshold
    val bmiObese: Double,
    val note: String,
    // See ActivityLevel above for why these are additive fields, not a rename.
    val labelFr: String = label,
    val noteFr: String = note,
)

fun EthnicityOption.label(lang: String): String = if (lang == "en") label else labelFr
fun EthnicityOption.note(lang: String): String = if (lang == "en") note else noteFr

// Sources: Deurenberg et al. 1998 (Int J Obes 22:1164–71), WHO Expert Consultation 2004
// (Lancet 363:157–163), NICE / ADA thresholds.
val ETHNICITY_OPTIONS = listOf(
    EthnicityOption("caucasian",    "Caucasian / White",              0.0,  25.0, 30.0, "Standard WHO thresholds (reference population)",
        labelFr = "Caucasien / Blanc", noteFr = "Seuils OMS standards (population de référence)"),
    EthnicityOption("east_asian",   "East Asian",                    -2.6,  23.0, 27.5, "Chinese, Japanese, Korean, SE Asian · WHO WPRO 2004 thresholds",
        labelFr = "Asiatique de l'Est", noteFr = "Chinois, Japonais, Coréen, Asie du Sud-Est · seuils OMS WPRO 2004"),
    EthnicityOption("south_asian",  "South Asian",                   -2.5,  23.0, 27.5, "Indian, Pakistani, Bangladeshi, Sri Lankan · NICE / ADA thresholds",
        labelFr = "Asiatique du Sud", noteFr = "Indien, Pakistanais, Bangladais, Sri-lankais · seuils NICE / ADA"),
    EthnicityOption("black_african","Black / African",                +1.3, 25.0, 30.0, "Higher bone density + lean mass; standard WHO thresholds apply",
        labelFr = "Noir / Africain", noteFr = "Densité osseuse et masse maigre plus élevées ; seuils OMS standards"),
    EthnicityOption("hispanic",     "Hispanic / Latino",             -0.5,  25.0, 30.0, "Modest adiposity offset vs Caucasian; standard WHO thresholds",
        labelFr = "Hispanique / Latino", noteFr = "Léger écart d'adiposité vs Caucasien ; seuils OMS standards"),
    EthnicityOption("polynesian",   "Polynesian / Pacific Islander", +4.5,  26.0, 32.0, "Highest lean-mass density; Deurenberg 1998 meta-analysis",
        labelFr = "Polynésien / Îles du Pacifique", noteFr = "Densité de masse maigre la plus élevée ; méta-analyse Deurenberg 1998"),
    EthnicityOption("prefer_not",   "Prefer not to say",              0.0,  25.0, 30.0, "Standard WHO thresholds (no ethnic correction applied)",
        labelFr = "Je préfère ne pas préciser", noteFr = "Seuils OMS standards (aucune correction ethnique appliquée)"),
)
