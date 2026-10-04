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
    private HexDataSource currentDataSource;

    public interface HexDataSource extends java.io.Closeable {
        long size();
        int read(long offset, byte[] dest, int destOffset, int length);
    }

    public static class MappedFileSource implements HexDataSource {
        private final java.io.RandomAccessFile raf;
        private final java.nio.channels.FileChannel channel;
        private final java.nio.MappedByteBuffer mappedBuffer;
        private final long size;

        public MappedFileSource(File file) throws Exception {
            this.raf = new java.io.RandomAccessFile(file, "r");
            this.channel = raf.getChannel();
            this.size = channel.size();
            this.mappedBuffer = channel.map(java.nio.channels.FileChannel.MapMode.READ_ONLY, 0, size);
        }

        @Override
        public long size() {
            return size;
        }

        @Override
        public int read(long offset, byte[] dest, int destOffset, int length) {
            if (offset >= size) return 0;
            int toRead = (int) Math.min((long) length, size - offset);
            synchronized (mappedBuffer) {
                mappedBuffer.position((int) offset);
                mappedBuffer.get(dest, destOffset, toRead);
            }
            return toRead;
        }

        @Override
        public void close() {
            try {
                channel.close();
            } catch (Exception ignored) {}
            try {
                raf.close();
            } catch (Exception ignored) {}
        }
    }

    public static class ByteArraySource implements HexDataSource {
        private final byte[] data;

        public ByteArraySource(byte[] data) {
            this.data = data != null ? data : new byte[0];
        }

        @Override
        public long size() {
            return data.length;
        }

        @Override
        public int read(long offset, byte[] dest, int destOffset, int length) {
            if (offset >= data.length) return 0;
            int toRead = (int) Math.min((long) length, data.length - offset);
            System.arraycopy(data, (int) offset, dest, destOffset, toRead);
            return toRead;
        }

        @Override
        public void close() {}
    }

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

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (currentDataSource != null) {
            try {
                currentDataSource.close();
            } catch (Exception ignored) {}
            currentDataSource = null;
        }
    }

    private void loadDataFromIntent() {
        Uri fileUri = getIntent().getData();
        String fileName = "bios.bin";

        // Leer el origen del archivo (importado vs leído del chip)
        String biosSource = getSharedPreferences("flashrom_prefs", MODE_PRIVATE)
                .getString("bios_source", null);

        try {
            if (fileUri != null) {
                fileName = fileUri.getLastPathSegment();
                biosSource = getString(R.string.str_external_file, fileName);
                byte[] data = readUriToBytes(fileUri);

                if (fileName != null && fileName.toLowerCase().endsWith(".hex")) {
                    parseIntelHex(data, biosSource);
                } else {
                    displayBinary(new ByteArraySource(data), fileName, biosSource);
                }
            } else {
                // Cargar el archivo rastreado (último leído o importado)
                String trackedFile = getSharedPreferences("flashrom_prefs", MODE_PRIVATE)
                        .getString("last_read_file", "bios.bin");
                File dataFile = new File(getFilesDir(), trackedFile);
                if (!dataFile.exists()) {
                    dataFile = new File(getFilesDir(), "bios.bin");
                }
                if (!dataFile.exists()) {
                    tvHexSummary.setText(R.string.str_err_no_data_visualize);
                    return;
                }
                fileName = dataFile.getName();

                if (fileName.toLowerCase().endsWith(".hex")) {
                    byte[] data = java.nio.file.Files.readAllBytes(dataFile.toPath());
                    parseIntelHex(data, biosSource);
                } else {
                    // Mapeo virtual directo con mmap (0 ms, 0 MB heap)
                    MappedFileSource mmapSource = new MappedFileSource(dataFile);
                    displayBinary(mmapSource, fileName, biosSource);
                }
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

    private void displayBinary(HexDataSource source, String name, String biosSource) {
        if (currentDataSource != null && currentDataSource != source) {
            try {
                currentDataSource.close();
            } catch (Exception ignored) {}
        }
        currentDataSource = source;

        String summary = getString(R.string.str_binary_summary, name, source.size());
        if (biosSource != null) {
            summary += getString(R.string.str_source_label, biosSource);
        }
        tvHexSummary.setText(summary);
        hexAdapter = new HexAdapter(source, 0);
        recyclerHex.setAdapter(hexAdapter);
    }

    private void parseIntelHex(byte[] hexData, String biosSource) {
        try {
            String content = new String(hexData);
            String[] lines = content.split("\\r?\\n");

            long minAddr = Long.MAX_VALUE;
            long maxAddr = 0;
            int upperAddress = 0;

            for (String line : lines) {
                line = line.trim();
                if (!line.startsWith(":") || line.length() < 11)
                    continue;
                int byteCount = Integer.parseInt(line.substring(1, 3), 16);
                int address = Integer.parseInt(line.substring(3, 7), 16);
                int type = Integer.parseInt(line.substring(7, 9), 16);
                if (type == 0x00) {
                    long absolute = (long) upperAddress + address;
                    minAddr = Math.min(minAddr, absolute);
                    maxAddr = Math.max(maxAddr, absolute + byteCount);
                } else if (type == 0x04 && line.length() >= 15) {
                    upperAddress = Integer.parseInt(line.substring(9, 13), 16) << 16;
                } else if (type == 0x02 && line.length() >= 15) {
                    upperAddress = Integer.parseInt(line.substring(9, 13), 16) << 4;
                } else if (type == 0x01) {
                    break;
                }
            }

            if (minAddr == Long.MAX_VALUE) {
                tvHexSummary.setText(R.string.str_err_hex_no_records);
                return;
            }

            long bufferSize = maxAddr - minAddr;
            if (bufferSize > 32L * 1024 * 1024) {
                tvHexSummary.setText(getString(R.string.str_err_hex_too_large, String.valueOf(bufferSize / 1024 / 1024)));
                return;
            }

            byte[] binBuffer = new byte[(int) bufferSize];
            java.util.Arrays.fill(binBuffer, (byte) 0xFF);

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
            ByteArraySource hexSource = new ByteArraySource(binBuffer);
            if (currentDataSource != null) {
                try { currentDataSource.close(); } catch (Exception ignored) {}
            }
            currentDataSource = hexSource;
            hexAdapter = new HexAdapter(hexSource, (int) minAddr);
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
        private final HexDataSource dataSource;
        private final long startAddress;

        public HexAdapter(HexDataSource dataSource, long startAddress) {
            this.dataSource = dataSource;
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
            long rowStart = (long) position * 16;
            long currentAddr = startAddress + rowStart;
            byte[] rowData = new byte[16];
            int length = dataSource.read(rowStart, rowData, 0, 16);

            StringBuilder hexBuilder = new StringBuilder(48);
            StringBuilder asciiBuilder = new StringBuilder(16);

            for (int i = 0; i < 16; i++) {
                if (i < length) {
                    byte b = rowData[i];
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
            return (int) Math.ceil((double) dataSource.size() / 16.0);
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
