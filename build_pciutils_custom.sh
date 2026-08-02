#!/bin/bash
set -euo pipefail

# ==========================================
# 0. INSTALACIÓN DE DEPENDENCIAS
# ==========================================
echo "Instalando dependencias necesarias en Termux para pciutils..."
pkg install -y zlib-static

# ==========================================
# 1. CARGA DE VARIABLES DE ENTORNO CRÍTICAS
# ==========================================
cd "$HOME" || exit 1

export APP_PREFIX=/data/data/com.diamon.curso/files/usr
export DESTDIR="$HOME/fake_root"
export FAKE_USR="$DESTDIR$APP_PREFIX"

export CC=clang

# Alineación de 16KB, LTO, Hardening de seguridad y limpieza de rutas
# Se omite -fPIE porque interfiere con la creación de libpci.so
export COMMON_CFLAGS="-fPIC -Oz -flto -fstack-protector-strong -D_FORTIFY_SOURCE=2 -ffile-prefix-map=$DESTDIR="

# Se omite -pie (el compilador de Android lo hace automático para ejecutables)
# Se omite -lz (el Makefile lo maneja gracias a ZLIB=yes)
export COMMON_LDFLAGS="-flto -Wl,-z,max-page-size=16384 -Wl,-z,relro,-z,now"

# ==========================================
# 2. PREPARACIÓN DEL CÓDIGO FUENTE
# ==========================================
echo "Limpiando y descargando código fuente de pciutils..."
rm -rf "$HOME/pciutils"
git clone https://github.com/pciutils/pciutils.git --depth 1

# ==========================================
# 3. PARCHEANDO PARA ANDROID (BIONIC LIBC)
# ==========================================
echo "Aplicando parches para Android..."
cd "$HOME/pciutils" || exit 1

# Bionic (la libc de Android) ya incluye la resolución DNS internamente.
sed -i 's/-lresolv//g' lib/configure

make clean || true

# ==========================================
# 4. COMPILACIÓN DIRECTA MEDIANTE MAKE
# ==========================================
echo "Compilando pciutils (activando ZLIB, DNS y SHARED)..."

make -j"$(nproc)" \
    PREFIX="$APP_PREFIX" \
    CC="$CC" \
    CFLAGS="$COMMON_CFLAGS" \
    LDFLAGS="$COMMON_LDFLAGS" \
    ZLIB=yes \
    DNS=yes \
    LIBRESOLV="" \
    SHARED=yes

# ==========================================
# 5. INSTALACIÓN Y VERIFICACIÓN
# ==========================================
echo "Instalando binarios y librerías en fake_root..."

make install install-lib \
    DESTDIR="$DESTDIR" \
    PREFIX="$APP_PREFIX" \
    SHARED=yes

echo
echo "=== Compilación de pciutils Exitosa ==="
ls -lh "$FAKE_USR/sbin/setpci"
ls -lh "$FAKE_USR/lib/libpci.so"

echo
echo "=== Dependencias dinámicas de setpci ==="
readelf -d "$FAKE_USR/sbin/setpci" | grep NEEDED || true

echo
echo "=== Dependencias dinámicas de libpci.so ==="
readelf -d "$FAKE_USR/lib/libpci.so" | grep NEEDED || true

echo
echo "=== Alineación 16KB en ejecutable setpci ==="
readelf -l "$FAKE_USR/sbin/setpci" | grep LOAD || true
