#!/usr/bin/env bash
# Runs the Minecraft-independent checks for the Nika/Gear 5 core.
#
# These cover the pure math and the ability-routing rules: elastic timing curves,
# volume-preserving squash/stretch, damped ripples, swept (between-tick) collision,
# and the invariant that one key can never fire two fruit techniques.
#
# They need only a JDK - no Forge, no Minecraft, no Gradle - so they run anywhere:
#   ./tools/checks/run.sh
set -euo pipefail
cd "$(dirname "$0")/../.."
OUT="$(mktemp -d)"
trap 'rm -rf "$OUT"' EXIT

javac -d "$OUT" --release 17 \
  src/main/java/com/hexhaki/fruit/BattleMode.java \
  src/main/java/com/hexhaki/fruit/NikaSlot.java \
  src/main/java/com/hexhaki/fruit/NikaTechnique.java \
  src/main/java/com/hexhaki/fruit/NikaCurves.java \
  src/main/java/com/hexhaki/fruit/NikaSweep.java

javac -cp "$OUT" -d "$OUT" tools/checks/*.java

status=0
for check in SlotCheck CurveCheck SweepCheck; do
  echo "=== $check ==="
  java -cp "$OUT" "$check" || status=1
  echo
done
exit $status
