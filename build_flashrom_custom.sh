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
# 2.1 PARCHEO DE MESON PARA SOPORTE SERIE EN ANDROID
# ==========================================
echo "Aplicando parches para habilitar programadores serie (serprog, buspirate_spi, spidriver) en Android..."

python3 - << 'EOF'
import os
import re

meson_path = "meson.build"
if not os.path.exists(meson_path):
    print("Error: meson.build no encontrado.")
    exit(1)

with open(meson_path, "r", encoding="utf-8") as f:
    content = f.read()

# 1. Habilitar sistemas serie en Android agregando 'android' a systems_serial
if "'android'" not in content:
    content = re.sub(
        r"(systems_serial\s*=\s*\[)([^\]]+)(\])",
        r"\1\2, 'android'\3",
        content
    )
    print("[PATCH] Añadido 'android' a systems_serial en meson.build")

# 2. Mapear custom_baud_linux en Android (Android usa el kernel Linux y soporta termios2 / BOTHER)
content = re.sub(
    r"host_machine\.system\(\)\s*==\s*'linux'",
    "host_machine.system() in ['linux', 'android']",
    content
)
print("[PATCH] Mapeado host_machine.system() en Android para soporte custom baud")

# 3. Habilitar systems_linux_spi y systems_linux_mtd en Android si están presentes
content = re.sub(
    r"(systems_linux_spi\s*=\s*\[)([^\]]+)(\])",
    r"\1\2, 'android'\3",
    content
)
content = re.sub(
    r"(systems_linux_mtd\s*=\s*\[)([^\]]+)(\])",
    r"\1\2, 'android'\3",
    content
)

with open(meson_path, "w", encoding="utf-8") as f:
    f.write(content)

print("[PATCH] Parcheo de meson.build completado exitosamente.")
EOF

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

    echo
    echo "=== Verificación de Programadores Soportados en el Binario ==="
    for prog in ch341a_spi ch347_spi ft2232_spi usbblaster_spi serprog buspirate_spi spidriver dirtyjtag_spi stlinkv3_spi jlink_spi; do
        if strings "$FLASHROM_BIN" 2>/dev/null | grep -q "$prog"; then
            echo "  [OK] $prog: Incluido"
        else
            echo "  [AVISO] $prog: No detectado en cadenas"
        fi
    done
else
    echo "Error: No se encontró el binario de flashrom instalado."
    exit 1
fi
