#!/bin/bash
# Reproducción automatizada del issue #101:
# FT232H + Macronix MX25L128 de 16 MB (JEDEC C2 20 18).
#
# 1. El probe sin -c debe listar las cinco definiciones MX25L128 y el mensaje
#    "Multiple flash chip definitions match".
# 2. La lectura con -c debe devolver exactamente 16.777.216 bytes.
# 3. La escritura con -c debe terminar con "VERIFIED." y el contenido debe
#    coincidir byte a byte con la imagen escrita.
set -uo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
EMULATOR="$SCRIPT_DIR/emulador_flashrom"
FLASHROM="$SCRIPT_DIR/flashrom_local.sh"
PROGRAMMER="ft2232_spi:type=232H"
SELECTED_CHIP="MX25L12835F/MX25L12873F"
EXPECTED_SIZE=16777216

TMP_DIR="$(mktemp -d)"
trap 'rm -rf "$TMP_DIR"' EXIT

fail() {
    echo "❌ $*"
    exit 1
}

echo "=== Issue #101: MX25L128 16 MB (JEDEC C2 20 18) sobre FT232H ==="
make -C "$SCRIPT_DIR" >/dev/null || fail "No se pudo compilar el emulador"

# ---------------------------------------------------------------------------
# 1. Probe sin -c: flashrom debe encontrar las cinco definiciones y abortar
#    pidiendo el parámetro -c.
# ---------------------------------------------------------------------------
"$EMULATOR" --ftdi --mx25l128 "$FLASHROM" -p "$PROGRAMMER" \
    > "$TMP_DIR/probe.log" 2>&1
PROBE_RC=$?

for CHIP in \
    "MX25L12805D" \
    "MX25L12833F" \
    "MX25L12835F/MX25L12873F" \
    "MX25L12845E/MX25L12865E" \
    "MX25L12850F"; do
    grep -Fq "Found Macronix flash chip \"$CHIP\"" "$TMP_DIR/probe.log" \
        || fail "No se detectó la definición $CHIP"
done

grep -Fq "Multiple flash chip definitions match" "$TMP_DIR/probe.log" \
    || fail "No apareció el mensaje 'Multiple flash chip definitions match'"
grep -Fq "Please specify which chip definition to use with the -c" "$TMP_DIR/probe.log" \
    || fail "No apareció la petición de -c"

echo "✅ Probe: 5 definiciones MX25L128 + mensaje de ambigüedad (exit=$PROBE_RC)"

# ---------------------------------------------------------------------------
# 2. Lectura de 16 MB con -c. Se compara contra la memoria emulada (--save).
# ---------------------------------------------------------------------------
"$EMULATOR" --ftdi --mx25l128 --fill count --save "$TMP_DIR/expected.bin" \
    "$FLASHROM" -p "$PROGRAMMER" -c "$SELECTED_CHIP" -r "$TMP_DIR/read.bin" \
    > "$TMP_DIR/read.log" 2>&1 \
    || fail "La lectura de 16 MB falló"

READ_SIZE=$(stat -c%s "$TMP_DIR/read.bin")
[ "$READ_SIZE" -eq "$EXPECTED_SIZE" ] \
    || fail "Tamaño leído incorrecto: $READ_SIZE (esperado $EXPECTED_SIZE)"
cmp -s "$TMP_DIR/read.bin" "$TMP_DIR/expected.bin" \
    || fail "El contenido leído no coincide con la memoria emulada"

echo "✅ Lectura: $READ_SIZE bytes idénticos al contenido emulado"

# ---------------------------------------------------------------------------
# 3. Escritura + verificación de flashrom sobre los 16 MB.
# ---------------------------------------------------------------------------
"$EMULATOR" --ftdi --mx25l128 --save "$TMP_DIR/written.bin" \
    "$FLASHROM" -p "$PROGRAMMER" -c "$SELECTED_CHIP" -w "$TMP_DIR/read.bin" \
    > "$TMP_DIR/write.log" 2>&1 \
    || fail "La escritura de 16 MB falló"

grep -Fq "VERIFIED." "$TMP_DIR/write.log" \
    || fail "flashrom no reportó VERIFIED."
cmp -s "$TMP_DIR/read.bin" "$TMP_DIR/written.bin" \
    || fail "El contenido escrito no coincide con la imagen de origen"

echo "✅ Escritura: 16 MB programados y VERIFIED."

echo "=== TODO OK: issue #101 reproducible con el emulador ==="
