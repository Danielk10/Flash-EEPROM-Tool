package com.diamon.curso.core;

import android.app.Activity;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbDeviceConnection;
import android.hardware.usb.UsbManager;
import android.os.Build;
import androidx.core.content.IntentCompat;
import com.diamon.curso.R;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class UsbController {
    private static final String ACTION_USB_PERMISSION = "com.diamon.curso.USB_PERMISSION";
    public static final int DEFAULT_SERPROG_BAUD = 115200;
    private static int serprogBaud = DEFAULT_SERPROG_BAUD;

    public static int getSerprogBaud() {
        return serprogBaud;
    }

    public static void setSerprogBaud(int baud) {
        serprogBaud = (baud > 0) ? baud : DEFAULT_SERPROG_BAUD;
    }

    public interface Callback {
        void log(String message);
        void onDeviceConnected(String deviceName, int fd, String vidPid, boolean isRecognized, String autoProg);
        void onDeviceConnectionFailed(String deviceName);
        void onDeviceDisconnected();
    }

    /** Interfaz compatible con API 23+ para reemplazar java.util.function.Consumer (API 24+) */
    public interface OnProgrammerSelectedListener {
        void onSelected(String programmer);
    }

    private final Activity activity;
    private final UsbManager usbManager;
    private final Callback callback;

    private UsbDevice currentDevice;
    private UsbDeviceConnection currentConnection;
    private int currentFd = -1;
    private PtyBridge ptyBridge = null;

    public static final Map<String, String> USB_AUTO_MAP = new HashMap<String, String>() {{
        // CH341A SPI
        put("1a86:5512", "ch341a_spi");
        put("1a86:5523", "ch347_spi"); // ch347 SPI/I2C/UART
        put("1a86:55db", "ch347_spi"); // CH347T SPI (Mode 3)
        put("1a86:55de", "ch347_spi"); // CH347F SPI
        // FT2232H / FT4232H / FT232H / FT4233H y placas FTDI compatibles
        put("0403:6010", "ft2232_spi");
        put("0403:6011", "ft2232_spi");
        put("0403:6014", "ft2232_spi");
        put("0403:6015", "ft2232_spi");
        put("0403:6041", "ft2232_spi"); // FT4233H
        put("0403:8a98", "ft2232_spi"); // TIAO TUMPA
        put("0403:8a99", "ft2232_spi"); // TIAO TUMPA Lite
        put("0403:bbe2", "ft2232_spi"); // Kristech KT-LINK
        put("0403:cff8", "ft2232_spi"); // Amontec JTAGkey
        put("096c:1449", "ft2232_spi"); // GOEPEL PicoTAP
        put("1457:5118", "ft2232_spi"); // FIC OpenMoko debug board
        put("15ba:0003", "ft2232_spi"); // Olimex ARM-USB-OCD
        put("15ba:0004", "ft2232_spi"); // Olimex ARM-USB-TINY
        put("15ba:002a", "ft2232_spi"); // Olimex ARM-USB-TINY-H
        put("15ba:002b", "ft2232_spi"); // Olimex ARM-USB-OCD-H
        put("18d1:5001", "ft2232_spi"); // Google Servo
        put("18d1:5002", "ft2232_spi"); // Google Servo V2 legacy
        put("18d1:5003", "ft2232_spi"); // Google Servo V2
        // Bus Pirate
        put("0403:6001", "buspirate_spi");
        // ST-LINK
        put("0483:3748", "stlinkv3_spi");
        put("0483:374b", "stlinkv3_spi");
        put("0483:374d", "stlinkv3_spi");
        put("0483:374e", "stlinkv3_spi");
        put("0483:374f", "stlinkv3_spi");
        put("0483:3752", "stlinkv3_spi");
        put("0483:3753", "stlinkv3_spi");
        put("0483:3754", "stlinkv3_spi");
        // J-Link
        put("1366:0101", "jlink_spi");
        put("1366:0105", "jlink_spi");
        put("1fc9:000c", "jlink_spi");
        // Pickit2
        put("04d8:0033", "pickit2_spi");
        // USB-Blaster
        put("09fb:6001", "usbblaster_spi");
        // Dediprog
        put("0483:dada", "dediprog");
        put("0483:dae0", "dediprog");
        // Digilent
        put("1443:0007", "digilent_spi");
        // DirtyJTAG
        put("1209:c0ca", "dirtyjtag_spi");
        // Serprog (adaptadores USB-serie; el firmware debe implementar serprog)
        put("2341:0043", "serprog"); // Arduino Uno
        put("2341:0001", "serprog"); // Arduino
        put("16c0:0483", "serprog"); // Teensy
        put("03eb:2044", "serprog"); // Atmel LUFA
        put("1eaf:0004", "serprog"); // LeafLabs Maple
        put("0d28:0204", "serprog"); // ARM mbed
        put("0483:5740", "serprog"); // STM32 virtual COM port
        put("2e8a:0005", "serprog"); // Raspberry Pi Pico (MicroPython)
        put("2e8a:000a", "serprog"); // Raspberry Pi Pico SDK
        put("1a86:7523", "serprog"); // CH340
        put("1a86:55d4", "serprog"); // CH9102F
        put("10c4:ea60", "serprog"); // CP2102
        put("10c4:ea70", "serprog"); // CP2105
        put("10c4:ea71", "serprog"); // CP2108
        put("067b:2303", "serprog"); // PL2303
        put("067b:23a3", "serprog"); // PL2303GC
        put("067b:23b3", "serprog"); // PL2303GB
        put("067b:23c3", "serprog"); // PL2303GT
        put("067b:23d3", "serprog"); // PL2303GL
        put("067b:23e3", "serprog"); // PL2303GE
        put("067b:23f3", "serprog"); // PL2303GS
        put("067b:2323", "serprog"); // PL2303 variant
        put("067b:2304", "serprog"); // PL2303 variant
    }};

    private final BroadcastReceiver usbReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (ACTION_USB_PERMISSION.equals(action)) {
                synchronized (this) {
                    UsbDevice device = IntentCompat.getParcelableExtra(intent, UsbManager.EXTRA_DEVICE, UsbDevice.class);
                    if (intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)) {
                        if (device != null) {
                            connectToDevice(device);
                        }
                    } else {
                        callback.log(activity.getString(R.string.str_error) + ": " + activity.getString(R.string.str_usb_permission_denied));
                    }
                }
            } else if (UsbManager.ACTION_USB_DEVICE_DETACHED.equals(action)) {
                synchronized (this) {
                    UsbDevice device = IntentCompat.getParcelableExtra(intent, UsbManager.EXTRA_DEVICE, UsbDevice.class);
                    if (device != null && currentConnection != null) {
                        if (currentDevice == null || device.getDeviceId() == currentDevice.getDeviceId()) {
                            callback.log(activity.getString(R.string.str_usb_physically_disconnected));
                            disconnectDevice();
                        }
                    }
                }
            }
        }
    };

    public UsbController(Activity activity, Callback callback) {
        this.activity = activity;
        this.callback = callback;
        this.usbManager = (UsbManager) activity.getSystemService(Context.USB_SERVICE);
    }

    public void registerReceiver() {
        IntentFilter filter = new IntentFilter(ACTION_USB_PERMISSION);
        filter.addAction(UsbManager.ACTION_USB_DEVICE_DETACHED);
        androidx.core.content.ContextCompat.registerReceiver(
                activity,
                usbReceiver,
                filter,
                androidx.core.content.ContextCompat.RECEIVER_NOT_EXPORTED
        );
    }

    public void unregisterReceiver() {
        try {
            activity.unregisterReceiver(usbReceiver);
        } catch (Exception ignored) {}
    }

    public int getCurrentFd() {
        return currentFd;
    }

    public PtyBridge getPtyBridge() {
        return ptyBridge;
    }

    public UsbDevice getCurrentDevice() {
        return currentDevice;
    }

    public boolean isConnected() {
        return currentFd >= 0;
    }

    public boolean isUsbHostSupported() {
        return usbManager != null;
    }

    public void searchAndRequestProgrammer(String currentProgrammer, OnProgrammerSelectedListener onProgrammerAutoSelected) {
        if (usbManager == null) {
            callback.log(activity.getString(R.string.str_error) + ": " + activity.getString(R.string.str_err_usb_host_not_supported));
            return;
        }
        Map<String, UsbDevice> devices = usbManager.getDeviceList();
        if (devices == null || devices.isEmpty()) {
            callback.log(activity.getString(R.string.str_error) + ": " + activity.getString(R.string.str_no_usb_detected));
            return;
        }

        List<UsbDevice> candidates = new ArrayList<>(devices.values());

        // Auto-selección lógica
        for (UsbDevice device : candidates) {
            String key = String.format(Locale.US, "%04x:%04x", device.getVendorId(), device.getProductId());
            String autoProg = resolveAutoProgrammer(device);
            if (autoProg != null) {
                onProgrammerAutoSelected.onSelected(autoProg);
                callback.log(activity.getString(R.string.str_auto_detect_recognized, key, autoProg));
                requestUsbPermission(device);
                return;
            }
        }

        Collections.sort(candidates, new Comparator<UsbDevice>() {
            @Override
            public int compare(UsbDevice a, UsbDevice b) {
                int vid = Integer.compare(a.getVendorId(), b.getVendorId());
                if (vid != 0) return vid;
                int pid = Integer.compare(a.getProductId(), b.getProductId());
                if (pid != 0) return pid;
                return Integer.compare(a.getDeviceId(), b.getDeviceId());
            }
        });

        callback.log(activity.getString(R.string.str_usb_not_recognized));
        callback.log(activity.getString(R.string.str_known_devices));
        callback.log(activity.getString(R.string.str_can_connect_manually));

        if (candidates.size() == 1) {
            requestUsbPermission(candidates.get(0));
            return;
        }

        CharSequence[] labels = new CharSequence[candidates.size()];
        for (int i = 0; i < candidates.size(); i++) {
            labels[i] = formatUsbDeviceLabel(candidates.get(i));
        }

        new android.app.AlertDialog.Builder(activity)
                .setTitle(activity.getString(R.string.str_select_usb_device))
                .setItems(labels, (dialog, which) -> requestUsbPermission(candidates.get(which)))
                .setNegativeButton(activity.getString(R.string.str_cancelar), null)
                .show();
    }

    public void requestUsbPermission(UsbDevice device) {
        if (usbManager == null) {
            callback.log(activity.getString(R.string.str_error) + ": " + activity.getString(R.string.str_err_usb_host_not_supported));
            return;
        }
        String deviceName = device.getProductName() == null ? activity.getString(R.string.str_usb_device) : device.getProductName();
        callback.log(activity.getString(R.string.str_device_detected_requesting, deviceName));
        callback.log(activity.getString(R.string.str_vid_pid_detected,
                String.format(Locale.US, "%04x:%04x", device.getVendorId(), device.getProductId())));

        if (usbManager.hasPermission(device)) {
            connectToDevice(device);
        } else {
            int flags = PendingIntent.FLAG_UPDATE_CURRENT;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                flags |= PendingIntent.FLAG_MUTABLE;
            }
            Intent intent = new Intent(ACTION_USB_PERMISSION);
            intent.setPackage(activity.getPackageName());
            PendingIntent permissionIntent = PendingIntent.getBroadcast(activity, 0, intent, flags);
            usbManager.requestPermission(device, permissionIntent);
        }
    }

    private String formatUsbDeviceLabel(UsbDevice device) {
        String productName = device.getProductName();
        if (productName == null || productName.trim().isEmpty()) {
            productName = activity.getString(R.string.str_usb_device);
        }
        String manufacturer = device.getManufacturerName();
        if (manufacturer == null || manufacturer.trim().isEmpty()) {
            manufacturer = activity.getString(R.string.str_unknown_manufacturer);
        }
        return productName + " (" + manufacturer + ")\nVID:PID "
                + String.format(Locale.US, "%04x:%04x", device.getVendorId(), device.getProductId());
    }

    private static String resolveAutoProgrammer(UsbDevice device) {
        if (device == null) {
            return null;
        }
        String productName = device.getProductName();
        if (productName != null && productName.toLowerCase(Locale.US).contains("spidriver")) {
            return "spidriver";
        }
        String key = String.format(Locale.US, "%04x:%04x", device.getVendorId(), device.getProductId());
        String mapped = USB_AUTO_MAP.get(key);
        if (mapped != null) {
            return mapped;
        }
        // libjaylink reconoce cualquier J-Link de SEGGER, no solo los PID listados.
        if (device.getVendorId() == 0x1366) {
            return "jlink_spi";
        }
        return null;
    }

    private void connectToDevice(UsbDevice device) {
        if (usbManager == null) {
            callback.log(activity.getString(R.string.str_error) + ": " + activity.getString(R.string.str_err_usb_host_not_supported));
            callback.onDeviceConnectionFailed(device.getProductName());
            return;
        }
        currentDevice = device;
        currentConnection = usbManager.openDevice(device);
        if (currentConnection == null) {
            currentDevice = null;
            callback.onDeviceConnectionFailed(device.getProductName());
            return;
        }

        currentFd = currentConnection.getFileDescriptor();
        String deviceName = device.getProductName() == null ? activity.getString(R.string.str_usb_device) : device.getProductName();
        String vidPid = String.format(Locale.US, "%04x:%04x", device.getVendorId(), device.getProductId());

        String autoProg = resolveAutoProgrammer(device);
        boolean isRecognized = autoProg != null;

        callback.onDeviceConnected(deviceName, currentFd, vidPid, isRecognized, autoProg);

        // Programadores seriales (serprog, buspirate_spi, spidriver)
        if (needsPtyBridge(autoProg != null ? autoProg : "")) {
            closePtyBridge();
            callback.log(activity.getString(R.string.str_serial_programmer_detected));
            PtyBridge bridge = new PtyBridge();
            bridge.setLogCallback(msg -> callback.log(msg));
            if (bridge.open(activity, device, usbManager, currentConnection, serprogBaud)) {
                ptyBridge = bridge;
                callback.log(activity.getString(R.string.str_pty_bridge_active_format, ptyBridge.getSlavePath(), serprogBaud));
            } else {
                callback.log(activity.getString(R.string.str_warn_pty_start_failed));
            }
        }
    }

    public void closePtyBridge() {
        if (ptyBridge != null) {
            ptyBridge.close();
            ptyBridge = null;
        }
    }

    public void disconnectDevice() {
        closePtyBridge();
        if (currentConnection != null) {
            try {
                currentConnection.close();
            } catch (Exception ignored) {}
            currentConnection = null;
        }
        currentDevice = null;
        currentFd = -1;
        callback.onDeviceDisconnected();
    }

    public static boolean needsPtyBridge(String prog) {
        return "serprog".equals(prog)
                || "buspirate_spi".equals(prog)
                || "spidriver".equals(prog);
    }

    // ── Transporte serprog (Socket TCP local vs PTY) ──
    public static final String PREFS = "flashrom_prefs";
    public static final String KEY_SERPROG_TRANSPORT = "serprog_transport_mode";
    public static final String TRANSPORT_SOCKET = "socket";
    public static final String TRANSPORT_PTY = "pty";

    /**
     * True si para este programador debe usarse el Socket TCP local.
     * Sólo serprog lo soporta en flashrom (buspirate_spi/spidriver requieren dev=).
     */
    public boolean shouldUseSocketTransport(String prog) {
        if (!"serprog".equals(prog)) return false;
        String mode = activity.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .getString(KEY_SERPROG_TRANSPORT, TRANSPORT_SOCKET);
        return TRANSPORT_SOCKET.equals(mode);
    }

    /**
     * Inicia el forwarding del puente con el transporte preferido para el programador.
     * El transporte queda fijado mientras dure la conexión USB.
     */
    public boolean startBridgeForwarding(String prog) {
        if (ptyBridge == null) return false;
        boolean ok = ptyBridge.startForwarding(shouldUseSocketTransport(prog), "serprog".equals(prog));
        if (ok && "serprog".equals(prog) && ptyBridge.isSocketMode() != shouldUseSocketTransport(prog)) {
            callback.log("[INFO] Transporte activo: " + (ptyBridge.isSocketMode() ? "Socket TCP" : "PTY")
                    + " (el cambio de transporte se aplica al reconectar el programador)");
        }
        return ok;
    }

    public String buildPtyProgrammerParam(String programmer) {
        if (ptyBridge == null || !ptyBridge.isOpen())
            return programmer;
        if ("serprog".equals(programmer) && ptyBridge.isSocketMode()) {
            return "serprog:ip=127.0.0.1:" + ptyBridge.getSocketPort();
        }
        String devPath = ptyBridge.getSlavePath();
        if ("serprog".equals(programmer)) {
            return "serprog:dev=" + devPath + ":" + ptyBridge.getBaudRate();
        } else {
            return programmer + ":dev=" + devPath;
        }
    }
}
