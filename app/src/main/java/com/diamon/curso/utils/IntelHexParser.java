package com.diamon.curso.utils;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

/**
 * Parser de archivos en formato Intel HEX (.hex / .ihex).
 * Convierte registros Intel HEX a un array de bytes binario (ROM image).
 *
 * <p>Diseñado para ser eficiente en memoria:
 * <ul>
 *   <li>Usa BufferedReader para lectura línea-por-línea (streaming) en lugar
 *       de cargar todo el archivo en un String.</li>
 *   <li>Los saltos de dirección (address gaps) se rellenan con ceros en
 *       bloques pequeños de {@link #GAP_FILL_CHUNK_SIZE} bytes para evitar
 *       asignaciones masivas que causan OutOfMemoryError.</li>
 * </ul>
 */
public final class IntelHexParser {

    /** Tamaño del bloque para rellenar saltos de dirección (4 KB). */
    static final int GAP_FILL_CHUNK_SIZE = 4096;

    private IntelHexParser() {
        // Utility class
    }

    /**
     * Parsea un archivo Intel HEX representado como byte[] y devuelve
     * la imagen binaria resultante.
     *
     * @param source contenido del archivo Intel HEX en ASCII
     * @return imagen binaria
     * @throws IllegalArgumentException si el formato es inválido
     */
    public static byte[] parse(byte[] source) {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        int upperAddress = 0;
        int expectedAddress = -1;

        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(
                        new ByteArrayInputStream(source),
                        StandardCharsets.US_ASCII))) {

            String rawLine;
            while ((rawLine = reader.readLine()) != null) {
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
                        fillGap(output, absolute - expectedAddress);
                        expectedAddress = absolute;
                    }
                    if (absolute < expectedAddress) {
                        throw new IllegalArgumentException(
                                "Intel HEX desordenado: dirección decreciente no soportada.");
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
        } catch (IllegalArgumentException e) {
            throw e; // Re-throw parse errors as-is
        } catch (IOException e) {
            throw new IllegalArgumentException("Error leyendo Intel HEX: " + e.getMessage());
        }
        return output.toByteArray();
    }

    /**
     * Rellena un salto de dirección (gap) con ceros, escribiendo en bloques
     * pequeños de {@link #GAP_FILL_CHUNK_SIZE} para evitar OOM.
     */
    static void fillGap(ByteArrayOutputStream output, int gapSize) {
        byte[] zeroBuf = new byte[Math.min(gapSize, GAP_FILL_CHUNK_SIZE)];
        int remaining = gapSize;
        while (remaining > 0) {
            int toWrite = Math.min(remaining, zeroBuf.length);
            output.write(zeroBuf, 0, toWrite);
            remaining -= toWrite;
        }
    }

    static int parseHexByte(String line, int idx) {
        return Integer.parseInt(line.substring(idx, idx + 2), 16);
    }

    static int parseHexWord(String line, int idx) {
        return Integer.parseInt(line.substring(idx, idx + 4), 16);
    }
}
