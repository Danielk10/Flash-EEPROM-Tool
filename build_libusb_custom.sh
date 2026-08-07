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

export COMMON_CFLAGS="-fPIC -Oz -flto -fstack-protector-strong -D_FORTIFY_SOURCE=2 -ffile-prefix-map=$DESTDIR="
export COMMON_CXXFLAGS="-fPIC -Oz -flto -fstack-protector-strong -D_FORTIFY_SOURCE=2 -ffile-prefix-map=$DESTDIR="
export COMMON_LDFLAGS="-flto -Wl,-z,max-page-size=16384 -Wl,-z,relro,-z,now"

# ==========================================
# 2. PREPARACIÓN DEL CÓDIGO FUENTE
# ==========================================
echo "Limpiando y descargando código fuente de libusb..."
rm -rf "$HOME/libusb"
git clone https://github.com/libusb/libusb.git --depth 1
cd "$HOME/libusb/libusb" || exit 1

cat > patch_libusb.py << 'EOF'
import os

def run_patch():
    # ----------------------------------------
    # A) PARCHE PARA CORE.C (Gestión de la conexión)
    # ----------------------------------------
    core_path = 'core.c'
    if os.path.exists(core_path):
        with open(core_path, 'r') as f:
            lines = f.readlines()

        for i, line in enumerate(lines):
            if '#include <stdio.h>' in line:
                lines.insert(i + 1, '#include <stdlib.h>\n#include <stdint.h>\n')
                break

        for i, line in enumerate(lines):
            if 'r = usbi_backend.init(_ctx);' in line:
                for j in range(i, i + 10):
                    if 'if (r) {' in lines[j] or 'if (r)' in lines[j]:
                        lines[j] = '\t\t\tif (r) { r = 0; /* HACK para Android: Ignorar fallo netlink */ }\n\t\t\tif (0)\n'
                        break
                break

        for i, line in enumerate(lines):
            if 'ssize_t API_EXPORTED libusb_get_device_list' in line:
                for j in range(i, i + 20):
                    if 'ssize_t i, len' in lines[j]:
                        lines[j+1:j+1] = [
                            '\n\tchar *f1 = getenv("ANDROID_USB_FD");\n',
                            '\tif (!f1) {\n',
                            '\t\t*list = calloc(1, sizeof(void*));\n',
                            '\t\treturn 0;\n',
                            '\t}\n',
                            '\tfprintf(stderr, "[LIBUSB-HACK] Creando dispositivo emulado en la lista\\n");\n',
                            '\tret = calloc(2, sizeof(void*));\n',
                            '\tstruct libusb_device *d = usbi_alloc_device(usbi_get_context(ctx), 0);\n',
                            '\tret[0] = d; ret[1] = NULL; *list = ret;\n',
                            '\tif (discdevs) discovered_devs_free(discdevs);\n',
                            '\treturn 1;\n'
                        ]
                        break
                break

        for i, line in enumerate(lines):
            if 'int API_EXPORTED libusb_open(' in line:
                for j in range(i, i + 20):
                    if 'int r;' in lines[j]:
                        lines[j+1:j+1] = [
                            '\n\tchar *f2 = getenv("ANDROID_USB_FD");\n',
                            '\tif (f2) {\n',
                            '\t\tfprintf(stderr, "[LIBUSB-HACK] libusb_open llamado! Envolviendo FD %s\\n", f2);\n',
                            '\t\tint fd = atoi(f2);\n',
                            '\t\treturn libusb_wrap_sys_device(ctx, (intptr_t)fd, dev_handle);\n',
                            '\t}\n'
                        ]
                        break
                break

        with open(core_path, 'w') as f:
            f.writelines(lines)

    desc_path = 'descriptor.c'
    if os.path.exists(desc_path):
        with open(desc_path, 'r') as f: lines = f.readlines()
        for i, line in enumerate(lines):
            if '#include <stdio.h>' in line or '#include <string.h>' in line:
                lines.insert(i + 1, '#include <stdio.h>\n#include <stdlib.h>\n#include <unistd.h>\n#include <errno.h>\n#include <string.h>\n#include <sys/ioctl.h>\n#include <linux/usbdevice_fs.h>\n')
                break

        for i, line in enumerate(lines):
            if 'int API_EXPORTED libusb_get_device_descriptor(' in line:
                for j in range(i, i + 15):
                    if '{' in lines[j]:
                        lines[j+1:j+1] = [
                            '\n\tchar *f_fd = getenv("ANDROID_USB_FD");\n',
                            '\tif (f_fd) {\n',
                            '\t\tstatic unsigned char cached_desc[18];\n',
                            '\t\tstatic int has_cached_desc = 0;\n',
                            '\t\tint fd = atoi(f_fd);\n',
                            '\t\tunsigned char buf[18];\n',
                            '\t\tif (!has_cached_desc) {\n',
                            '\t\t\tfprintf(stderr, "[LIBUSB-HACK] Obteniendo Descriptor por ioctl (FD %d)... ", fd);\n',
                            '\t\t\tstruct usbdevfs_ctrltransfer ctrl;\n',
                            '\t\t\tctrl.bRequestType = 0x80;\n',
                            '\t\t\tctrl.bRequest = 0x06;\n',
                            '\t\t\tctrl.wValue = 0x0100;\n',
                            '\t\t\tctrl.wIndex = 0;\n',
                            '\t\t\tctrl.wLength = 18;\n',
                            '\t\t\tctrl.timeout = 1000;\n',
                            '\t\t\tctrl.data = buf;\n',
                            '\t\t\tint res = ioctl(fd, 0xC0185500, &ctrl); // USBDEVFS_CONTROL\n',
                            '\t\t\tif (res == 18) {\n',
                            '\t\t\t\tmemcpy(cached_desc, buf, 18);\n',
                            '\t\t\t\thas_cached_desc = 1;\n',
                            '\t\t\t} else {\n',
                            '\t\t\t\tfprintf(stderr, "FALLO IOCTL! (res=%d, errno=%d: %s)\\n", res, errno, strerror(errno));\n',
                            '\t\t\t\treturn -1;\n',
                            '\t\t\t}\n',
                            '\t\t}\n',
                            '\t\tmemcpy(buf, cached_desc, 18);\n',
                            '\t\tdesc->bLength = buf[0];\n',
                            '\t\tdesc->bDescriptorType = buf[1];\n',
                            '\t\tdesc->bcdUSB = (buf[3] << 8) | buf[2];\n',
                            '\t\tdesc->bDeviceClass = buf[4];\n',
                            '\t\tdesc->bDeviceSubClass = buf[5];\n',
                            '\t\tdesc->bDeviceProtocol = buf[6];\n',
                            '\t\tdesc->bMaxPacketSize0 = buf[7];\n',
                            '\t\tdesc->idVendor = (buf[9] << 8) | buf[8];\n',
                            '\t\tdesc->idProduct = (buf[11] << 8) | buf[10];\n',
                            '\t\tdesc->bcdDevice = (buf[13] << 8) | buf[12];\n',
                            '\t\tdesc->iManufacturer = buf[14];\n',
                            '\t\tdesc->iProduct = buf[15];\n',
                            '\t\tdesc->iSerialNumber = buf[16];\n',
                            '\t\tdesc->bNumConfigurations = buf[17];\n',
                            '\t\tfprintf(stderr, "[LIBUSB-HACK] EXITO (Desde Caché)! VID=%04x PID=%04x\\n", desc->idVendor, desc->idProduct);\n',
                            '\t\treturn 0;\n',
                            '\t}\n'
                        ]
                        break
                break

        with open(desc_path, 'w') as f:
            f.writelines(lines)

    linux_usbfs_path = 'os/linux_usbfs.c'
    if os.path.exists(linux_usbfs_path):
        with open(linux_usbfs_path, 'r') as f: lines = f.readlines()
        for i, line in enumerate(lines):
            if 'assert(init_count != 0);' in line:
                lines[i] = '\tif (init_count == 0) return;\n'
        with open(linux_usbfs_path, 'w') as f: f.writelines(lines)

if __name__ == "__main__":
    run_patch()
EOF


python3 patch_libusb.py

# ==========================================
# 4. CONFIGURACIÓN Y COMPILACIÓN
# ==========================================
echo "Configurando y compilando libusb..."
cd "$HOME/libusb" || exit 1
NOCONFIGURE=1 ./autogen.sh
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
