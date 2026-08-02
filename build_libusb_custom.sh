#!/bin/bash
set -euo pipefail

# ==========================================
# 1. CARGA DE VARIABLES DE ENTORNO CRÍTICAS
# ==========================================
cd "$HOME" || exit 1

export APP_PREFIX=/data/data/com.diamon.curso/files/usr
export DESTDIR="$HOME/fake_root"
export FAKE_USR="$DESTDIR$APP_PREFIX"

export CC=clang
export CXX=clang++

# Banderas de compilación: 
# -fPIC para librerías compartidas (removido -fPIE)
# Hardening: LTO, stack-protector, FORTIFY_SOURCE
export COMMON_CFLAGS="-fPIC -Oz -flto -fstack-protector-strong -D_FORTIFY_SOURCE=2 -ffile-prefix-map=$DESTDIR="
export COMMON_CXXFLAGS="-fPIC -Oz -flto -fstack-protector-strong -D_FORTIFY_SOURCE=2 -ffile-prefix-map=$DESTDIR="

# Banderas de enlazado:
# Alineación de 16KB, LTO, y protección RELRO (removido -pie)
export COMMON_LDFLAGS="-flto -Wl,-z,max-page-size=16384 -Wl,-z,relro,-z,now"

# ==========================================
# 2. PREPARACIÓN DEL CÓDIGO FUENTE
# ==========================================
echo "Limpiando directorio previo y descargando código fuente de libusb..."
rm -rf "$HOME/libusb"
git clone https://github.com/libusb/libusb.git --depth 1

# ==========================================
# 3. APLICACIÓN DEL PARCHE PYTHON
# ==========================================
echo "Generando y aplicando parche Python en core.c..."
cd "$HOME/libusb/libusb" || exit 1

cat > patch_libusb.py << 'EOF'
import os

def run_patch():
    file_path = 'core.c'
    if not os.path.exists(file_path):
        print("Error: No se encontro core.c")
        return

    with open(file_path, 'r') as f:
        lines = f.readlines()

    # 1. Insertar Headers
    for i, line in enumerate(lines):
        if '#include <stdio.h>' in line:
            lines.insert(i + 1, '#include <stdlib.h>\n#include <stdint.h>\n')
            break

    # 2. Parchear libusb_get_device_list (aprox linea 841)
    for i, line in enumerate(lines):
        if 'ssize_t API_EXPORTED libusb_get_device_list' in line:
            for j in range(i, i + 20):
                if 'ssize_t i, len' in lines[j]:
                    p = [
                        '\n\tchar *f1 = getenv("ANDROID_USB_FD");\n',
                        '\tif (f1) {\n',
                        '\t\tret = calloc(2, sizeof(void*));\n',
                        '\t\tstruct libusb_device *d = usbi_alloc_device(usbi_get_context(ctx), 0);\n',
                        '\t\tret[0] = d; ret[1] = NULL; *list = ret;\n',
                        '\t\tif (discdevs) discovered_devs_free(discdevs);\n',
                        '\t\treturn 1;\n',
                        '\t}\n'
                    ]
                    lines[j+1:j+1] = p
                    break
            break

    # 3. Parchear libusb_open (aprox linea 1449)
    for i, line in enumerate(lines):
        if 'int API_EXPORTED libusb_open(' in line:
            for j in range(i, i + 20):
                if 'int r;' in lines[j]:
                    p = [
                        '\n\tchar *f2 = getenv("ANDROID_USB_FD");\n',
                        '\tif (f2) {\n',
                        '\t\tint fd = atoi(f2);\n',
                        '\t\treturn libusb_wrap_sys_device(ctx, (intptr_t)fd, dev_handle);\n',
                        '\t}\n'
                    ]
                    lines[j+1:j+1] = p
                    break
            break

    with open(file_path, 'w') as f:
        f.writelines(lines)
    print("--- Parche aplicado con exito ---")

if __name__ == "__main__":
    run_patch()
EOF

python3 patch_libusb.py

# ==========================================
# 4. CONFIGURACIÓN Y COMPILACIÓN
# ==========================================
echo "Generando scripts de configuración (autogen.sh sin auto-configure)..."
cd "$HOME/libusb" || exit 1

# Pasamos NOCONFIGURE=1 para evitar que configure se ejecute solo y falle
NOCONFIGURE=1 ./autogen.sh

echo "Configurando libusb..."
# Ahora ejecutamos configure nosotros mismos de forma controlada
./configure \
  --prefix="$APP_PREFIX" \
  --host=aarch64-linux-android \
  --disable-udev \
  --enable-shared \
  --enable-static \
  --enable-system-log \
  CC="$CC" \
  CFLAGS="$COMMON_CFLAGS" \
  LDFLAGS="$COMMON_LDFLAGS"

echo "Compilando libusb..."
make -j"$(nproc)"

# ==========================================
# 5. INSTALACIÓN Y VERIFICACIÓN
# ==========================================
echo "Instalando en fake_root..."
make install DESTDIR="$DESTDIR"

echo
echo "=== Compilación de libusb Exitosa ==="
ls -lh "$FAKE_USR/lib/libusb-1.0.so"

echo
echo "=== Dependencias dinámicas ==="
readelf -d "$FAKE_USR/lib/libusb-1.0.so" | grep NEEDED || true

echo
echo "=== Alineación 16KB ==="
readelf -l "$FAKE_USR/lib/libusb-1.0.so" | grep LOAD || true
