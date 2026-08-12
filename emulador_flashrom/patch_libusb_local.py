import os

def run_patch():
    # ----------------------------------------
    # A) PARCHE PARA CORE.C (Gestión de la conexión)
    # ----------------------------------------
    core_path = 'core.c'
    if os.path.exists(core_path):
        print("Parcheando core.c...")
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
                        lines[j] = '\t\t\tif (r) { r = 0; /* HACK local: Ignorar fallo netlink/udev */ }\n\t\t\tif (0)\n'
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

    # ----------------------------------------
    # B) PARCHE PARA DESCRIPTOR.C (Lector de Descriptor con read() local)
    # ----------------------------------------
    desc_path = 'descriptor.c'
    if os.path.exists(desc_path):
        print("Parcheando descriptor.c...")
        with open(desc_path, 'r') as f:
            lines = f.readlines()

        for i, line in enumerate(lines):
            if '#include <stdio.h>' in line or '#include <string.h>' in line:
                lines.insert(i + 1, '#include <stdio.h>\n#include <stdlib.h>\n#include <unistd.h>\n#include <errno.h>\n#include <string.h>\n')
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
                            '\t\t\tfprintf(stderr, "[LIBUSB-HACK] Leyendo Descriptor del FD %d... ", fd);\n',
                            '\t\t\tssize_t res = read(fd, buf, 18);\n',
                            '\t\t\tif (res == 18) {\n',
                            '\t\t\t\tmemcpy(cached_desc, buf, 18);\n',
                            '\t\t\t\thas_cached_desc = 1;\n',
                            '\t\t\t} else {\n',
                            '\t\t\t\tfprintf(stderr, "FALLO! (res=%ld, errno=%d: %s)\\n", (long)res, errno, strerror(errno));\n',
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
                            '\t\tfprintf(stderr, "[LIBUSB-HACK] EXITO! VID=%04x PID=%04x\\n", desc->idVendor, desc->idProduct);\n',
                            '\t\treturn 0;\n',
                            '\t}\n'
                        ]
                        break
                break

        with open(desc_path, 'w') as f:
            f.writelines(lines)

    # ----------------------------------------
    # C) PARCHE PARA LINUX_USBFS.C (Bypass ioctls y descriptor caching en emulación)
    # ----------------------------------------
    linux_usbfs_path = 'os/linux_usbfs.c'
    if os.path.exists(linux_usbfs_path):
        print("Parcheando os/linux_usbfs.c...")
        with open(linux_usbfs_path, 'r') as f:
            lines = f.readlines()

        for i, line in enumerate(lines):
            if 'assert(init_count != 0);' in line:
                lines[i] = '\tif (init_count == 0) return;\n'

        for i, line in enumerate(lines):
            if '/* cache descriptors in memory */' in line:
                lines.insert(i, '\tif (getenv("ANDROID_USB_FD")) {\n\t\tdev->speed = LIBUSB_SPEED_FULL;\n\t\tpriv->descriptors = malloc(50);\n\t\tunsigned char mock_desc[50] = {\n\t\t\t18, 1, 0x00, 0x02, 0xff, 0, 0, 64, 0x86, 0x1a, 0x12, 0x55, 0, 1, 0, 0, 0, 1,\n\t\t\t9, 2, 32, 0, 1, 1, 0, 0x80, 50,\n\t\t\t9, 4, 0, 0, 2, 0xff, 0, 0, 0,\n\t\t\t7, 5, 0x02, 2, 64, 0, 0,\n\t\t\t7, 5, 0x82, 2, 64, 0, 0\n\t\t};\n\t\tmemcpy(priv->descriptors, mock_desc, 50);\n\t\tpriv->descriptors_len = 50;\n\t\tr = parse_config_descriptors(dev);\n\t\tif (r >= 0) {\n\t\t\tmemcpy(&dev->device_descriptor, priv->descriptors, LIBUSB_DT_DEVICE_SIZE);\n\t\t}\n\t\treturn r;\n\t}\n\n')
                break

        for i, line in enumerate(lines):
            if 'static int op_detach_kernel_driver(' in line:
                for j in range(i, i + 10):
                    if '{' in lines[j]:
                        lines.insert(j + 1, '\tif (getenv("ANDROID_USB_FD")) {\n\t\treturn 0;\n\t}\n')
                        break
                break

        for i, line in enumerate(lines):
            if 'static int op_handle_events(' in line:
                for j in range(i, i + 10):
                    if '{' in lines[j]:
                        lines.insert(j + 1, '\tif (getenv("ANDROID_USB_FD")) {\n\t\treturn 0;\n\t}\n')
                        break
                break

        for i, line in enumerate(lines):
            if 'r = ioctl(fd, IOCTL_USBFS_CONNECTINFO, &ci);' in line:
                lines[i+1:i+9] = [
                    '\t\tif (r < 0) {\n',
                    '\t\t\tif (getenv("ANDROID_USB_FD")) {\n',
                    '\t\t\t\tbusnum = 1;\n',
                    '\t\t\t\tdevaddr = 1;\n',
                    '\t\t\t} else {\n',
                    '\t\t\t\tusbi_err(ctx, "connectinfo failed, errno=%d", errno);\n',
                    '\t\t\t\treturn LIBUSB_ERROR_IO;\n',
                    '\t\t\t}\n',
                    '\t\t} else {\n',
                    '\t\t\tbusnum = 0;\n',
                    '\t\t\tdevaddr = ci.devnum;\n',
                    '\t\t}\n'
                ]
                break

        for i, line in enumerate(lines):
            if 'static int claim_interface(' in line:
                for j in range(i, i + 10):
                    if 'int r = ioctl(fd, IOCTL_USBFS_CLAIMINTERFACE, &iface);' in lines[j]:
                        lines[j] = '\tint r = getenv("ANDROID_USB_FD") ? 0 : ioctl(fd, IOCTL_USBFS_CLAIMINTERFACE, &iface);\n'
                        break
                break

        for i, line in enumerate(lines):
            if 'static int release_interface(' in line:
                for j in range(i, i + 10):
                    if 'int r = ioctl(fd, IOCTL_USBFS_RELEASEINTERFACE, &iface);' in lines[j]:
                        lines[j] = '\tint r = getenv("ANDROID_USB_FD") ? 0 : ioctl(fd, IOCTL_USBFS_RELEASEINTERFACE, &iface);\n'
                        break
                break

        with open(linux_usbfs_path, 'w') as f:
            f.writelines(lines)

    # ----------------------------------------
    # D) PARCHE PARA IO.C (Interceptación diferida con logs debug y stdio.h)
    # ----------------------------------------
    io_path = 'io.c'
    if os.path.exists(io_path):
        print("Parcheando io.c...")
        with open(io_path, 'r') as f:
            lines = f.readlines()

        for i, line in enumerate(lines):
            if '#include "libusbi.h"' in line:
                lines.insert(i + 1, '#include <unistd.h>\n#include <stdio.h>\n#include <errno.h>\n#include <string.h>\nstatic struct libusb_transfer *pending_transfers[256];\nstatic int pending_count = 0;\n')
                break

        for i, line in enumerate(lines):
            if 'int API_EXPORTED libusb_submit_transfer' in line:
                for j in range(i, i + 10):
                    if '{' in lines[j]:
                        lines[j+1:j+1] = [
                            '\n\tchar *env_fd = getenv("ANDROID_USB_FD");\n',
                            '\tif (env_fd) {\n',
                            '\t\tint fd = atoi(env_fd);\n',
                            '\t\ttransfer->actual_length = transfer->length;\n',
                            '\t\ttransfer->status = LIBUSB_TRANSFER_COMPLETED;\n',
                            '\t\tif ((transfer->endpoint & 0x80) == 0x80) {\n',
                            '\t\t\tfprintf(stderr, "[LIBUSB-DEBUG] Leyendo %d bytes del FD %d...\\n", transfer->length, fd);\n',
                            '\t\t\tint total_read = 0;\n',
                            '\t\t\twhile (total_read < transfer->length) {\n',
                            '\t\t\t\tint r = read(fd, transfer->buffer + total_read, transfer->length - total_read);\n',
                            '\t\t\t\tif (r <= 0) {\n',
                            '\t\t\t\t\tfprintf(stderr, "[LIBUSB-DEBUG] r=%d, fallo al leer del FD %d: %s\\n", r, fd, strerror(errno));\n',
                            '\t\t\t\t\ttransfer->status = LIBUSB_TRANSFER_ERROR;\n',
                            '\t\t\t\t\tbreak;\n',
                            '\t\t\t\t}\n',
                            '\t\t\t\ttotal_read += r;\n',
                            '\t\t\t}\n',
                            '\t\t\tfprintf(stderr, "[LIBUSB-DEBUG] Leidos %d bytes del FD %d\\n", total_read, fd);\n',
                            '\t\t\ttransfer->actual_length = total_read;\n',
                            '\t\t} else {\n',
                            '\t\t\tfprintf(stderr, "[LIBUSB-DEBUG] Escribiendo %d bytes al FD %d...\\n", transfer->length, fd);\n',
                            '\t\t\tuint32_t len = transfer->length;\n',
                            '\t\t\twrite(fd, &len, 4);\n',
                            '\t\t\tint total_written = 0;\n',
                            '\t\t\twhile (total_written < transfer->length) {\n',
                            '\t\t\t\tint w = write(fd, transfer->buffer + total_written, transfer->length - total_written);\n',
                            '\t\t\t\tif (w <= 0) {\n',
                            '\t\t\t\t\tfprintf(stderr, "[LIBUSB-DEBUG] fallo al escribir al FD %d: %s\\n", fd, strerror(errno));\n',
                            '\t\t\t\t\ttransfer->status = LIBUSB_TRANSFER_ERROR;\n',
                            '\t\t\t\t\tbreak;\n',
                            '\t\t\t\t}\n',
                            '\t\t\t\ttotal_written += w;\n',
                            '\t\t\t}\n',
                            '\t\t\tfprintf(stderr, "[LIBUSB-DEBUG] Escritos %d bytes al FD %d\\n", total_written, fd);\n',
                            '\t\t\ttransfer->actual_length = total_written;\n',
                            '\t\t}\n',
                            '\t\tif (pending_count < 256) {\n',
                            '\t\t\tpending_transfers[pending_count++] = transfer;\n',
                            '\t\t}\n',
                            '\t\treturn 0;\n',
                            '\t}\n'
                        ]
                        break
                break

        for i, line in enumerate(lines):
            if 'int API_EXPORTED libusb_handle_events_timeout_completed' in line:
                for j in range(i, i + 10):
                    if '{' in lines[j]:
                        lines[j+1:j+1] = [
                            '\n\tif (getenv("ANDROID_USB_FD")) {\n',
                            '\t\tint count = pending_count;\n',
                            '\t\tpending_count = 0;\n',
                            '\t\tfor (int k = 0; k < count; k++) {\n',
                            '\t\t\tstruct libusb_transfer *t = pending_transfers[k];\n',
                            '\t\t\tif (t->callback) {\n',
                            '\t\t\t\tt->callback(t);\n',
                            '\t\t\t}\n',
                            '\t\t}\n',
                            '\t\tif (completed) {\n',
                            '\t\t\t*completed = 1;\n',
                            '\t\t}\n',
                            '\t\treturn 0;\n',
                            '\t}\n'
                        ]
                        break
                break

        with open(io_path, 'w') as f:
            f.writelines(lines)

if __name__ == "__main__":
    run_patch()
