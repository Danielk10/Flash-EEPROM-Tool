#!/bin/bash
set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
EMULATOR="$SCRIPT_DIR/emulador_flashrom"
FLASHROM="$SCRIPT_DIR/flashrom_local.sh"

echo "=== INICIANDO VALIDACIÓN DE TODOS LOS DISPOSITIVOS EN EL EMULADOR ==="

# Asegurar que el emulador esté compilado
make -C "$SCRIPT_DIR"

# Función auxiliar para comprobar la salida de flashrom
check_probe() {
    local log_file=$1
    if grep -q 'Found GigaDevice flash chip "GD25Q80(B)"' "$log_file"; then
        echo "✅ [ÉXITO] Dispositivo detectado correctamente."
        return 0
    else
        echo "❌ [FALLO] No se detectó el chip GD25Q80(B)."
        cat "$log_file"
        return 1
    fi
}

# --- 1. PROBAR SERPROG ---
echo ""
echo "--- Probando SERPROG (PTY) ---"
$EMULATOR --serprog &
EMU_PID=$!
sleep 2

$FLASHROM -p serprog:dev=./serprog_pty:115200 > serprog_test.log 2>&1 || true
kill $EMU_PID || true
check_probe serprog_test.log

# --- 2. PROBAR BUS PIRATE ---
echo ""
echo "--- Probando BUS PIRATE (PTY) ---"
$EMULATOR --buspirate &
EMU_PID=$!
sleep 2

$FLASHROM -p buspirate_spi:dev=./buspirate_pty > buspirate_test.log 2>&1 || true
kill $EMU_PID || true
check_probe buspirate_test.log

# --- 3. PROBAR SPIDRIVER ---
echo ""
echo "--- Probando SPIDRIVER (PTY) ---"
$EMULATOR --spidriver &
EMU_PID=$!
sleep 2

$FLASHROM -p spidriver:dev=./spidriver_pty > spidriver_test.log 2>&1 || true
kill $EMU_PID || true
check_probe spidriver_test.log

# --- 4. PROBAR CH341A (USB Directo) ---
echo ""
echo "--- Probando CH341A (USB Socket) ---"
# El modo auto-contenido de CH341A se lanza pasándole el comando flashrom como argumento al emulador
$EMULATOR --ch341a $FLASHROM -p ch341a_spi > ch341a_test.log 2>&1 || true
check_probe ch341a_test.log

echo ""
echo "=== FIN DE LAS PRUEBAS ==="
