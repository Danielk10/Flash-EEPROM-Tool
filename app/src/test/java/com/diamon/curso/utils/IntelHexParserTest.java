package com.diamon.curso.utils;

import org.junit.Test;
import static org.junit.Assert.*;



import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

/**
 * Tests unitarios para IntelHexParser.
 * Validan el parseo de archivos Intel HEX, el relleno de gaps en bloques
 * pequeños (fix OOM #87, #88, #89), y la detección de formatos inválidos.
 */
public class IntelHexParserTest {

    // ========================================================================
    // Helper: convierte un String Intel HEX a byte[] para pasarlo al parser
    // ========================================================================
    private static byte[] hex(String content) {
        return content.getBytes(StandardCharsets.US_ASCII);
    }

    // Genera un checksum válido para una línea Intel HEX (sin el ':' inicial)
    private static String makeRecord(int byteCount, int address, int type, byte[] data) {
        StringBuilder sb = new StringBuilder(":");
        sb.append(String.format("%02X", byteCount));
        sb.append(String.format("%04X", address));
        sb.append(String.format("%02X", type));
        int sum = byteCount + (address >> 8) + (address & 0xFF) + type;
        for (byte b : data) {
            sb.append(String.format("%02X", b & 0xFF));
            sum += (b & 0xFF);
        }
        sb.append(String.format("%02X", (-(sum & 0xFF)) & 0xFF));
        return sb.toString();
    }

    private static final String EOF_RECORD = ":00000001FF";

    // ========================================================================
    // 1. Tests básicos de parseo
    // ========================================================================

    @Test
    public void testParseSimpleDataRecord() {
        // Archivo HEX con 4 bytes: 0x01, 0x02, 0x03, 0x04 en dirección 0x0000
        String record = makeRecord(4, 0x0000, 0x00,
                new byte[]{0x01, 0x02, 0x03, 0x04});
        byte[] result = IntelHexParser.parse(hex(record + "\n" + EOF_RECORD + "\n"));
        assertArrayEquals(new byte[]{0x01, 0x02, 0x03, 0x04}, result);
    }

    @Test
    public void testParseMultipleDataRecords() {
        // Dos registros consecutivos de 2 bytes cada uno
        String r1 = makeRecord(2, 0x0000, 0x00, new byte[]{(byte) 0xAA, (byte) 0xBB});
        String r2 = makeRecord(2, 0x0002, 0x00, new byte[]{(byte) 0xCC, (byte) 0xDD});
        byte[] result = IntelHexParser.parse(hex(r1 + "\n" + r2 + "\n" + EOF_RECORD + "\n"));
        assertArrayEquals(new byte[]{(byte) 0xAA, (byte) 0xBB, (byte) 0xCC, (byte) 0xDD}, result);
    }

    @Test
    public void testParseEmptyFile() {
        // Solo EOF, resultado vacío
        byte[] result = IntelHexParser.parse(hex(EOF_RECORD + "\n"));
        assertEquals(0, result.length);
    }

    @Test
    public void testParseWithBlankLinesAndCR() {
        // Líneas en blanco y retornos de carro (\r\n) deben ignorarse
        String r1 = makeRecord(2, 0x0000, 0x00, new byte[]{0x55, 0x66});
        String content = "\r\n" + r1 + "\r\n\r\n" + EOF_RECORD + "\r\n";
        byte[] result = IntelHexParser.parse(hex(content));
        assertArrayEquals(new byte[]{0x55, 0x66}, result);
    }

    // ========================================================================
    // 2. Tests de Extended Address (tipos 02 y 04)
    // ========================================================================

    @Test
    public void testExtendedLinearAddress_Type04() {
        // Tipo 04: Extended Linear Address → upperAddress = 0x0001_0000
        String extAddr = makeRecord(2, 0x0000, 0x04, new byte[]{0x00, 0x01});
        String data = makeRecord(2, 0x0000, 0x00, new byte[]{(byte) 0xFE, (byte) 0xED});
        byte[] result = IntelHexParser.parse(hex(extAddr + "\n" + data + "\n" + EOF_RECORD + "\n"));
        // Datos en dirección absoluta 0x10000, pero output comienza desde la primera data
        assertArrayEquals(new byte[]{(byte) 0xFE, (byte) 0xED}, result);
    }

    @Test
    public void testExtendedSegmentAddress_Type02() {
        // Tipo 02: Extended Segment Address → upperAddress = valor << 4
        String extAddr = makeRecord(2, 0x0000, 0x02, new byte[]{0x10, 0x00});
        String data = makeRecord(1, 0x0000, 0x00, new byte[]{(byte) 0xAB});
        byte[] result = IntelHexParser.parse(hex(extAddr + "\n" + data + "\n" + EOF_RECORD + "\n"));
        assertArrayEquals(new byte[]{(byte) 0xAB}, result);
    }

    // ========================================================================
    // 3. Tests de relleno de gaps (FIX #3 - bloques de 4 KB)
    // ========================================================================

    @Test
    public void testSmallGapFillIsAllZeros() {
        // Gap de 8 bytes entre registros: debe rellenarse con 0x00
        String r1 = makeRecord(2, 0x0000, 0x00, new byte[]{0x11, 0x22});
        String r2 = makeRecord(2, 0x000A, 0x00, new byte[]{0x33, 0x44});
        byte[] result = IntelHexParser.parse(hex(r1 + "\n" + r2 + "\n" + EOF_RECORD + "\n"));

        assertEquals(12, result.length); // 2 + 8 gap + 2
        assertEquals(0x11, result[0] & 0xFF);
        assertEquals(0x22, result[1] & 0xFF);
        // Gap bytes [2..9] must all be zero
        for (int i = 2; i < 10; i++) {
            assertEquals("Gap byte at index " + i + " should be 0x00", 0, result[i]);
        }
        assertEquals(0x33, result[10] & 0xFF);
        assertEquals(0x44, result[11] & 0xFF);
    }

    @Test
    public void testLargeGapFillExceedingChunkSize() {
        // Gap de 10000 bytes (> 4096 chunk size) → debe rellenarse correctamente
        // con múltiples bloques, NO con una sola asignación new byte[10000]
        String r1 = makeRecord(1, 0x0000, 0x00, new byte[]{(byte) 0xAA});
        // Siguiente registro en dirección 0x2711 (decimal 10001) → gap de 10000 bytes
        String r2 = makeRecord(1, 0x2711, 0x00, new byte[]{(byte) 0xBB});
        byte[] result = IntelHexParser.parse(hex(r1 + "\n" + r2 + "\n" + EOF_RECORD + "\n"));

        assertEquals(10002, result.length); // 1 + 10000 gap + 1
        assertEquals((byte) 0xAA, result[0]);
        // Todos los bytes del gap deben ser 0x00
        for (int i = 1; i <= 10000; i++) {
            assertEquals("Gap byte at index " + i + " should be 0x00", 0, result[i]);
        }
        assertEquals((byte) 0xBB, result[10001]);
    }

    @Test
    public void testGapFillExactlyOneChunk() {
        // Gap de exactamente 4096 bytes (= GAP_FILL_CHUNK_SIZE)
        String r1 = makeRecord(1, 0x0000, 0x00, new byte[]{0x01});
        // dirección 0x1001 = 4097 → gap = 4096
        String r2 = makeRecord(1, 0x1001, 0x00, new byte[]{0x02});
        byte[] result = IntelHexParser.parse(hex(r1 + "\n" + r2 + "\n" + EOF_RECORD + "\n"));

        assertEquals(4098, result.length); // 1 + 4096 gap + 1
        assertEquals(0x01, result[0]);
        for (int i = 1; i <= 4096; i++) {
            assertEquals(0, result[i]);
        }
        assertEquals(0x02, result[4097]);
    }

    @Test
    public void testGapFillMultipleChunksNotAligned() {
        // Gap de 5000 bytes (4096 + 904) → dos iteraciones del bucle
        String r1 = makeRecord(1, 0x0000, 0x00, new byte[]{(byte) 0xFF});
        // dirección 0x1389 = 5001 → gap = 5000
        String r2 = makeRecord(1, 0x1389, 0x00, new byte[]{(byte) 0xEE});
        byte[] result = IntelHexParser.parse(hex(r1 + "\n" + r2 + "\n" + EOF_RECORD + "\n"));

        assertEquals(5002, result.length);
        assertEquals((byte) 0xFF, result[0]);
        for (int i = 1; i <= 5000; i++) {
            assertEquals(0, result[i]);
        }
        assertEquals((byte) 0xEE, result[5001]);
    }

    @Test
    public void testNoGapNoFill() {
        // Registros consecutivos sin gap → no se necesita relleno
        String r1 = makeRecord(4, 0x0000, 0x00, new byte[]{0x01, 0x02, 0x03, 0x04});
        String r2 = makeRecord(4, 0x0004, 0x00, new byte[]{0x05, 0x06, 0x07, 0x08});
        byte[] result = IntelHexParser.parse(hex(r1 + "\n" + r2 + "\n" + EOF_RECORD + "\n"));

        assertEquals(8, result.length);
        assertArrayEquals(new byte[]{0x01, 0x02, 0x03, 0x04, 0x05, 0x06, 0x07, 0x08}, result);
    }

    // ========================================================================
    // 4. Tests de fillGap() directo (package-visible)
    // ========================================================================

    @Test
    public void testFillGapDirectSmall() {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        IntelHexParser.fillGap(out, 100);
        byte[] result = out.toByteArray();
        assertEquals(100, result.length);
        for (byte b : result) {
            assertEquals(0, b);
        }
    }

    @Test
    public void testFillGapDirectLarge() {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        int gapSize = 4096 * 3 + 500; // 12788 bytes
        IntelHexParser.fillGap(out, gapSize);
        byte[] result = out.toByteArray();
        assertEquals(gapSize, result.length);
        for (byte b : result) {
            assertEquals(0, b);
        }
    }

    @Test
    public void testFillGapZero() {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        IntelHexParser.fillGap(out, 0);
        assertEquals(0, out.toByteArray().length);
    }

    @Test
    public void testFillGapExactChunkSize() {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        IntelHexParser.fillGap(out, IntelHexParser.GAP_FILL_CHUNK_SIZE);
        assertEquals(IntelHexParser.GAP_FILL_CHUNK_SIZE, out.toByteArray().length);
    }

    // ========================================================================
    // 5. Tests de validación de errores
    // ========================================================================

    @Test(expected = IllegalArgumentException.class)
    public void testInvalidLineNoColon() {
        IntelHexParser.parse(hex("ABCDEF01234567890\n"));
    }

    @Test(expected = IllegalArgumentException.class)
    public void testInvalidLineTooShort() {
        IntelHexParser.parse(hex(":0000\n"));
    }

    @Test(expected = IllegalArgumentException.class)
    public void testInvalidChecksum() {
        // Registro con checksum incorrecto (último byte alterado)
        IntelHexParser.parse(hex(":0100000001FD\n")); // checksum debería ser FE, no FD
    }

    @Test(expected = IllegalArgumentException.class)
    public void testDecreasingAddressThrows() {
        // Segundo registro tiene dirección menor que el primero → error
        String r1 = makeRecord(2, 0x0010, 0x00, new byte[]{0x01, 0x02});
        String r2 = makeRecord(2, 0x0008, 0x00, new byte[]{0x03, 0x04});
        IntelHexParser.parse(hex(r1 + "\n" + r2 + "\n" + EOF_RECORD + "\n"));
    }

    @Test(expected = IllegalArgumentException.class)
    public void testInvalidType04ByteCount() {
        // Tipo 04 con byteCount != 2
        String badType04 = makeRecord(3, 0x0000, 0x04, new byte[]{0x00, 0x01, 0x00});
        IntelHexParser.parse(hex(badType04 + "\n" + EOF_RECORD + "\n"));
    }

    @Test(expected = IllegalArgumentException.class)
    public void testInvalidType02ByteCount() {
        // Tipo 02 con byteCount != 2
        String badType02 = makeRecord(1, 0x0000, 0x02, new byte[]{0x10});
        IntelHexParser.parse(hex(badType02 + "\n" + EOF_RECORD + "\n"));
    }

    // ========================================================================
    // 6. Tests de parseHexByte y parseHexWord (package-visible)
    // ========================================================================

    @Test
    public void testParseHexByte() {
        assertEquals(0x00, IntelHexParser.parseHexByte(":00", 1));
        assertEquals(0xFF, IntelHexParser.parseHexByte(":FF", 1));
        assertEquals(0xA5, IntelHexParser.parseHexByte(":A5", 1));
    }

    @Test
    public void testParseHexWord() {
        assertEquals(0x0000, IntelHexParser.parseHexWord(":0000", 1));
        assertEquals(0xFFFF, IntelHexParser.parseHexWord(":FFFF", 1));
        assertEquals(0x1234, IntelHexParser.parseHexWord(":1234", 1));
    }

    // ========================================================================
    // 7. Test de gap con Extended Address (escenario real de firmware)
    // ========================================================================

    @Test
    public void testGapAcrossExtendedAddressSegments() {
        // Simula un firmware real: datos en segmento 0x0000, luego salto a segmento 0x0001
        // Segmento 0: datos en 0x0000
        String data1 = makeRecord(2, 0x0000, 0x00, new byte[]{(byte) 0xDE, (byte) 0xAD});
        // Extended Linear Address → segmento 0x0001 (dirección base 0x10000)
        String extAddr = makeRecord(2, 0x0000, 0x04, new byte[]{0x00, 0x01});
        // Datos en 0x10000
        String data2 = makeRecord(2, 0x0000, 0x00, new byte[]{(byte) 0xBE, (byte) 0xEF});

        byte[] result = IntelHexParser.parse(hex(
                data1 + "\n" + extAddr + "\n" + data2 + "\n" + EOF_RECORD + "\n"));

        // data1 (2 bytes) están ya en el output, extAddr resetea expectedAddress a -1,
        // luego data2 (2 bytes) se escribe sin gap (expectedAddress era -1 → se reinicia).
        // Total: 4 bytes en el output stream.
        assertEquals(4, result.length);
        assertEquals((byte) 0xDE, result[0]);
        assertEquals((byte) 0xAD, result[1]);
        assertEquals((byte) 0xBE, result[2]);
        assertEquals((byte) 0xEF, result[3]);
    }
}
