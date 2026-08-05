package com.diamon.curso.core;

import android.content.Context;
import android.util.Log;
import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class FlashromExecutor {
    private static final String TAG = "FlashromExecutor";

    // JNI: duplica el FD USB sin O_CLOEXEC para que sea heredable por procesos hijos
    private static native int dupFdForChild(int fd);
    // JNI: cierra el FD duplicado tras finalizar flashrom
    private static native void closeDupedFd(int fd);

    static {
        System.loadLibrary("curso");
    }

    public interface Callback {
        void log(String message);
        void onProcessStarted();
        void onProcessFinished(int exitCode, String[] args);
        void onAmbiguityDetected(String[] args, List<String> suggestedChips);
    }

    private final Context context;
    private final Callback callback;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private Process currentProcess;

    public FlashromExecutor(Context context, Callback callback) {
        this.context = context;
        this.callback = callback;
    }

    public synchronized void abort() {
        if (currentProcess != null) {
            currentProcess.destroy();
            currentProcess = null;
            callback.log("\n[PROCESO ABORTADO POR EL USUARIO]\n");
        }
    }

    public synchronized boolean isRunning() {
        return currentProcess != null;
    }

    public void execute(File flashromBin, String[] args, int currentFd, boolean needsPty, String selectedProgrammer) {
        if (!flashromBin.exists()) {
            callback.log("Fallo crítico: Binario 'flashrom' no existe. (" + flashromBin.getAbsolutePath() + ")");
            return;
        }

        executor.execute(() -> runProcess(flashromBin, args, currentFd, needsPty, selectedProgrammer));
    }

    private void runProcess(File flashromBin, String[] args, int currentFd, boolean needsPty, String selectedProgrammer) {
        List<String> command = new ArrayList<>();
        command.add(flashromBin.getAbsolutePath());
        for (String arg : args) {
            command.add(arg);
        }

        // ── Duplicar el FD USB para herencia en el proceso hijo ──
        // El FD original de UsbDeviceConnection tiene O_CLOEXEC: Android lo
        // cierra durante exec(). dup() crea un nuevo FD sin esa flag,
        // permitiendo que flashrom lo use a través de ANDROID_USB_FD.
        int inheritableFd = -1;

        try {
            ProcessBuilder pb = new ProcessBuilder(command);
            pb.directory(context.getFilesDir());
            pb.redirectErrorStream(true);

            Map<String, String> env = pb.environment();
            if (needsPty) {
                env.remove("ANDROID_USB_FD");
            } else if (currentFd >= 0) {
                inheritableFd = dupFdForChild(currentFd);
                if (inheritableFd >= 0) {
                    env.put("ANDROID_USB_FD", String.valueOf(inheritableFd));
                    Log.i(TAG, "USB FD duplicado: " + currentFd + " -> " + inheritableFd + " (heredable)");
                } else {
                    // Fallback: pasar el FD original (puede no funcionar en todos los devices)
                    env.put("ANDROID_USB_FD", String.valueOf(currentFd));
                    Log.w(TAG, "dup() falló, usando FD original: " + currentFd);
                }
            } else {
                env.remove("ANDROID_USB_FD");
            }

            String jniLibs = context.getApplicationInfo().nativeLibraryDir;
            String fallbackPath = System.getenv("PATH");
            env.put("LD_LIBRARY_PATH", jniLibs + ":" + new File(context.getFilesDir(), "usr/lib").getAbsolutePath());
            env.put("PATH", jniLibs + (fallbackPath != null ? ":" + fallbackPath : ""));

            synchronized (this) {
                currentProcess = pb.start();
            }
            callback.onProcessStarted();

            boolean multipleChipsFound = false;
            List<String> suggestedChips = new ArrayList<>();

            try (BufferedReader reader = new BufferedReader(new InputStreamReader(currentProcess.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    callback.log(line);

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

            int exitCode = currentProcess.waitFor();
            synchronized (this) {
                currentProcess = null;
            }

            if (exitCode != 0 && multipleChipsFound && !suggestedChips.isEmpty()) {
                callback.onAmbiguityDetected(args, suggestedChips);
                return;
            }

            callback.onProcessFinished(exitCode, args);

        } catch (Exception e) {
            Log.e(TAG, "Error fatal ejecutando flashrom", e);
            callback.log("[CRITICAL] ProcessBuilder falló: " + e.getMessage());
            callback.log(stackTrace(e));
            synchronized (this) {
                currentProcess = null;
            }
            callback.onProcessFinished(-1, args);
        } finally {
            // ── Cerrar el FD duplicado DESPUÉS de que flashrom terminó ──
            if (inheritableFd >= 0) {
                closeDupedFd(inheritableFd);
                Log.i(TAG, "FD duplicado " + inheritableFd + " cerrado");
            }
        }
    }

    private String stackTrace(Throwable error) {
        StringWriter sw = new StringWriter();
        PrintWriter pw = new PrintWriter(sw);
        error.printStackTrace(pw);
        return sw.toString();
    }
}
