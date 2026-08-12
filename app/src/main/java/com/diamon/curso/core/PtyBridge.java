package com.diamon.curso.core;

import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbDeviceConnection;
import android.hardware.usb.UsbManager;
import android.os.ParcelFileDescriptor;
import android.util.Log;

import com.hoho.android.usbserial.driver.UsbSerialDriver;
import com.hoho.android.usbserial.driver.UsbSerialPort;
import com.hoho.android.usbserial.driver.UsbSerialProber;

import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.List;

/**
 * PtyBridge: Puente entre el pseudo-terminal (PTY) que usa flashrom (-p
 * serprog:dev=/dev/pts/N)
 * y el dispositivo USB-Serial real (Arduino con firmware serprog) via
 * usb-serial-for-android.
 *
 * Arquitectura:
 * flashrom <-> /dev/pts/N (slave PTY)
 * ↕ (ParcelFileDescriptor del master PTY)
 * PtyBridge (2 hilos de forwarding)
 * ↕
 * UsbSerialPort (usb-serial-for-android)
 * ↕
 * [Arduino con firmware serprog]
 */
public class PtyBridge {

    private static final String TAG = "PtyBridge";
    private static final int USB_TIMEOUT_MS = 50;
    private static final int BUFFER_SIZE = 4096;
    private static final int BEACON_BYTE_1 = 0xAA;
    private static final int BEACON_BYTE_2 = 0x55;
    /** Cuántos bytes iniciales loggear en hex para diagnóstico */
    private static final int DEBUG_HEX_LIMIT = 32;

    /** Callback para enviar logs al UI de la app (no sólo logcat) */
    public interface LogCallback {
        void onLog(String message);
    }

    // Cargado desde native-lib.cpp (mismo .so que el resto de la app)
    static {
        System.loadLibrary("curso");
    }

    // JNI: retorna [masterFdAsString, slavePath] o null si falla
    public static native String[] createPty();

    // JNI: cierra el master FD nativo
    public static native void closeFd(int fd);

    // JNI: test round-trip completo a través del PTY slave (como flashrom)
    public static native String nativeTestRoundTrip(String slavePath);

    // JNI: escribe bytes directamente en un FD nativo (retorna bytes escritos o -1)
    public static native int writeFd(int fd, byte[] data, int len);

    // -------- Estado --------
    private int masterFd = -1;
    private String slavePath = null;
    private UsbSerialPort usbPort = null;
    private UsbDeviceConnection usbConnection = null;
    private ParcelFileDescriptor masterPfd = null;
    private volatile boolean running = false;
    private Thread threadMasterToUsb = null;
    private Thread threadUsbToMaster = null;
    private volatile boolean masterToUsbReady = false;
    private volatile boolean usbToMasterReady = false;
    private int baudRate = 57600;
    private LogCallback logCallback = null;

    // Contadores de diagnóstico para Thread B
    private volatile int diagPtyReads = 0;
    private volatile int diagUsbBytesWritten = 0;
    private volatile int diagUsbWriteErrors = 0;
    private volatile int diagUsbReads = 0;
    private volatile int diagUsbBytesReceived = 0;
    private volatile int diagPtyWrites = 0;
    private volatile int diagPtyWriteErrors = 0;
    private volatile String diagLastError = "none";

    /** Configura el callback para que los logs del bridge aparezcan en la app */
    public void setLogCallback(LogCallback cb) {
        this.logCallback = cb;
    }

    private void bridgeLog(String msg) {
        Log.i(TAG, msg);
        if (logCallback != null) {
            logCallback.onLog("[PtyBridge] " + msg);
        }
    }

    /**
     * Abre el puente:
     * 1. Crea el par PTY vía JNI (obtiene master FD y slave path).
     * 2. Abre el UsbSerialPort del dispositivo Arduino.
     * 3. Inicia los dos hilos de forwarding bidireccional.
     *
     * @param device     Dispositivo USB detectado
     * @param manager    UsbManager de la app
     * @param connection Conexión ya abierta (openDevice) del dispositivo
     * @param baud       Velocidad serie del firmware serprog (típico: 115200)
     * @return true si todo se abrió correctamente, false en caso de error
     */
    public boolean open(UsbDevice device, UsbManager manager, UsbDeviceConnection connection, int baud) {
        this.baudRate = baud;
        this.usbConnection = connection;

        // 1. Crear PTY
        String[] ptyResult = createPty();
        if (ptyResult == null || ptyResult.length < 2) {
            Log.e(TAG, "createPty() falló: posix_openpt no disponible en este kernel.");
            return false;
        }
        try {
            masterFd = Integer.parseInt(ptyResult[0]);
            slavePath = ptyResult[1];
        } catch (NumberFormatException e) {
            Log.e(TAG, "FD inválido retornado por createPty(): " + ptyResult[0]);
            return false;
        }
        Log.i(TAG, "PTY creado: masterFd=" + masterFd + " slavePath=" + slavePath);

        // 2. Abrir puerto USB-Serial con usb-serial-for-android
        List<UsbSerialDriver> drivers = UsbSerialProber.getDefaultProber().findAllDrivers(manager);
        UsbSerialDriver targetDriver = null;
        for (UsbSerialDriver d : drivers) {
            if (d.getDevice().getDeviceId() == device.getDeviceId()) {
                targetDriver = d;
                break;
            }
        }
        if (targetDriver == null || targetDriver.getPorts().isEmpty()) {
            Log.e(TAG, "No se encontró driver USB-Serial para el dispositivo: " + device.getDeviceName());
            cleanupPty();
            return false;
        }

        usbPort = targetDriver.getPorts().get(0);
        try {
            usbPort.open(connection);
            usbPort.setParameters(baudRate, 8, UsbSerialPort.STOPBITS_1, UsbSerialPort.PARITY_NONE);
            // DTR/RTS en false para NO disparar Auto-Reset del Arduino.
            // El Arduino ya se reinició durante la enumeración USB.
            // Un pulso DTR adicional aquí causaría un SEGUNDO reset,
            // cuya basura del bootloader contaminaría el PTY slave.
            usbPort.setDTR(false);
            usbPort.setRTS(false);
        } catch (IOException e) {
            Log.e(TAG, "Error abriendo UsbSerialPort: " + e.getMessage());
            cleanupPty();
            return false;
        }

        // 3. Crear ParcelFileDescriptor ADOPTANDO el master FD.
        // adoptFd() transfiere la propiedad del FD al PFD: el PFD lo cerrará
        // en cleanupPfd(). Usamos adoptFd (no fromFd) para evitar que un
        // dup() temporal sea GC'd y cierre el FD original.
        masterPfd = ParcelFileDescriptor.adoptFd(masterFd);

        // 4. NO iniciar hilos de forwarding aquí.
        // Se inician DESPUÉS del purge+handshake con startForwarding().
        // Esto evita que los hilos reenvíen basura del bootloader al PTY slave.
        Log.i(TAG, "PtyBridge preparado: flashrom usará " + slavePath + " a " + baudRate + " bps");
        return true;
    }

    /**
     * Inicia los hilos de forwarding bidireccional PTY↔USB.
     * DEBE llamarse DESPUÉS de purge() y testHandshake(), justo antes de flashrom.
     * Si se llama antes, los hilos reenviarán basura del bootloader al PTY slave
     * y flashrom leerá esa basura como respuesta corrupta.
     */
    public void startForwarding() {
        if (running) {
            Log.w(TAG, "startForwarding() llamado pero ya está running — ignorando");
            return;
        }

        running = true;
        masterToUsbReady = false;
        usbToMasterReady = false;
        startForwardingThreads();
        waitForwardingReady();
        bridgeLog("Forwarding activo — puente PTY↔USB listo");
    }

    /**
     * Prepara la sesión serprog antes de lanzar flashrom:
     * 1) activa DTR/RTS,
     * 2) espera beacon 0xAA 0x55 directamente en USB (sin PTY forwarding),
     * 3) purga buffers.
     *
     * @return true si se recibió beacon, false por timeout o error.
     */
    public boolean prepareForFlashromSession(long timeoutMs) {
        if (usbPort == null) {
            bridgeLog("prepareForFlashromSession: puerto USB no disponible");
            return false;
        }
        if (running) {
            bridgeLog("prepareForFlashromSession: forwarding ya activo, no se espera beacon");
            return true;
        }

        try {
            usbPort.setDTR(true);
            usbPort.setRTS(true);
            bridgeLog("DTR/RTS activados — esperando beacon 0xAA 0x55");
        } catch (IOException e) {
            bridgeLog("Error activando DTR/RTS: " + e.getMessage());
            return false;
        }

        boolean beaconOk = waitForBeacon(timeoutMs);
        if (!beaconOk) {
            bridgeLog("Timeout esperando beacon de arranque");
            return false;
        }

        bridgeLog("Beacon recibido — Arduino listo para serprog");
        purge();
        return true;
    }

    /**
     * Prepara la sesión serial para programadores que NO envían beacon
     * (buspirate_spi, spidriver, etc.):
     * 1) activa DTR/RTS para habilitar flujo de datos CH340/FTDI/CP2102,
     * 2) espera breve estabilización,
     * 3) purga buffers residuales.
     *
     * @return true si todo fue bien, false si hay error.
     */
    public boolean prepareForSerialSession() {
        if (usbPort == null) {
            bridgeLog("prepareForSerialSession: puerto USB no disponible");
            return false;
        }
        if (running) {
            bridgeLog("prepareForSerialSession: forwarding ya activo");
            return true;
        }

        try {
            usbPort.setDTR(true);
            usbPort.setRTS(true);
            bridgeLog("DTR/RTS activados — programador serial listo");
            // Breve espera de estabilización sin beacon
            Thread.sleep(500);
            purge();
        } catch (IOException | InterruptedException e) {
            bridgeLog("Error preparando sesión serial: " + e.getMessage());
            return false;
        }
        return true;
    }

    private boolean waitForBeacon(long timeoutMs) {
        long deadlineMs = System.currentTimeMillis() + timeoutMs;
        int state = 0;
        byte[] buf = new byte[64];

        while (System.currentTimeMillis() < deadlineMs) {
            if (usbPort == null) return false;
            try {
                int n = usbPort.read(buf, 100);
                if (n <= 0) {
                    continue;
                }
                for (int i = 0; i < n; i++) {
                    int b = buf[i] & 0xFF;
                    if (state == 0) {
                        state = (b == BEACON_BYTE_1) ? 1 : 0;
                    } else {
                        if (b == BEACON_BYTE_2) {
                            return true;
                        }
                        state = (b == BEACON_BYTE_1) ? 1 : 0;
                    }
                }
            } catch (IOException e) {
                diagLastError = "beacon-read: " + e.getMessage();
            }
        }
        return false;
    }

    /**
     * Devuelve el path del slave PTY para pasárselo a flashrom:
     * "-p serprog:dev=" + getSlavePath() + ":" + getBaudRate()
     */
    public String getSlavePath() {
        return slavePath;
    }

    public int getBaudRate() {
        return baudRate;
    }

    /**
     * Detiene los hilos y libera todos los recursos (PTY + puerto USB-serial).
     * Seguro llamarlo desde onDestroy() o cuando flashrom termina.
     */
    public void close() {
        running = false;

        // Interrumpir hilos
        if (threadMasterToUsb != null) {
            threadMasterToUsb.interrupt();
            threadMasterToUsb = null;
        }
        if (threadUsbToMaster != null) {
            threadUsbToMaster.interrupt();
            threadUsbToMaster = null;
        }

        cleanupPort();
        cleanupPfd();
        cleanupPty();
        Log.i(TAG, "PtyBridge cerrado.");
    }

    /** Reporte de diagnóstico de Thread A/B para depuración visible en la app */
    public String getDiagnosticReport() {
        return "ptyReads=" + diagPtyReads + " usbBytesWritten=" + diagUsbBytesWritten
                + " usbWriteErrors=" + diagUsbWriteErrors
                + " usbReads=" + diagUsbReads + " usbBytesRecv=" + diagUsbBytesReceived
                + " ptyWrites=" + diagPtyWrites + " ptyErrors=" + diagPtyWriteErrors
                + " lastError=" + diagLastError;
    }

    /** True si el puente está preparado (PTY creado + USB abierto). */
    public boolean isOpen() {
        return slavePath != null && usbPort != null;
    }

    /** True si los hilos de forwarding PTY↔USB están activos. */
    public boolean isForwardingActive() {
        return running;
    }

    /**
     * Descarta toda la basura acumulada en el buffer USB y el PTY master.
     * Llamar ANTES de iniciar flashrom para que lo primero que lea sea
     * el ACK real del Arduino y no restos del bootloader o ruido del CH340.
     */
    public void purge() {
        // Evitar carreras con los hilos de forwarding: drenar aquí mientras Thread A/B
        // están activos puede duplicar/perder bytes y romper SYNC de serprog.
        if (running) {
            bridgeLog("Purge omitido: forwarding activo (evita carrera USB↔PTY)");
            return;
        }
        // Drenar buffer USB con reintentos (el bootloader puede seguir enviando)
        if (usbPort != null) {
            byte[] drain = new byte[BUFFER_SIZE];
            int totalDrained = 0;
            for (int attempt = 0; attempt < 3; attempt++) {
                try {
                    int n;
                    while ((n = usbPort.read(drain, 20)) > 0) {
                        totalDrained += n;
                    }
                    Thread.sleep(50); // Esperar por datos rezagados
                } catch (IOException | InterruptedException ignored) {
                }
            }
            if (totalDrained > 0) {
                Log.i(TAG, "Purge USB: descartados " + totalDrained + " bytes de basura");
            }
        }
        // Drenar buffer PTY master (bytes que ya pasaron del USB al PTY)
        if (masterPfd != null) {
            try {
                FileInputStream masterIn = new FileInputStream(masterPfd.getFileDescriptor());
                int avail = masterIn.available();
                if (avail > 0) {
                    byte[] skip = new byte[avail];
                    masterIn.read(skip);
                    Log.i(TAG, "Purge PTY: descartados " + avail + " bytes del master");
                }
            } catch (IOException ignored) {
            }
        }
    }

    /**
     * Test de handshake completo: envía SYNCNOP (0x10) + NOP (0x00) y verifica
     * que el Arduino responde la secuencia correcta: 0x15 0x06 0x06.
     *
     * Esto prueba 3 cosas a la vez:
     * - Que el comando 0x10 llega íntegro al Arduino
     * - Que el firmware responde NAK+ACK (protocolo serprog correcto)
     * - Que los bytes de vuelta viajan íntegros por USB y por el PTY
     *
     * @return String con el diagnóstico legible (para mostrar en el log de la app)
     */
    public String testHandshake() {
        if (usbPort == null)
            return "[ERROR] Puerto USB no disponible";
        try {
            // 1) SYNCNOP (0x10) -> esperado: 15 06
            usbPort.write(new byte[] { 0x10 }, 1, USB_TIMEOUT_MS);
            byte[] syncResp = readUsbResponse(2, 5);
            Log.i(TAG, "Handshake test SYNCNOP: [" + bytesToHex(syncResp, syncResp.length) + "]");

            // 2) NOP (0x00) -> esperado: 06
            usbPort.write(new byte[] { 0x00 }, 1, USB_TIMEOUT_MS);
            byte[] nopResp = readUsbResponse(1, 3);
            Log.i(TAG, "Handshake test NOP: [" + bytesToHex(nopResp, nopResp.length) + "]");

            // 3) Query Programmer Name (0x03) -> esperado: 06 + 16 bytes
            usbPort.write(new byte[] { 0x03 }, 1, USB_TIMEOUT_MS);
            byte[] nameResp = readUsbResponse(17, 6);
            Log.i(TAG, "Handshake test NAME: [" + bytesToHex(nameResp, nameResp.length) + "]");

            boolean syncOk = syncResp.length >= 2
                    && (syncResp[0] & 0xFF) == 0x15
                    && (syncResp[1] & 0xFF) == 0x06;
            boolean nopOk = nopResp.length >= 1
                    && (nopResp[0] & 0xFF) == 0x06;
            boolean nameOk = nameResp.length >= 17
                    && (nameResp[0] & 0xFF) == 0x06;

            if (!syncOk || !nopOk || !nameOk) {
                return "[FALLO] Handshake incompleto: SYNC=" + bytesToHex(syncResp, syncResp.length)
                        + " NOP=" + bytesToHex(nopResp, nopResp.length)
                        + " NAME=" + bytesToHex(nameResp, nameResp.length);
            }

            byte[] nameBytes = new byte[16];
            System.arraycopy(nameResp, 1, nameBytes, 0, 16);
            String progName = new String(nameBytes).trim();
            return "[OK] Handshake completo: SYNC=" + bytesToHex(syncResp, syncResp.length)
                    + " NOP=" + bytesToHex(nopResp, nopResp.length)
                    + " NAME='" + progName + "'";

        } catch (IOException | InterruptedException e) {
            Log.w(TAG, "Handshake test fallo: " + e.getMessage());
            return "[ERROR] Excepción en test: " + e.getMessage();
        }
    }

    private byte[] readUsbResponse(int minBytes, int maxAttempts) throws IOException, InterruptedException {
        byte[] readBuf = new byte[64];
        java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();

        for (int attempt = 0; attempt < maxAttempts && out.size() < minBytes; attempt++) {
            int n = usbPort.read(readBuf, USB_TIMEOUT_MS);
            if (n > 0) {
                out.write(readBuf, 0, n);
            }
            if (out.size() < minBytes) {
                Thread.sleep(40);
            }
        }
        return out.toByteArray();
    }

    /**
     * Test end-to-end: envía SYNCNOP a través del PTY slave (como flashrom)
     * y verifica que la respuesta del Arduino llega de vuelta por toda la cadena.
     * REQUIERE que startForwarding() ya se haya llamado.
     */
    public String testPtyRoundTrip() {
        if (!running)
            return "[ERROR] Forwarding no iniciado — llama startForwarding() primero";
        if (slavePath == null)
            return "[ERROR] Slave PTY no disponible";
        return nativeTestRoundTrip(slavePath);
    }

    // -------- Privados --------

    private void startForwardingThreads() {
        // Hilo A: PTY master → USB (lo que flashrom escribe al puerto serie virtual)
        threadMasterToUsb = new Thread(() -> {
            int totalSent = 0;
            int zeroReads = 0;
            diagPtyReads = 0;
            diagUsbBytesWritten = 0;
            diagUsbWriteErrors = 0;
            masterToUsbReady = true;
            boolean firstWriteLogged = false;
            bridgeLog("Thread A iniciado — masterFd=" + masterPfd.getFd());
            try (FileInputStream masterIn = new FileInputStream(masterPfd.getFileDescriptor())) {
                byte[] buf = new byte[BUFFER_SIZE];
                while (running && !Thread.currentThread().isInterrupted()) {
                    int n;
                    try {
                        n = masterIn.read(buf);
                    } catch (IOException readError) {
                        // En PTY master, EIO significa que el slave (/dev/pts/N)
                        // todavía no fue abierto por flashrom. No es fatal: reintentar.
                        if (running && isPtySlaveNotOpenedYet(readError)) {
                            sleepQuietly(5);
                            continue;
                        }
                        throw readError;
                    }
                    diagPtyReads++;
                    if (n > 0 && usbPort != null) {
                        zeroReads = 0;
                        if (!firstWriteLogged) {
                            Log.d(TAG, "PTY→USB write " + n + " bytes");
                            firstWriteLogged = true;
                        }
                        // Debug: loggear los primeros bytes enviados por flashrom
                        if (totalSent < DEBUG_HEX_LIMIT) {
                            int logLen = Math.min(n, DEBUG_HEX_LIMIT - totalSent);
                            String debugMsg = "[PTY→USB] " + n + " B: " + bytesToHex(buf, logLen);
                            Log.d(TAG, debugMsg);
                            bridgeLog(debugMsg);
                        }
                        totalSent += n;
                        try {
                            // El UART se maneja como un flujo binario. Mantener cada
                            // lectura del PTY intacta evita separar cabeceras/payloads
                            // de O_SPIOP y evita depender de temporizaciones del CH340.
                            usbPort.write(buf, n, USB_TIMEOUT_MS);
                            diagUsbBytesWritten += n;
                        } catch (IOException e) {
                            diagUsbWriteErrors++;
                            if (running) {
                                diagLastError = "usb-write: " + e.getMessage();
                                Log.w(TAG, "Error escribiendo a USB: " + e.getMessage());
                                bridgeLog("Thread A USB WRITE ERROR: " + e.getMessage());
                            }
                        }
                    } else if (n == 0) {
                        zeroReads++;
                        if (zeroReads == 50) {
                            bridgeLog("Thread A: 50 lecturas PTY vacías (flashrom no está escribiendo?)");
                        }
                    }
                }
            } catch (IOException e) {
                if (running) {
                    diagLastError = "pty-read: " + e.getMessage();
                    Log.w(TAG, "Hilo master→USB terminado: " + e.getMessage());
                    bridgeLog("Thread A READ ERROR: " + e.getMessage());
                }
            }
            bridgeLog("Thread A fin — ptyReads=" + diagPtyReads + " usbBytesWritten=" + diagUsbBytesWritten
                    + " usbWriteErrors=" + diagUsbWriteErrors);
        }, "PtyBridge-master-to-usb");
        threadMasterToUsb.setDaemon(true);
        threadMasterToUsb.start();

        // Hilo B: USB → PTY master (respuestas del Arduino que flashrom lee)
        // Usa FileOutputStream sobre el master PTY para las escrituras (más fiable que
        // JNI writeFd)
        threadUsbToMaster = new Thread(() -> {
            int totalReceived = 0;
            int zeroReads = 0;
            diagUsbReads = 0;
            diagUsbBytesReceived = 0;
            diagPtyWrites = 0;
            diagPtyWriteErrors = 0;
            diagLastError = "none";
            usbToMasterReady = true;
            byte[] buf = new byte[BUFFER_SIZE];
            bridgeLog("Thread B iniciado — masterFd=" + masterPfd.getFd());
            try (FileOutputStream masterOut = new FileOutputStream(masterPfd.getFileDescriptor())) {
                while (running && !Thread.currentThread().isInterrupted()) {
                    try {
                        if (usbPort != null) {
                            // Timeout 200ms para CH340G
                            int n = usbPort.read(buf, 200);
                            diagUsbReads++;
                            if (n > 0) {
                                zeroReads = 0;
                                diagUsbBytesReceived += n;
                                // Loggear primeros bytes recibidos del Arduino
                                if (totalReceived < DEBUG_HEX_LIMIT) {
                                    int logLen = Math.min(n, DEBUG_HEX_LIMIT - totalReceived);
                                    bridgeLog("USB→PTY RECV [" + n + "B]: " + bytesToHex(buf, logLen));
                                }
                                totalReceived += n;

                                // Escribir al master PTY usando FileOutputStream Java
                                try {
                                    masterOut.write(buf, 0, n);
                                    masterOut.flush();
                                    diagPtyWrites++;
                                } catch (IOException we) {
                                    diagPtyWriteErrors++;
                                    diagLastError = "write: " + we.getMessage();
                                    bridgeLog("USB→PTY WRITE ERROR: " + we.getMessage());
                                }
                            } else {
                                zeroReads++;
                                if (zeroReads == 50) {
                                    bridgeLog("Thread B: 50 lecturas USB vacías (Arduino no responde?)");
                                }
                            }
                        }
                    } catch (IOException e) {
                        if (running) {
                            diagLastError = "read: " + e.getMessage();
                            bridgeLog("USB READ ERROR: " + e.getMessage());
                        }
                    }
                }
            } catch (IOException e) {
                bridgeLog("Thread B: FileOutputStream error: " + e.getMessage());
                diagLastError = "stream: " + e.getMessage();
            }
            bridgeLog("Thread B fin — usbRecv=" + totalReceived + " ptyWrites=" + diagPtyWrites + " errors="
                    + diagPtyWriteErrors);
        }, "PtyBridge-usb-to-master");
        threadUsbToMaster.setDaemon(true);
        threadUsbToMaster.start();
    }

    private static boolean isPtySlaveNotOpenedYet(IOException e) {
        if (e == null || e.getMessage() == null) {
            return false;
        }
        // Android típicamente entrega: "read failed: EIO (I/O error)"
        return e.getMessage().contains("EIO");
    }

    private static void sleepQuietly(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException ignored) {
            Thread.currentThread().interrupt();
        }
    }

    private void waitForwardingReady() {
        for (int i = 0; i < 20 && running; i++) {
            if (masterToUsbReady && usbToMasterReady) {
                return;
            }
            try {
                Thread.sleep(25);
            } catch (InterruptedException ignored) {
                Thread.currentThread().interrupt();
                break;
            }
        }
        if (!(masterToUsbReady && usbToMasterReady)) {
            Log.w(TAG, "Forwarding arrancó sin confirmación completa de ambos hilos");
        }
    }

    /**
     * Convierte los primeros 'len' bytes del buffer a string hexadecimal legible.
     */
    private static String bytesToHex(byte[] buf, int len) {
        StringBuilder sb = new StringBuilder(len * 3);
        for (int i = 0; i < len; i++) {
            if (i > 0)
                sb.append(' ');
            sb.append(String.format("%02X", buf[i] & 0xFF));
        }
        return sb.toString();
    }

    private void cleanupPort() {
        if (usbPort != null) {
            try {
                usbPort.close();
            } catch (IOException ignored) {
            }
            usbPort = null;
        }
        if (usbConnection != null) {
            usbConnection.close();
            usbConnection = null;
        }
    }

    private void cleanupPfd() {
        if (masterPfd != null) {
            try {
                masterPfd.close();
            } catch (IOException ignored) {
            }
            masterPfd = null;
        }
    }

    private void cleanupPty() {
        // El FD real es cerrado por masterPfd.close() en cleanupPfd()
        // (adoptFd transfirió la propiedad al PFD).
        // Aquí solo limpiamos el estado Java.
        masterFd = -1;
        slavePath = null;
    }
}
