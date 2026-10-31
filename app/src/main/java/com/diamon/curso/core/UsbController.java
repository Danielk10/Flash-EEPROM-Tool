package com.diamon.curso.core;

import android.app.Activity;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbDeviceConnection;
import android.hardware.usb.UsbInterface;
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

    public static final Map<String, String> USB_AUTO_MAP;

    static {
        Map<String, String> map = new HashMap<>();

        // CH341A: solo el PID 0x5512 corresponde al modo SPI.
        // 0x5523 es CH341 en modo UART y no sirve para flashrom.
        map.put("1a86:5512", "ch341a_spi");

        // CH347T / CH347F.
        map.put("1a86:55db", "ch347_spi");
        map.put("1a86:55de", "ch347_spi");

        // FTDI: el nombre del programador no basta; hay que indicar el tipo
        // porque ft2232_spi usa FT4232H (0x6011) como PID por defecto.
        map.put("0403:6010", "ft2232_spi:type=2232H");
        map.put("0403:6011", "ft2232_spi:type=4232H");
        map.put("0403:6014", "ft2232_spi:type=232H");
        map.put("0403:6041", "ft2232_spi:type=4233H");
        map.put("0403:8a98", "ft2232_spi:type=tumpa");
        map.put("0403:8a99", "ft2232_spi:type=tumpalite");
        map.put("0403:bbe2", "ft2232_spi:type=kt-link");
        map.put("0403:cff8", "ft2232_spi:type=jtagkey");
        map.put("096c:1449", "ft2232_spi:type=picotap");
        map.put("1457:5118", "ft2232_spi:type=openmoko");
        map.put("15ba:0003", "ft2232_spi:type=arm-usb-ocd");
        map.put("15ba:0004", "ft2232_spi:type=arm-usb-tiny");
        map.put("15ba:002a", "ft2232_spi:type=arm-usb-tiny-h");
        map.put("15ba:002b", "ft2232_spi:type=arm-usb-ocd-h");
        map.put("18d1:5001", "ft2232_spi:type=google-servo");
        map.put("18d1:5002", "ft2232_spi:type=google-servo-v2-legacy");
        map.put("18d1:5003", "ft2232_spi:type=google-servo-v2");

        // ST-LINK V3 con bridge SPI. 0x374E (V3E Mini/MiniE) queda fuera
        // porque flashrom lo marca como BAD (no soporta el bridge).
        map.put("0483:374f", "stlinkv3_spi");
        map.put("0483:3753", "stlinkv3_spi");
        map.put("0483:3754", "stlinkv3_spi");

        // J-Link: libjaylink reconoce cualquier J-Link de SEGGER.
        map.put("1366:0101", "jlink_spi");
        map.put("1366:0105", "jlink_spi");
        map.put("1fc9:000c", "jlink_spi");

        map.put("04d8:0033", "pickit2_spi");
        map.put("09fb:6001", "usbblaster_spi");
        map.put("0483:dada", "dediprog");
        map.put("1443:0007", "digilent_spi");
        map.put("1209:c0ca", "dirtyjtag_spi");
        map.put("0955:cf11", "nv_sma_spi");

        // Serprog sobre adaptadores USB-serie (el firmware debe implementar serprog).
        map.put("2341:0043", "serprog"); // Arduino Uno
        map.put("2341:0001", "serprog"); // Arduino
        map.put("16c0:0483", "serprog"); // Teensy
        map.put("03eb:2044", "serprog"); // Atmel LUFA
        map.put("1eaf:0004", "serprog"); // LeafLabs Maple
        map.put("0d28:0204", "serprog"); // ARM mbed
        map.put("0483:5740", "serprog"); // STM32 virtual COM port
        map.put("2e8a:0005", "serprog"); // Raspberry Pi Pico (MicroPython)
        map.put("2e8a:000a", "serprog"); // Raspberry Pi Pico SDK
        map.put("1a86:7523", "serprog"); // CH340
        map.put("1a86:55d4", "serprog"); // CH9102F
        map.put("10c4:ea60", "serprog"); // CP2102
        map.put("10c4:ea70", "serprog"); // CP2105
        map.put("10c4:ea71", "serprog"); // CP2108
        map.put("067b:2303", "serprog"); // PL2303
        map.put("067b:23a3", "serprog"); // PL2303GC
        map.put("067b:23b3", "serprog"); // PL2303GB
        map.put("067b:23c3", "serprog"); // PL2303GT
        map.put("067b:23d3", "serprog"); // PL2303GL
        map.put("067b:23e3", "serprog"); // PL2303GE
        map.put("067b:23f3", "serprog"); // PL2303GS
        map.put("067b:2323", "serprog"); // PL2303 variant
        map.put("067b:2304", "serprog"); // PL2303 variant

        USB_AUTO_MAP = Collections.unmodifiableMap(map);
    }

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
        return resolveAutoProgrammer(
                device.getVendorId(),
                device.getProductId(),
                device.getProductName(),
                hasRaidenInterface(device));
    }

    /**
     * Resuelve el programador a partir del VID/PID y, cuando el PID es ambiguo,
     * del nombre del producto. Es estático y sin dependencias de Android para
     * poder probarse en tests unitarios.
     */
    static String resolveAutoProgrammer(int vendorId, int productId, String productName,
            boolean hasRaidenInterface) {
        String name = productName == null ? "" : productName.toLowerCase(Locale.US);

        // Adaptadores USB-serie cuyo VID/PID es genérico: solo el nombre del
        // producto permite saber qué firmware/protocolo implementan.
        if (name.contains("spidriver")) {
            return "spidriver";
        }
        if (name.contains("bus pirate") || name.contains("buspirate")) {
            return "buspirate_spi";
        }
        if (name.contains("developerbox")) {
            return "developerbox";
        }

        // Variantes FT2232H que comparten VID/PID con el FT2232H genérico pero
        // necesitan un type= distinto por su cableado de GPIO.
        if (name.contains("busblaster") || name.contains("bus blaster")) {
            return "ft2232_spi:type=busblaster";
        }
        if (name.contains("flyswatter")) {
            return "ft2232_spi:type=flyswatter";
        }

        // raiden_debug_spi no usa una tabla de PID: coincide con cualquier
        // dispositivo Google que exponga la interfaz vendor-specific 0xff/0x51.
        if (vendorId == 0x18d1 && hasRaidenInterface) {
            return "raiden_debug_spi";
        }

        String key = String.format(Locale.US, "%04x:%04x", vendorId, productId);
        String mapped = USB_AUTO_MAP.get(key);
        if (mapped != null) {
            return mapped;
        }

        // libjaylink reconoce cualquier J-Link de SEGGER, no solo los PID listados.
        if (vendorId == 0x1366 || (vendorId == 0x1fc9 && productId == 0x000c)) {
            return "jlink_spi";
        }
        return null;
    }

    private static boolean hasRaidenInterface(UsbDevice device) {
        for (int i = 0; i < device.getInterfaceCount(); i++) {
            UsbInterface usbInterface = device.getInterface(i);
            if (usbInterface != null
                    && usbInterface.getInterfaceClass() == 0xff
                    && usbInterface.getInterfaceSubclass() == 0x51) {
                return true;
            }
        }
        return false;
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
        boolean ok = ptyBridge.startForwarding(shouldUseSocketTransport(prog));
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
