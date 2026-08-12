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

    // Native process control to bypass ProcessBuilder FD closure
    private static native int[] startNativeProcess(String executable, String[] args, int usbFd, String ldLibraryPath, String miniproData, String workingDir);
    private static native int waitForNativeProcess(int pid);
    private static native void terminateNativeProcess(int pid);

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
    private volatile int currentPid = -1;

    public FlashromExecutor(Context context, Callback callback) {
        this.context = context;
        this.callback = callback;
    }

    public synchronized void abort() {
        int pid = currentPid;
        if (pid > 0) {
            try {
                terminateNativeProcess(pid);
                callback.log("\n[PROCESO ABORTADO POR EL USUARIO]\n");
            } catch (Exception e) {
                callback.log("[ERROR] No se pudo detener el proceso nativo: " + e.getMessage());
            }
            currentPid = -1;
        }
    }

    public synchronized boolean isRunning() {
        return currentPid > 0;
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
        String[] commandArgs = command.subList(1, command.size()).toArray(new String[0]);

        int inheritableFd = -1;
        int childPid = -1;
        int readFd = -1;

        try {
            String jniLibs = context.getApplicationInfo().nativeLibraryDir;
            String ldPath = jniLibs + ":" + new File(context.getFilesDir(), "usr/lib").getAbsolutePath();

            if (!needsPty && currentFd >= 0) {
                inheritableFd = dupFdForChild(currentFd);
            }

            int fdToPass = inheritableFd >= 0 ? inheritableFd : (needsPty ? -1 : currentFd);

            callback.onProcessStarted();

            // Start process natively to prevent ProcessBuilder from closing the file descriptor
            int[] processInfo = startNativeProcess(flashromBin.getAbsolutePath(), commandArgs, fdToPass, ldPath, "", context.getFilesDir().getAbsolutePath());
            if (processInfo == null || processInfo[0] <= 0) {
                callback.log("[ERROR] Error al iniciar el proceso nativo.");
                callback.onProcessFinished(-1, args);
                return;
            }

            childPid = processInfo[0];
            readFd = processInfo[1];
            synchronized (this) {
                currentPid = childPid;
            }

            boolean multipleChipsFound = false;
            List<String> suggestedChips = new ArrayList<>();

            try (android.os.ParcelFileDescriptor pfd = android.os.ParcelFileDescriptor.adoptFd(readFd);
                 java.io.FileInputStream fis = new java.io.FileInputStream(pfd.getFileDescriptor());
                 BufferedReader reader = new BufferedReader(new InputStreamReader(fis))) {
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

            int exitCode = waitForNativeProcess(childPid);
            synchronized (this) {
                currentPid = -1;
            }

            if (exitCode != 0 && multipleChipsFound && !suggestedChips.isEmpty()) {
                callback.onAmbiguityDetected(args, suggestedChips);
                return;
            }

            callback.onProcessFinished(exitCode, args);

        } catch (Exception e) {
            Log.e(TAG, "Error fatal ejecutando flashrom", e);
            callback.log("[CRITICAL] Proceso nativo falló: " + e.getMessage());
            callback.log(stackTrace(e));
            synchronized (this) {
                currentPid = -1;
            }
            callback.onProcessFinished(-1, args);
        } finally {
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
