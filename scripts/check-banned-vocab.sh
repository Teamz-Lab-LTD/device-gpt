#!/usr/bin/env bash
# Play policy guard — Deceptive Behavior strike cleared 2026-07-10 (v3.1.13).
# Fails the build if any user-facing surface re-introduces the vocabulary family
# that caused the strike ("app performs optimization" claims).
#
# Scanned surfaces:
#   1. Kotlin string literals in app/src/main/java (user-facing text)
#   2. XML resources in app/src/main/res
#   3. Play listing metadata for EVERY locale (fastlane/metadata/android/*/)
#
# Exemptions (NOT violations):
#   - Android API identifiers (IGNORE_BATTERY_OPTIMIZATION_SETTINGS etc.)
#   - Educational text about hardware ("boost clock", "CPUs optimize power")
#   - Storage guidance quoting Android's own Settings vocabulary ("Free up space")
#   - Comments and internal identifiers
#
# Usage: scripts/check-banned-vocab.sh   (exit 0 = clean, exit 1 = violation)

set -u
cd "$(dirname "$0")/.."

# Phrases that claim THE APP optimizes/boosts/cleans — the strike class.
BANNED_PATTERNS=(
  "[Tt]ap to optimize"
  "[Oo]ptimize now"
  "[Oo]ptimizing\.\.\."
  "✅ Optimized"
  "[Bb]oost your (phone|device|speed|performance|ram|memory)"
  "[Ff]ree up RAM"
  "[Cc]lean junk"
  "[Jj]unk clean"
  "[Ss]peed up your (phone|device)"
  "[Oo]ne.tap (boost|clean|optimize)"
  "[Dd]on.t break your.*streak"
  "[Kk]eep your.*streak alive"
  "maintain your.*streak"
  "streak is at risk"
  "-day streak"
  "streak today"
  "streak alive"
)

# Files/lines that may legitimately contain a banned substring.
ALLOWLIST_FILE="scripts/banned-vocab-allowlist.txt"

VIOLATIONS=0
scan() {
  local label="$1"; shift
  local hits
  for pattern in "${BANNED_PATTERNS[@]}"; do
    hits=$(grep -rnE "$pattern" "$@" 2>/dev/null \
      | grep -v "check-banned-vocab" \
      | grep -viE "IGNORE_BATTERY_OPTIMIZATION|isIgnoringBatteryOptimizations|REQUEST_IGNORE_BATTERY" \
      | grep -vE ":[0-9]+: *(//|\*)" || true)
    if [ -n "$hits" ]; then
      # drop allowlisted lines
      if [ -f "$ALLOWLIST_FILE" ]; then
        hits=$(echo "$hits" | grep -vFf "$ALLOWLIST_FILE" || true)
      fi
      if [ -n "$hits" ]; then
        echo "❌ [$label] banned pattern '$pattern':"
        echo "$hits" | head -10
        VIOLATIONS=1
      fi
    fi
  done
}

scan "kotlin"  app/src/main/java --include="*.kt"
scan "res-xml" app/src/main/res  --include="*.xml"
scan "listing" fastlane/metadata/android

if [ "$VIOLATIONS" -eq 1 ]; then
  echo ""
  echo "BANNED VOCABULARY FOUND. This vocabulary family caused the 2026-07 Play"
  echo "Deceptive Behavior strike. Rewrite as factual state + honest action"
  echo "(e.g. 'Memory 85% used — see details'). To exempt a legitimate line,"
  echo "add its exact text to $ALLOWLIST_FILE with a reason comment."
  exit 1
fi
echo "✅ banned-vocab check clean (kotlin + res + all listing locales)"
exit 0
