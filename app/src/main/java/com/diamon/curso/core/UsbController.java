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
    private static final int SERPROG_BAUD = 115200;

    public interface Callback {
        void log(String message);
        void onDeviceConnected(String deviceName, int fd, String vidPid, boolean isRecognized, String autoProg);
        void onDeviceConnectionFailed(String deviceName);
        void onDeviceDisconnected();
    }

    private final Activity activity;
    private final UsbManager usbManager;
    private final Callback callback;

    private UsbDeviceConnection currentConnection;
    private int currentFd = -1;
    private PtyBridge ptyBridge = null;

    public static final Map<String, String> USB_AUTO_MAP = new HashMap<String, String>() {{
        // CH341A SPI
        put("1a86:5512", "ch341a_spi");
        put("1a86:5523", "ch347_spi"); // ch347 SPI/I2C/UART
        put("1a86:55db", "ch347_spi"); // ch347 SPI (Mode 3)
        // FT2232H / FT4232H / FT232H (VID 0403)
        put("0403:6010", "ft2232_spi");
        put("0403:6011", "ft2232_spi");
        put("0403:6014", "ft2232_spi");
        put("0403:6015", "ft2232_spi");
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
        // Serprog
        put("2341:0043", "serprog");
        put("2341:0001", "serprog");
        put("1a86:7523", "serprog");
        put("10c4:ea60", "serprog");
        put("067b:2303", "serprog");
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
                        callback.log(activity.getString(R.string.str_error) + ": USB permission denied.");
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
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            activity.registerReceiver(usbReceiver, filter, Context.RECEIVER_NOT_EXPORTED);
        } else {
            activity.registerReceiver(usbReceiver, filter);
        }
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

    public boolean isConnected() {
        return currentFd >= 0;
    }

    public void searchAndRequestProgrammer(String currentProgrammer, java.util.function.Consumer<String> onProgrammerAutoSelected) {
        Map<String, UsbDevice> devices = usbManager.getDeviceList();
        if (devices == null || devices.isEmpty()) {
            callback.log(activity.getString(R.string.str_error) + ": No USB device detected.");
            return;
        }

        List<UsbDevice> candidates = new ArrayList<>(devices.values());

        // Auto-selección lógica
        for (UsbDevice device : candidates) {
            String key = String.format(Locale.US, "%04x:%04x", device.getVendorId(), device.getProductId());
            String autoProg = null;
            String prodName = device.getProductName();
            if (prodName != null && prodName.toLowerCase(Locale.US).contains("spidriver")) {
                autoProg = "spidriver";
            } else if (USB_AUTO_MAP.containsKey(key)) {
                autoProg = USB_AUTO_MAP.get(key);
            }
            if (autoProg != null) {
                onProgrammerAutoSelected.accept(autoProg);
                callback.log("Detección automática: Dispositivo " + key + " reconocido como " + autoProg);
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

        callback.log(activity.getString(R.string.str_log_warn_dependencies_failed).replace("[WARN]", "[AVISO]") + " -> USB programmer not automatically recognized.");
        callback.log("Dispositivos conocidos: CH341A, FT2232, Bus Pirate, Dediprog, ST-LINK, etc.");
        callback.log("Puedes intentar conectarte manualmente — flashrom reportará si es compatible.");

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
        String deviceName = device.getProductName() == null ? activity.getString(R.string.str_usb_device) : device.getProductName();
        callback.log("Dispositivo detectado: " + deviceName + " | Solicitando enlace...");
        callback.log("VID:PID detectado => "
                + String.format(Locale.US, "%04x:%04x", device.getVendorId(), device.getProductId()));

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

    private void connectToDevice(UsbDevice device) {
        currentConnection = usbManager.openDevice(device);
        if (currentConnection == null) {
            callback.onDeviceConnectionFailed(device.getProductName());
            return;
        }

        currentFd = currentConnection.getFileDescriptor();
        String deviceName = device.getProductName() == null ? activity.getString(R.string.str_usb_device) : device.getProductName();
        String vidPid = String.format(Locale.US, "%04x:%04x", device.getVendorId(), device.getProductId());

        boolean isRecognized = USB_AUTO_MAP.containsKey(vidPid);
        String autoProg = isRecognized ? USB_AUTO_MAP.get(vidPid) : null;
        if (deviceName != null && deviceName.toLowerCase(Locale.US).contains("spidriver")) {
            autoProg = "spidriver";
            isRecognized = true;
        }

        callback.onDeviceConnected(deviceName, currentFd, vidPid, isRecognized, autoProg);

        // Programadores seriales (serprog, buspirate_spi, spidriver)
        if (needsPtyBridge(autoProg != null ? autoProg : "")) {
            closePtyBridge();
            callback.log("Programador serial detectado — iniciando puente PTY...");
            PtyBridge bridge = new PtyBridge();
            bridge.setLogCallback(msg -> callback.log(msg));
            if (bridge.open(device, usbManager, currentConnection, SERPROG_BAUD)) {
                ptyBridge = bridge;
                callback.log("PtyBridge activo: flashrom usará " + ptyBridge.getSlavePath()
                        + " a " + SERPROG_BAUD + " bps");
            } else {
                callback.log("[WARN] PtyBridge no pudo iniciarse. ¿devpts disponible? Revisa el log nativo.");
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
        currentFd = -1;
        callback.onDeviceDisconnected();
    }

    public static boolean needsPtyBridge(String prog) {
        return "serprog".equals(prog)
                || "buspirate_spi".equals(prog)
                || "spidriver".equals(prog);
    }

    public String buildPtyProgrammerParam(String programmer) {
        if (ptyBridge == null || !ptyBridge.isOpen())
            return programmer;
        String devPath = ptyBridge.getSlavePath();
        if ("serprog".equals(programmer)) {
            return "serprog:dev=" + devPath + ":" + ptyBridge.getBaudRate();
        } else {
            return programmer + ":dev=" + devPath;
        }
    }
}
