package fr.scanneat.domain.engine.medication

// ============================================================================
// MEDICATION LOOKUP DB — a small, hand-verified starter set sourced from the
// official French public drug database (BDPM, ANSM / base-donnees-publique
// .medicaments.gouv.fr, data.gouv.fr), joining CIS_bdpm.txt (drug identity)
// with CIS_CIP_bdpm.txt (CIP13 barcode per commercial presentation).
// Fetched and cross-checked against the live BDPM flat files on 2026-07-13.
//
// Deliberately small (10 entries, common OTC drugs) rather than importing the
// full ~15k CIS / ~40k CIP13 dataset in one go - this proves the barcode ->
// medication lookup path end-to-end with real, verifiable data; growing the
// table further is a separate, incremental step (e.g. a periodic import job
// reading the same two flat files), not something to do all at once here.
// ============================================================================

data class MedicationDbEntry(
    val barcode: String,   // CIP13, EAN-13 scannable on the box
    val name: String,      // BDPM denomination
    val form: String,      // pharmaceutical form (comprimé, gélule, ...)
    val route: String,     // administration route (orale, ...)
    val holder: String,    // marketing authorization holder
    val cis: String,       // BDPM's own drug identifier (CIS code)
)

private val ENTRIES: List<MedicationDbEntry> = listOf(
    MedicationDbEntry("3400932959358", "ADVIL 200 mg, comprimé enrobé", "comprimé enrobé", "orale", "HALEON FRANCE", "68634000"),
    MedicationDbEntry("3400935655769", "AERIUS 5 mg, comprimé pelliculé", "comprimé pelliculé", "orale", "ORGANON (HOLLANDE)", "61833327"),
    MedicationDbEntry("3400930170441", "DAFALGAN 1000 mg, comprimé pelliculé", "comprimé pelliculé", "orale", "UPSA", "62887947"),
    MedicationDbEntry("3400935955838", "DOLIPRANE 1000 mg, comprimé", "comprimé", "orale", "OPELLA HEALTHCARE FRANCE", "60234100"),
    MedicationDbEntry("3400932320189", "DOLIPRANE 500 mg, comprimé", "comprimé", "orale", "OPELLA HEALTHCARE FRANCE", "63368332"),
    MedicationDbEntry("3400938571905", "IMODIUMCAPS 2 mg, gélule", "gélule", "orale", "KENVUE FRANCE", "61203061"),
    MedicationDbEntry("3400933964351", "NUROFEN 200 mg, comprimé enrobé", "comprimé enrobé", "orale", "RECKITT BENCKISER HEALTHCARE FRANCE", "60305960"),
    MedicationDbEntry("3400930986080", "SPASFON, comprimé enrobé", "comprimé enrobé", "orale", "TEVA SANTE", "68081368"),
    MedicationDbEntry("3400932351176", "VOLTARENE 50 mg, comprimé enrobé gastro-résistant", "comprimé enrobé gastro-résistant(e)", "orale", "NOVARTIS PHARMA", "62431527"),
    MedicationDbEntry("3400932644551", "XANAX 0,50 mg, comprimé sécable", "comprimé sécable", "orale", "VIATRIS UP", "60647049"),
)

private val BY_BARCODE: Map<String, MedicationDbEntry> = ENTRIES.associateBy { it.barcode }

fun findMedicationByBarcode(barcode: String): MedicationDbEntry? = BY_BARCODE[barcode.filter { it.isDigit() }]
