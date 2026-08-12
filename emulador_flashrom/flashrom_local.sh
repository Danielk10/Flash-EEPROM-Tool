#!/bin/bash
# Wrapper to run local flashrom with local patched libusb
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
export LD_LIBRARY_PATH="$SCRIPT_DIR/local_root/lib:$SCRIPT_DIR/local_root/lib/x86_64-linux-gnu:${LD_LIBRARY_PATH:-}"
exec "$SCRIPT_DIR/local_root/sbin/flashrom" "$@"
