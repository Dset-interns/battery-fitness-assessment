#!/bin/zsh
# Compiles BatteryFitnessCore together with Scripts/CoreChecks/main.swift into one throwaway
# binary and runs it. Needs only the Swift compiler (Command Line Tools); no Xcode, no XCTest.
set -euo pipefail

cd "$(dirname "$0")/.."
OUT="${TMPDIR:-/tmp}/battery_core_checks"

swiftc -swift-version 5 -module-name CoreChecks \
  Sources/BatteryFitnessCore/*.swift Scripts/CoreChecks/main.swift -o "$OUT"
"$OUT"
