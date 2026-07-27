#!/usr/bin/env python3
"""
Diffs the server's hand-maintained Kotlin scoring-logic copies against the
Android app's domain scoring engine to catch drift between the two.

The two codebases are not laid out file-for-file (the server keeps
CategoryThresholds/pillars/ScoringEngine in fewer, larger files than the
Android app), so this compares matched *declarations* by name rather than
whole files: it extracts each named `fun` (via brace-matching) or top-level
`val` (via paren-matching on its initializer) from both sides, normalizes
cosmetic differences (blank lines, trailing whitespace, comments, visibility
modifiers, package-qualified references), and diffs the normalized bodies.

`val` constants matter here too, not just `fun` bodies: WHOLE_FOOD_KEYWORDS,
CATEGORY_THRESHOLDS and friends are referenced *by name* inside the checked
pillar functions, so a function-body-only diff would never notice their
actual values silently diverging between the two hand-maintained copies —
exactly what happened before this script covered them (see WHOLE_FOOD_KEYWORDS'
own history: server and android both defined it, both were checked function-
body-wise via isWholeFood/scoreIngredientIntegrity, and it still could have
drifted without the drift check being able to tell).

Exit code is non-zero if any matched declaration's normalized body differs,
or if a declaration expected on both sides is missing from either side.
"""
from __future__ import annotations

import re
import sys
from pathlib import Path

REPO = Path(__file__).resolve().parent.parent

SERVER_DIR = REPO / "scan-eat-server/src/main/kotlin/fr/scanneat/shared"
ANDROID_SCORING_DIR = REPO / "scan-eat-android/app/src/main/java/fr/scanneat/domain/engine/scoring"
# OffMapper.kt's OFF-normalization functions live under domain/engine/nutrition/
# on the Android side, not domain/engine/scoring/ - the scoring pillars and the
# OFF-mapping logic are different packages there, unlike the server which keeps
# both under the single `shared` package (ScoringEngine.kt/ServerOffMapper.kt).
ANDROID_NUTRITION_DIR = REPO / "scan-eat-android/app/src/main/java/fr/scanneat/domain/engine/nutrition"
# barcodeCandidates and its helpers live in fr.scanneat.util on the Android side
# (a plain utility, not a domain/engine file) - the server keeps its own manual
# copy inline in OffService.kt (see that file's own "manual sync" comment).
ANDROID_UTIL_DIR = REPO / "scan-eat-android/app/src/main/java/fr/scanneat/util"
SERVER_SERVICE_DIR = REPO / "scan-eat-server/src/main/kotlin/fr/scanneat/service"

# (function name, server path, android path) — pure-logic functions that must
# stay identical between the two hand-maintained copies.
PAIRS = [
    ("getThresholds", SERVER_DIR / "ScoringEngine.kt", ANDROID_SCORING_DIR / "CategoryThresholds.kt"),
    ("inferCategoryFromName", SERVER_DIR / "ScoringEngine.kt", ANDROID_SCORING_DIR / "CategoryThresholds.kt"),
    ("detectUPFMarkers", SERVER_DIR / "ScoringEngine.kt", ANDROID_SCORING_DIR / "ProcessingPillar.kt"),
    ("inferNovaClassWithConfidence", SERVER_DIR / "ScoringEngine.kt", ANDROID_SCORING_DIR / "ProcessingPillar.kt"),
    ("scoreProcessing", SERVER_DIR / "ScoringEngine.kt", ANDROID_SCORING_DIR / "ProcessingPillar.kt"),
    ("getNutrientValue", SERVER_DIR / "ScoringEngine.kt", ANDROID_SCORING_DIR / "NutritionalDensityPillar.kt"),
    ("scoreNutritionalDensity", SERVER_DIR / "ScoringEngine.kt", ANDROID_SCORING_DIR / "NutritionalDensityPillar.kt"),
    ("scoreNegativeNutrients", SERVER_DIR / "ScoringEngine.kt", ANDROID_SCORING_DIR / "NegativeNutrientsPillar.kt"),
    ("scoreAdditiveRisk", SERVER_DIR / "ScoringEngine.kt", ANDROID_SCORING_DIR / "AdditiveRiskPillar.kt"),
    ("countTier1Additives", SERVER_DIR / "ScoringEngine.kt", ANDROID_SCORING_DIR / "AdditiveRiskPillar.kt"),
    ("containsWord", SERVER_DIR / "ScoringEngine.kt", ANDROID_SCORING_DIR / "IngredientIntegrityPillar.kt"),
    ("isWholeFood", SERVER_DIR / "ScoringEngine.kt", ANDROID_SCORING_DIR / "IngredientIntegrityPillar.kt"),
    ("scoreIngredientIntegrity", SERVER_DIR / "ScoringEngine.kt", ANDROID_SCORING_DIR / "IngredientIntegrityPillar.kt"),
    ("scoreToGrade", SERVER_DIR / "ScoringEngine.kt", ANDROID_SCORING_DIR / "ScoringEngine.kt"),
    ("gradeVerdict", SERVER_DIR / "ScoringEngine.kt", ANDROID_SCORING_DIR / "ScoringEngine.kt"),
    ("computeGlobalBonuses", SERVER_DIR / "ScoringEngine.kt", ANDROID_SCORING_DIR / "ScoringEngine.kt"),
    ("computeGlobalPenalties", SERVER_DIR / "ScoringEngine.kt", ANDROID_SCORING_DIR / "ScoringEngine.kt"),
    ("checkVeto", SERVER_DIR / "ScoringEngine.kt", ANDROID_SCORING_DIR / "ScoringEngine.kt"),
    ("buildFlags", SERVER_DIR / "ScoringEngine.kt", ANDROID_SCORING_DIR / "ScoringEngine.kt"),
    ("collectWarnings", SERVER_DIR / "ScoringEngine.kt", ANDROID_SCORING_DIR / "ScoringEngine.kt"),
    ("scoreProduct", SERVER_DIR / "ScoringEngine.kt", ANDROID_SCORING_DIR / "ScoringEngine.kt"),
    # ADDITIVES_DB itself (the ~90-entry Tier/category/concern-text dataset) was
    # never checked here, only the matching logic *around* it (findAdditive/
    # computeFindAdditive/NORMALIZED_ADDITIVES) - a wrong Tier or diverging
    # concern text on just one side, for an entry that exists identically on
    # both, would pass every one of those checks undetected since none of them
    # inspect the data the matching logic returns.
    ("ADDITIVES_DB", SERVER_DIR / "AdditivesDb.kt", ANDROID_SCORING_DIR / "AdditivesDb.kt"),
    ("COSMETIC_ADDITIVE_CATEGORIES", SERVER_DIR / "AdditivesDb.kt", ANDROID_SCORING_DIR / "AdditivesDb.kt"),
    ("normalizeForMatching", SERVER_DIR / "AdditivesDb.kt", ANDROID_SCORING_DIR / "AdditivesDb.kt"),
    ("findAdditive", SERVER_DIR / "AdditivesDb.kt", ANDROID_SCORING_DIR / "AdditivesDb.kt"),
    # findAdditive() is just a cache-lookup wrapper - the actual E-number/name/
    # natural-colorant matching logic (and the real drift risk) lives in
    # computeFindAdditive(), which was never itself checked. A real bug (E150's
    # own "caramel" synonym silently shadowing its more specific E150a-d
    # children via first-match-wins scanning) went undetected here for exactly
    # that reason - fixing the drift check's blind spot alongside the bug fix,
    # not just the bug.
    ("computeFindAdditive", SERVER_DIR / "AdditivesDb.kt", ANDROID_SCORING_DIR / "AdditivesDb.kt"),
    ("NORMALIZED_ADDITIVES", SERVER_DIR / "AdditivesDb.kt", ANDROID_SCORING_DIR / "AdditivesDb.kt"),
    # OFF-mapping/merge logic — same drift risk as the scoring pillars above,
    # previously unchecked despite ServerOffMapper.kt/OffMapper.kt being two
    # hand-maintained copies of the exact same barcode-lookup normalization.
    ("mapCategory", SERVER_DIR / "ServerOffMapper.kt", ANDROID_NUTRITION_DIR / "OffMapper.kt"),
    ("classifyNonFood", SERVER_DIR / "ServerOffMapper.kt", ANDROID_NUTRITION_DIR / "OffMapper.kt"),
    ("parseIngredients", SERVER_DIR / "ServerOffMapper.kt", ANDROID_NUTRITION_DIR / "OffMapper.kt"),
    ("additiveTagsToIngredients", SERVER_DIR / "ServerOffMapper.kt", ANDROID_NUTRITION_DIR / "OffMapper.kt"),
    ("parseWeightG", SERVER_DIR / "ServerOffMapper.kt", ANDROID_NUTRITION_DIR / "OffMapper.kt"),
    ("isOffSparse", SERVER_DIR / "ServerOffMapper.kt", ANDROID_NUTRITION_DIR / "OffMapper.kt"),
    ("mergeOffWithLlm", SERVER_DIR / "ServerOffMapper.kt", ANDROID_NUTRITION_DIR / "OffMapper.kt"),
    ("detectSourceConflicts", SERVER_DIR / "ServerOffMapper.kt", ANDROID_NUTRITION_DIR / "OffMapper.kt"),
    ("declaredMicronutrientsOf", SERVER_DIR / "DomainModels.kt", ANDROID_NUTRITION_DIR / "OffMapper.kt"),
    # Keyword/threshold constants referenced *by name* from the pillar functions
    # above — a function-body diff alone can't see these actually diverging
    # (see this script's own module docstring for why WHOLE_FOOD_KEYWORDS in
    # particular was a real blind spot until now).
    ("WHOLE_FOOD_KEYWORDS", SERVER_DIR / "ScoringEngine.kt", ANDROID_SCORING_DIR / "ScoringKeywords.kt"),
    ("GENERIC_OIL_TERMS", SERVER_DIR / "ScoringEngine.kt", ANDROID_SCORING_DIR / "ScoringKeywords.kt"),
    ("HIDDEN_SUGAR_NAMES", SERVER_DIR / "ScoringEngine.kt", ANDROID_SCORING_DIR / "ScoringKeywords.kt"),
    ("UPF_MARKER_PATTERNS", SERVER_DIR / "ScoringEngine.kt", ANDROID_SCORING_DIR / "ScoringKeywords.kt"),
    ("FIRST_INGREDIENT_PENALTY_PATTERNS", SERVER_DIR / "ScoringEngine.kt", ANDROID_SCORING_DIR / "ScoringKeywords.kt"),
    ("FRESH_PRODUCE_NAME", SERVER_DIR / "ScoringEngine.kt", ANDROID_SCORING_DIR / "ScoringKeywords.kt"),
    ("NAME_CATEGORY_PATTERNS", SERVER_DIR / "ScoringEngine.kt", ANDROID_SCORING_DIR / "CategoryThresholds.kt"),
    ("DEFAULT_THRESHOLDS", SERVER_DIR / "ScoringEngine.kt", ANDROID_SCORING_DIR / "CategoryThresholds.kt"),
    ("CATEGORY_THRESHOLDS", SERVER_DIR / "ScoringEngine.kt", ANDROID_SCORING_DIR / "CategoryThresholds.kt"),
    ("NRV_TARGETS", SERVER_DIR / "ScoringEngine.kt", ANDROID_SCORING_DIR / "NutritionalDensityPillar.kt"),
    # GTIN candidate expansion (compressed UPC-E / GTIN-14 case codes -> the
    # EAN-13/UPC-A form OFF actually indexes) - OffService.kt's own header calls
    # this "manual sync of ScanRepository's Android logic", but nothing actually
    # verified that until now. A silent drift here means the exact same barcode
    # finds a product in one API mode (DIRECT or SERVER) and 404s in the other.
    ("barcodeCandidates", SERVER_SERVICE_DIR / "OffService.kt", ANDROID_UTIL_DIR / "BarcodeNormalizer.kt"),
    ("upcEToUpcA", SERVER_SERVICE_DIR / "OffService.kt", ANDROID_UTIL_DIR / "BarcodeNormalizer.kt"),
    ("gtin14ToEan13", SERVER_SERVICE_DIR / "OffService.kt", ANDROID_UTIL_DIR / "BarcodeNormalizer.kt"),
    ("ean13CheckDigit", SERVER_SERVICE_DIR / "OffService.kt", ANDROID_UTIL_DIR / "BarcodeNormalizer.kt"),
    ("upcCheckDigit", SERVER_SERVICE_DIR / "OffService.kt", ANDROID_UTIL_DIR / "BarcodeNormalizer.kt"),
    # Allergen tag vocabulary - LlmLabelParser.kt's ANNEX_II_KEY_TO_OFF_TAG used to
    # be its own independently hand-written reversed literal (drift risk squared:
    # against the Android original AND against its own file's reverse direction).
    # Refactored to derive from this same OFF_ALLERGEN_TAG_MAP the way
    # AllergenDetector.kt's own ANNEX_II_KEY_TO_OFF_TAG already does, so only the
    # one canonical (tag -> key) direction needs checking here.
    ("OFF_ALLERGEN_TAG_MAP", SERVER_SERVICE_DIR / "LlmLabelParser.kt", ANDROID_SCORING_DIR / "AllergenDetector.kt"),
]

FUNC_START_RE_TMPL = r"^(?:private |internal |public )?fun {name}\b"
VAL_START_RE_TMPL = r"^(?:private |internal |public )?val {name}\b"


CONCAT_RE = re.compile(
    r"^(?:private |internal |public )?val \w+(?:\s*:\s*[^=]+)?=\s*"
    r"([A-Za-z_][A-Za-z0-9_]*(?:\s*\+\s*[A-Za-z_][A-Za-z0-9_]*)+)\s*$"
)


def _find_in_dir(directory: Path, name: str, extractor) -> str | None:
    """Scan every sibling .kt file in `directory` for `name`, trying `extractor`
    on each. Used both as a fallback when a PAIRS-listed file no longer holds a
    declaration atomization moved elsewhere in the same package, and to resolve
    the identifiers referenced by a tier-concatenation val (see extract_val)."""
    for candidate in sorted(directory.glob("*.kt")):
        try:
            return extractor(candidate, name)
        except ValueError:
            continue
    return None


def extract_val(path: Path, name: str) -> str:
    """Top-level `val NAME = listOf(...)/mapOf(...)/Regex(...)` etc, falling
    back to a directory-wide search (see `_extract_val_in_file`'s docstring)
    when `name` isn't declared in `path` itself."""
    try:
        return _extract_val_in_file(path, name)
    except ValueError:
        found = _find_in_dir(path.parent, name, _extract_val_in_file)
        if found is not None:
            return found
        raise ValueError(f"val `{name}` not found in {path} or its directory")


def _extract_val_in_file(path: Path, name: str) -> str:
    """Top-level `val NAME = listOf(...)/mapOf(...)/Regex(...)` etc. Unlike a
    `fun` body, these never contain `{ }` (Kotlin string templates aside), so
    brace-matching can't find the end - instead this counts paren depth from
    the first `(` after the declaration, which is exactly where every one of
    these constants' own initializer starts and ends (including nested
    `Pair(Regex(...), "label")`-style entries and the regex patterns' own
    internal `(?:...)`/`(?!...)` groups, which are always paren-balanced
    since they're valid compiled regexes).

    Some constants (e.g. ADDITIVES_DB, atomized into per-tier files) are now a
    bare `TIER1 + TIER2 + ...` sum of other top-level vals with no `(` of its
    own - for that shape, each referenced identifier is resolved (searching
    every sibling file in the same directory, since atomization scatters them
    across files in the same package) and their listOf(...) bodies are
    spliced back into one synthetic body, so the comparison still sees the
    real entries instead of just the assembly expression.

    No directory fallback happens in here (that's `extract_val`'s job) - this
    only ever looks at `path` itself, so it's safe to call from `_find_in_dir`
    without recursing back into a directory scan.
    """
    text = path.read_text()
    lines = text.splitlines()
    start_re = re.compile(VAL_START_RE_TMPL.format(name=re.escape(name)))
    start_idx = next((i for i, line in enumerate(lines) if start_re.match(line)), None)
    if start_idx is None:
        raise ValueError(f"val `{name}` not found in {path}")

    body_lines = []
    depth = 0
    started_paren = False
    top_level_re = re.compile(r"^(private |internal |public )?(fun|val|const|class|object) ")
    comment_re = re.compile(r"^\s*(/\*|//)")
    for line in lines[start_idx:]:
        # Stop before the next declaration if this one never opened a paren -
        # e.g. a bare "IDENT + IDENT + ..." concatenation (see below), which
        # would otherwise keep scanning until some LATER unrelated
        # declaration's own parens happened to balance out, corrupting the
        # "body" with that unrelated text (the exact bug extract_function's
        # own top_level_re/comment_re stop condition already guards against).
        if body_lines and depth == 0 and not started_paren and (top_level_re.match(line) or comment_re.match(line)):
            break
        body_lines.append(line)
        for ch in line:
            if ch == "(":
                depth += 1
                started_paren = True
            elif ch == ")":
                depth -= 1
        if started_paren and depth == 0:
            break
    if not started_paren:
        # Might be a bare "IDENT + IDENT + ..." concatenation of other vals
        # (atomized data split across sibling files) rather than a genuinely
        # paren-less shape this function can't handle at all.
        joined = " ".join(l.strip() for l in body_lines)
        m = CONCAT_RE.match(joined)
        if not m:
            raise ValueError(f"val `{name}` in {path} has no `(` initializer - extract_val can't handle this shape")
        parts = []
        for ident in re.split(r"\s*\+\s*", m.group(1)):
            part_body = _find_in_dir(path.parent, ident, _extract_val_in_file)
            if part_body is None:
                raise ValueError(f"val `{name}` in {path}: referenced `{ident}` not found in directory")
            # Strip the "val IDENT: Type = listOf(" header and trailing ")" so
            # only the inner entries survive, then re-wrap once below.
            inner = part_body
            inner = re.sub(r"^.*?\blistOf\(", "", inner, count=1, flags=re.DOTALL)
            inner = inner.rstrip()
            if inner.endswith(")"):
                inner = inner[:-1]
            inner = inner.rstrip()
            # Each tier's own listOf(...) already ends with a trailing comma
            # after its last entry (standard Kotlin style) - since parts are
            # re-joined with ",\n" below, keeping it here would double it up.
            if inner.endswith(","):
                inner = inner[:-1]
            parts.append(inner.strip())
        # Reuse this val's own declaration line (with its real type annotation,
        # e.g. "val ADDITIVES_DB: List<AdditiveInfo> =") rather than a
        # fabricated header, so only the entries themselves are ever the
        # source of a reported diff - and keep the server side's convention of
        # a trailing comma after the last entry.
        header = lines[start_idx].rstrip()
        header = re.sub(r"=\s*$", "= listOf(", header)
        return header + "\n" + ",\n".join(parts) + ",\n)"
    return "\n".join(body_lines)


def extract_function(path: Path, name: str) -> str:
    """`fun NAME(...) {...}`/`fun NAME(...) = ...`, falling back to a
    directory-wide search (see `_extract_function_in_file`'s docstring) when
    `name` isn't declared in `path` itself."""
    try:
        return _extract_function_in_file(path, name)
    except ValueError:
        found = _find_in_dir(path.parent, name, _extract_function_in_file)
        if found is not None:
            return found
        raise ValueError(f"function `{name}` not found in {path} or its directory")


def _extract_function_in_file(path: Path, name: str) -> str:
    """No directory fallback happens in here (that's `extract_function`'s
    job) - this only ever looks at `path` itself, so it's safe to call from
    `_find_in_dir` without recursing back into a directory scan."""
    text = path.read_text()
    lines = text.splitlines()
    start_re = re.compile(FUNC_START_RE_TMPL.format(name=re.escape(name)))
    start_idx = None
    for i, line in enumerate(lines):
        if start_re.match(line):
            start_idx = i
            break
    if start_idx is None:
        raise ValueError(f"function `{name}` not found in {path}")

    # Collect lines belonging to this declaration only. A brace-bodied function
    # (`fun f() { ... }`) is terminated by brace-depth returning to 0 after
    # having opened at least one `{`, tracked below. An expression-bodied one
    # (`fun f() = ...`, possibly spanning several chained-call lines, e.g.
    # normalizeForMatching's `.replace(...).replace(...).trim()`) never opens a
    # brace at all - for those, stop as soon as we reach the next top-level
    # declaration (`fun`/`val`/`const`/`class`/`object`, optional visibility
    # modifier) or a comment block that precedes one, while still at depth 0
    # and no brace has been seen. Without that second stop condition, a
    # brace-less function was scanned all the way to wherever *some* later,
    # unrelated declaration happened to contain a self-contained `{ ... }`
    # (e.g. a lambda literal several declarations down) - silently sweeping
    # every doc comment and declaration in between into this function's
    # "body" and comparing that unrelated text as if it were drift. This is
    # exactly how expanding PAIRS to cover normalizeForMatching produced a
    # DRIFT report whose diff was actually NORMALIZED_ADDITIVES' own doc
    # comment, several declarations past normalizeForMatching's real body.
    body_lines = []
    depth = 0
    started_brace = False
    top_level_re = re.compile(r"^(private |internal |public )?(fun|val|const|class|object) ")
    comment_re = re.compile(r"^\s*(/\*|//)")
    for line in lines[start_idx:]:
        if body_lines and depth == 0 and not started_brace and (top_level_re.match(line) or comment_re.match(line)):
            break
        body_lines.append(line)
        for ch in line:
            if ch == "{":
                depth += 1
                started_brace = True
            elif ch == "}":
                depth -= 1
        if started_brace and depth == 0:
            break
        if not started_brace and line.rstrip().endswith(";"):
            break

    while body_lines and body_lines[-1].strip() == "":
        body_lines.pop()

    return "\n".join(body_lines)


def normalize(body: str) -> str:
    lines = []
    for line in body.splitlines():
        # strip line comments
        line = re.sub(r"//.*$", "", line)
        # strip fully-qualified package prefixes so `fr.scanneat.shared.Foo`
        # and a bare `Foo` (imported) compare equal. Package segments are
        # lowercase by Kotlin convention and class/enum names start uppercase,
        # so this only consumes the lowercase run - `[a-zA-Z0-9_.]*` here
        # (any-case) used to also swallow a following `ClassName.` segment,
        # e.g. `fr.scanneat.domain.model.IngredientCategory.ADDITIVE` collapsed
        # all the way to bare `ADDITIVE`, discarding the `IngredientCategory.`
        # qualifier a same-package server reference (`IngredientCategory.
        # ADDITIVE`, nothing to strip) keeps - a real divergence in the two
        # sides' computeFindAdditive() bodies this masked until this fix.
        line = re.sub(r"\bfr\.scanneat\.(?:[a-z][a-zA-Z0-9_]*\.)*", "", line)
        # strip a leading visibility modifier on the declaration signature line -
        # e.g. declaredMicronutrientsOf is `internal` on Android (module-visibility
        # need, since other domain/engine/* files call it) but plain top-level `fun`
        # on the server (no module boundary there); that's a packaging difference,
        # not logic drift, so it shouldn't fail the comparison. Same story for the
        # `val` keyword constants: `internal val` on Android (other domain/engine/
        # scoring/* files reference them), `private val` on server (single file,
        # no module boundary to guard against).
        line = re.sub(r"^(private |internal |public )(?=fun |val )", "", line)
        line = line.rstrip()
        if line.strip() == "":
            continue
        lines.append(line.strip())
    return "\n".join(lines)


def extract_declaration(path: Path, name: str) -> str:
    """Try `fun NAME` first, fall back to top-level `val NAME` - a PAIRS entry
    doesn't say which kind it is, and the two need different extraction
    strategies (brace-matching vs paren-matching, see extract_val)."""
    try:
        return extract_function(path, name)
    except ValueError:
        return extract_val(path, name)


def main() -> int:
    failures = []
    for name, server_path, android_path in PAIRS:
        try:
            server_body = extract_declaration(server_path, name)
        except ValueError as e:
            failures.append(f"[{name}] {e}")
            continue
        try:
            android_body = extract_declaration(android_path, name)
        except ValueError as e:
            failures.append(f"[{name}] {e}")
            continue

        norm_server = normalize(server_body)
        norm_android = normalize(android_body)
        if norm_server != norm_android:
            failures.append(
                f"[{name}] DRIFT between {server_path.relative_to(REPO)} and "
                f"{android_path.relative_to(REPO)}:\n"
                f"--- server ---\n{norm_server}\n--- android ---\n{norm_android}\n"
            )

    if failures:
        print("Scoring-logic drift detected:\n")
        print("\n".join(failures))
        return 1

    print(f"OK — {len(PAIRS)} matched declarations are in sync between server and android.")
    return 0


if __name__ == "__main__":
    sys.exit(main())
