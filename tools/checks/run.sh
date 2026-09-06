#!/usr/bin/env bash
# Runs the Minecraft-independent checks for the Nika/Gear 5 core.
#
# These cover the pure math, the ability-routing rules and the authored poses: elastic
# timing curves, volume-preserving squash/stretch, damped ripples, swept (between-tick)
# collision, the invariant that one key can never fire two fruit techniques, and the
# joint-integrity of every player_animation file.
#
# They need only a JDK and Python 3 - no Forge, no Minecraft, no Gradle - so they run
# anywhere:
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

echo "=== player animations ==="
python3 tools/checks/validate_animations.py || status=1

exit $status
