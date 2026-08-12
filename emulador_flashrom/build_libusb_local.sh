#!/bin/bash
set -euo pipefail

LOCAL_PREFIX="/home/danielpdiamon/emulador_flashrom/local_root"
echo "=== 1. Clonando libusb ==="
cd /tmp
rm -rf libusb_native_flash
git clone https://github.com/libusb/libusb.git libusb_native_flash --depth 1

echo "=== 2. Aplicando parche local ==="
cd libusb_native_flash/libusb
python3 /home/danielpdiamon/emulador_flashrom/patch_libusb_local.py

echo "=== 3. Configurando autotools ==="
cd ..
NOCONFIGURE=1 ./autogen.sh
./configure --prefix="$LOCAL_PREFIX" --disable-udev --enable-shared

echo "=== 4. Compilando e instalando ==="
make -j"$(nproc)"
make install

echo "=== LIBUSB INSTALADO CON ÉXITO ==="
ls -lh "$LOCAL_PREFIX/lib/libusb-1.0.so"
