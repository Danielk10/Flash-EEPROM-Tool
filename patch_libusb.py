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

        for i, line in enumerate(lines):
            if 'int API_EXPORTED libusb_get_configuration(' in line:
                for j in range(i, i + 10):
                    if '{' in lines[j]:
                        lines.insert(j + 1, '\tif (getenv("ANDROID_USB_FD")) {\n\t\tif (config) *config = 1;\n\t\treturn 0;\n\t}\n')
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
                            '\t\tif (dev) memcpy(&dev->device_descriptor, desc, sizeof(struct libusb_device_descriptor));\n',
                            '\t\tfprintf(stderr, "[LIBUSB-HACK] EXITO (Desde Caché)! VID=%04x PID=%04x\\n", desc->idVendor, desc->idProduct);\n',
                            '\t\treturn 0;\n',
                            '\t}\n'
                        ]
                        break
                break

        for i, line in enumerate(lines):
            if 'int API_EXPORTED libusb_get_config_descriptor(' in line:
                for j in range(i, i + 10):
                    if '{' in lines[j]:
                        lines[j+1:j+1] = [
                            '\n\tchar *f_fd = getenv("ANDROID_USB_FD");\n',
                            '\tif (f_fd) {\n',
                            '\t\tstatic unsigned char cached_config[1024];\n',
                            '\t\tstatic int cached_config_len = 0;\n',
                            '\t\tint fd = atoi(f_fd);\n',
                            '\t\tif (cached_config_len == 0) {\n',
                            '\t\t\tfprintf(stderr, "[LIBUSB-HACK] Obteniendo Config Descriptor por ioctl (FD %d)... ", fd);\n',
                            '\t\t\tunsigned char header[9];\n',
                            '\t\t\tstruct usbdevfs_ctrltransfer ctrl;\n',
                            '\t\t\tmemset(&ctrl, 0, sizeof(ctrl));\n',
                            '\t\t\tctrl.bRequestType = 0x80;\n',
                            '\t\t\tctrl.bRequest = 0x06;\n',
                            '\t\t\tctrl.wValue = (uint16_t)((0x02 << 8) | config_index);\n',
                            '\t\t\tctrl.wIndex = 0;\n',
                            '\t\t\tctrl.wLength = 9;\n',
                            '\t\t\tctrl.timeout = 1000;\n',
                            '\t\t\tctrl.data = header;\n',
                            '\t\t\tint res = ioctl(fd, 0xC0185500, &ctrl); // USBDEVFS_CONTROL\n',
                            '\t\t\tif (res >= 4) {\n',
                            '\t\t\t\tuint16_t total_len = (uint16_t)(header[2] | (header[3] << 8));\n',
                            '\t\t\t\tif (total_len > sizeof(cached_config)) total_len = sizeof(cached_config);\n',
                            '\t\t\t\tctrl.wLength = total_len;\n',
                            '\t\t\t\tctrl.data = cached_config;\n',
                            '\t\t\t\tres = ioctl(fd, 0xC0185500, &ctrl);\n',
                            '\t\t\t\tif (res >= 9) {\n',
                            '\t\t\t\t\tcached_config_len = res;\n',
                            '\t\t\t\t\tfprintf(stderr, "EXITO! (%d bytes)\\n", res);\n',
                            '\t\t\t\t} else {\n',
                            '\t\t\t\t\tfprintf(stderr, "FALLO leyendo config completo (res=%d, errno=%d)\\n", res, errno);\n',
                            '\t\t\t\t\treturn -1;\n',
                            '\t\t\t\t}\n',
                            '\t\t\t} else {\n',
                            '\t\t\t\tfprintf(stderr, "FALLO leyendo cabecera config (res=%d, errno=%d)\\n", res, errno);\n',
                            '\t\t\t\treturn -1;\n',
                            '\t\t\t}\n',
                            '\t\t}\n',
                            '\t\tfprintf(stderr, "[LIBUSB-HACK] Config Descriptor entregado (%d bytes)\\n", cached_config_len);\n',
                            '\t\treturn raw_desc_to_config(usbi_device_ctx(dev), cached_config, cached_config_len, config);\n',
                            '\t}\n'
                        ]
                        break
                break

        for i, line in enumerate(lines):
            if 'int API_EXPORTED libusb_get_active_config_descriptor(' in line:
                for j in range(i, i + 10):
                    if '{' in lines[j]:
                        lines[j+1:j+1] = [
                            '\n\tif (getenv("ANDROID_USB_FD")) return libusb_get_config_descriptor(dev, 0, config);\n'
                        ]
                        break
                break

        for i, line in enumerate(lines):
            if 'int API_EXPORTED libusb_get_config_descriptor_by_value(' in line:
                for j in range(i, i + 10):
                    if '{' in lines[j]:
                        lines[j+1:j+1] = [
                            '\n\tif (getenv("ANDROID_USB_FD")) return libusb_get_config_descriptor(dev, 0, config);\n'
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
