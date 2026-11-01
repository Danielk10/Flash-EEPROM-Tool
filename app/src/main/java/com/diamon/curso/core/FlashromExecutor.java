package com.diamon.curso.core;

import android.content.Context;
import android.util.Log;

import com.diamon.curso.R;
import java.io.File;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.List;
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

    private static boolean nativeLibraryLoaded = false;
    static {
        try {
            System.loadLibrary("curso");
            nativeLibraryLoaded = true;
        } catch (UnsatisfiedLinkError e) {
            Log.e(TAG, "Error cargando librería nativa: " + e.getMessage());
        }
    }

    public static boolean isNativeLibraryLoaded() {
        return nativeLibraryLoaded;
    }

    public interface Callback {
        void log(String message);
        void onProcessOutput(String chunk);
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

    public static class AmbiguityInfo {
        public final boolean multipleChipsFound;
        public final List<String> suggestedChips;

        public AmbiguityInfo(boolean multipleChipsFound, List<String> suggestedChips) {
            this.multipleChipsFound = multipleChipsFound;
            this.suggestedChips = suggestedChips != null ? suggestedChips : new ArrayList<>();
        }
    }

    /** Comprueba si el comando ya incluye una selección explícita de chip (-c). */
    public static boolean hasChipArgument(String[] args) {
        if (args == null) return false;
        for (String arg : args) {
            if ("-c".equals(arg)) return true;
        }
        return false;
    }

    /**
     * Devuelve una copia del comando con {@code -c <chip>}.
     *
     * Si ya existe un {@code -c}, reemplaza su valor. Si no existe, inserta la
     * selección justo después del par {@code -p <programador>} para que
     * flashrom reciba el argumento en una posición válida.
     */
    public static String[] withChipArgument(String[] args, String chip) {
        if (args == null || args.length == 0 || chip == null || chip.trim().isEmpty()) {
            return args;
        }

        String chipName = chip.trim();
        List<String> result = new ArrayList<>();
        boolean replaced = false;

        for (int i = 0; i < args.length; i++) {
            if ("-c".equals(args[i])) {
                result.add("-c");
                result.add(chipName);
                if (i + 1 < args.length) i++; // omitir el valor anterior
                replaced = true;
            } else {
                result.add(args[i]);
            }
        }

        if (!replaced) {
            int insertAt = -1;
            for (int i = 0; i < result.size(); i++) {
                if ("-p".equals(result.get(i)) && i + 1 < result.size()) {
                    insertAt = i + 2;
                    break;
                }
            }
            if (insertAt >= 0) {
                result.add(insertAt, "-c");
                result.add(insertAt + 1, chipName);
            } else {
                result.add(0, "-c");
                result.add(1, chipName);
            }
        }

        return result.toArray(new String[0]);
    }

    public static AmbiguityInfo parseOutputForAmbiguity(String fullOutput) {
        boolean multipleChipsFound = false;
        List<String> suggestedChips = new ArrayList<>();
        List<String> allDetectedChips = new ArrayList<>();

        if (fullOutput == null || fullOutput.isEmpty()) {
            return new AmbiguityInfo(false, suggestedChips);
        }

        String[] lines = fullOutput.split("\\r?\\n");
        for (String rawLine : lines) {
            String line = rawLine.trim();
            if (line.isEmpty()) continue;

            if (line.contains("Found ") && line.contains("flash chip")) {
                int startQuote = line.indexOf('"');
                int endQuote = line.indexOf('"', startQuote + 1);
                if (startQuote != -1 && endQuote != -1) {
                    String chip = line.substring(startQuote + 1, endQuote).trim();
                    if (!chip.isEmpty() && !allDetectedChips.contains(chip)) {
                        allDetectedChips.add(chip);
                    }
                }
            }

            if (line.contains("Multiple flash chip definitions match") ||
                line.contains("Please specify which chip definition to use with the -c")) {
                multipleChipsFound = true;
                int startQuote = -1;
                while ((startQuote = line.indexOf('"', startQuote + 1)) != -1) {
                    int endQuote = line.indexOf('"', startQuote + 1);
                    if (endQuote == -1) break;
                    String chip = line.substring(startQuote + 1, endQuote).trim();
                    if (!chip.isEmpty() && !suggestedChips.contains(chip)) {
                        suggestedChips.add(chip);
                    }
                    startQuote = endQuote;
                }
            }
        }

        if (multipleChipsFound) {
            for (String chip : allDetectedChips) {
                if (!suggestedChips.contains(chip)) {
                    suggestedChips.add(chip);
                }
            }
        }

        return new AmbiguityInfo(multipleChipsFound, suggestedChips);
    }

    public synchronized void abort() {
        if (!nativeLibraryLoaded) return;
        int pid = currentPid;
        if (pid > 0) {
            try {
                terminateNativeProcess(pid);
                callback.log(context.getString(R.string.str_log_process_aborted));
            } catch (Exception e) {
                callback.log(context.getString(R.string.str_log_err_stop_native_process, e.getMessage()));
            }
            currentPid = -1;
        }
    }

    public synchronized boolean isRunning() {
        return currentPid > 0;
    }

    public void execute(File flashromBin, String[] args, int currentFd, boolean needsPty, String selectedProgrammer) {
        if (!nativeLibraryLoaded) {
            callback.log("[CRITICAL] La librería nativa no está disponible en este dispositivo. Verifica que tu arquitectura (ABI) sea arm64-v8a.");
            callback.onProcessFinished(-1, args);
            return;
        }
        if (!flashromBin.exists()) {
            callback.log(context.getString(R.string.str_err_critical_flashrom_missing, flashromBin.getAbsolutePath()));
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
                callback.log(context.getString(R.string.str_log_err_start_native_process));
                callback.onProcessFinished(-1, args);
                return;
            }

            childPid = processInfo[0];
            readFd = processInfo[1];
            synchronized (this) {
                currentPid = childPid;
            }

            StringBuilder fullOutputCollector = new StringBuilder();

            try (android.os.ParcelFileDescriptor pfd = android.os.ParcelFileDescriptor.adoptFd(readFd);
                 java.io.FileInputStream fis = new java.io.FileInputStream(pfd.getFileDescriptor());
                 InputStreamReader reader = new InputStreamReader(fis, "UTF-8")) {
                
                char[] buffer = new char[512];
                int charsRead;
                
                while ((charsRead = reader.read(buffer)) != -1) {
                    String chunk = new String(buffer, 0, charsRead);
                    callback.onProcessOutput(chunk);
                    fullOutputCollector.append(chunk);
                }
            }

            int exitCode = waitForNativeProcess(childPid);
            synchronized (this) {
                currentPid = -1;
            }

            AmbiguityInfo ambiguity = parseOutputForAmbiguity(fullOutputCollector.toString());
            // No dependemos del código de salida: algunas versiones de flashrom
            // pueden devolver 0 aunque hayan detectado varias definiciones y no
            // hayan realizado la operación solicitada.
            if (ambiguity.multipleChipsFound) {
                callback.onAmbiguityDetected(args, ambiguity.suggestedChips);
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
