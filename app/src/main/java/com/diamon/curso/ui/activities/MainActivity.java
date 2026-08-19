package com.diamon.curso.ui.activities;

import com.diamon.curso.R;
import com.diamon.curso.ads.MostrarPublicidad;
import com.diamon.curso.core.PtyBridge;
import com.diamon.curso.core.UsbController;
import com.diamon.curso.core.FlashromExecutor;
import com.diamon.curso.core.FlashromService;
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
import android.os.Environment;
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
import com.diamon.curso.ui.views.LogScrollView;
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
import java.util.ArrayList;
import java.util.Arrays;
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
    private static final String KEY_DUMMY_CHIP_INDEX = "dummy_chip_index";




    private UsbController usbController;
    private FlashromExecutor flashromExecutor;

    private LinearLayout layoutLoading;
    private LinearLayout layoutMainUI;
    private LogScrollView scrollLog;
    private TextView tvStatus, tvLog, tvLoadingText;
    private android.widget.FrameLayout adContainer;

    private TextView tvOperationStatus;
    private Button btnConnect, btnProbe, btnVerify, btnRead, btnWrite, btnImport, btnExport;
    private Button btnRunCustomCommand, btnClearLogs, btnQuickClear, btnEraseChip, btnAbort;
    private android.widget.CheckBox cbVerifyWrite;
    private EditText etCustomCommand;

    private final List<StringBuilder> consoleLines = new ArrayList<>();
    private int currentLineIndex = -1;
    private boolean cursorAtStartOfLine = false;
    private final android.os.Handler logHandler = new android.os.Handler(android.os.Looper.getMainLooper());
    private boolean isLogUpdatePending = false;

    private boolean isScrollAtBottom() {
        if (scrollLog == null || tvLog == null) return true;
        int scrollY = scrollLog.getScrollY();
        int scrollHeight = scrollLog.getHeight();
        int contentHeight = tvLog.getHeight();
        if (contentHeight == 0) return true; // Por defecto abajo si aún no se ha dibujado
        // Tolerancia de 100 píxeles para cubrir márgenes y padding
        return (scrollY + scrollHeight) >= (contentHeight - 100);
    }

    private final Runnable logUpdater = new Runnable() {
        @Override
        public void run() {
            if (isFinishing() || isDestroyed()) {
                isLogUpdatePending = false;
                return;
            }
            String fullLogs;
            synchronized (consoleLines) {
                StringBuilder sb = new StringBuilder();
                for (int i = 0; i < consoleLines.size(); i++) {
                    if (i > 0) {
                        sb.append("\n");
                    }
                    sb.append(consoleLines.get(i).toString());
                }
                fullLogs = sb.toString();
                isLogUpdatePending = false;
            }

            // Detectar si el usuario está al final del scroll antes de actualizar
            final boolean wasAtBottom = isScrollAtBottom();
            final int scrollY = scrollLog.getScrollY();

            tvLog.setText(fullLogs);

            // Si el proceso de flashrom está activo, forzamos el scroll al fondo para seguir el progreso en tiempo real.
            // Si no está corriendo, respetamos la decisión del usuario (solo scroll al fondo si ya estaba abajo).
            boolean shouldScrollToBottom = wasAtBottom;
            if (flashromExecutor != null && flashromExecutor.isRunning()) {
                shouldScrollToBottom = true;
            }

            if (shouldScrollToBottom) {
                scrollLog.post(() -> scrollLog.fullScroll(ScrollView.FOCUS_DOWN));
            } else {
                scrollLog.post(() -> scrollLog.setScrollY(scrollY));
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

    // Chips predefinidos que el programador dummy reconoce.
    // Formato: [etiqueta, nombre emulate=, tamaño bytes, chipname para -c (o null si no hay ambigüedad)]
    private final String[][] DUMMY_CHIPS = {
            { "VARIABLE_SIZE 16 MB", "VARIABLE_SIZE", "16777216", null },
            { "W25Q128.V (16 MB)", "W25Q128FV", "16777216", "W25Q128.V" },
            { "MX25L6405D (8 MB)", "MX25L6436", "8388608", "MX25L6405D" },
            { "SST25VF032B (4 MB)", "SST25VF032B", "4194304", null },
            { "SST25VF040 (512 KB)", "SST25VF040.REMS", "524288", "SST25VF040" },
            { "M25P10.RES (128 KB)", "M25P10.RES", "131072", null },
            { "S25FL128L 16 MB", "S25FL128L", "16777216", null }
    };
    private int selectedDummyChipIndex = 0;

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
                            log(getString(R.string.str_export_dir_saved));
                        } catch (Exception e) {
                            getSharedPreferences(PREFS, MODE_PRIVATE).edit()
                                    .putString(KEY_EXPORT_URI, treeUri.toString())
                                    .apply();
                            log(getString(R.string.str_export_dir_limited));
                        }
                    }
                }
            });



    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                requestPermissions(new String[]{android.Manifest.permission.POST_NOTIFICATIONS}, 101);
            }
        }

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
        cbVerifyWrite = findViewById(R.id.cbVerifyWrite);
        etCustomCommand = findViewById(R.id.etCustomCommand);

        btnAbort.setOnClickListener(v -> flashromExecutor.abort());

        clearTransientRomState(false);

        usbController = new UsbController(this, new UsbController.Callback() {
            @Override
            public void log(String message) {
                MainActivity.this.log(message);
            }

            @Override
            public void onDeviceConnected(String deviceName, int fd, String vidPid, boolean isRecognized, String autoProg) {
                MainActivity.this.runOnUiThread(() -> {
                    tvStatus.setText(getString(R.string.str_status_usb_connected, deviceName));
                    MainActivity.this.log("¡Permiso otorgado! Token interno de USB: " + fd);
                    MainActivity.this.log("Conectado a USB VID:PID " + vidPid);

                    if (isRecognized && autoProg != null && !autoProg.isEmpty()) {
                        selectedProgrammer = autoProg;
                        getSharedPreferences(PREFS, MODE_PRIVATE).edit().putString(KEY_PROGRAMMER, selectedProgrammer).apply();
                        MainActivity.this.log("[OK] Dispositivo reconocido: " + deviceName + " → programador '" + autoProg + "'");
                        MainActivity.this.log("Auto-configuración: Programador cambiado automáticamente a '" + selectedProgrammer + "'");
                    } else {
                        MainActivity.this.log("════════════════════════════════════════");
                        MainActivity.this.log("[AVISO] Dispositivo NO reconocido como programador flashrom.");
                        MainActivity.this.log("VID:PID " + vidPid + " (" + deviceName + ") no está en la lista de dispositivos compatibles.");
                        MainActivity.this.log("Esto NO significa que no funcione — puedes intentar con los botones o la consola.");
                        MainActivity.this.log("Si falla, cambia el programador en 'Ajustes de Programador' o reporta el VID:PID.");
                        MainActivity.this.log("════════════════════════════════════════");
                    }

                    btnProbe.setEnabled(true);
                    btnVerify.setEnabled(true);
                    btnRead.setEnabled(true);
                    btnWrite.setEnabled(true);
                    btnEraseChip.setEnabled(true);

                    if (selectedProgrammer == null || selectedProgrammer.trim().isEmpty()) {
                        selectedProgrammer = "ch341a_spi";
                        MainActivity.this.log("Programador no configurado — usando 'ch341a_spi' por defecto. Cámbialo en 'Ajustes de Programador' si es necesario.");
                    }
                    MainActivity.this.log("Programador flashrom activo: " + selectedProgrammer);
                });
            }

            @Override
            public void onDeviceConnectionFailed(String deviceName) {
                MainActivity.this.log(deviceName + " falló en enlazarse a la app (openDevice == null)");
            }

            @Override
            public void onDeviceDisconnected() {
                MainActivity.this.runOnUiThread(() -> {
                    tvStatus.setText(getString(R.string.str_estado_usb_desc));
                    btnProbe.setEnabled(false);
                    btnVerify.setEnabled(false);
                    btnRead.setEnabled(false);
                    btnWrite.setEnabled(false);
                    btnEraseChip.setEnabled(false);
                    MainActivity.this.log("Dispositivo USB desconectado.");
                });
            }
        });

        flashromExecutor = new FlashromExecutor(this, new FlashromExecutor.Callback() {
            @Override
            public void log(String message) {
                MainActivity.this.log(message);
            }

            @Override
            public void onProcessOutput(String chunk) {
                MainActivity.this.logRaw(chunk);
            }

            @Override
            public void onProcessStarted() {
                MainActivity.this.runOnUiThread(() -> {
                    if (!MainActivity.this.isFinishing() && !MainActivity.this.isDestroyed()) {
                        if (btnAbort != null) btnAbort.setVisibility(View.VISIBLE);
                    }
                });

                try {
                    Intent serviceIntent = new Intent(MainActivity.this, FlashromService.class);
                    startService(serviceIntent);
                } catch (Exception e) {
                    Log.e("MainActivity", "Error al iniciar FlashromService", e);
                }
            }

            @Override
            public void onProcessFinished(int exitCode, String[] args) {
                MainActivity.this.runOnUiThread(() -> {
                    if (!MainActivity.this.isFinishing() && !MainActivity.this.isDestroyed()) {
                        if (btnAbort != null) btnAbort.setVisibility(View.GONE);
                    }
                });

                try {
                    Intent serviceIntent = new Intent(MainActivity.this, FlashromService.class);
                    stopService(serviceIntent);
                } catch (Exception e) {
                    Log.e("MainActivity", "Error al detener FlashromService", e);
                }

                if (MainActivity.this.isDestroyed() || MainActivity.this.isFinishing()) {
                    if (usbController != null) {
                        usbController.disconnectDevice();
                        usbController.unregisterReceiver();
                    }
                    executor.shutdownNow();
                }

                if (exitCode == 0) {
                    for (int i = 0; i < args.length; i++) {
                        if ("-r".equals(args[i]) && i + 1 < args.length) {
                            String readFile = args[i + 1];
                            hasReadData = true;
                            lastReadFile = readFile;
                            SharedPreferences.Editor editor = getSharedPreferences(PREFS, MODE_PRIVATE).edit();
                            editor.putString(KEY_BIOS_SOURCE, "Leído del chip (" + selectedProgrammer + ")");
                            editor.putString(KEY_LAST_READ_FILE, readFile);
                            editor.apply();
                            break;
                        }
                    }
                } else {
                    if (UsbController.needsPtyBridge(selectedProgrammer) && usbController.getPtyBridge() != null) {
                        MainActivity.this.log("[DIAG PtyBridge] " + usbController.getPtyBridge().getDiagnosticReport());
                    }
                }
            }

            @Override
            public void onAmbiguityDetected(String[] args, List<String> suggestedChips) {
                MainActivity.this.runOnUiThread(() -> {
                    if (MainActivity.this.isFinishing() || MainActivity.this.isDestroyed()) {
                        return;
                    }
                    if (btnAbort != null) btnAbort.setVisibility(View.GONE);
                    String[] items = suggestedChips.toArray(new String[0]);
                    new android.app.AlertDialog.Builder(MainActivity.this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
                        .setTitle(R.string.str_ambiguity_title)
                        .setMessage(R.string.str_ambiguity_msg)
                        .setItems(items, (dialog, which) -> {
                            String chosenChip = items[which];
                            java.util.List<String> newArgs = new ArrayList<>(Arrays.asList(args));
                            newArgs.add("-c");
                            newArgs.add(chosenChip);
                            executeCustomFlashromCommand("flashrom " + String.join(" ", newArgs));
                        })
                        .setNegativeButton(R.string.str_cancelar, null)
                        .show();
                });
            }
        });

        selectedProgrammer = getSharedPreferences(PREFS, MODE_PRIVATE).getString(KEY_PROGRAMMER, "ch341a_spi");
        selectedDummyChipIndex = getSharedPreferences(PREFS, MODE_PRIVATE).getInt(KEY_DUMMY_CHIP_INDEX, 0);
        if (selectedDummyChipIndex < 0 || selectedDummyChipIndex >= DUMMY_CHIPS.length) {
            selectedDummyChipIndex = 0;
        }

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
                    runOnUiThread(() -> tvLoadingText.setText(R.string.str_extracting_libs));
                } else {
                    runOnUiThread(() -> tvLoadingText.setText(R.string.str_verifying_dependencies));
                }

                boolean runtimeReady = AssetHelper.ensureRuntimeReady(getApplicationContext());

                runOnUiThread(() -> {
                    layoutLoading.setVisibility(View.GONE);
                    layoutMainUI.setVisibility(View.VISIBLE);

                    boolean isUpdate = (lastVersion != -1 && currentVersion != lastVersion);
                    if (!wasExtracted) {
                        log(getString(R.string.str_log_new_install));
                        log(getString(R.string.str_log_preparing_resources));
                    } else if (isUpdate) {
                        log(getString(R.string.str_log_update_detected, lastVersion, currentVersion));
                        log(getString(R.string.str_log_verifying_resources));
                    }

                    // Mostrar información útil para el usuario
                    logRuntimeInfo();

                    if (!runtimeReady) {
                        log(getString(R.string.str_log_warn_dependencies_failed));
                    } else {
                        if (!wasExtracted) {
                            log(getString(R.string.str_log_assets_copied));
                        } else {
                            log(getString(R.string.str_log_resources_verified));
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

        // Setup Broadcast Receiver via UsbController
        usbController.registerReceiver();

        // Listener setup para todos los botones
        btnConnect.setOnClickListener(v -> usbController.searchAndRequestProgrammer(selectedProgrammer, prog -> selectedProgrammer = prog));

        btnProbe.setOnClickListener(v -> ensureProgrammerThenRun(() -> {
            if (isDummyProgrammer()) {
                executeMainDummyCommand("");
            } else {
                executeFlashromTask("-p", selectedProgrammer);
            }
        }));
        btnVerify.setOnClickListener(
                v -> ensureProgrammerThenRun(() -> {
                    if (isDummyProgrammer()) {
                        executeMainDummyCommand("-v");
                    } else {
                        executeFlashromTask("-p", selectedProgrammer, "-v", "bios.bin");
                    }
                }));
        btnRead.setOnClickListener(
                v -> ensureProgrammerThenRun(() -> {
                    if (isDummyProgrammer()) {
                        executeMainDummyCommand("-r");
                    } else {
                        executeFlashromTask("-p", selectedProgrammer, "-r", "bios.bin");
                    }
                }));
        btnWrite.setOnClickListener(
                v -> ensureProgrammerThenRun(() -> {
                    if (isDummyProgrammer()) {
                        executeMainDummyCommand("-w");
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

        btnEraseChip.setOnClickListener(v -> {
            new android.app.AlertDialog.Builder(this)
                    .setTitle(R.string.str_confirm_erase_title)
                    .setMessage(R.string.str_confirm_erase_msg)
                    .setPositiveButton(R.string.str_yes_erase, (dialog, which) -> {
                        ensureProgrammerThenRun(() -> {
                            if (isDummyProgrammer()) {
                                executeMainDummyCommand("--erase");
                            } else {
                                executeFlashromTask("-p", selectedProgrammer, "--erase");
                            }
                        });
                    })
                    .setNegativeButton(R.string.str_cancelar, null)
                    .show();
        });

        btnRunCustomCommand.setOnClickListener(v -> {
            String rawCommand = etCustomCommand.getText() == null ? "" : etCustomCommand.getText().toString().trim();
            if (rawCommand.isEmpty()) {
                log(getString(R.string.str_log_write_command_help));
                return;
            }
            // Validación básica de comandos
            if (rawCommand.contains("-p") && !rawCommand.contains("dummy") && !usbController.isConnected()) {
                log(getString(R.string.str_log_warn_no_usb));
                log(getString(R.string.str_log_dummy_ok));
            }
            executeCustomFlashromCommand(rawCommand);
        });

        btnClearLogs.setOnClickListener(v -> {
            synchronized (consoleLines) {
                consoleLines.clear();
                currentLineIndex = -1;
                cursorAtStartOfLine = false;
            }
            tvLog.setText("");
            log(getString(R.string.str_log_terminal_reset));
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
            
            String safeFileName = fileName.replaceAll("[^a-zA-Z0-9_\\-\\.]", "_");
            if (!safeFileName.isEmpty() && !safeFileName.equals("bios.bin")) {
                try {
                    java.nio.file.Files.copy(outFile.toPath(), new File(getFilesDir(), safeFileName).toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                } catch (Exception e) {
                    log("Aviso: No se pudo crear la copia con el nombre original para comandos manuales.");
                }
            }

            log("Archivo guardado como 'bios.bin' — listo para Flashear o Verificar.");
            if (!safeFileName.isEmpty() && !safeFileName.equals("bios.bin")) {
                log("También disponible como '" + safeFileName + "' para comandos manuales.");
            }

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
        List<String> deletedFiles = new ArrayList<>();
        boolean anyDeleted = false;

        for (String fileName : transientFiles) {
            try {
                File f = new File(getFilesDir(), fileName);
                if (f.exists()) {
                    if (f.delete()) {
                        deletedFiles.add(fileName);
                        anyDeleted = true;
                    } else {
                        Log.w(TAG, "No se pudo eliminar archivo temporal: " + fileName);
                    }
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
            if (anyDeleted) {
                log("Datos temporales eliminados (" + String.join(", ", deletedFiles) + ").");
            } else {
                log("No se encontraron archivos temporales para eliminar.");
            }
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
        if (!usbController.isConnected()) {
            log("Error: No hay dispositivo USB conectado. Conecta tu programador primero.");
            return;
        }
        // ── Programadores seriales (serprog, buspirate_spi, spidriver): iniciar PTY ──
        if (UsbController.needsPtyBridge(selectedProgrammer)) {
            PtyBridge ptyBridge = usbController.getPtyBridge();
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
                        if (usbController.getPtyBridge() == currentBridge) usbController.closePtyBridge();
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
        flashromExecutor.abort();
    }

    private void executeCustomFlashromCommand(String rawCommand) {
        if (flashromExecutor.isRunning()) {
            log(getString(R.string.str_err_flashrom_running));
            return;
        }

        String[] args = rawCommand.split("\\s+");
        if (args.length == 0 || !("flashrom".equals(args[0]) || "./flashrom".equals(args[0]))) {
            log(getString(R.string.str_err_missing_flashrom_prefix));
            return;
        }
        args = Arrays.copyOfRange(args, 1, args.length);
        for (int i = 0; i < args.length; i++) {
            if ("-r".equals(args[i])) {
                clearTransientRomState(false);
                break;
            }
        }

        // ── Programadores seriales: si el comando usa "-p serprog/buspirate_spi/spidriver"
        // activar puente PTY↔USB ──
        String detectedSerialProg = null;
        for (int i = 0; i < args.length; i++) {
            if ("-p".equals(args[i]) && i + 1 < args.length) {
                String pVal = args[i + 1];
                if (pVal.startsWith("serprog") || pVal.startsWith("buspirate_spi") || pVal.startsWith("spidriver")) {
                    int colon = pVal.indexOf(':');
                    detectedSerialProg = (colon > 0) ? pVal.substring(0, colon) : pVal;
                }
                break;
            }
        }

        if (detectedSerialProg != null && usbController.getPtyBridge() != null && usbController.getPtyBridge().isOpen()) {
            PtyBridge ptyBridge = usbController.getPtyBridge();
            if (!ptyBridge.isForwardingActive()) {
                log(getString(R.string.str_log_starting_pty, detectedSerialProg));
                ptyBridge.purge();
                ptyBridge.startForwarding();
                log(getString(R.string.str_log_forwarding_active));
            }

            final String bareProgName = detectedSerialProg;
            List<String> argList = new ArrayList<>();
            for (int i = 0; i < args.length; i++) {
                if ("-p".equals(args[i]) && i + 1 < args.length && (bareProgName.equals(args[i + 1]) || args[i + 1].startsWith(bareProgName + ":"))) {
                    argList.add("-p");
                    argList.add(usbController.buildPtyProgrammerParam(bareProgName));
                    i++;
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
            log(getString(R.string.str_err_critical_flashrom_missing, preferredFlashromBin.getAbsolutePath()));
            return;
        }
        log("$ flashrom " + String.join(" ", args));

        flashromExecutor.execute(preferredFlashromBin, args, usbController.getCurrentFd(),
                UsbController.needsPtyBridge(detectedSerialProg != null ? detectedSerialProg : ""),
                detectedSerialProg != null ? detectedSerialProg : "");
    }

    private void executeFlashromTask(String... args) {
        if (flashromExecutor.isRunning()) {
            log(getString(R.string.str_err_flashrom_running));
            return;
        }

        // Dummy no necesita FD de USB
        if (!usbController.isConnected() && !isDummyProgrammer()) {
            log(getString(R.string.str_err_logical_usb_lost));
            return;
        }

        for (int i = 0; i < args.length; i++) {
            if ("-r".equals(args[i])) {
                clearTransientRomState(false);
                break;
            }
        }

        String[] resolvedArgs = args;
        PtyBridge ptyBridge = usbController.getPtyBridge();
        if (ptyBridge != null && ptyBridge.isOpen()) {
            List<String> argList = new ArrayList<>();
            for (int i = 0; i < args.length; i++) {
                if ("-p".equals(args[i]) && i + 1 < args.length && UsbController.needsPtyBridge(args[i + 1])) {
                    argList.add("-p");
                    String progName = args[i + 1];
                    int colon = progName.indexOf(':');
                    String bareProg = (colon > 0) ? progName.substring(0, colon) : progName;
                    argList.add(usbController.buildPtyProgrammerParam(bareProg));
                    i++;
                } else {
                    argList.add(args[i]);
                }
            }
            resolvedArgs = argList.toArray(new String[0]);
        }

        File preferredFlashromBin = new File(getFilesDir(), "usr/sbin/flashrom");
        if (!preferredFlashromBin.exists()) {
            log("[WARN] flashrom en files/usr/sbin no encontrado; usando fallback jniLibs.");
            preferredFlashromBin = new File(getApplicationInfo().nativeLibraryDir, "libflashrom_bin.so");
        }
        if (!preferredFlashromBin.exists()) {
            log(getString(R.string.str_err_critical_flashrom_missing, preferredFlashromBin.getAbsolutePath()));
            return;
        }

        log("$ flashrom " + String.join(" ", args));

        // Configurar si saltar verificación en escritura
        String opLabel = "";
        boolean isLongOp = false;
        for (String arg : resolvedArgs) {
            if ("-w".equals(arg)) {
                opLabel = getString(R.string.str_log_writing_flash_op);
            }
            if ("-r".equals(arg) || "-w".equals(arg) || "-v".equals(arg) || "-E".equals(arg) || "--erase".equals(arg)) {
                isLongOp = true;
            }
        }
        
        List<String> finalArgsList = new ArrayList<>(Arrays.asList(resolvedArgs));
        
        if (cbVerifyWrite != null && !cbVerifyWrite.isChecked() && getString(R.string.str_log_writing_flash_op).equals(opLabel)) {
            finalArgsList.add("-n");
            log(getString(R.string.str_log_verify_disabled));
        }
        
        if (isLongOp && !finalArgsList.contains("--progress")) {
            finalArgsList.add("--progress");
        }
        
        resolvedArgs = finalArgsList.toArray(new String[0]);

        flashromExecutor.execute(preferredFlashromBin, resolvedArgs, usbController.getCurrentFd(),
                UsbController.needsPtyBridge(selectedProgrammer), selectedProgrammer);
    }

    private boolean isDummyProgrammer() {
        return selectedProgrammer != null && selectedProgrammer.startsWith("dummy");
    }

    private void log(String message) {
        Log.i(TAG, message);
        if (Looper.myLooper() == Looper.getMainLooper()) {
            appendLogOnUi(message);
        } else {
            runOnUiThread(() -> appendLogOnUi(message));
        }
    }

    private void logRaw(String chunk) {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            appendRawLogOnUi(chunk);
        } else {
            runOnUiThread(() -> appendRawLogOnUi(chunk));
        }
    }

    private void appendLogOnUi(String message) {
        appendRawLogOnUi(message + "\n");
    }

    private void appendRawLogOnUi(String text) {
        synchronized (consoleLines) {
            if (consoleLines.isEmpty()) {
                consoleLines.add(new StringBuilder());
                currentLineIndex = 0;
            }

            for (int i = 0; i < text.length(); i++) {
                char c = text.charAt(i);
                if (c == '\n') {
                    consoleLines.add(new StringBuilder());
                    currentLineIndex = consoleLines.size() - 1;
                    cursorAtStartOfLine = false;
                } else if (c == '\r') {
                    cursorAtStartOfLine = true;
                } else if (c == '\b') {
                    StringBuilder currentLine = consoleLines.get(currentLineIndex);
                    if (currentLine.length() > 0) {
                        currentLine.setLength(currentLine.length() - 1);
                    }
                } else {
                    StringBuilder currentLine = consoleLines.get(currentLineIndex);
                    if (cursorAtStartOfLine) {
                        currentLine.setLength(0); // Overwrite the line from the start
                        cursorAtStartOfLine = false;
                    }
                    currentLine.append(c);
                }
            }

            // Limit console buffer size to 1000 lines to prevent memory issues
            while (consoleLines.size() > 1000) {
                consoleLines.remove(0);
                currentLineIndex--;
            }
            if (currentLineIndex < 0) {
                currentLineIndex = 0;
            }
        }

        if (!isLogUpdatePending) {
            isLogUpdatePending = true;
            logHandler.postDelayed(logUpdater, 80); // Fast 80ms refresh for responsive feel
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
                        .makeText(this, R.string.str_no_log_to_copy, android.widget.Toast.LENGTH_SHORT)
                        .show();
                return true;
            }
            ClipboardManager clipboard = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
            if (clipboard == null) {
                android.widget.Toast
                        .makeText(this, R.string.str_clipboard_error, android.widget.Toast.LENGTH_SHORT)
                        .show();
                return true;
            }
            clipboard.setPrimaryClip(ClipData.newPlainText("flash_spi_tool_logs", logs));
            android.widget.Toast.makeText(this, R.string.str_logs_copied, android.widget.Toast.LENGTH_SHORT)
                    .show();
            return true;
        });
    }

    private void logRuntimeInfo() {
        log(getString(R.string.str_log_started));
        log(getString(R.string.str_log_android_info, Build.VERSION.RELEASE, Build.VERSION.SDK_INT));
        log(getString(R.string.str_log_programmer_selected, selectedProgrammer));
        log(getString(R.string.str_log_connect_help));
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
        } else if (id == R.id.action_export_serprog) {
            exportSerprogFirmware();
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    private void exportSerprogFirmware() {
        File downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
        if (downloadsDir != null) {
            boolean successIno = exportAssetToDir("serprog_arduino.ino", downloadsDir);
            boolean successHex = exportAssetToDir("serprog_arduino.hex", downloadsDir);
            
            if (successIno && successHex) {
                log(getString(R.string.str_export_success_log));
                android.widget.Toast.makeText(this, R.string.str_export_success_toast, android.widget.Toast.LENGTH_LONG).show();
            } else {
                log(getString(R.string.str_export_error_log));
            }
        }
    }

    private boolean exportAssetToDir(String assetName, File targetDir) {
        try {
            File outFile = new File(targetDir, assetName);
            try (InputStream in = getAssets().open(assetName);
                 OutputStream out = new FileOutputStream(outFile)) {
                byte[] buffer = new byte[8192];
                int read;
                while ((read = in.read(buffer)) != -1) {
                    out.write(buffer, 0, read);
                }
            }
            return true;
        } catch (Exception e) {
            log(getString(R.string.str_error_copying_asset, assetName, e.getMessage()));
            return false;
        }
    }

    private void showAboutDialog() {
        TextView aboutText = new TextView(this);
        int padding = (int) (24 * getResources().getDisplayMetrics().density);
        aboutText.setPadding(padding, padding, padding, padding / 2);
        // Permitir que el texto tome el color por defecto (adapta al Dark theme)
        aboutText.setMovementMethod(LinkMovementMethod.getInstance());
        String aboutHtml = getString(R.string.str_about_html);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            aboutText.setText(Html.fromHtml(aboutHtml, Html.FROM_HTML_MODE_COMPACT));
        } else {
            @SuppressWarnings("deprecation")
            CharSequence text = Html.fromHtml(aboutHtml);
            aboutText.setText(text);
        }

        new android.app.AlertDialog.Builder(this)
                .setTitle(R.string.str_acerca_de)
                .setView(aboutText)
                .setPositiveButton(R.string.str_close, null)
                .show();
    }

    private void showPinoutsDialog() {
        String[] pinoutOptions = {
                getString(R.string.str_pinout_ch341a),
                getString(R.string.str_pinout_soic8),
                getString(R.string.str_pinout_arduino),
                getString(R.string.str_pinout_buspirate),
                getString(R.string.str_pinout_spidriver),
                getString(R.string.str_pinout_spi_bus),
                getString(R.string.str_pinout_lpc_bus)
        };

        new android.app.AlertDialog.Builder(this)
                .setTitle(R.string.str_pinouts_de_hard)
                .setItems(pinoutOptions, (dialog, which) -> {
                    android.widget.ImageView iv = new android.widget.ImageView(this);
                    iv.setBackgroundColor(0xFF1B1E2B);
                    int pad = (int) (8 * getResources().getDisplayMetrics().density);
                    iv.setPadding(pad, pad, pad, pad);
                    String title = pinoutOptions[which];
                    switch (which) {
                        case 0:
                            PinoutView.dibujarCH341A(this, iv);
                            break;
                        case 1:
                            PinoutView.dibujarSOIC8(this, iv);
                            break;
                        case 2:
                            PinoutView.dibujarArduinoSerprog(this, iv);
                            break;
                        case 3:
                            PinoutView.dibujarBusPirate(this, iv);
                            break;
                        case 4:
                            PinoutView.dibujarSPIDriver(this, iv);
                            break;
                        case 5:
                            PinoutView.dibujarSPI(this, iv);
                            break;
                        default:
                            PinoutView.dibujarLPC(this, iv);
                            break;
                    }
                    ScrollView scroll = new ScrollView(this);
                    scroll.addView(iv);
                    new android.app.AlertDialog.Builder(this)
                            .setTitle(title)
                            .setView(scroll)
                            .setPositiveButton(R.string.str_close, null)
                            .show();
                })
                .setNegativeButton(R.string.str_close, null)
                .show();
    }

    private void executeMainDummyCommand(String action) {
        String chipName = DUMMY_CHIPS[selectedDummyChipIndex][1];
        int chipSize = Integer.parseInt(DUMMY_CHIPS[selectedDummyChipIndex][2]);
        String chipFlag = DUMMY_CHIPS[selectedDummyChipIndex].length > 3 ? DUMMY_CHIPS[selectedDummyChipIndex][3] : null;

        File userBios = new File(getFilesDir(), "bios.bin");
        
        // Si la acción requiere un archivo (read, write, verify) pero no hay bios.bin
        if (("-r".equals(action) || "-w".equals(action) || "-v".equals(action)) && (!userBios.exists() || userBios.length() == 0)) {
            log("Error: No hay bios.bin cargado. Usa 'Cargar ROM' primero.");
            return;
        }

        if (userBios.exists() && userBios.length() > 0) {
            long actualSize = userBios.length();
            if (actualSize != chipSize) {
                log("Aviso: El archivo bios.bin (" + actualSize + " bytes) no coincide con el chip dummy seleccionado (" + chipSize + " bytes).");
                log("Forzando emulación a VARIABLE_SIZE para evitar error de flashrom.");
                String cmd = "flashrom -p dummy:emulate=VARIABLE_SIZE,size=" + actualSize + ",image=bios.bin " + action;
                if ("-r".equals(action) || "-w".equals(action) || "-v".equals(action)) {
                    cmd += " bios.bin";
                }
                executeCustomFlashromCommand(cmd.trim());
                return;
            }
            
            String cmd = buildDummyCmd(chipName, chipSize, chipFlag, action);
            if ("-r".equals(action) || "-w".equals(action) || "-v".equals(action)) {
                cmd += " bios.bin";
            }
            // Cambiar image=bios_test.bin a image=bios.bin
            cmd = cmd.replace("image=bios_test.bin", "image=bios.bin");
            executeCustomFlashromCommand(cmd);
        } else {
            // Para probe o erase sin archivo cargado
            log("No hay bios.bin cargado. Generando/usando archivo de prueba...");
            ensureDummyTestFile(chipSize);
            executeCustomFlashromCommand(buildDummyCmd(chipName, chipSize, chipFlag, action));
        }
    }

    private void showDummyTestDialog() {
        String[] testOptions = {
                getString(R.string.str_dummy_opt_read),
                getString(R.string.str_dummy_opt_write),
                getString(R.string.str_dummy_opt_erase),
                getString(R.string.str_dummy_opt_probe),
                getString(R.string.str_dummy_opt_select),
                getString(R.string.str_dummy_opt_info)
        };

        new android.app.AlertDialog.Builder(this)
                .setTitle(R.string.str_dummy_test_mode)
                .setItems(testOptions, (dialog, which) -> {
                    String chipName = DUMMY_CHIPS[selectedDummyChipIndex][1];
                    int size = Integer.parseInt(DUMMY_CHIPS[selectedDummyChipIndex][2]);
                    String chipFlag = DUMMY_CHIPS[selectedDummyChipIndex].length > 3 ? DUMMY_CHIPS[selectedDummyChipIndex][3] : null;
                    
                    switch (which) {
                        case 0: // Leer
                            ensureDummyTestFile(size);
                            executeCustomFlashromCommand(buildDummyCmd(chipName, size, chipFlag, "-r read_test.bin"));
                            break;
                        case 1: // Escribir + verificar
                            ensureDummyTestFile(size);
                            executeCustomFlashromCommand(buildDummyCmd(chipName, size, chipFlag, "-w bios_test.bin"));
                            break;
                        case 2: // Borrar chip emulado
                            ensureDummyTestFile(size);
                            executeCustomFlashromCommand(buildDummyCmd(chipName, size, chipFlag, "--erase"));
                            break;
                        case 3: // Probe
                            ensureDummyTestFile(size);
                            executeCustomFlashromCommand(buildDummyCmd(chipName, size, chipFlag, ""));
                            break;
                        case 4: // Seleccionar chip
                            showDummyChipSelector();
                            break;
                        case 5: // Info
                            showDummyChipInfo();
                            break;
                    }
                })
                .setNegativeButton(R.string.str_cancelar, null)
                .show();
    }

    private String buildDummyCmd(String chipName, int size, String chipFlag, String action) {
        String cmd;
        if ("VARIABLE_SIZE".equals(chipName)) {
            cmd = "flashrom -p dummy:emulate=VARIABLE_SIZE,size=" + size + ",image=bios_test.bin " + action;
        } else if (chipFlag != null) {
            cmd = "flashrom -p dummy:emulate=" + chipName + ",image=bios_test.bin -c " + chipFlag + " " + action;
        } else {
            cmd = "flashrom -p dummy:emulate=" + chipName + ",image=bios_test.bin " + action;
        }
        return cmd.trim();
    }

    private void showDummyChipSelector() {
        String[] labels = new String[DUMMY_CHIPS.length];
        for (int i = 0; i < DUMMY_CHIPS.length; i++) {
            labels[i] = DUMMY_CHIPS[i][0];
        }

        new android.app.AlertDialog.Builder(this)
                .setTitle(R.string.str_dummy_chip_emulate)
                .setItems(labels, (dialog, which) -> {
                    selectedDummyChipIndex = which;
                    getSharedPreferences(PREFS, MODE_PRIVATE).edit().putInt(KEY_DUMMY_CHIP_INDEX, which).apply();
                    
                    String chipName = DUMMY_CHIPS[which][1];
                    int size = Integer.parseInt(DUMMY_CHIPS[which][2]);
                    String chipFlag = DUMMY_CHIPS[which].length > 3 ? DUMMY_CHIPS[which][3] : null;
                    ensureDummyTestFile(size);

                    String cmd = buildDummyCmd(chipName, size, chipFlag, "-r read_test.bin");
                    log(getString(R.string.str_dummy_chip_selected, DUMMY_CHIPS[which][0]
                            + (chipFlag != null ? " -c " + chipFlag : "")));
                    executeCustomFlashromCommand(cmd);
                })
                .setNegativeButton(R.string.str_cancelar, null)
                .show();
    }

    private void showDummyChipInfo() {
        new android.app.AlertDialog.Builder(this)
                .setTitle(R.string.str_dummy_info_title)
                .setMessage(R.string.str_dummy_info_msg)
                .setPositiveButton(R.string.str_close, null)
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
            log(getString(R.string.str_log_test_file_created, (sizeBytes / 1024)));
        } catch (Exception e) {
            log("Error creando archivo de prueba: " + e.getMessage());
        }
    }

    @Override
    protected void onDestroy() {
        if (mostrarPublicidad != null) {
            mostrarPublicidad.disposeBanner();
        }
        if (flashromExecutor == null || !flashromExecutor.isRunning()) {
            if (usbController != null) {
                usbController.disconnectDevice();
                usbController.unregisterReceiver();
            }
            executor.shutdownNow(); // Finalizar todos los hilos
        }
        super.onDestroy();
        clearTransientRomState(false);
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
