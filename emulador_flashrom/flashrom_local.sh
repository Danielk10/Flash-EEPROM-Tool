#!/bin/bash
# Wrapper to run local flashrom with local patched libusb
export LD_LIBRARY_PATH="/home/danielpdiamon/emulador_flashrom/local_root/lib:/home/danielpdiamon/emulador_flashrom/local_root/lib/x86_64-linux-gnu:${LD_LIBRARY_PATH:-}"
exec "/home/danielpdiamon/emulador_flashrom/local_root/sbin/flashrom" "$@"
