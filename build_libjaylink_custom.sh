#!/bin/bash
set -euo pipefail

# ==========================================
# 0. INSTALACIÓN DE DEPENDENCIAS
# ==========================================
echo "Instalando dependencias necesarias en Termux para libjaylink..."
pkg install -y git autoconf automake libtool pkg-config clang make

# ==========================================
# 1. CARGA DE VARIABLES DE ENTORNO CRÍTICAS
# ==========================================
cd "$HOME" || exit 1

export APP_PREFIX=/data/data/com.diamon.curso/files/usr
export DESTDIR="$HOME/fake_root"
export FAKE_USR="$DESTDIR$APP_PREFIX"
export TMX_PREFIX=/data/data/com.termux/files/usr

export CC=clang
export CXX=clang++

# Banderas globales: -fPIC para librerías. Evitamos -fPIE/-pie global para no romper .so
export CFLAGS="-fPIC -Oz -fstack-protector-strong -I$FAKE_USR/include -I$FAKE_USR/include/libusb-1.0 -I$TMX_PREFIX/include"
export CPPFLAGS="$CFLAGS"
export LDFLAGS="-Wl,-z,max-page-size=16384 -Wl,-z,relro,-z,now -L$FAKE_USR/lib -L$TMX_PREFIX/lib"

# Búsqueda de dependencias (.pc) primero en fake_root, luego en Termux
export PKG_CONFIG_PATH="$FAKE_USR/lib/pkgconfig:$TMX_PREFIX/lib/pkgconfig:${PKG_CONFIG_PATH:-}"

# ==========================================
# 2. PREPARACIÓN DEL CÓDIGO FUENTE
# ==========================================
echo "Descargando código fuente de libjaylink..."
rm -rf "$HOME/libjaylink"
git clone --depth 1 https://gitlab.zapb.de/libjaylink/libjaylink.git "$HOME/libjaylink"

cd "$HOME/libjaylink" || exit 1

# Generar el script configure
echo "Ejecutando autogen.sh..."
./autogen.sh

# ==========================================
# 3. CONFIGURACIÓN
# ==========================================
echo "Configurando libjaylink..."
./configure \
  --prefix="$APP_PREFIX" \
  --enable-shared \
  --enable-static

# ==========================================
# 4. COMPILACIÓN E INSTALACIÓN
# ==========================================
echo "Compilando libjaylink..."
make -j"$(nproc)"

echo "Instalando en fake_root..."
make install DESTDIR="$DESTDIR"

# ==========================================
# 5. VERIFICACIÓN FINAL
# ==========================================
echo
echo "=== Compilación de libjaylink Exitosa ==="
ls -lh "$FAKE_USR/lib"/libjaylink* || true

echo
echo "=== Dependencias de libjaylink.so ==="
readelf -d "$FAKE_USR/lib/libjaylink.so" | grep NEEDED || true

echo
echo "=== Alineación 16KB en libjaylink.so ==="
readelf -l "$FAKE_USR/lib/libjaylink.so" | grep LOAD || true
