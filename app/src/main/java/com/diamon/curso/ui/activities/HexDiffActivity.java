package com.diamon.curso.ui.activities;

import com.diamon.curso.R;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.view.LayoutInflater;
import android.widget.Button;
import android.widget.TextView;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.InputStream;

public class HexDiffActivity extends AppCompatActivity {

    private TextView tvDiffSummary, tvDiffStats;
    private RecyclerView recyclerDiff;
    private Button btnLoadFile1, btnLoadFile2;

    private byte[] dataA = null;
    private byte[] dataB = null;
    private String nameA = "Archivo A";
    private String nameB = "Archivo B";

    private final ActivityResultLauncher<Intent> file1Launcher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            result -> {
                if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                    Uri uri = result.getData().getData();
                    if (uri != null) {
                        try {
                            dataA = readUriToBytes(uri);
                            nameA = getFileName(uri);
                            btnLoadFile1.setText("A: " + nameA);
                            tryCompare();
                        } catch (Exception e) {
                            tvDiffSummary.setText("Error cargando Archivo A: " + e.getMessage());
                        }
                    }
                }
            });

    private final ActivityResultLauncher<Intent> file2Launcher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            result -> {
                if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                    Uri uri = result.getData().getData();
                    if (uri != null) {
                        try {
                            dataB = readUriToBytes(uri);
                            nameB = getFileName(uri);
                            btnLoadFile2.setText("B: " + nameB);
                            tryCompare();
                        } catch (Exception e) {
                            tvDiffSummary.setText("Error cargando Archivo B: " + e.getMessage());
                        }
                    }
                }
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_hex_diff);

        if (getSupportActionBar() != null) {
            getSupportActionBar().setTitle("Comparar HEX (Diff)");
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }

        tvDiffSummary = findViewById(R.id.tvDiffSummary);
        tvDiffStats = findViewById(R.id.tvDiffStats);
        recyclerDiff = findViewById(R.id.recyclerDiff);
        recyclerDiff.setLayoutManager(new LinearLayoutManager(this));
        btnLoadFile1 = findViewById(R.id.btnLoadFile1);
        btnLoadFile2 = findViewById(R.id.btnLoadFile2);

        // Pre-cargar bios.bin automáticamente si existe
        File biosFile = new File(getFilesDir(), "bios.bin");
        if (biosFile.exists()) {
            try {
                dataA = java.nio.file.Files.readAllBytes(biosFile.toPath());
                nameA = "bios.bin (interno)";
                btnLoadFile1.setText("A: " + nameA);
            } catch (Exception ignored) {
            }
        }

        btnLoadFile1.setOnClickListener(v -> {
            Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
            intent.addCategory(Intent.CATEGORY_OPENABLE);
            intent.setType("*/*");
            file1Launcher.launch(intent);
        });

        btnLoadFile2.setOnClickListener(v -> {
            Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
            intent.addCategory(Intent.CATEGORY_OPENABLE);
            intent.setType("*/*");
            file2Launcher.launch(intent);
        });
    }

    private void tryCompare() {
        if (dataA == null || dataB == null) {
            tvDiffSummary.setText("Carga ambos archivos para comparar.");
            return;
        }

        int maxLen = Math.max(dataA.length, dataB.length);
        int minLen = Math.min(dataA.length, dataB.length);
        int diffCount = 0;

        for (int i = 0; i < maxLen; i++) {
            byte a = i < dataA.length ? dataA[i] : (byte) 0xFF;
            byte b = i < dataB.length ? dataB[i] : (byte) 0xFF;
            if (a != b)
                diffCount++;
        }

        tvDiffSummary.setText(String.format("A: %s (%d bytes) vs B: %s (%d bytes)",
                nameA, dataA.length, nameB, dataB.length));

        tvDiffStats.setVisibility(View.VISIBLE);
        if (diffCount == 0) {
            tvDiffStats.setText("✅ Archivos idénticos — 0 diferencias.");
            tvDiffStats.setTextColor(0xFF4CAF50);
        } else {
            double pct = (diffCount * 100.0) / maxLen;
            tvDiffStats.setText(String.format("⚠ %d bytes diferentes (%.1f%%) de %d bytes totales.",
                    diffCount, pct, maxLen));
            tvDiffStats.setTextColor(0xFFFF5252);
        }

        recyclerDiff.setAdapter(new DiffAdapter(dataA, dataB));
    }

    private byte[] readUriToBytes(Uri uri) throws Exception {
        try (InputStream is = getContentResolver().openInputStream(uri)) {
            if (is == null)
                throw new IllegalStateException("No se pudo abrir el archivo.");
            
            android.database.Cursor cursor = getContentResolver().query(uri, null, null, null, null);
            if (cursor != null && cursor.moveToFirst()) {
                int sizeIdx = cursor.getColumnIndex(android.provider.OpenableColumns.SIZE);
                if (sizeIdx >= 0) {
                    long size = cursor.getLong(sizeIdx);
                    if (size > 16 * 1024 * 1024) {
                        cursor.close();
                        throw new IllegalArgumentException("El archivo es demasiado grande (>16MB). Imposible comparar en memoria.");
                    }
                }
                cursor.close();
            }

            ByteArrayOutputStream buffer = new ByteArrayOutputStream();
            byte[] buf = new byte[16384];
            int nRead;
            long totalRead = 0;
            while ((nRead = is.read(buf)) != -1) {
                totalRead += nRead;
                if (totalRead > 16 * 1024 * 1024) {
                    throw new IllegalArgumentException("El archivo excede el límite de 16MB. Imposible comparar en memoria.");
                }
                buffer.write(buf, 0, nRead);
            }
            return buffer.toByteArray();
        }
    }

    private String getFileName(Uri uri) {
        String name = "archivo";
        try {
            android.database.Cursor cursor = getContentResolver().query(uri, null, null, null, null);
            if (cursor != null && cursor.moveToFirst()) {
                int idx = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME);
                if (idx >= 0)
                    name = cursor.getString(idx);
                cursor.close();
            }
        } catch (Exception ignored) {
        }
        return name;
    }

    @Override
    public boolean onSupportNavigateUp() {
        getOnBackPressedDispatcher().onBackPressed();
        return true;
    }

    // Adapter que muestra filas HEX con diferencias resaltadas en rojo
    static class DiffAdapter extends RecyclerView.Adapter<DiffAdapter.DiffViewHolder> {
        private final byte[] dataA;
        private final byte[] dataB;
        private final int maxLen;

        DiffAdapter(byte[] dataA, byte[] dataB) {
            this.dataA = dataA;
            this.dataB = dataB;
            this.maxLen = Math.max(dataA.length, dataB.length);
        }

        @NonNull
        @Override
        public DiffViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_hex_row, parent, false);
            return new DiffViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull DiffViewHolder holder, int position) {
            int rowStart = position * 16;
            int length = Math.min(16, maxLen - rowStart);

            StringBuilder hexBuilder = new StringBuilder();
            StringBuilder asciiBuilder = new StringBuilder();
            boolean rowHasDiff = false;

            for (int i = 0; i < 16; i++) {
                if (i < length) {
                    int idx = rowStart + i;
                    byte a = idx < dataA.length ? dataA[idx] : (byte) 0xFF;
                    byte b = idx < dataB.length ? dataB[idx] : (byte) 0xFF;
                    boolean isDiff = (a != b);
                    if (isDiff)
                        rowHasDiff = true;

                    // Muestra: A→B si difieren, solo A si iguales
                    if (isDiff) {
                        hexBuilder.append(String.format("%02X→%02X ", a & 0xFF, b & 0xFF));
                    } else {
                        hexBuilder.append(String.format("%02X     ", a & 0xFF));
                    }

                    if (a >= 32 && a <= 126) {
                        asciiBuilder.append((char) a);
                    } else {
                        asciiBuilder.append(".");
                    }
                } else {
                    hexBuilder.append("       ");
                    asciiBuilder.append(" ");
                }
            }

            holder.tvAddress.setText(String.format("%08X", rowStart));
            holder.tvHex.setText(hexBuilder.toString());
            holder.tvAscii.setText(asciiBuilder.toString());

            // Resaltar filas con diferencias
            if (rowHasDiff) {
                holder.tvHex.setTextColor(0xFFFF5252); // Rojo
                holder.tvAddress.setTextColor(0xFFFF9800); // Naranja
            } else {
                holder.tvHex.setTextColor(0xFFE0E0E0); // Normal
                holder.tvAddress.setTextColor(0xFF2196F3); // Azul normal
            }
        }

        @Override
        public int getItemCount() {
            return (int) Math.ceil((double) maxLen / 16.0);
        }

        static class DiffViewHolder extends RecyclerView.ViewHolder {
            TextView tvAddress, tvHex, tvAscii;

            DiffViewHolder(View itemView) {
                super(itemView);
                tvAddress = itemView.findViewById(R.id.tvAddress);
                tvHex = itemView.findViewById(R.id.tvHex);
                tvAscii = itemView.findViewById(R.id.tvAscii);
            }
        }
    }
}
