#!/bin/bash
# Reproducción automatizada del issue #101:
# FT232H + Macronix MX25L128 de 16 MB (JEDEC C2 20 18).
#
# 1. Primer intento de lectura SIN -c (lo que ejecuta la app antes del
#    diálogo): flashrom debe listar las cinco definiciones MX25L128 y abortar
#    con "Multiple flash chip definitions match".
# 2. El usuario selecciona un chip en el diálogo y la app reintenta el MISMO
#    comando insertando -c tras -p (FlashromExecutor.withChipArgument). La
#    lectura debe devolver exactamente 16.777.216 bytes.
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
# 1. Primer intento de lectura SIN -c. Es el comando que lanza la app antes
#    del diálogo: flashrom detecta cinco definiciones y aborta sin leer.
# ---------------------------------------------------------------------------
"$EMULATOR" --ftdi --mx25l128 "$FLASHROM" -p "$PROGRAMMER" \
    -r "$TMP_DIR/read.bin" > "$TMP_DIR/first_attempt.log" 2>&1
FIRST_RC=$?

for CHIP in \
    "MX25L12805D" \
    "MX25L12833F" \
    "MX25L12835F/MX25L12873F" \
    "MX25L12845E/MX25L12865E" \
    "MX25L12850F"; do
    grep -Fq "Found Macronix flash chip \"$CHIP\"" "$TMP_DIR/first_attempt.log" \
        || fail "No se detectó la definición $CHIP"
done

grep -Fq "Multiple flash chip definitions match" "$TMP_DIR/first_attempt.log" \
    || fail "No apareció el mensaje 'Multiple flash chip definitions match'"
grep -Fq "Please specify which chip definition to use with the -c" "$TMP_DIR/first_attempt.log" \
    || fail "No apareció la petición de -c"
[ ! -s "$TMP_DIR/read.bin" ] \
    || fail "El primer intento sin -c no debía generar un volcado válido"

echo "✅ Primer intento sin -c: 5 definiciones + ambigüedad, la app mostraría el diálogo (exit=$FIRST_RC)"

# ---------------------------------------------------------------------------
# 2. El usuario elige un chip en el diálogo: la app reintenta el MISMO comando
#    insertando -c justo después de -p (FlashromExecutor.withChipArgument).
#    La lectura se compara contra la memoria emulada (--save).
# ---------------------------------------------------------------------------
"$EMULATOR" --ftdi --mx25l128 --fill count --save "$TMP_DIR/expected.bin" \
    "$FLASHROM" -p "$PROGRAMMER" -c "$SELECTED_CHIP" -r "$TMP_DIR/read.bin" \
    > "$TMP_DIR/read.log" 2>&1 \
    || fail "La lectura de 16 MB con -c falló"

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
