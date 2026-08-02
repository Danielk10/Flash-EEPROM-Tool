#!/bin/bash
set -euo pipefail

# ==========================================
# 0. INSTALACIÓN DE DEPENDENCIAS
# ==========================================
echo "Instalando dependencias necesarias en Termux para flashrom..."
pkg install -y bash-completion python git pkg-config clang make

# Instalación de meson, ninja y sphinx mediante pip
pip install meson ninja sphinx --break-system-packages || pip install meson ninja sphinx || true

# ==========================================
# 1. CARGA DE VARIABLES DE ENTORNO CRÍTICAS
# ==========================================
cd "$HOME" || exit 1

export APP_PREFIX=/data/data/com.diamon.curso/files/usr
export DESTDIR="$HOME/fake_root"
export FAKE_USR="$DESTDIR$APP_PREFIX"
export FAKE_INC="$FAKE_USR/include"
export TMX_PREFIX=/data/data/com.termux/files/usr

export CC=clang
export CXX=clang++

# Asegurar que pkg-config encuentre librerías en fake_root y en Termux
export PKG_CONFIG_PATH="$FAKE_USR/lib/pkgconfig:$TMX_PREFIX/lib/pkgconfig:${PKG_CONFIG_PATH:-}"

# Banderas de compilación y enlazado con alineación de 16KB y hardening
COMMON_CFLAGS="-Oz -flto -fstack-protector-strong -D_FORTIFY_SOURCE=2 -ffile-prefix-map=$DESTDIR= -I$FAKE_INC -I$FAKE_INC/libusb-1.0 -I$FAKE_INC/libftdi1 -I$TMX_PREFIX/include"
COMMON_LDFLAGS="-flto -Wl,-z,max-page-size=16384 -Wl,-z,relro,-z,now -L$FAKE_USR/lib -L$TMX_PREFIX/lib"

# ==========================================
# 2. PREPARACIÓN DEL ENTORNO Y CÓDIGO FUENTE
# ==========================================
echo "Configurando enlace simbólico para pciutils..."
if [ -d "$FAKE_INC/pci" ] && [ ! -d "$FAKE_INC/pciutils" ]; then
    ln -sf "$FAKE_INC/pci" "$FAKE_INC/pciutils"
fi

echo "Descargando código fuente de flashrom..."
rm -rf "$HOME/flashrom"
git clone --depth 1 https://github.com/flashrom/flashrom.git "$HOME/flashrom"

cd "$HOME/flashrom" || exit 1
rm -rf builddir

# ==========================================
# 3. CONFIGURACIÓN CON MESON
# ==========================================
echo "Configurando flashrom con Meson..."

meson setup builddir \
  --prefix="$APP_PREFIX" \
  -Dtests=disabled \
  -Dwerror=false \
  -Dprogrammer=all \
  -Dc_args="$COMMON_CFLAGS" \
  -Dc_link_args="$COMMON_LDFLAGS"

# ==========================================
# 4. COMPILACIÓN E INSTALACIÓN
# ==========================================
echo "Compilando flashrom con Ninja..."
meson compile -C builddir

echo "Instalando en fake_root..."
DESTDIR="$DESTDIR" meson install -C builddir

# ==========================================
# 5. VERIFICACIÓN FINAL
# ==========================================
echo
echo "=== Compilación de flashrom Exitosa ==="

FLASHROM_BIN=""
if [ -f "$FAKE_USR/sbin/flashrom" ]; then
    FLASHROM_BIN="$FAKE_USR/sbin/flashrom"
elif [ -f "$FAKE_USR/bin/flashrom" ]; then
    FLASHROM_BIN="$FAKE_USR/bin/flashrom"
fi

if [ -n "$FLASHROM_BIN" ]; then
    ls -lh "$FLASHROM_BIN"

    echo
    echo "=== Dependencias dinámicas de flashrom ==="
    readelf -d "$FLASHROM_BIN" 2>/dev/null | grep NEEDED || true

    echo
    echo "=== Alineación 16KB en ejecutable flashrom ==="
    readelf -l "$FLASHROM_BIN" 2>/dev/null | grep LOAD || true
else
    echo "Error: No se encontró el binario de flashrom instalado."
    exit 1
fi
