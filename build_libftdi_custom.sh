#!/bin/bash
set -euo pipefail

# ==========================================
# 0. INSTALACIÓN DE DEPENDENCIAS
# ==========================================
echo "Instalando dependencias necesarias en Termux..."
pkg install -y libconfuse python swig doxygen gettext boost

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

# Eliminado -fPIE para no romper las librerías compartidas. 
# Añadido LTO y mitigaciones de seguridad en memoria.
export COMMON_CFLAGS="-fPIC -Oz -flto -fstack-protector-strong -D_FORTIFY_SOURCE=2 -ffile-prefix-map=$DESTDIR= -I$FAKE_USR/include -I$TMX_PREFIX/include"
export COMMON_CXXFLAGS="-fPIC -Oz -flto -fstack-protector-strong -D_FORTIFY_SOURCE=2 -ffile-prefix-map=$DESTDIR= -I$FAKE_USR/include -I$TMX_PREFIX/include"

# Añadido LTO y RELRO completo a la base del enlazador.
export BASE_LDFLAGS="-flto -Wl,-z,max-page-size=16384 -Wl,-z,relro,-z,now -llog -L$FAKE_USR/lib -L$TMX_PREFIX/lib"

# Separación de banderas para ejecutables y librerías dinámicas
export EXE_LDFLAGS="-pie $BASE_LDFLAGS"
export SHARED_LDFLAGS="$BASE_LDFLAGS"

export PKG_CONFIG_PATH="$FAKE_USR/lib/pkgconfig:$TMX_PREFIX/lib/pkgconfig:${PKG_CONFIG_PATH:-}"

# ==========================================
# 2. PREPARACIÓN DEL CÓDIGO FUENTE (ESPEJO GITHUB)
# ==========================================
echo "Descargando código fuente de libftdi desde espejo oficial..."
rm -rf "$HOME/libftdi"

# Usamos el espejo de GitHub para evitar el bloqueo del servidor de Intra2net
git clone https://github.com/mcuee/libftdi.git --depth 1 "$HOME/libftdi"

mkdir -p "$HOME/libftdi/build"
cd "$HOME/libftdi/build" || exit 1

# ==========================================
# 3. CONFIGURACIÓN CON CMAKE
# ==========================================
echo "Configurando libftdi..."

cmake .. \
  -Wno-dev \
  -DCMAKE_POLICY_VERSION_MINIMUM=3.5 \
  -DCMAKE_INSTALL_PREFIX="$APP_PREFIX" \
  -DCMAKE_C_COMPILER="$CC" \
  -DCMAKE_CXX_COMPILER="$CXX" \
  -DCMAKE_C_FLAGS="$COMMON_CFLAGS" \
  -DCMAKE_CXX_FLAGS="$COMMON_CXXFLAGS" \
  -DCMAKE_EXE_LINKER_FLAGS="$EXE_LDFLAGS" \
  -DCMAKE_SHARED_LINKER_FLAGS="$SHARED_LDFLAGS" \
  -DCMAKE_MODULE_LINKER_FLAGS="$SHARED_LDFLAGS" \
  -DCMAKE_PREFIX_PATH="$FAKE_USR;$TMX_PREFIX" \
  -DCMAKE_FIND_ROOT_PATH="$TMX_PREFIX;$FAKE_USR" \
  -DLIBUSB_INCLUDE_DIR="$FAKE_USR/include/libusb-1.0" \
  -DLIBUSB_LIBRARIES="$FAKE_USR/lib/libusb-1.0.so" \
  -DCONFUSE_INCLUDE_DIR="$TMX_PREFIX/include" \
  -DCONFUSE_LIBRARY="$TMX_PREFIX/lib/libconfuse.so" \
  -DBUILD_SHARED_LIBS=ON \
  -DFTDIPP=ON \
  -DPYTHON_BINDINGS=ON \
  -DDOCUMENTATION=ON \
  -DFTDI_EEPROM=ON \
  -DEXAMPLES=ON \
  -DBUILD_TESTS=ON

# ==========================================
# 4. COMPILACIÓN E INSTALACIÓN
# ==========================================
echo "Compilando libftdi..."
make -j"$(nproc)"

echo "Instalando en fake_root..."
make install DESTDIR="$DESTDIR"

# ==========================================
# 5. VERIFICACIÓN FINAL
# ==========================================
echo
echo "=== Compilación de libftdi Exitosa ==="
ls -lh "$FAKE_USR/lib/"libftdi1* || true
ls -lh "$FAKE_USR/lib/"libftdipp1* || true
ls -lh "$FAKE_USR/bin/ftdi_eeprom" || true

echo
echo "=== Dependencias de libftdi1.so ==="
readelf -d "$FAKE_USR/lib/libftdi1.so" | grep NEEDED || true

echo
echo "=== Alineación 16KB en librerías y binario ==="
readelf -l "$FAKE_USR/lib/libftdi1.so" | grep LOAD || true
readelf -l "$FAKE_USR/lib/libftdipp1.so" | grep LOAD || true
readelf -l "$FAKE_USR/bin/ftdi_eeprom" | grep LOAD || true
