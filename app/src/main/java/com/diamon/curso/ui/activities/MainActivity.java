package com.diamon.curso.ui.activities;

import com.diamon.curso.R;
import com.diamon.curso.ads.MostrarPublicidad;
import com.diamon.curso.core.PtyBridge;
import com.diamon.curso.ui.views.PinoutView;
import com.diamon.curso.utils.AssetHelper;

import android.app.PendingIntent;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbDeviceConnection;
import android.hardware.usb.UsbManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Looper;
import android.text.InputType;
import android.text.Html;
import android.text.method.LinkMovementMethod;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Collections;
import java.util.Comparator;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import android.content.SharedPreferences;
import android.widget.ProgressBar;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import com.microsoft.appcenter.AppCenter;
import com.microsoft.appcenter.analytics.Analytics;
import com.microsoft.appcenter.crashes.Crashes;

public class MainActivity extends AppCompatActivity {

    private static final String TAG = "FlashromApp";
    private static final String ACTION_USB_PERMISSION = "com.diamon.curso.USB_PERMISSION";
    private static final String PREFS = "flashrom_prefs";
    private static final String KEY_PROGRAMMER = "selected_programmer";
    private static final String KEY_EXPORT_URI = "export_uri";
    private static final String KEY_BIOS_SOURCE = "bios_source";
    private static final String KEY_LAST_READ_FILE = "last_read_file";
    private static final String KEY_LAST_VERSION = "last_version_code";
    private static final String[] SUPPORTED_PROGRAMMERS = {
            "asm106x",
            "atavia",
            "buspirate_spi",
            "ch341a_spi",
            "ch347_spi",
            "dediprog",
            "developerbox_spi",
            "digilent_spi",
            "dirtyjtag_spi",
            "drkaiser",
            "dummy",
            "ft2232_spi",
            "gfxnvidia",
            "internal",
            "it8212",
            "jlink_spi",
            "linux_mtd",
            "linux_spi",
            "parade_lspcon",
            "mediatek_i2c_spi",
            "mstarddc_spi",
            "nicintel",
            "nicintel_eeprom",
            "nicintel_spi",
            "nv_sma_spi",
            "ogp_spi",
            "pickit2_spi",
            "pony_spi",
            "raiden_debug_spi",
            "realtek_mst_i2c_spi",
            "satasii",
            "serprog",
            "spidriver",
            "stlinkv3_spi",
            "usbblaster_spi"
    };

    private static final Map<String, String> USB_AUTO_MAP = new java.util.HashMap<String, String>() {
        {
            // CH341A SPI
            put("1a86:5512", "ch341a_spi");
            put("1a86:5523", "ch341a_spi");
            // CH347 SPI
            put("1a86:55db", "ch347_spi");
            // FT2232 / FT232H / FT4232H
            put("0403:6010", "ft2232_spi");
            put("0403:6011", "ft2232_spi");
            put("0403:6014", "ft2232_spi");
            put("0403:6015", "ft2232_spi"); // Algunos
                                            // usan
                                            // ft232r_spi
                                            // emulado
            // Bus Pirate
            put("0403:6001", "buspirate_spi");
            // ST-LINK
            put("0483:3748", "stlinkv3_spi"); // V2
            put("0483:374b", "stlinkv3_spi"); // V2.1
            put("0483:374d", "stlinkv3_spi"); // V3
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
            // Serprog (Arduinos y adaptadores comunes)
            put("2341:0043", "serprog"); // Uno
            put("2341:0001", "serprog"); // Uno R3
            put("1a86:7523", "serprog"); // CH340 clones
            put("10c4:ea60", "serprog"); // CP2102
        }
    };

    private UsbManager usbManager;
    private UsbDeviceConnection currentConnection;
    private int currentFd = -1;

    // Puente PTY para programador serprog (Arduino con firmware serprog)
    private PtyBridge ptyBridge = null;
    // Baud rate usado por el firmware serprog del Arduino (configurable)
    private static final int SERPROG_BAUD = 115200;

    private LinearLayout layoutLoading;
    private LinearLayout layoutMainUI;
    private ScrollView scrollLog;
    private TextView tvStatus, tvLog, tvLoadingText;
    private android.widget.FrameLayout adContainer;

    private TextView tvOperationStatus;
    private static final Pattern PROGRESS_PATTERN = Pattern.compile("(\\d{1,3})\\s*%");
    private Button btnConnect, btnProbe, btnVerify, btnRead, btnWrite, btnImport, btnExport;
    private Button btnRunCustomCommand, btnClearLogs, btnQuickClear, btnEraseChip, btnAbort;
    private Button btnProgSettings, btnDiagrams, btnDummy;
    private android.widget.CheckBox cbVerifyWrite;
    private Process currentFlashromProcess;
    private EditText etCustomCommand;

    private final StringBuilder logBuffer = new StringBuilder();
    private final android.os.Handler logHandler = new android.os.Handler(android.os.Looper.getMainLooper());
    private boolean isLogUpdatePending = false;
    private final Runnable logUpdater = new Runnable() {
        @Override
        public void run() {
            String newLogs;
            synchronized (logBuffer) {
                newLogs = logBuffer.toString();
                logBuffer.setLength(0);
                isLogUpdatePending = false;
            }
            if (!newLogs.isEmpty()) {
                tvLog.append(newLogs);
                scrollLog.post(() -> scrollLog.fullScroll(ScrollView.FOCUS_DOWN));
            }
        }
    };

    private ExecutorService executor = Executors.newSingleThreadExecutor();
    private String selectedProgrammer = "ch341a_spi";
    private volatile boolean hasReadData = false; // true cuando hay datos LEÍDOS del chip
    private volatile String lastReadFile = "bios.bin"; // archivo del último read exitoso
    private MostrarPublicidad mostrarPublicidad;

    // API para Visor Hexadecimal (Anuncio al regresar)
    private final ActivityResultLauncher<Intent> hexViewerLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            result -> {
                if (mostrarPublicidad != null) {
                    mostrarPublicidad.mostrarInterstitial();
                    mostrarPublicidad.cargarInterstial(); // Precarga para la próxima vez
                }
            });

    // API para importar (Cargar archivo de cualquier carpeta)
    private final ActivityResultLauncher<Intent> fileOpenLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            result -> {
                if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                    Uri uri = result.getData().getData();
                    if (uri != null) {
                        importRomFile(uri);
                    }
                }
            });

    // API para exportar (Guardar archivo en carpeta seleccionada por el usuario)
    private final ActivityResultLauncher<Intent> fileSaveLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(), result -> {
                if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                    Uri uri = result.getData().getData();
                    if (uri != null) {
                        // Persistir permiso para el futuro si es posible
                        try {
                            getContentResolver().takePersistableUriPermission(uri,
                                    Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
                            getSharedPreferences(PREFS, MODE_PRIVATE).edit().putString(KEY_EXPORT_URI, uri.toString())
                                    .apply();
                        } catch (Exception e) {
                            // Algunos URIs no soportan persistencia (SAF ciego), se ignora
                        }

                        exportRomFileToUri(uri);
                    }
                }
            });

    // API para configurar directorio por defecto
    private final ActivityResultLauncher<Intent> directoryPickerLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(), result -> {
                if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                    Uri treeUri = result.getData().getData();
                    if (treeUri != null) {
                        try {
                            getContentResolver().takePersistableUriPermission(treeUri,
                                    Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
                            getSharedPreferences(PREFS, MODE_PRIVATE).edit()
                                    .putString(KEY_EXPORT_URI, treeUri.toString()).apply();
                            log("Directorio de exportación configurado y guardado.");
                        } catch (Exception e) {
                            getSharedPreferences(PREFS, MODE_PRIVATE).edit()
                                    .putString(KEY_EXPORT_URI, treeUri.toString())
                                    .apply();
                            log("Directorio configurado (sin persistencia extendida).");
                        }
                    }
                }
            });

    private final BroadcastReceiver usbReceiver = new BroadcastReceiver() {
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (ACTION_USB_PERMISSION.equals(action)) {
                synchronized (this) {
                    UsbDevice device;
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        device = intent.getParcelableExtra(UsbManager.EXTRA_DEVICE, UsbDevice.class);
                    } else {
                        @SuppressWarnings("deprecation")
                        UsbDevice d = intent.getParcelableExtra(UsbManager.EXTRA_DEVICE);
                        device = d;
                    }

                    if (intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)) {
                        if (device != null) {
                            connectToDevice(device);
                        }
                    } else {
                        log("Permiso USB denegado para " + device);
                    }
                }
            }
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        AppCenter.start(
                getApplication(),
                "cf7ac082-49cd-4cef-bd2d-3f1a3377efa9",
                Analytics.class,
                Crashes.class);

        layoutLoading = findViewById(R.id.layoutLoading);
        layoutMainUI = findViewById(R.id.layoutMainUI);
        tvLoadingText = findViewById(R.id.tvLoadingText);

        tvStatus = findViewById(R.id.tvStatus);
        tvLog = findViewById(R.id.tvLog);
        scrollLog = findViewById(R.id.scrollLog);

        tvOperationStatus = findViewById(R.id.tvOperationStatus);

        btnConnect = findViewById(R.id.btnConnect);
        btnProbe = findViewById(R.id.btnProbe);
        btnVerify = findViewById(R.id.btnVerify);
        btnRead = findViewById(R.id.btnRead);
        btnWrite = findViewById(R.id.btnWrite);
        btnImport = findViewById(R.id.btnImport);
        btnExport = findViewById(R.id.btnExport);
        btnQuickClear = findViewById(R.id.btnQuickClear);
        btnEraseChip = findViewById(R.id.btnEraseChip);
        btnRunCustomCommand = findViewById(R.id.btnRunCustomCommand);
        btnClearLogs = findViewById(R.id.btnClearLogs);
        btnAbort = findViewById(R.id.btnAbort);
        btnProgSettings = findViewById(R.id.btnProgSettings);
        btnDiagrams = findViewById(R.id.btnDiagrams);
        btnDummy = findViewById(R.id.btnDummy);
        cbVerifyWrite = findViewById(R.id.cbVerifyWrite);
        etCustomCommand = findViewById(R.id.etCustomCommand);

        btnAbort.setOnClickListener(v -> abortFlashromProcess());
        btnProgSettings.setOnClickListener(v -> startActivity(new Intent(this, ProgrammerSettingsActivity.class)));
        btnDiagrams.setOnClickListener(v -> showPinoutsDialog());
        btnDummy.setOnClickListener(v -> showDummyTestDialog());

        clearTransientRomState(false);

        usbManager = (UsbManager) getSystemService(Context.USB_SERVICE);
        selectedProgrammer = getSharedPreferences(PREFS, MODE_PRIVATE).getString(KEY_PROGRAMMER, "ch341a_spi");
        // Si el programador es dummy, habilitar botones sin necesidad de USB
        if (isDummyProgrammer()) {
            btnProbe.setEnabled(true);
            btnVerify.setEnabled(true);
            btnRead.setEnabled(true);
            btnWrite.setEnabled(true);
            btnEraseChip.setEnabled(true);
        }
        setupLogCopySupport();

        mostrarPublicidad = new MostrarPublicidad(this);
        adContainer = findViewById(R.id.adContainer);
        if (adContainer != null) {
            adContainer.addView(mostrarPublicidad.getBanner());
            mostrarPublicidad.cargarBanner();
            mostrarPublicidad.cargarInterstial();
        }

        log("--- Aplicación Iniciada ---");

        // Lógica de inicio: primera instalación vs. aperturas posteriores
        int currentVersion = getVersionCode();
        int lastVersion = getSharedPreferences(PREFS, MODE_PRIVATE).getInt(KEY_LAST_VERSION, -1);
        boolean assetsReady = AssetHelper.areAssetsExtracted(getApplicationContext());
        boolean skipLoading = assetsReady && (currentVersion == lastVersion);

        if (skipLoading) {
            // --- APERTURA POSTERIOR: UI inmediata, sin barra de progreso ---
            layoutMainUI.setVisibility(View.VISIBLE);
            layoutLoading.setVisibility(View.GONE);
            log("Sistema flashrom y assets listos.");

            // Verificación silenciosa en background (solo repara enlaces/pci.ids si faltan)
            executor.execute(() -> {
                // Limpieza de directorios erróneos
                File buggedDir = new File(getFilesDir(), "usr/usr");
                if (buggedDir.exists()) {
                    deleteRecursively(buggedDir);
                }
                AssetHelper.ensureRuntimeReady(getApplicationContext());
            });
        } else {
            // --- PRIMERA INSTALACIÓN / ACTUALIZACIÓN / DATOS BORRADOS ---
            layoutMainUI.setVisibility(View.GONE);
            layoutLoading.setVisibility(View.VISIBLE);

            executor.execute(() -> {
                // Limpieza de directorios erróneos
                File buggedDir = new File(getFilesDir(), "usr/usr");
                if (buggedDir.exists()) {
                    deleteRecursively(buggedDir);
                    Log.d("MainActivity", "Carpeta residual usr/usr eliminada automáticamente.");
                }

                boolean wasExtracted = AssetHelper.areAssetsExtracted(getApplicationContext());
                if (!wasExtracted) {
                    runOnUiThread(() -> tvLoadingText.setText("Extrayendo binarios nativos por primera vez..."));
                } else {
                    runOnUiThread(() -> tvLoadingText.setText("Verificando dependencias locales..."));
                }

                boolean runtimeReady = AssetHelper.ensureRuntimeReady(getApplicationContext());

                runOnUiThread(() -> {
                    layoutLoading.setVisibility(View.GONE);
                    layoutMainUI.setVisibility(View.VISIBLE);

                    boolean isUpdate = (lastVersion != -1 && currentVersion != lastVersion);
                    if (!wasExtracted) {
                        log("--- Nueva instalación detectada ---");
                        log("Preparando recursos locales en el almacenamiento interno...");
                    } else if (isUpdate) {
                        log("--- Actualización detectada (v" + lastVersion + " -> v" + currentVersion + ") ---");
                        log("Verificando recursos locales para la nueva versión...");
                    }

                    // Mostrar info completa solo en primera instalación/actualización
                    logRuntimeInfo();
                    logDependencyChecklist();

                    if (!runtimeReady) {
                        log("[WARN] No se pudieron preparar todas las dependencias locales.");
                    } else {
                        if (!wasExtracted) {
                            log("Assets copiados correctamente al almacenamiento interno.");
                        } else {
                            log("Recursos verificados y actualizados correctamente.");
                        }
                        // Guardar versión actual tras éxito
                        getSharedPreferences(PREFS, MODE_PRIVATE).edit().putInt(KEY_LAST_VERSION, currentVersion)
                                .apply();
                    }

                    // Forzar actualización inmediata del log tras la carga
                    logHandler.removeCallbacks(logUpdater);
                    logUpdater.run();
                });
            });
        }

        // Setup Broadcast Receiver
        IntentFilter filter = new IntentFilter(ACTION_USB_PERMISSION);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(usbReceiver, filter, Context.RECEIVER_NOT_EXPORTED);
        } else {
            registerReceiver(usbReceiver, filter);
        }

        // Listener setup para todos los botones
        btnConnect.setOnClickListener(v -> searchAndRequestProgrammer());

        btnProbe.setOnClickListener(v -> ensureProgrammerThenRun(() -> {
            if (isDummyProgrammer()) {
                // Probe con dummy: si hay bios.bin del usuario, usarlo;
                // si no, generar archivo de prueba automáticamente.
                File userBios = new File(getFilesDir(), "bios.bin");
                if (userBios.exists() && userBios.length() > 0) {
                    long size = userBios.length();
                    executeCustomFlashromCommand(
                            "-p dummy:emulate=VARIABLE_SIZE,size=" + size + ",image=bios.bin");
                } else {
                    log("No hay bios.bin cargado. Generando archivo de prueba para identificación...");
                    ensureDummyTestFile(16777216);
                    executeCustomFlashromCommand(
                            "-p dummy:emulate=VARIABLE_SIZE,size=16777216,image=bios_test.bin");
                }
            } else {
                executeFlashromTask("-p", selectedProgrammer);
            }
        }));
        btnVerify.setOnClickListener(
                v -> ensureProgrammerThenRun(() -> {
                    if (isDummyProgrammer()) {
                        File userBios = new File(getFilesDir(), "bios.bin");
                        if (!userBios.exists() || userBios.length() == 0) {
                            log("Error: No hay bios.bin cargado. Usa 'Cargar ROM' primero.");
                            return;
                        }
                        long size = userBios.length();
                        executeCustomFlashromCommand(
                                "-p dummy:emulate=VARIABLE_SIZE,size=" + size + ",image=bios.bin -v bios.bin");
                    } else {
                        executeFlashromTask("-p", selectedProgrammer, "-v", "bios.bin");
                    }
                }));
        btnRead.setOnClickListener(
                v -> ensureProgrammerThenRun(() -> {
                    if (isDummyProgrammer()) {
                        File userBios = new File(getFilesDir(), "bios.bin");
                        if (!userBios.exists() || userBios.length() == 0) {
                            log("Error: No hay bios.bin cargado. Usa 'Cargar ROM' primero.");
                            return;
                        }
                        long size = userBios.length();
                        executeCustomFlashromCommand(
                                "-p dummy:emulate=VARIABLE_SIZE,size=" + size + ",image=bios.bin -r bios.bin");
                    } else {
                        executeFlashromTask("-p", selectedProgrammer, "-r", "bios.bin");
                    }
                }));
        btnWrite.setOnClickListener(
                v -> ensureProgrammerThenRun(() -> {
                    if (isDummyProgrammer()) {
                        File userBios = new File(getFilesDir(), "bios.bin");
                        if (!userBios.exists() || userBios.length() == 0) {
                            log("Error: No hay bios.bin cargado. Usa 'Cargar ROM' primero.");
                            return;
                        }
                        long size = userBios.length();
                        executeCustomFlashromCommand(
                                "-p dummy:emulate=VARIABLE_SIZE,size=" + size + ",image=bios.bin -w bios.bin");
                    } else {
                        executeFlashromTask("-p", selectedProgrammer, "-w", "bios.bin");
                    }
                }));

        btnExport.setOnClickListener(v -> {
            if (!hasReadData) {
                log("Error: No hay datos leídos del chip aún.");
                log("Usa 'Leer Backup' primero para leer el contenido del chip.");
                log("(El botón 'Guardar ROM' exporta datos LEÍDOS, no archivos importados.)");
                return;
            }
            File sourceFile = new File(getFilesDir(), lastReadFile);
            if (!sourceFile.exists()) {
                log("Error: El archivo '" + lastReadFile + "' no existe. Lee el chip primero.");
                return;
            }

            // Nombre de exportación basado en el archivo leído
            String exportName = lastReadFile;
            if ("bios.bin".equals(exportName)) {
                exportName = "bios_backup.bin";
            }

            Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
            intent.addCategory(Intent.CATEGORY_OPENABLE);
            intent.setType("application/octet-stream");
            intent.putExtra(Intent.EXTRA_TITLE, exportName);
            fileSaveLauncher.launch(intent);
        });

        btnImport.setOnClickListener(v -> {
            Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
            intent.addCategory(Intent.CATEGORY_OPENABLE);
            intent.setType("*/*");
            // flashrom acepta cualquier formato binario (.bin, .rom, .img, .hex, etc.)
            // No restringir por MIME type — igual que en PC

            String savedDir = getSharedPreferences(PREFS, MODE_PRIVATE).getString("working_dir", null);
            if (savedDir != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                intent.putExtra(android.provider.DocumentsContract.EXTRA_INITIAL_URI, Uri.parse(savedDir));
            }

            fileOpenLauncher.launch(intent);
        });

        btnQuickClear.setOnClickListener(v -> clearTransientRomState(true));

        btnEraseChip.setOnClickListener(v -> ensureProgrammerThenRun(() -> {
            new android.app.AlertDialog.Builder(this)
                    .setTitle("⚠ Confirmar Borrado de Chip")
                    .setMessage("Esta operación BORRARÁ completamente el contenido del chip flash.\n\n"
                            + "Todos los datos del chip se perderán (se llenarán con 0xFF).\n\n"
                            + "¿Estás seguro de continuar?")
                    .setPositiveButton("Sí, Borrar", (dialog, which) -> {
                        if (isDummyProgrammer()) {
                            File userBios = new File(getFilesDir(), "bios.bin");
                            if (userBios.exists() && userBios.length() > 0) {
                                long size = userBios.length();
                                executeCustomFlashromCommand(
                                        "-p dummy:emulate=VARIABLE_SIZE,size=" + size + ",image=bios.bin --erase");
                            } else {
                                log("No hay bios.bin cargado. Usando archivo de prueba para borrado emulado...");
                                ensureDummyTestFile(16777216);
                                executeCustomFlashromCommand(
                                        "-p dummy:emulate=VARIABLE_SIZE,size=16777216,image=bios_test.bin --erase");
                            }
                        } else {
                            executeFlashromTask("-p", selectedProgrammer, "--erase");
                        }
                    })
                    .setNegativeButton("Cancelar", null)
                    .show();
        }));

        btnRunCustomCommand.setOnClickListener(v -> {
            String rawCommand = etCustomCommand.getText() == null ? "" : etCustomCommand.getText().toString().trim();
            if (rawCommand.isEmpty()) {
                log("Escribe un comando para ejecutar. Ej: --version o -p ch341a_spi -r bios.bin");
                return;
            }
            // Validación básica de comandos
            if (rawCommand.contains("-p") && !rawCommand.contains("dummy") && currentFd < 0) {
                log("[AVISO] El comando usa '-p' pero no hay USB conectado.");
                log("Si usas 'dummy', está bien. Para hardware real, conecta el programador primero.");
            }
            executeCustomFlashromCommand(rawCommand);
        });

        btnClearLogs.setOnClickListener(v -> {
            tvLog.setText("--- Log ---");
            log("Terminal reiniciada.");
        });
    }

    private void exportRomFileToUri(Uri uri) {
        File sourceFile = new File(getFilesDir(), lastReadFile);
        try (InputStream in = new java.io.FileInputStream(sourceFile);
                OutputStream out = getContentResolver().openOutputStream(uri)) {

            if (out == null)
                throw new Exception("No se pudo obtener acceso de escritura al archivo destino.");

            byte[] buffer = new byte[8192];
            int read;
            while ((read = in.read(buffer)) != -1) {
                out.write(buffer, 0, read);
            }
            log("Éxito: '" + lastReadFile + "' respaldado correctamente en la carpeta seleccionada.");
        } catch (Exception e) {
            log("Error guardando el archivo: " + e.getMessage());
        }
    }

    private void importRomFile(Uri uri) {
        try (InputStream in = getContentResolver().openInputStream(uri)) {
            if (in == null) {
                throw new IllegalStateException("No se pudo abrir el archivo seleccionado para lectura.");
            }

            // Detectar metadata y tamano sin cargar a RAM
            String fileName = "archivo";
            long fileSize = -1;
            try {
                android.database.Cursor cursor = getContentResolver().query(uri, null, null, null, null);
                if (cursor != null && cursor.moveToFirst()) {
                    int idx = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME);
                    if (idx >= 0) fileName = cursor.getString(idx);
                    int sizeIdx = cursor.getColumnIndex(android.provider.OpenableColumns.SIZE);
                    if (sizeIdx >= 0) fileSize = cursor.getLong(sizeIdx);
                    cursor.close();
                }
            } catch (Exception ignored) {
            }

            long maxSize = 128L * 1024 * 1024; // 128 MB
            if (fileSize > maxSize) {
                log("Error: El archivo es demasiado grande (" + (fileSize / 1024 / 1024)
                        + " MB). Máximo soportado: 128 MB.");
                return;
            }

            boolean isIntelHex = fileName.toLowerCase().endsWith(".hex");
            File outFile = new File(getFilesDir(), "bios.bin");
            clearTransientRomState(false);
            long totalWritten = 0;

            try (OutputStream out = new FileOutputStream(outFile)) {
                if (isIntelHex) {
                    // HEX suele ser pequeno, y la funcion parseIntelHex requiere el arreglo completo en memoria.
                    ByteArrayOutputStream baos = new ByteArrayOutputStream();
                    byte[] buffer = new byte[8192];
                    int read;
                    while ((read = in.read(buffer)) != -1) {
                        baos.write(buffer, 0, read);
                    }
                    byte[] data = baos.toByteArray();
                    if (data.length > 0 && data[0] == ':') {
                        byte[] parsed = parseIntelHex(data);
                        if (parsed.length == 0) {
                            log("Error: El Intel HEX no contiene datos útiles.");
                            return;
                        }
                        out.write(parsed);
                        totalWritten = parsed.length;
                    } else {
                        // Falsa alarma, era binario
                        out.write(data);
                        totalWritten = data.length;
                        isIntelHex = false;
                    }
                } else {
                    // Si es binario crudo, transmitir directamente en bloques para evitar OOM (Fix Issue #72)
                    byte[] buffer = new byte[65536]; // 64 KB chunk
                    int read;
                    boolean firstChunk = true;
                    while ((read = in.read(buffer)) != -1) {
                        if (firstChunk) {
                            boolean looksLikeText = true;
                            int checkLen = Math.min(read, 512);
                            for (int i = 0; i < checkLen; i++) {
                                int b = buffer[i] & 0xFF;
                                if (b < 0x09 || (b > 0x0D && b < 0x20 && b != 0x1A)) {
                                    looksLikeText = false;
                                    break;
                                }
                            }
                            if (looksLikeText && fileSize < 1024) {
                                log("[AVISO] El archivo '" + fileName + "' parece ser texto plano.");
                            }
                            firstChunk = false;
                        }
                        out.write(buffer, 0, read);
                        totalWritten += read;
                    }
                }
            }

            if (totalWritten == 0) {
                log("Error: El archivo seleccionado está vacío. No es un binario válido.");
                return;
            }

            String sizeStr;
            if (totalWritten >= 1024 * 1024) {
                sizeStr = String.format(java.util.Locale.US, "%.2f MB", totalWritten / (1024.0 * 1024.0));
            } else {
                sizeStr = String.format(java.util.Locale.US, "%.1f KB", totalWritten / 1024.0);
            }

            log("ROM importada: '" + fileName + "' (" + sizeStr + ", " + (isIntelHex ? "Intel HEX" : "binario crudo")
                    + ")");
            if (isIntelHex) {
                log("Conversión Intel HEX → binario aplicada correctamente.");
            }
            log("Archivo guardado como 'bios.bin' — listo para Flashear o Verificar.");

            SharedPreferences.Editor editor = getSharedPreferences(PREFS, MODE_PRIVATE).edit();
            editor.putString(KEY_BIOS_SOURCE, "Importado: " + fileName + " (" + sizeStr + ")");
            editor.putString(KEY_LAST_READ_FILE, "bios.bin");
            editor.apply();
        } catch (Exception e) {
            log("Error copiando ROM desde almacenamiento: " + e.getMessage());
        }
    }

    private byte[] parseIntelHex(byte[] source) {
        String content = new String(source, java.nio.charset.StandardCharsets.US_ASCII);
        String[] lines = content.replace("\r", "").split("\n");
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        int upperAddress = 0;
        int expectedAddress = -1;

        for (String rawLine : lines) {
            String line = rawLine.trim();
            if (line.isEmpty()) {
                continue;
            }
            if (!line.startsWith(":")) {
                throw new IllegalArgumentException("Línea Intel HEX inválida (sin ':'): " + line);
            }
            if (line.length() < 11 || (line.length() % 2) == 0) {
                throw new IllegalArgumentException("Línea Intel HEX malformada: " + line);
            }

            int byteCount = parseHexByte(line, 1);
            int address = parseHexWord(line, 3);
            int recordType = parseHexByte(line, 7);
            int expectedLen = 11 + (byteCount * 2);
            if (line.length() != expectedLen) {
                throw new IllegalArgumentException("Longitud Intel HEX inconsistente: " + line);
            }

            int checksum = 0;
            for (int i = 1; i < line.length(); i += 2) {
                checksum = (checksum + parseHexByte(line, i)) & 0xFF;
            }
            if (checksum != 0) {
                throw new IllegalArgumentException("Checksum inválido en Intel HEX: " + line);
            }

            if (recordType == 0x00) {
                int absolute = upperAddress + address;
                if (expectedAddress < 0) {
                    expectedAddress = absolute;
                }
                if (absolute > expectedAddress) {
                    output.write(new byte[absolute - expectedAddress], 0, absolute - expectedAddress);
                    expectedAddress = absolute;
                }
                if (absolute < expectedAddress) {
                    throw new IllegalArgumentException("Intel HEX desordenado: dirección decreciente no soportada.");
                }
                int dataStart = 9;
                for (int i = 0; i < byteCount; i++) {
                    output.write(parseHexByte(line, dataStart + (i * 2)));
                }
                expectedAddress += byteCount;
            } else if (recordType == 0x01) {
                break; // EOF
            } else if (recordType == 0x04) {
                if (byteCount != 2) {
                    throw new IllegalArgumentException("Intel HEX tipo 04 inválido: " + line);
                }
                upperAddress = parseHexWord(line, 9) << 16;
                expectedAddress = -1;
            } else if (recordType == 0x02) {
                if (byteCount != 2) {
                    throw new IllegalArgumentException("Intel HEX tipo 02 inválido: " + line);
                }
                upperAddress = parseHexWord(line, 9) << 4;
                expectedAddress = -1;
            }
        }
        return output.toByteArray();
    }

    private int parseHexByte(String line, int idx) {
        return Integer.parseInt(line.substring(idx, idx + 2), 16);
    }

    private int parseHexWord(String line, int idx) {
        return Integer.parseInt(line.substring(idx, idx + 4), 16);
    }

    private void clearTransientRomState(boolean notifyUser) {
        String[] transientFiles = { "bios.bin", "read_test.bin", "bios_test.bin" };
        for (String fileName : transientFiles) {
            try {
                File f = new File(getFilesDir(), fileName);
                if (f.exists() && !f.delete()) {
                    Log.w(TAG, "No se pudo eliminar archivo temporal: " + fileName);
                }
            } catch (Exception e) {
                Log.w(TAG, "Error eliminando archivo temporal " + fileName, e);
            }
        }

        hasReadData = false;
        lastReadFile = "bios.bin";
        getSharedPreferences(PREFS, MODE_PRIVATE).edit()
                .remove(KEY_BIOS_SOURCE)
                .remove(KEY_LAST_READ_FILE)
                .apply();

        if (notifyUser) {
            log("Datos ROM temporales eliminados (bios.bin / read_test.bin / bios_test.bin).");
        }
    }

    private void searchAndRequestProgrammer() {
        Map<String, UsbDevice> devices = usbManager.getDeviceList();
        if (devices == null || devices.isEmpty()) {
            log("No se detectó ningún dispositivo USB conectado.");
            return;
        }

        List<UsbDevice> candidates = new ArrayList<>(devices.values());

        // Auto-selección lógica
        for (UsbDevice device : candidates) {
            String key = String.format(Locale.US, "%04x:%04x", device.getVendorId(), device.getProductId());
            if (USB_AUTO_MAP.containsKey(key)) {
                String autoProg = USB_AUTO_MAP.get(key);
                selectedProgrammer = autoProg;
                getSharedPreferences(PREFS, MODE_PRIVATE).edit().putString(KEY_PROGRAMMER, autoProg).apply();
                log("Detección automática: Dispositivo " + key + " reconocido como " + autoProg);
                requestUsbPermission(device);
                return;
            }
        }

        Collections.sort(candidates, new Comparator<UsbDevice>() {
            @Override
            public int compare(UsbDevice a, UsbDevice b) {
                int vid = Integer.compare(a.getVendorId(), b.getVendorId());
                if (vid != 0)
                    return vid;
                int pid = Integer.compare(a.getProductId(), b.getProductId());
                if (pid != 0)
                    return pid;
                return Integer.compare(a.getDeviceId(), b.getDeviceId());
            }
        });

        // Ningún dispositivo fue auto-detectado — avisar pero no bloquear
        log("[AVISO] Ningún dispositivo USB reconocido automáticamente como programador flashrom.");
        log("Dispositivos conocidos: CH341A, FT2232, Bus Pirate, Dediprog, ST-LINK, etc.");
        log("Puedes intentar conectarte manualmente — flashrom reportará si es compatible.");

        if (candidates.size() == 1) {
            requestUsbPermission(candidates.get(0));
            return;
        }

        CharSequence[] labels = new CharSequence[candidates.size()];
        for (int i = 0; i < candidates.size(); i++) {
            labels[i] = formatUsbDeviceLabel(candidates.get(i));
        }

        new android.app.AlertDialog.Builder(this)
                .setTitle("Selecciona dispositivo USB")
                .setItems(labels, (dialog, which) -> requestUsbPermission(candidates.get(which)))
                .setNegativeButton("Cancelar", null)
                .show();
    }

    private void requestUsbPermission(UsbDevice device) {
        String deviceName = device.getProductName() == null ? "Dispositivo USB" : device.getProductName();
        log("Dispositivo detectado: " + deviceName + " | Solicitando enlace...");
        log("VID:PID detectado => "
                + String.format(Locale.US, "%04x:%04x", device.getVendorId(), device.getProductId()));

        if (usbManager.hasPermission(device)) {
            connectToDevice(device);
        } else {
            int flags = PendingIntent.FLAG_UPDATE_CURRENT;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                flags |= PendingIntent.FLAG_MUTABLE;
            }
            Intent intent = new Intent(ACTION_USB_PERMISSION);
            intent.setPackage(getPackageName());
            PendingIntent permissionIntent = PendingIntent.getBroadcast(this, 0, intent, flags);
            usbManager.requestPermission(device, permissionIntent);
        }
    }

    private String formatUsbDeviceLabel(UsbDevice device) {
        String productName = device.getProductName();
        if (productName == null || productName.trim().isEmpty()) {
            productName = "Dispositivo USB";
        }
        String manufacturer = device.getManufacturerName();
        if (manufacturer == null || manufacturer.trim().isEmpty()) {
            manufacturer = "Fabricante desconocido";
        }
        return productName + " (" + manufacturer + ")\nVID:PID "
                + String.format(Locale.US, "%04x:%04x", device.getVendorId(), device.getProductId());
    }

    private void connectToDevice(UsbDevice device) {
        currentConnection = usbManager.openDevice(device);
        if (currentConnection == null) {
            log(device.getProductName() + " falló en enlazarse a la app (openDevice == null)");
            return;
        }

        currentFd = currentConnection.getFileDescriptor();
        String deviceName = device.getProductName() == null ? "Dispositivo USB" : device.getProductName();
        String vidPid = String.format(Locale.US, "%04x:%04x", device.getVendorId(), device.getProductId());

        tvStatus.setText("Status: " + deviceName + " Conectado");
        log("¡Permiso otorgado! Token interno de USB: " + currentFd);
        log("Conectado a USB VID:PID " + vidPid + " | DeviceId: " + device.getDeviceId());

        // Verificar si es un dispositivo reconocido
        boolean isRecognized = USB_AUTO_MAP.containsKey(vidPid);
        if (isRecognized) {
            String autoProg = USB_AUTO_MAP.get(vidPid);
            log("[OK] Dispositivo reconocido: " + deviceName + " → programador '" + autoProg + "'");
        } else {
            log("════════════════════════════════════════");
            log("[AVISO] Dispositivo NO reconocido como programador flashrom.");
            log("VID:PID " + vidPid + " (" + deviceName + ") no está en la lista de dispositivos compatibles.");
            log("Esto NO significa que no funcione — puedes intentar con los botones o la consola.");
            log("Si falla, cambia el programador en 'Ajustes de Programador' o reporta el VID:PID.");
            log("════════════════════════════════════════");
        }

        btnProbe.setEnabled(true);
        btnVerify.setEnabled(true);
        btnRead.setEnabled(true);
        btnWrite.setEnabled(true);
        btnEraseChip.setEnabled(true);

        if (selectedProgrammer == null || selectedProgrammer.trim().isEmpty()) {
            selectedProgrammer = "ch341a_spi";
            log("Programador no configurado — usando 'ch341a_spi' por defecto. Cámbialo en 'Ajustes de Programador' si es necesario.");
        }
        log("Programador flashrom activo: " + selectedProgrammer);

        // ── Programadores seriales (serprog, buspirate_spi, spidriver): abrir puente
        // PTY ↔ USB ──
        if (needsPtyBridge(selectedProgrammer)) {
            // Cerrar puente anterior si hubiera uno activo
            if (ptyBridge != null) {
                ptyBridge.close();
                ptyBridge = null;
            }
            log("Programador serial detectado (" + selectedProgrammer + ") — iniciando puente PTY...");
            PtyBridge bridge = new PtyBridge();
            // Callback para que los logs del bridge aparezcan en el UI
            bridge.setLogCallback(msg -> log(msg));
            // Pasamos la conexión ya abierta; PtyBridge también abre UsbSerialPort con ella
            if (bridge.open(device, usbManager, currentConnection, SERPROG_BAUD)) {
                ptyBridge = bridge;
                log("PtyBridge activo: flashrom usará " + ptyBridge.getSlavePath()
                        + " a " + SERPROG_BAUD + " bps");
            } else {
                log("[WARN] PtyBridge no pudo iniciarse. ¿devpts disponible? Revisa el log nativo.");
                log("Los comandos con programador serial probablemente fallen sin PTY.");
            }
        }
    }

    private boolean isDummyProgrammer() {
        return selectedProgrammer != null && selectedProgrammer.startsWith("dummy");
    }

    /**
     * ¿Este programador necesita puente PTY↔USB (comunicación serial)?
     * - serprog: Arduino con firmware serprog
     * - buspirate_spi: Bus Pirate vía FTDI serial
     * - spidriver: SPIDriver vía USB-CDC serial
     */
    private static boolean needsPtyBridge(String prog) {
        return "serprog".equals(prog)
                || "buspirate_spi".equals(prog)
                || "spidriver".equals(prog);
    }

    /**
     * Construye el parámetro -p correcto para un programador serial con PTY.
     * Cada programador tiene un formato ligeramente diferente:
     * - serprog:dev=/dev/pts/N:115200 (baud inline)
     * - buspirate_spi:dev=/dev/pts/N (baud configurable aparte)
     * - spidriver:dev=/dev/pts/N (baud fijo interno)
     */
    private String buildPtyProgrammerParam(String programmer) {
        if (ptyBridge == null || !ptyBridge.isOpen())
            return programmer;
        String devPath = ptyBridge.getSlavePath();
        if ("serprog".equals(programmer)) {
            return "serprog:dev=" + devPath + ":" + ptyBridge.getBaudRate();
        } else {
            // buspirate_spi, spidriver: dev=PATH sin baud inline
            return programmer + ":dev=" + devPath;
        }
    }

    private void ensureProgrammerThenRun(Runnable action) {
        if (selectedProgrammer == null || selectedProgrammer.trim().isEmpty()) {
            log("Error: No se ha seleccionado un programador. Por favor, configúralo en los ajustes.");
            return;
        }
        // Dummy no requiere USB conectado
        if (isDummyProgrammer()) {
            action.run();
            return;
        }
        // Programador real: verificar que hay conexión USB
        if (currentFd < 0) {
            log("Error: No hay dispositivo USB conectado. Conecta tu programador primero.");
            return;
        }
        // ── Programadores seriales (serprog, buspirate_spi, spidriver): iniciar PTY ──
        if (needsPtyBridge(selectedProgrammer)) {
            if (ptyBridge == null || !ptyBridge.isOpen()) {
                log("[WARN] PtyBridge no está listo. Se intentará ejecutar flashrom sin sincronización previa.");
                action.run();
                return;
            }

            final boolean isSerprog = "serprog".equals(selectedProgrammer);
            if (isSerprog) {
                log("Sincronizando con Arduino... esperando beacon de arranque.");
            } else {
                log("Preparando puente serial para " + selectedProgrammer + "...");
            }

            final com.diamon.curso.core.PtyBridge currentBridge = ptyBridge;
            executor.execute(() -> {
                if (currentBridge == null) {
                    runOnUiThread(() -> log("[ERROR] Conexión PTY perdida."));
                    return;
                }
                // Serprog espera beacon 0xAA 0x55 del firmware Arduino;
                // los demás (buspirate, spidriver) solo activan DTR/RTS + purge.
                boolean ready = isSerprog
                        ? currentBridge.prepareForFlashromSession(8000)
                        : currentBridge.prepareForSerialSession();
                runOnUiThread(() -> {
                    if (!ready) {
                        log("[ERROR] No se pudo preparar sesión serial — abortando.");
                        currentBridge.close();
                        if (ptyBridge == currentBridge) ptyBridge = null;
                        return;
                    }
                    currentBridge.purge();
                    if (!currentBridge.isForwardingActive()) {
                        currentBridge.startForwarding();
                        log("Hilos de forwarding activos.");
                    }
                    log("Puente PTY↔USB listo — lanzando flashrom.");
                    action.run();
                });
            });
        } else {
            // CH341A y otros programadores con parche libusb son instantáneos
            action.run();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (mostrarPublicidad != null) {
            mostrarPublicidad.resumenBanner();
        }
        // Recargar preferencias al volver si hubo cambios (ej: se cambio el chip o se
        // limpiaron terminales)
        SharedPreferences prefs = getSharedPreferences(PREFS, MODE_PRIVATE);
        String updatedProgrammer = prefs.getString(KEY_PROGRAMMER, "ch341a_spi");
        if (!updatedProgrammer.equals(selectedProgrammer)) {
            selectedProgrammer = updatedProgrammer;
            log("Programador modificado vía Ajustes: " + selectedProgrammer);
            // Habilitar botones si es dummy (no requiere USB)
            if (isDummyProgrammer()) {
                btnProbe.setEnabled(true);
                btnVerify.setEnabled(true);
                btnRead.setEnabled(true);
                btnWrite.setEnabled(true);
                btnEraseChip.setEnabled(true);
                log("Modo Dummy activo: Los botones están habilitados sin necesidad de USB.");
            }
        }
    }

    @Override
    protected void onPause() {
        if (mostrarPublicidad != null) {
            mostrarPublicidad.pausarBanner();
        }
        super.onPause();
    }

    private void abortFlashromProcess() {
        if (currentFlashromProcess != null) {
            log("[ABORTAR] Enviando señal de terminación a flashrom...");
            currentFlashromProcess.destroy();
            if (ptyBridge != null && ptyBridge.isOpen()) {
                ptyBridge.purge(); 
            }
        }
    }

    private void executeCustomFlashromCommand(String rawCommand) {
        String[] args = rawCommand.split("\\s+");
        for (int i = 0; i < args.length; i++) {
            if ("-r".equals(args[i])) {
                clearTransientRomState(false);
                break;
            }
        }

        // ── Programadores seriales: si el comando usa "-p
        // serprog/buspirate_spi/spidriver"
        // activar puente PTY↔USB ──
        String detectedSerialProg = null;
        for (int i = 0; i < args.length; i++) {
            if ("-p".equals(args[i]) && i + 1 < args.length) {
                String pVal = args[i + 1];
                // Detectar tanto "-p serprog" como "-p serprog:dev=..."
                if (pVal.startsWith("serprog") || pVal.startsWith("buspirate_spi")
                        || pVal.startsWith("spidriver")) {
                    // Extraer el nombre base del programador (antes de ":")
                    int colon = pVal.indexOf(':');
                    detectedSerialProg = (colon > 0) ? pVal.substring(0, colon) : pVal;
                }
                break;
            }
        }

        if (detectedSerialProg != null && ptyBridge != null && ptyBridge.isOpen()) {
            // Asegurar que los hilos de forwarding estén corriendo
            if (!ptyBridge.isForwardingActive()) {
                log("Iniciando puente PTY↔USB para " + detectedSerialProg + "...");
                ptyBridge.purge();
                ptyBridge.startForwarding();
                log("Hilos de forwarding activos.");
            }

            // Solo si el usuario escribió "-p <prog>" a secas (sin dev=),
            // completar automáticamente con la ruta del PTY slave.
            // Si ya escribió "-p serprog:dev=/dev/pts/17:115200", se respeta tal cual.
            final String bareProgName = detectedSerialProg;
            List<String> argList = new ArrayList<>();
            for (int i = 0; i < args.length; i++) {
                if ("-p".equals(args[i]) && i + 1 < args.length && bareProgName.equals(args[i + 1])) {
                    argList.add("-p");
                    argList.add(buildPtyProgrammerParam(bareProgName));
                    i++; // saltar el nombre bare del programador
                } else {
                    argList.add(args[i]);
                }
            }
            args = argList.toArray(new String[0]);
        }
        File preferredFlashromBin = new File(getFilesDir(), "usr/sbin/flashrom");
        if (!preferredFlashromBin.exists()) {
            log("[WARN] flashrom en files/usr/sbin no encontrado; usando fallback jniLibs.");
            preferredFlashromBin = new File(getApplicationInfo().nativeLibraryDir, "libflashrom_bin.so");
        }
        if (!preferredFlashromBin.exists()) {
            log("Fallo crítico: Binario 'flashrom' no existe. (" + preferredFlashromBin.getAbsolutePath() + ")");
            return;
        }
        log("Comando manual solicitado: flashrom " + String.join(" ", args));

        if (currentFd < 0) {
            log("Ejecutando sin USB conectado: útil para comandos como --version, -L o --help.");
        }
        final File flashromBin = preferredFlashromBin;
        final String[] finalArgs = args;
        executor.execute(() -> {
            runFlashromProcess(flashromBin, finalArgs);
        });
    }

    private void executeFlashromTask(String... args) {
        // Dummy no necesita FD de USB
        if (currentFd == -1 && !isDummyProgrammer()) {
            log("Error lógico: El FD de USB se perdió.");
            return;
        }

        for (int i = 0; i < args.length; i++) {
            if ("-r".equals(args[i])) {
                clearTransientRomState(false);
                break;
            }
        }

        // ── Programadores seriales: sustituir "-p <prog>" por "-p
        // <prog>:dev=/dev/pts/N[:baud]" ──
        String[] resolvedArgs = args;
        if (ptyBridge != null && ptyBridge.isOpen()) {
            List<String> argList = new ArrayList<>();
            for (int i = 0; i < args.length; i++) {
                if ("-p".equals(args[i]) && i + 1 < args.length && needsPtyBridge(args[i + 1])) {
                    argList.add("-p");
                    argList.add(buildPtyProgrammerParam(args[i + 1]));
                    i++; // saltar el nombre bare del programador
                } else {
                    argList.add(args[i]);
                }
            }
            resolvedArgs = argList.toArray(new String[0]);
        }

        File preferredFlashromBin = new File(getFilesDir(), "usr/sbin/flashrom");
        if (!preferredFlashromBin.exists()) {
            // Fallback directo a jniLibs por si la creación de enlaces falló.
            log("[WARN] flashrom en files/usr/sbin no encontrado; usando fallback jniLibs.");
            preferredFlashromBin = new File(getApplicationInfo().nativeLibraryDir, "libflashrom_bin.so");
        }
        final File flashromBin = preferredFlashromBin;
        if (!flashromBin.exists()) {
            log("Fallo crítico: Binario 'flashrom' no existe. (" + flashromBin.getAbsolutePath() + ")");
            return;
        }

        log("------------\n[INICIANDO OPERACIÓN] flashrom " + String.join(" ", resolvedArgs));
        log("Directorio de trabajo: " + getFilesDir().getAbsolutePath());
        log("Binario objetivo: " + flashromBin.getAbsolutePath());

        final String[] finalArgs = resolvedArgs;
        executor.execute(() -> {
            runFlashromProcess(flashromBin, finalArgs);
        });
    }

    private void runFlashromProcess(File flashromBin, String[] args) {
        List<String> command = new ArrayList<>();
        command.add(flashromBin.getAbsolutePath());
        for (String arg : args) {
            command.add(arg);
        }

        try {
            ProcessBuilder pb = new ProcessBuilder(command);
            pb.directory(getFilesDir()); // Corriendo desde la raid de Archivos
            pb.redirectErrorStream(true); // Redirige System.err a System.out

            // Inyectando entorno para las librerías fake_root
            Map<String, String> env = pb.environment();
            // Serprog usa PTY (/dev/pts/N) y NO debe pasar por libusb directo.
            // Si exportamos ANDROID_USB_FD aquí, el wrapper de libusb parcheado puede
            // interferir con el mismo dispositivo USB-Serial y romper la sincronización.
            if (needsPtyBridge(selectedProgrammer)) {
                // Programadores seriales usan PTY, NO deben usar libusb directo
                env.remove("ANDROID_USB_FD");
            } else if (currentFd >= 0) {
                env.put("ANDROID_USB_FD", String.valueOf(currentFd));
            } else {
                env.remove("ANDROID_USB_FD");
            }

            // Recrear las variables de entorno de PTC que incluyen la ruta PATH necesaria
            // si fuesemos a usar otras libs anidadas
            String jniLibs = getApplicationInfo().nativeLibraryDir;
            String fallbackPath = System.getenv("PATH");
            env.put("LD_LIBRARY_PATH", jniLibs + ":" + new File(getFilesDir(), "usr/lib").getAbsolutePath());
            env.put("PATH", jniLibs + (fallbackPath != null ? ":" + fallbackPath : ""));

            String fdLogValue = needsPtyBridge(selectedProgrammer)
                    ? "NO DEFINIDO (" + selectedProgrammer + " por PTY)"
                    : (currentFd >= 0 ? String.valueOf(currentFd) : "NO DEFINIDO");
            log("Entorno flashrom => ANDROID_USB_FD=" + fdLogValue);
            log("Entorno flashrom => LD_LIBRARY_PATH=" + env.get("LD_LIBRARY_PATH"));
            log("Entorno flashrom => PATH=" + env.get("PATH"));

            // Detectar tipo de operación para etiqueta de progreso
            String opLabel = "Operación";
            for (String arg : args) {
                if ("-r".equals(arg)) {
                    opLabel = "Leyendo flash";
                    break;
                }
                if ("-w".equals(arg)) {
                    opLabel = "Escribiendo flash";
                    break;
                }
                if ("-v".equals(arg)) {
                    opLabel = "Verificando flash";
                    break;
                }
                if ("--erase".equals(arg)) {
                    opLabel = "Borrando flash";
                    break;
                }
            }
            
            if (cbVerifyWrite != null && !cbVerifyWrite.isChecked() && opLabel.equals("Escribiendo flash")) {
                List<String> newArgs = new ArrayList<>(Arrays.asList(args));
                newArgs.add("-n");
                args = newArgs.toArray(new String[0]);
                log("Verificación deshabilitada: Saltando paso de verificación (-n)");
            }
            final String operationLabel = opLabel;


            Process process = pb.start();
            currentFlashromProcess = process;
            runOnUiThread(() -> {
                if (btnAbort != null) btnAbort.setVisibility(View.VISIBLE);
            });
            
            boolean multipleChipsFound = false;
            java.util.List<String> suggestedChips = new ArrayList<>();

            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    log("[native] " + line);
                    
                    if (line.contains("Multiple flash chip definitions match")) {
                        multipleChipsFound = true;
                    }
                    if (multipleChipsFound && line.startsWith("Found ") && line.contains("flash chip")) {
                        int startQuote = line.indexOf('"');
                        int endQuote = line.indexOf('"', startQuote + 1);
                        if (startQuote != -1 && endQuote != -1) {
                            suggestedChips.add(line.substring(startQuote + 1, endQuote));
                        }
                    }
                }
            }

            int exitCode = process.waitFor();
            currentFlashromProcess = null;
            runOnUiThread(() -> {
                if (btnAbort != null) btnAbort.setVisibility(View.GONE);
            });

            if (exitCode != 0 && multipleChipsFound && !suggestedChips.isEmpty()) {
                final String[] finalOldArgs = args;
                runOnUiThread(() -> {
                    String[] items = suggestedChips.toArray(new String[0]);
                    new android.app.AlertDialog.Builder(MainActivity.this)
                        .setTitle("Ambigüedad Detectada")
                        .setMessage("flashrom detectó múltiples chips posibles. Selecciona el modelo exacto:")
                        .setItems(items, (dialog, which) -> {
                            String chosenChip = items[which];
                            java.util.List<String> newArgs = new ArrayList<>(Arrays.asList(finalOldArgs));
                            newArgs.add("-c");
                            newArgs.add(chosenChip);
                            executor.execute(() -> runFlashromProcess(flashromBin, newArgs.toArray(new String[0])));
                        })
                        .setNegativeButton("Cancelar", null)
                        .show();
                });
                return;
            }

            if (exitCode == 0) {
                log("[PROCESO TERMINADO] Exit Code: " + exitCode + " (OK)\n");
                // Detectar si fue una operación de lectura exitosa y rastrear el archivo
                for (int i = 0; i < args.length; i++) {
                    if ("-r".equals(args[i]) && i + 1 < args.length) {
                        String readFile = args[i + 1];
                        hasReadData = true;
                        lastReadFile = readFile;
                        // Guardar para visor HEX y exportación
                        SharedPreferences.Editor editor = getSharedPreferences(PREFS, MODE_PRIVATE).edit();
                        editor.putString(KEY_BIOS_SOURCE, "Leído del chip (" + selectedProgrammer + ")");
                        editor.putString(KEY_LAST_READ_FILE, readFile);
                        editor.apply();
                        final String rf = readFile;
                        runOnUiThread(() -> {
                        });
                        break;
                    }
                }
            } else {
                log("[PROCESO TERMINADO] Exit Code: " + exitCode + " (ERROR)");
                // Diagnóstico del puente PTY para serprog
                if (needsPtyBridge(selectedProgrammer) && ptyBridge != null) {
                    log("[DIAG PtyBridge] " + ptyBridge.getDiagnosticReport());
                }
                log("");
            }

        } catch (Exception e) {
            Log.e(TAG, "Error fatal de SO ejecutando: flashrom", e);
            log("[CRITICAL] ProcessBuilder falló: " + e.getMessage());
            log(stackTrace(e));
        }
    }

    private void log(String message) {
        Log.i(TAG, message);
        if (Looper.myLooper() == Looper.getMainLooper()) {
            appendLogOnUi(message);
        } else {
            runOnUiThread(() -> appendLogOnUi(message));
        }
    }

    private void appendLogOnUi(String message) {
        String time = new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.getDefault())
                .format(new java.util.Date());
        synchronized (logBuffer) {
            logBuffer.append("\n[").append(time).append("] ").append(message);
            if (!isLogUpdatePending) {
                isLogUpdatePending = true;
                logHandler.postDelayed(logUpdater, 150);
            }
        }
    }

    private String stackTrace(Throwable error) {
        StringWriter sw = new StringWriter();
        PrintWriter pw = new PrintWriter(sw);
        error.printStackTrace(pw);
        return sw.toString();
    }

    private void setupLogCopySupport() {
        tvLog.setTextIsSelectable(true);
        tvLog.setLongClickable(true);
        tvLog.setOnLongClickListener(v -> {
            String logs = tvLog.getText() == null ? "" : tvLog.getText().toString();
            if (logs.trim().isEmpty()) {
                android.widget.Toast
                        .makeText(this, "No hay texto en el log para copiar.", android.widget.Toast.LENGTH_SHORT)
                        .show();
                return true;
            }
            ClipboardManager clipboard = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
            if (clipboard == null) {
                android.widget.Toast
                        .makeText(this, "No se pudo acceder al portapapeles.", android.widget.Toast.LENGTH_SHORT)
                        .show();
                return true;
            }
            clipboard.setPrimaryClip(ClipData.newPlainText("flash_eeprom_tool_logs", logs));
            android.widget.Toast.makeText(this, "Logs copiados al portapapeles.", android.widget.Toast.LENGTH_SHORT)
                    .show();
            return true;
        });
    }

    private void logRuntimeInfo() {
        File nativeDir = new File(getApplicationInfo().nativeLibraryDir);
        File usrLibDir = new File(getFilesDir(), "usr/lib");
        File usrShareDir = new File(getFilesDir(), "usr/share");
        String assetRuntimeRoot = AssetHelper.getResolvedRuntimeRoot(getApplicationContext());
        log("App iniciada. Dependencias locales en orden.");
        log("Android " + Build.VERSION.RELEASE + " (SDK " + Build.VERSION.SDK_INT + ")");
        log("Runtime root en assets: " + (assetRuntimeRoot == null ? "NO DETECTADO" : assetRuntimeRoot));
        log("Ruta nativeLibraryDir: " + nativeDir.getAbsolutePath());
        log("Ruta usr/lib runtime: " + usrLibDir.getAbsolutePath());
        log("Ruta usr/share runtime: " + usrShareDir.getAbsolutePath());
        log("libflashrom_bin.so presente: " + new File(nativeDir, "libflashrom_bin.so").exists());
        log("libcrypto.so.3 en runtime: " + new File(usrLibDir, "libcrypto.so.3").exists()
                + " (jni origen: " + findNativeName(nativeDir, "libcrypto.so.3") + ")");
        log("libssl.so.3 en runtime: " + new File(usrLibDir, "libssl.so.3").exists()
                + " (jni origen: " + findNativeName(nativeDir, "libssl.so.3") + ")");
        log("libz.so.1 en runtime: " + new File(usrLibDir, "libz.so.1").exists()
                + " (jni origen: " + findNativeName(nativeDir, "libz.so.1") + ")");
        log("pci.ids.gz en runtime: " + new File(usrShareDir, "pci.ids.gz").exists());
    }

    private void logDependencyChecklist() {
        File nativeDir = new File(getApplicationInfo().nativeLibraryDir);
        File filesDir = getFilesDir();
        File usrBin = new File(filesDir, "usr/bin");
        File usrSbin = new File(filesDir, "usr/sbin");
        File usrLib = new File(filesDir, "usr/lib");

        log("--- Verificación de Binarios (jniLibs) ---");
        String[] requiredBins = { "libflashrom_bin.so", "libsetpci.so", "libpcilmr.so", "liblspci.so",
                "libupdate-pciids.so", "libftdi_eeprom.so" };
        for (String name : requiredBins) {
            log("jniLibs bin " + name + ": " + new File(nativeDir, name).exists());
        }

        log("--- Verificación de Librerías (jniLibs) ---");
        String[] requiredLibs = { "libusb-1.0.so", "libflashrom.so", "libpci.so", "libftdi1.so", "libjaylink.so",
                "libcrypto.so.3", "libssl.so.3", "libz.so.1", "libconfuse.so", "libc++_shared.so" };
        for (String name : requiredLibs) {
            String nativeName = findNativeName(nativeDir, name);
            log("jniLibs lib " + name + ": " + !"NO".equals(nativeName) + " (" + nativeName + ")");
        }

        log("--- Verificación de Enlaces (Runtime) ---");
        log("Link flashrom: " + new File(usrSbin, "flashrom").exists());
        log("Link lspci: " + new File(usrBin, "lspci").exists());
        log("Link libcrypto: " + new File(usrLib, "libcrypto.so.3").exists());
        log("Link libssl: " + new File(usrLib, "libssl.so.3").exists());
        log("Link libusb: " + new File(usrLib, "libusb-1.0.so").exists());
    }

    private String findNativeName(File nativeDir, String runtimeSoname) {
        File exact = new File(nativeDir, runtimeSoname);
        if (exact.exists()) {
            return runtimeSoname;
        }
        int marker = runtimeSoname.indexOf(".so.");
        if (marker > 0) {
            String altName = runtimeSoname.substring(0, marker) + "_"
                    + runtimeSoname.substring(marker + 4).replace('.', '_') + ".so";
            if (new File(nativeDir, altName).exists()) {
                return altName;
            }
        }
        return "NO";
    }

    // Función de soporte para limpiar directorios defectuosos guardados por el
    // sistema
    private void deleteRecursively(File fileOrDirectory) {
        if (fileOrDirectory.isDirectory()) {
            File[] children = fileOrDirectory.listFiles();
            if (children != null) {
                for (File child : children) {
                    deleteRecursively(child);
                }
            }
        }
        fileOrDirectory.delete();
    }

    private void setButtonsEnabled(boolean enabled) {
        btnProbe.setEnabled(enabled);
        btnVerify.setEnabled(enabled);
        btnRead.setEnabled(enabled);
        btnWrite.setEnabled(enabled);
        btnEraseChip.setEnabled(enabled);
        btnConnect.setEnabled(enabled);
        btnImport.setEnabled(enabled);
        btnExport.setEnabled(enabled);
        btnRunCustomCommand.setEnabled(enabled);
        etCustomCommand.setEnabled(enabled);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.main_menu, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();

        if (id == R.id.action_hex_viewer) {
            hexViewerLauncher.launch(new Intent(this, HexViewerActivity.class));
            return true;
        } else if (id == R.id.action_programmer_settings) {
            startActivity(new Intent(this, ProgrammerSettingsActivity.class));
            return true;
        } else if (id == R.id.action_set_working_dir) {
            Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
            directoryPickerLauncher.launch(intent);
            return true;
        } else if (id == R.id.action_dummy_test) {
            showDummyTestDialog();
            return true;
        } else if (id == R.id.action_hex_diff) {
            startActivity(new Intent(this, HexDiffActivity.class));
            return true;
        } else if (id == R.id.action_pinouts) {
            showPinoutsDialog();
            return true;
        } else if (id == R.id.action_about) {
            showAboutDialog();
            return true;
        } else if (id == R.id.action_policy) {
            startActivity(new Intent(this, PolicyActivity.class));
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    private void showAboutDialog() {
        TextView aboutText = new TextView(this);
        int padding = (int) (24 * getResources().getDisplayMetrics().density);
        aboutText.setPadding(padding, padding, padding, padding / 2);
        // Permitir que el texto tome el color por defecto (adapta al Dark theme)
        aboutText.setMovementMethod(LinkMovementMethod.getInstance());
        String aboutHtml = "<h2>Flash EEPROM Tool</h2>"
                + "<p>Aplicación Android avanzada para lectura, verificación y escritura de Firmware (SPI/I2C/NAND) con <b>flashrom</b> nativo.</p>"
                + "<hr>"
                + "<b>Licencia del proyecto:</b> GPLv3.<br/><br/>"
                + "<b>Dependencias Nativas Integradas:</b><br/>"
                + "• <a href='https://github.com/libusb/libusb'>libusb</a> (LGPL-2.1+)<br/>"
                + "• <a href='https://github.com/pciutils/pciutils'>pciutils</a> (GPL-2.0+)<br/>"
                + "• <a href='https://developer.intra2net.com/git/libftdi'>libftdi</a> (LGPL-2.1+)<br/>"
                + "• <a href='https://gitlab.zapb.de/libjaylink/libjaylink'>libjaylink</a> (GPL-2.0+)<br/>"
                + "• <a href='https://github.com/flashrom/flashrom'>flashrom</a> (GPL-2.0)<br/>"
                + "• <a href='https://github.com/stefanct/ch341eeprom'>ch341eeprom</a> (GPL-3.0+)<br/>"
                + "• <a href='https://github.com/mik3y/usb-serial-for-android'>usb-serial-for-android</a> (MIT)<br/><br/>"
                + "<b>Proyectos Relacionados del Desarrollador:</b><br/>"
                + "• <a href='https://github.com/Danielk10/PIC-k150-Programing'>PIC k150 Programming</a> — Programador de PIC vía Android con protocolo P018A<br/>";

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            aboutText.setText(Html.fromHtml(aboutHtml, Html.FROM_HTML_MODE_COMPACT));
        } else {
            @SuppressWarnings("deprecation")
            CharSequence text = Html.fromHtml(aboutHtml);
            aboutText.setText(text);
        }

        new android.app.AlertDialog.Builder(this)
                .setTitle("Acerca de")
                .setView(aboutText)
                .setPositiveButton("Cerrar", null)
                .show();
    }

    private void showPinoutsDialog() {
        String[] pinoutOptions = {
                "CH341A — Header SPI",
                "Clip SOIC8 / DIP8 Flash",
                "Arduino UNO (serprog) → Flash SPI",
                "Bus Pirate → Flash SPI",
                "SPIDriver → Flash SPI",
                "Bus SPI (Serial Peripheral)",
                "Bus LPC/FWH (BIOS Chips)"
        };

        new android.app.AlertDialog.Builder(this)
                .setTitle("\uD83D\uDCCC Pinouts de Hardware")
                .setItems(pinoutOptions, (dialog, which) -> {
                    android.widget.ImageView iv = new android.widget.ImageView(this);
                    iv.setBackgroundColor(0xFF1B1E2B);
                    int pad = (int) (8 * getResources().getDisplayMetrics().density);
                    iv.setPadding(pad, pad, pad, pad);
                    String title;
                    switch (which) {
                        case 0:
                            title = "CH341A — Header SPI";
                            PinoutView.dibujarCH341A(this, iv);
                            break;
                        case 1:
                            title = "Chip Flash SOIC8 / DIP8";
                            PinoutView.dibujarSOIC8(this, iv);
                            break;
                        case 2:
                            title = "Arduino UNO (serprog) → Flash SPI";
                            PinoutView.dibujarArduinoSerprog(this, iv);
                            break;
                        case 3:
                            title = "Bus Pirate → Flash SPI";
                            PinoutView.dibujarBusPirate(this, iv);
                            break;
                        case 4:
                            title = "SPIDriver → Flash SPI";
                            PinoutView.dibujarSPIDriver(this, iv);
                            break;
                        case 5:
                            title = "Bus SPI — Serial Peripheral Interface";
                            PinoutView.dibujarSPI(this, iv);
                            break;
                        default:
                            title = "Bus LPC/FWH — Low Pin Count";
                            PinoutView.dibujarLPC(this, iv);
                            break;
                    }
                    ScrollView scroll = new ScrollView(this);
                    scroll.addView(iv);
                    new android.app.AlertDialog.Builder(this)
                            .setTitle(title)
                            .setView(scroll)
                            .setPositiveButton("Cerrar", null)
                            .show();
                })
                .setNegativeButton("Cerrar", null)
                .show();
    }

    private void showDummyTestDialog() {
        // Chips predefinidos que el programador dummy reconoce.
        // Formato: [etiqueta, nombre emulate=, tamaño bytes, chipname para -c (o null
        // si no hay ambigüedad)]
        final String[][] DUMMY_CHIPS = {
                // VARIABLE_SIZE — chip virtual sin ambigüedad, -c no necesario
                { "VARIABLE_SIZE (16 MB)", "VARIABLE_SIZE", "16777216", null },
                // MX25L6436 — flashrom detecta 6 variantes; usamos la exacta que coincide con
                // el emulate=
                { "MX25L6436 (8 MB)", "MX25L6436", "8388608", "MX25L6436E/MX25L6445E/MX25L6465E" },
                // SST25VF032B — una sola definición en flashrom v1.7
                { "SST25VF032B (4 MB)", "SST25VF032B", "4194304", null },
                // SST25VF040.REMS — flashrom detecta SST25LF040A + SST25VF040; elegimos
                // SST25VF040
                { "SST25VF040/REMS (512 KB)", "SST25VF040.REMS", "524288", "SST25VF040" },
                // M25P10.RES — una sola definición
                { "M25P10 (128 KB)", "M25P10.RES", "131072", null }
        };

        String[] testOptions = {
                "Leer chip emulado (-r)",
                "Escribir y verificar (-w)",
                "Borrar chip emulado (--erase)",
                "Identificar chip emulado (-p dummy)",
                "Seleccionar chip predefinido",
                "Info: Chips válidos para emulación"
        };

        new android.app.AlertDialog.Builder(this)
                .setTitle("Modo Prueba (Programador Dummy)")
                .setItems(testOptions, (dialog, which) -> {
                    switch (which) {
                        case 0: // Leer
                            ensureDummyTestFile(16777216);
                            executeCustomFlashromCommand(
                                    "-p dummy:emulate=VARIABLE_SIZE,size=16777216,image=bios_test.bin -r read_test.bin");
                            break;
                        case 1: // Escribir + verificar
                            ensureDummyTestFile(16777216);
                            executeCustomFlashromCommand(
                                    "-p dummy:emulate=VARIABLE_SIZE,size=16777216,image=bios_test.bin -w bios_test.bin");
                            break;
                        case 2: // Borrar chip emulado
                            ensureDummyTestFile(16777216);
                            executeCustomFlashromCommand(
                                    "-p dummy:emulate=VARIABLE_SIZE,size=16777216,image=bios_test.bin --erase");
                            break;
                        case 3: // Probe
                            ensureDummyTestFile(16777216);
                            executeCustomFlashromCommand(
                                    "-p dummy:emulate=VARIABLE_SIZE,size=16777216,image=bios_test.bin");
                            break;
                        case 4: // Seleccionar chip
                            showDummyChipSelector(DUMMY_CHIPS);
                            break;
                        case 5: // Info
                            showDummyChipInfo();
                            break;
                    }
                })
                .setNegativeButton("Cancelar", null)
                .show();
    }

    private void showDummyChipSelector(String[][] chips) {
        String[] labels = new String[chips.length];
        for (int i = 0; i < chips.length; i++) {
            labels[i] = chips[i][0];
        }

        new android.app.AlertDialog.Builder(this)
                .setTitle("Selecciona chip a emular")
                .setItems(labels, (dialog, which) -> {
                    String chipName = chips[which][1];
                    int size = Integer.parseInt(chips[which][2]);
                    // Columna [3]: nombre exacto para -c (null si no hay ambigüedad)
                    String chipFlag = chips[which].length > 3 ? chips[which][3] : null;
                    ensureDummyTestFile(size);

                    String cmd;
                    if ("VARIABLE_SIZE".equals(chipName)) {
                        cmd = "-p dummy:emulate=VARIABLE_SIZE,size=" + size + ",image=bios_test.bin -r read_test.bin";
                    } else if (chipFlag != null) {
                        // Incluir -c para evitar "Multiple flash chip definitions match"
                        cmd = "-p dummy:emulate=" + chipName + ",image=bios_test.bin -c " + chipFlag
                                + " -r read_test.bin";
                    } else {
                        cmd = "-p dummy:emulate=" + chipName + ",image=bios_test.bin -r read_test.bin";
                    }
                    log("Chip seleccionado para emulación: " + chips[which][0]
                            + (chipFlag != null ? " (-c " + chipFlag + ")" : ""));
                    executeCustomFlashromCommand(cmd);
                })
                .setNegativeButton("Cancelar", null)
                .show();
    }

    private void showDummyChipInfo() {
        String info = "El programador 'dummy' de flashrom es un simulador virtual.\n\n"
                + "NO usa hardware USB real. Ignora ANDROID_USB_FD.\n\n"
                + "Chips válidos para emulación (v1.7.0):\n\n"
                + "• VARIABLE_SIZE — Tamaño libre (usar con size=N)\n"
                + "• MX25L6436 — 8 MB\n"
                + "• SST25VF032B — 4 MB\n"
                + "• SST25VF040.REMS — 512 KB\n"
                + "• M25P10.RES — 128 KB\n\n"
                + "Ejemplo de comando correcto:\n"
                + "flashrom -p dummy:emulate=VARIABLE_SIZE,size=16777216,image=bios_test.bin -r read_test.bin\n\n"
                + "⚠ NO uses nombres de chips reales (ej: W25Q128). Solo los listados arriba son válidos.\n\n"
                + "Para hardware real, usa el programador correspondiente:\n"
                + "• CH341A → ch341a_spi\n"
                + "• FT2232H → ft2232_spi\n"
                + "• ST-Link → stlinkv3_spi";

        new android.app.AlertDialog.Builder(this)
                .setTitle("Info: Programador Dummy")
                .setMessage(info)
                .setPositiveButton("Cerrar", null)
                .show();
    }

    private void ensureDummyTestFile(int sizeBytes) {
        File testFile = new File(getFilesDir(), "bios_test.bin");
        if (testFile.exists() && testFile.length() == sizeBytes) {
            return; // Ya existe con el tamaño correcto
        }
        try (FileOutputStream fos = new FileOutputStream(testFile)) {
            byte[] buffer = new byte[8192];
            java.util.Arrays.fill(buffer, (byte) 0xFF);
            int remaining = sizeBytes;
            while (remaining > 0) {
                int toWrite = Math.min(buffer.length, remaining);
                fos.write(buffer, 0, toWrite);
                remaining -= toWrite;
            }
            log("Archivo de prueba creado: bios_test.bin (" + (sizeBytes / 1024) + " KB)");
        } catch (Exception e) {
            log("Error creando archivo de prueba: " + e.getMessage());
        }
    }

    @Override
    protected void onDestroy() {
        if (mostrarPublicidad != null) {
            mostrarPublicidad.disposeBanner();
        }
        // Cerrar puente PTY antes de destruir la actividad
        if (ptyBridge != null) {
            ptyBridge.close();
            ptyBridge = null;
        }
        super.onDestroy();
        try {
            unregisterReceiver(usbReceiver);
        } catch (Exception e) {
        }

        if (currentConnection != null) {
            currentConnection.close();
        }
        clearTransientRomState(false);
        executor.shutdownNow(); // Finalizar todos los hilos
    }

    @SuppressWarnings("deprecation")
    private int getVersionCode() {
        try {
            android.content.pm.PackageInfo pInfo = getPackageManager().getPackageInfo(getPackageName(), 0);
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
                return (int) pInfo.getLongVersionCode();
            } else {
                return pInfo.versionCode;
            }
        } catch (Exception e) {
            return -1;
        }
    }
}
