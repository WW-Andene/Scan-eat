#!/usr/bin/env bash
# Runs the pure-JVM domain unit tests WITHOUT the Android SDK or a Gradle build.
#
# Compiles domain/** (minus the few files that need Android/Room/Hilt) with the
# Kotlin compiler bundled inside the Gradle distribution, then runs the matching
# JUnit 4 tests with JUnitCore. Takes ~1 minute vs. minutes for a Gradle build,
# and works in sandboxes that have a JDK + Gradle but no Android SDK.
#
# Usage: tools/run-domain-tests.sh [gradle-lib-dir]
#   gradle-lib-dir defaults to $GRADLE_HOME/lib, then /opt/gradle*/lib.
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)/app/src"
LIB="${1:-}"
if [ -z "$LIB" ]; then
  for c in "${GRADLE_HOME:-}/lib" /opt/gradle*/lib; do [ -f "$c"/junit-4.13.2.jar ] && LIB="$c" && break; done
fi
[ -n "$LIB" ] && [ -f "$LIB/junit-4.13.2.jar" ] || { echo "Gradle lib dir with junit-4.13.2.jar not found; pass it as \$1" >&2; exit 2; }

K=$(ls "$LIB"/kotlin-compiler-embeddable-*.jar | head -1)
STD=$(ls "$LIB"/kotlin-stdlib-[0-9]*.jar | head -1)
COMPILER_CP="$K:$STD:$(ls "$LIB"/kotlin-script-runtime-*.jar | head -1):$(ls "$LIB"/trove4j*.jar | head -1):$(ls "$LIB"/annotations-*.jar | head -1):$(ls "$LIB"/kotlinx-coroutines-core-jvm-*.jar | head -1)"
OUT="$(mktemp -d)"; trap 'rm -rf "$OUT"' EXIT
kc() { java -cp "$COMPILER_CP" org.jetbrains.kotlin.cli.jvm.K2JVMCompiler -no-stdlib -no-reflect -nowarn -module-name app "$@" 2>&1 | grep -v 'JAVA_TOOL_OPTIONS' || true; }

# Files that import Android / Room / Hilt-bound data classes cannot compile here.
EXCLUDE='engine/nonconsumable|MedicationLookupDb|MedicationSubstanceDb|IngredientLineParser|LocalRecipeImport|SchemaOrgRecipeParser|SleepMoodCorrelation|IntakeSleepMoodLink|OvertrainingCheck'
MAIN=$(find "$ROOT/main/java/fr/scanneat/domain" -name '*.kt' | grep -vE "$EXCLUDE")
MAIN="$MAIN $ROOT/main/java/fr/scanneat/util/DecimalFormat.kt $ROOT/main/java/fr/scanneat/util/BarcodeNormalizer.kt $ROOT/main/java/fr/scanneat/util/OcrNameMatching.kt $ROOT/main/java/fr/scanneat/util/PriceOcr.kt $ROOT/main/java/fr/scanneat/data/repository/backup/BackupPassphraseCipher.kt"

mkdir -p "$OUT/main" "$OUT/test"
ERR=$(kc -cp "$STD" -d "$OUT/main" $MAIN | grep -E 'error:' || true)
[ -z "$ERR" ] || { echo "$ERR"; exit 1; }

TESTS=$(find "$ROOT"/test/java/fr/scanneat/domain/engine/{scoring,nutrition,expense,biolism,dashboard,medication} "$ROOT"/test/java/fr/scanneat/data/repository/backup -name '*.kt')
RUN_CP="$STD:$OUT/main:$LIB/junit-4.13.2.jar:$LIB/hamcrest-core-1.3.jar"
ERR=$(kc -Xfriend-paths="$OUT/main" -cp "$RUN_CP" -d "$OUT/test" $TESTS | grep -E 'error:' || true)
[ -z "$ERR" ] || { echo "$ERR"; exit 1; }

CLASSES=$(cd "$OUT/test" && find . -name '*Test*.class' | grep -v '\$' | sed 's|^\./||;s|\.class$||;s|/|.|g' | tr '\n' ' ')
java -cp "$RUN_CP:$OUT/test" org.junit.runner.JUnitCore $CLASSES 2>&1 | grep -v JAVA_TOOL_OPTIONS
