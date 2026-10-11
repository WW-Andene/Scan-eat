package fr.scanneat.util

import java.text.Normalizer

/**
 * Normalizes a product/drug name for OCR-tolerant token matching: strips
 * diacritics, lowercases, keeps only letters/digits/spaces. Previously
 * duplicated byte-for-byte in MedicationLookupDb.kt and
 * NonConsumableLookupDb.kt (both match OCR text off a physical box front
 * against a lookup database) - extracted here so a future change to the
 * matching rule only needs to happen once.
 */
fun normalizeForOcrMatch(s: String): String =
    Normalizer.normalize(s, Normalizer.Form.NFD)
        .replace(Regex("\\p{Mn}"), "")
        .lowercase()
        .filter { it.isLetterOrDigit() || it == ' ' }
        .trim()

/** Splits an already-normalized string into non-blank whitespace-separated tokens. */
fun tokenizeForOcrMatch(s: String): List<String> = s.split(' ').filter { it.isNotBlank() }
