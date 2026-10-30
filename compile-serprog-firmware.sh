#!/usr/bin/env bash
set -euo pipefail

PROJECT_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
SKETCH_NAME="serprog_arduino_uno_ch340g"
SKETCH_SRC="${PROJECT_ROOT}/${SKETCH_NAME}.ino"
ASSETS_DIR="${PROJECT_ROOT}/app/src/main/assets"

WORK_DIR="$(mktemp -d)"
trap 'rm -rf "${WORK_DIR}"' EXIT

SKETCH_DIR="${WORK_DIR}/${SKETCH_NAME}"
OUT_DIR="${WORK_DIR}/out"
mkdir -p "${SKETCH_DIR}" "${OUT_DIR}"
cp "${SKETCH_SRC}" "${SKETCH_DIR}/${SKETCH_NAME}.ino"

if ! command -v arduino-cli >/dev/null 2>&1; then
    echo "ERROR: instala Arduino CLI (recomendado) y el core arduino:avr." >&2
    echo "  arduino-cli core install arduino:avr" >&2
    echo "Arduino CLI: https://arduino.github.io/arduino-cli/latest/installation/" >&2
    exit 127
fi

echo "Compilando firmware serprog (Arduino UNO)..."
arduino-cli compile --fqbn arduino:avr:uno --output-dir "${OUT_DIR}" "${SKETCH_DIR}"

HEX_SRC="${OUT_DIR}/${SKETCH_NAME}.ino.hex"
if [[ ! -f "${HEX_SRC}" ]]; then
    echo "ERROR: no se generó ${HEX_SRC}" >&2
    exit 1
fi

cp "${HEX_SRC}" "${PROJECT_ROOT}/${SKETCH_NAME}.hex"
cp "${HEX_SRC}" "${ASSETS_DIR}/serprog_arduino.hex"
cp "${SKETCH_SRC}" "${ASSETS_DIR}/serprog_arduino.ino"

echo "OK: firmware compilado y copiado a:"
echo "  ${PROJECT_ROOT}/${SKETCH_NAME}.hex"
echo "  ${ASSETS_DIR}/serprog_arduino.hex"
echo "  ${ASSETS_DIR}/serprog_arduino.ino"
