package com.diamon.curso.core;

/**
 * Utilidades puras del handshake serprog, sin dependencias de Android.
 *
 * El objetivo es que el control sea siempre <b>solicitado</b>: la app envía
 * HELLO (o NOP como fallback) y valida una respuesta de longitud fija. Nunca
 * se escanea el flujo de datos SPI en busca de una secuencia mágica, por lo
 * que un dato leído que contenga {@code AA 55} no puede confundirse con un
 * mensaje de control.
 *
 * Respuesta HELLO (8 bytes):
 * <pre>
 *   ACK  'S'  'P'  'R'  'G'  version  caps  xor
 *   0x06 0x53 0x50 0x52 0x47  ...
 * </pre>
 * El XOR cubre los 6 bytes anteriores a él (magic + version + caps).
 */
public final class SerprogHandshake {

    public static final int CMD_NOP = 0x00;
    public static final int CMD_HELLO = 0xF0;

    public static final int S_ACK = 0x06;
    public static final int S_NAK = 0x15;

    public static final byte[] HELLO_MAGIC = {'S', 'P', 'R', 'G'};
    public static final int HELLO_FRAME_LEN = 8;

    /** Información devuelta por el firmware en el handshake HELLO. */
    public static final class HelloInfo {
        public final int version;
        public final int capabilities;

        public HelloInfo(int version, int capabilities) {
            this.version = version;
            this.capabilities = capabilities;
        }
    }

    private SerprogHandshake() {
    }

    public static byte[] helloRequest() {
        return new byte[] {(byte) CMD_HELLO};
    }

    public static byte[] nopRequest() {
        return new byte[] {(byte) CMD_NOP};
    }

    /**
     * Busca una respuesta HELLO válida dentro del buffer.
     *
     * Se permite que haya bytes previos (beacon de arranque, ruido del
     * bootloader, etc.); la trama se localiza por el ACK y se valida completa.
     *
     * @param buf  buffer recibido
     * @param len  bytes válidos en {@code buf}
     * @param out  si no es null y tiene al menos un elemento, recibe la info
     * @return índice donde empieza la trama, o -1 si no hay ninguna válida
     */
    public static int findHelloFrame(byte[] buf, int len, HelloInfo[] out) {
        if (buf == null || len < HELLO_FRAME_LEN) return -1;
        final int max = Math.min(len, buf.length);
        for (int i = 0; i + HELLO_FRAME_LEN <= max; i++) {
            if ((buf[i] & 0xFF) != S_ACK) continue;
            if ((buf[i + 1] & 0xFF) != HELLO_MAGIC[0]) continue;
            if ((buf[i + 2] & 0xFF) != HELLO_MAGIC[1]) continue;
            if ((buf[i + 3] & 0xFF) != HELLO_MAGIC[2]) continue;
            if ((buf[i + 4] & 0xFF) != HELLO_MAGIC[3]) continue;

            final int version = buf[i + 5] & 0xFF;
            final int caps = buf[i + 6] & 0xFF;
            final int xor = buf[i + 7] & 0xFF;
            final int expected = (buf[i + 1] ^ buf[i + 2] ^ buf[i + 3]
                    ^ buf[i + 4] ^ version ^ caps) & 0xFF;
            if (xor != expected) continue;

            if (out != null && out.length > 0) {
                out[0] = new HelloInfo(version, caps);
            }
            return i;
        }
        return -1;
    }

    /** True si el buffer contiene un NAK (respuesta típica a un comando no soportado). */
    public static boolean containsNak(byte[] buf, int len) {
        if (buf == null) return false;
        final int max = Math.min(len, buf.length);
        for (int i = 0; i < max; i++) {
            if ((buf[i] & 0xFF) == S_NAK) return true;
        }
        return false;
    }
}
