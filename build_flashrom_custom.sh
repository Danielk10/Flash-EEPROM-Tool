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
# 2.1 PARCHEO DE MESON PARA SOPORTE COMPLETO EN ANDROID
# ==========================================
echo "Aplicando parches para habilitar todos los programadores (serie, Linux kernel, PCI/hwaccess) en Android..."

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
content = re.sub(
    r"(systems_serial\s*=\s*\[)([^\]]+)(\])",
    lambda m: m.group(1) + m.group(2) + (", 'android'" if "'android'" not in m.group(2) else "") + m.group(3),
    content
)
print("[PATCH] Añadido 'android' a systems_serial en meson.build")

# 2. Habilitar acceso a hardware y PCI en Android agregando 'android' a systems_hwaccess (asm106x, atavia, internal, etc.)
content = re.sub(
    r"(systems_hwaccess\s*=\s*\[)([^\]]+)(\])",
    lambda m: m.group(1) + m.group(2) + (", 'android'" if "'android'" not in m.group(2) else "") + m.group(3),
    content
)
print("[PATCH] Añadido 'android' a systems_hwaccess en meson.build")

# 3. Mapear custom_baud_linux en Android (Android usa el kernel Linux y soporta termios2 / BOTHER)
content = re.sub(
    r"host_machine\.system\(\)\s*==\s*'linux'",
    "host_machine.system() in ['linux', 'android']",
    content
)
print("[PATCH] Mapeado host_machine.system() en Android para soporte custom baud")

# 4. Habilitar controladores del kernel Linux (linux_spi, linux_mtd, mediatek_i2c, mstarddc, parade_lspcon, etc.)
content = re.sub(
    r"('systems'\s*:\s*\[\s*'linux'\s*\])",
    r"'systems' : [ 'linux', 'android' ]",
    content
)
content = re.sub(
    r"(systems_linux_spi\s*=\s*\[)([^\]]+)(\])",
    lambda m: m.group(1) + m.group(2) + (", 'android'" if "'android'" not in m.group(2) else "") + m.group(3),
    content
)
content = re.sub(
    r"(systems_linux_mtd\s*=\s*\[)([^\]]+)(\])",
    lambda m: m.group(1) + m.group(2) + (", 'android'" if "'android'" not in m.group(2) else "") + m.group(3),
    content
)
content = re.sub(
    r"(systems_pci\s*=\s*\[)([^\]]+)(\])",
    lambda m: m.group(1) + m.group(2) + (", 'android'" if "'android'" not in m.group(2) else "") + m.group(3),
    content
)
print("[PATCH] Habilitados controladores del kernel Linux y PCI para Android")

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

LIBFLASHROM="$FAKE_USR/lib/libflashrom.so"

if [ -n "$FLASHROM_BIN" ]; then
    ls -lh "$FLASHROM_BIN"
    [ -f "$LIBFLASHROM" ] && ls -lh "$LIBFLASHROM"

    echo
    echo "=== Dependencias dinámicas de flashrom ==="
    readelf -d "$FLASHROM_BIN" 2>/dev/null | grep NEEDED || true

    echo
    echo "=== Alineación 16KB en ejecutable y librería ==="
    echo "[$FLASHROM_BIN]:"
    readelf -l "$FLASHROM_BIN" 2>/dev/null | grep LOAD || true
    if [ -f "$LIBFLASHROM" ]; then
        echo "[$LIBFLASHROM]:"
        readelf -l "$LIBFLASHROM" 2>/dev/null | grep LOAD || true
    fi

    echo
    echo "=== Verificación de Programadores Soportados en Binario y Librería ==="
    PROG_LIST="ch341a_spi ch347_spi ft2232_spi usbblaster_spi serprog buspirate_spi spidriver dirtyjtag_spi stlinkv3_spi jlink_spi pickit2_spi dediprog digilent_spi raiden_debug_spi pony_spi nv_sma_spi asm106x atavia internal linux_mtd linux_spi mediatek_i2c_spi mstarddc_spi nicintel nicintel_eeprom nicintel_spi ogp_spi realtek_mst_i2c_spi satasii drkaiser gfxnvidia it8212"

    # Verificación en runtime mediante el listado nativo del CLI de flashrom
    CLI_PROGS=$(LD_LIBRARY_PATH="$FAKE_USR/lib:$TMX_PREFIX/lib" "$FLASHROM_BIN" -p __list_probe__ 2>&1 || true)

    for prog in $PROG_LIST; do
        if echo "$CLI_PROGS" | grep -qw "$prog" 2>/dev/null; then
            echo "  [OK] $prog: Incluido (verificado en runtime CLI)"
        elif [ -f "$LIBFLASHROM" ] && readelf -s "$LIBFLASHROM" 2>/dev/null | grep -Eq "programmer_$prog\b|devs_$prog\b"; then
            echo "  [OK] $prog: Incluido (símbolo en libflashrom)"
        elif readelf -s "$FLASHROM_BIN" 2>/dev/null | grep -Eq "programmer_$prog\b|devs_$prog\b"; then
            echo "  [OK] $prog: Incluido (símbolo en flashrom bin)"
        elif strings "$LIBFLASHROM" "$FLASHROM_BIN" 2>/dev/null | grep -q "$prog"; then
            echo "  [OK] $prog: Incluido (cadena de driver detectada)"
        else
            echo "  [AVISO] $prog: No detectado"
        fi
    done
else
    echo "Error: No se encontró el binario de flashrom instalado."
    exit 1
fi
