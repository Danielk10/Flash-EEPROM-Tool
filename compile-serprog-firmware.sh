#!/usr/bin/env bash
set -euo pipefail

SKETCH_SRC="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)/serprog_arduino_uno_ch340g.ino"
WORK_DIR="$(mktemp -d)"
trap 'rm -rf "${WORK_DIR}"' EXIT

SKETCH_DIR="${WORK_DIR}/serprog_sketch"
mkdir -p "${SKETCH_DIR}"
cp "${SKETCH_SRC}" "${SKETCH_DIR}/serprog_sketch.ino"

echo "Compilando firmware serprog (Arduino UNO)..."
if command -v arduino-cli >/dev/null 2>&1; then
    arduino-cli compile --fqbn arduino:avr:uno "${SKETCH_DIR}"
elif command -v arduino >/dev/null 2>&1; then
    # Arduino IDE 1.x mantiene esta interfaz de compilación.
    arduino --verify --board arduino:avr:uno "${SKETCH_DIR}/serprog_sketch.ino"
else
    echo "ERROR: instala Arduino CLI (recomendado) o Arduino IDE con 'arduino' en PATH." >&2
    echo "Arduino CLI: https://arduino.github.io/arduino-cli/latest/installation/" >&2
    exit 127
fi

echo "OK: compilación completada"
