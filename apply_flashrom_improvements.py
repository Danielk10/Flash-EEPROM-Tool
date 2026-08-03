import os
import re

main_activity_path = "app/src/main/java/com/diamon/curso/ui/activities/MainActivity.java"
layout_path = "app/src/main/res/layout/activity_main.xml"

print("Updating layout...")
with open(layout_path, "r") as f:
    xml = f.read()

abort_button_xml = """                <Button
                    android:id="@+id/btnAbort"
                    android:layout_width="wrap_content"
                    android:layout_height="wrap_content"
                    android:text="@string/str_detener"
                    android:textColor="#FFFFFF"
                    android:backgroundTint="#D32F2F"
                    android:minHeight="0dp"
                    android:paddingTop="4dp"
                    android:paddingBottom="4dp"
                    android:visibility="gone"
                    android:layout_marginEnd="4dp" />

                <Button
                    android:id="@+id/btnClearLogs\""""
xml = xml.replace('<Button\n                    android:id="@+id/btnClearLogs"', abort_button_xml)

fast_write_xml = """
        <CheckBox
            android:id="@+id/cbFastWrite"
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:text="Escritura Rápida (Ignorar Verificación)"
            android:textColor="#E0E0E0"
            android:layout_marginBottom="4dp"/>
            
        <Button
            android:id="@+id/btnRunCustomCommand"
"""
xml = xml.replace('<Button\n            android:id="@+id/btnRunCustomCommand"', fast_write_xml)

with open(layout_path, "w") as f:
    f.write(xml)

print("Updating MainActivity.java...")
with open(main_activity_path, "r") as f:
    java = f.read()

# Add Process currentFlashromProcess
if "private Process currentFlashromProcess" not in java:
    java = java.replace("private com.diamon.curso.core.PtyBridge ptyBridge;", 
                       "private com.diamon.curso.core.PtyBridge ptyBridge;\n    private Process currentFlashromProcess;")

# Add CheckBox cbFastWrite and btnAbort
java = java.replace("private Button btnClearLogs;", "private Button btnClearLogs, btnAbort;\n    private android.widget.CheckBox cbFastWrite;")
java = java.replace("btnClearLogs = findViewById(R.id.btnClearLogs);", 
                    "btnClearLogs = findViewById(R.id.btnClearLogs);\n        btnAbort = findViewById(R.id.btnAbort);\n        cbFastWrite = findViewById(R.id.cbFastWrite);\n        btnAbort.setOnClickListener(v -> abortFlashromProcess());")

abort_method = """
    private void abortFlashromProcess() {
        if (currentFlashromProcess != null) {
            log("[ABORTAR] Enviando señal de terminación a flashrom...");
            currentFlashromProcess.destroy();
            if (ptyBridge != null && ptyBridge.isOpen()) {
                ptyBridge.purge(); // Limpiar buffers seriales si está colgado
            }
        }
    }
"""
if "abortFlashromProcess()" not in java:
    java = java.replace("private void executeCustomFlashromCommand", abort_method + "\n    private void executeCustomFlashromCommand")

# Append -n if cbFastWrite is checked
fast_write_logic = """
            if (cbFastWrite != null && cbFastWrite.isChecked() && opLabel.equals("Escribiendo flash")) {
                List<String> newArgs = new ArrayList<>(Arrays.asList(args));
                newArgs.add("-n");
                args = newArgs.toArray(new String[0]);
                log("Fast Write activado: Saltando verificación (-n)");
            }
            final String operationLabel = opLabel;
"""
java = java.replace("final String operationLabel = opLabel;", fast_write_logic)

# The process loop
old_loop = """            Process process = pb.start();

            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    log("[native] " + line);
                }
            }

            int exitCode = process.waitFor();

            if (exitCode == 0) {"""

new_loop = """            Process process = pb.start();
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

            if (exitCode == 0) {"""

java = java.replace(old_loop, new_loop)

with open(main_activity_path, "w") as f:
    f.write(java)
print("Changes applied to layout and code.")
