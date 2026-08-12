#!/bin/bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
LOCAL_PREFIX="${SCRIPT_DIR}/local_root"

echo "=== 1. Clonando flashrom ==="
cd /tmp
rm -rf flashrom_native
git clone --depth 1 https://github.com/flashrom/flashrom.git flashrom_native
cd flashrom_native

echo "=== 2. Configurando flashrom con Meson ==="
export PKG_CONFIG_PATH="$LOCAL_PREFIX/lib/pkgconfig:${PKG_CONFIG_PATH:-}"
# Aseguramos que se encuentre pkgconfig de libusb y libpci del sistema
export PKG_CONFIG_PATH="$LOCAL_PREFIX/lib/pkgconfig:/usr/lib/x86_64-linux-gnu/pkgconfig:/usr/share/pkgconfig:${PKG_CONFIG_PATH}"

meson setup builddir \
  --prefix="$LOCAL_PREFIX" \
  -Dtests=disabled \
  -Dwerror=false \
  -Dc_args="-I$LOCAL_PREFIX/include" \
  -Dc_link_args="-L$LOCAL_PREFIX/lib"

echo "=== 3. Compilando e instalando ==="
meson compile -C builddir
meson install -C builddir

echo "=== FLASHROM INSTALADO CON ÉXITO ==="
ls -lh "$LOCAL_PREFIX/sbin/flashrom"
