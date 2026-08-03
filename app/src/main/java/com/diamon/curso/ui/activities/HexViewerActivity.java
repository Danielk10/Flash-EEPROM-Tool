package com.diamon.curso.ui.activities;

import com.diamon.curso.R;

import android.net.Uri;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

public class HexViewerActivity extends AppCompatActivity {

    private TextView tvHexSummary;
    private RecyclerView recyclerHex;
    private HexAdapter hexAdapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_hex_viewer);

        if (getSupportActionBar() != null) {
            getSupportActionBar().setTitle(R.string.str_visor_hexadecim);
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }

        tvHexSummary = findViewById(R.id.tvHexSummary);
        recyclerHex = findViewById(R.id.recyclerHex);
        recyclerHex.setLayoutManager(new LinearLayoutManager(this));

        loadDataFromIntent();
    }

    private void loadDataFromIntent() {
        Uri fileUri = getIntent().getData();
        String fileName = "bios.bin";

        // Leer el origen del archivo (importado vs leído del chip)
        String biosSource = getSharedPreferences("flashrom_prefs", MODE_PRIVATE)
                .getString("bios_source", null);

        byte[] data;
        try {
            if (fileUri != null) {
                data = readUriToBytes(fileUri);
                fileName = fileUri.getLastPathSegment();
                biosSource = "Archivo externo: " + fileName;
            } else {
                // Cargar el archivo rastreado (último leído o importado)
                String trackedFile = getSharedPreferences("flashrom_prefs", MODE_PRIVATE)
                        .getString("last_read_file", "bios.bin");
                File dataFile = new File(getFilesDir(), trackedFile);
                // Fallback a bios.bin si el archivo rastreado no existe
                if (!dataFile.exists()) {
                    dataFile = new File(getFilesDir(), "bios.bin");
                }
                if (!dataFile.exists()) {
                    tvHexSummary.setText(R.string.str_err_no_data_visualize);
                    return;
                }
                fileName = dataFile.getName();
                data = java.nio.file.Files.readAllBytes(dataFile.toPath());
            }

            if (fileName != null && fileName.toLowerCase().endsWith(".hex")) {
                parseIntelHex(data, biosSource);
            } else {
                displayBinary(data, fileName, biosSource);
            }

        } catch (Exception e) {
            tvHexSummary.setText(getString(R.string.str_err_load_data, e.getMessage()));
        }
    }

    private byte[] readUriToBytes(Uri uri) throws Exception {
        try (InputStream is = getContentResolver().openInputStream(uri)) {
            if (is == null)
                throw new IllegalStateException(getString(R.string.str_err_open_file));
            ByteArrayOutputStream buffer = new ByteArrayOutputStream();
            int nRead;
            byte[] data = new byte[16384];
            while ((nRead = is.read(data, 0, data.length)) != -1) {
                buffer.write(data, 0, nRead);
            }
            return buffer.toByteArray();
        }
    }

    private void displayBinary(byte[] data, String name, String biosSource) {
        String summary = getString(R.string.str_binary_summary, name, data.length);
        if (biosSource != null) {
            summary += getString(R.string.str_source_label, biosSource);
        }
        tvHexSummary.setText(summary);
        hexAdapter = new HexAdapter(data, 0);
        recyclerHex.setAdapter(hexAdapter);
    }

    private void parseIntelHex(byte[] hexData, String biosSource) {
        try {
            String content = new String(hexData);
            String[] lines = content.split("\\r?\\n");

            // Intel HEX puede tener Extended Address (Type 02/04) para direcciones > 64KB
            long minAddr = Long.MAX_VALUE;
            long maxAddr = 0;
            int upperAddress = 0;

            // Primera pasada: calcular rango incluyendo Extended Address
            for (String line : lines) {
                line = line.trim();
                if (!line.startsWith(":") || line.length() < 11)
                    continue;
                int byteCount = Integer.parseInt(line.substring(1, 3), 16);
                int address = Integer.parseInt(line.substring(3, 7), 16);
                int type = Integer.parseInt(line.substring(7, 9), 16);
                if (type == 0x00) { // Data record
                    long absolute = (long) upperAddress + address;
                    minAddr = Math.min(minAddr, absolute);
                    maxAddr = Math.max(maxAddr, absolute + byteCount);
                } else if (type == 0x04 && line.length() >= 15) { // Extended Linear Address
                    upperAddress = Integer.parseInt(line.substring(9, 13), 16) << 16;
                } else if (type == 0x02 && line.length() >= 15) { // Extended Segment Address
                    upperAddress = Integer.parseInt(line.substring(9, 13), 16) << 4;
                } else if (type == 0x01) { // EOF
                    break;
                }
            }

            if (minAddr == Long.MAX_VALUE) {
                tvHexSummary.setText(R.string.str_err_hex_no_records);
                return;
            }

            // Limitar el tamaño del buffer para evitar OOM
            long bufferSize = maxAddr - minAddr;
            if (bufferSize > 32L * 1024 * 1024) { // 32 MB máximo para visualización
                tvHexSummary.setText(getString(R.string.str_err_hex_too_large, String.valueOf(bufferSize / 1024 / 1024)));
                return;
            }

            byte[] binBuffer = new byte[(int) bufferSize];
            java.util.Arrays.fill(binBuffer, (byte) 0xFF);

            // Segunda pasada: llenar datos con Extended Address
            upperAddress = 0;
            for (String line : lines) {
                line = line.trim();
                if (!line.startsWith(":") || line.length() < 11)
                    continue;
                int byteCount = Integer.parseInt(line.substring(1, 3), 16);
                int address = Integer.parseInt(line.substring(3, 7), 16);
                int type = Integer.parseInt(line.substring(7, 9), 16);
                if (type == 0x00) {
                    long absolute = (long) upperAddress + address;
                    int offset = (int) (absolute - minAddr);
                    for (int i = 0; i < byteCount; i++) {
                        if (offset + i < binBuffer.length) {
                            binBuffer[offset + i] = (byte) Integer.parseInt(
                                     line.substring(9 + i * 2, 11 + i * 2), 16);
                        }
                    }
                } else if (type == 0x04 && line.length() >= 15) {
                    upperAddress = Integer.parseInt(line.substring(9, 13), 16) << 16;
                } else if (type == 0x02 && line.length() >= 15) {
                    upperAddress = Integer.parseInt(line.substring(9, 13), 16) << 4;
                } else if (type == 0x01) {
                    break;
                }
            }

            String summary = getString(R.string.str_hex_detected, (int) minAddr, (int) (maxAddr - 1));
            if (biosSource != null) {
                summary += getString(R.string.str_source_label, biosSource);
            }
            tvHexSummary.setText(summary);
            hexAdapter = new HexAdapter(binBuffer, (int) minAddr);
            recyclerHex.setAdapter(hexAdapter);

        } catch (Exception e) {
            tvHexSummary.setText(getString(R.string.str_err_parse_hex, e.getMessage()));
        }
    }

    @Override
    public boolean onSupportNavigateUp() {
        getOnBackPressedDispatcher().onBackPressed();
        return true;
    }

    static class HexAdapter extends RecyclerView.Adapter<HexAdapter.HexViewHolder> {
        private final byte[] data;
        private final int startAddress;

        public HexAdapter(byte[] data, int startAddress) {
            this.data = data;
            this.startAddress = startAddress;
        }

        @NonNull
        @Override
        public HexViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_hex_row, parent, false);
            return new HexViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull HexViewHolder holder, int position) {
            int rowStart = position * 16;
            int currentAddr = startAddress + rowStart;
            int length = Math.min(16, data.length - rowStart);

            StringBuilder hexBuilder = new StringBuilder(48);
            StringBuilder asciiBuilder = new StringBuilder(16);

            for (int i = 0; i < 16; i++) {
                if (i < length) {
                    byte b = data[rowStart + i];
                    hexBuilder.append(String.format("%02X ", b));
                    if (b >= 32 && b <= 126) {
                        asciiBuilder.append((char) b);
                    } else {
                        asciiBuilder.append(".");
                    }
                } else {
                    hexBuilder.append("   ");
                    asciiBuilder.append(" ");
                }
            }

            holder.tvAddress.setText(String.format("%08X", currentAddr));
            holder.tvHex.setText(hexBuilder.toString());
            holder.tvAscii.setText(asciiBuilder.toString());
        }

        @Override
        public int getItemCount() {
            return (int) Math.ceil((double) data.length / 16.0);
        }

        static class HexViewHolder extends RecyclerView.ViewHolder {
            TextView tvAddress, tvHex, tvAscii;

            HexViewHolder(View itemView) {
                super(itemView);
                tvAddress = itemView.findViewById(R.id.tvAddress);
                tvHex = itemView.findViewById(R.id.tvHex);
                tvAscii = itemView.findViewById(R.id.tvAscii);
            }
        }
    }
}
