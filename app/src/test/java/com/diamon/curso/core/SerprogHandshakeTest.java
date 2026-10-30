package com.diamon.curso.core;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class SerprogHandshakeTest {

    private static byte[] helloResponse(int version, int caps) {
        byte[] f = new byte[SerprogHandshake.HELLO_FRAME_LEN];
        f[0] = (byte) SerprogHandshake.S_ACK;
        f[1] = SerprogHandshake.HELLO_MAGIC[0];
        f[2] = SerprogHandshake.HELLO_MAGIC[1];
        f[3] = SerprogHandshake.HELLO_MAGIC[2];
        f[4] = SerprogHandshake.HELLO_MAGIC[3];
        f[5] = (byte) version;
        f[6] = (byte) caps;
        f[7] = (byte) (f[1] ^ f[2] ^ f[3] ^ f[4] ^ f[5] ^ f[6]);
        return f;
    }

    @Test
    public void detectsValidHelloFrame() {
        byte[] frame = helloResponse(2, 0x07);
        SerprogHandshake.HelloInfo[] out = new SerprogHandshake.HelloInfo[1];

        int idx = SerprogHandshake.findHelloFrame(frame, frame.length, out);

        assertEquals(0, idx);
        assertNotNull(out[0]);
        assertEquals(2, out[0].version);
        assertEquals(0x07, out[0].capabilities);
    }

    @Test
    public void findsFrameAfterStartupBeaconAndGarbage() {
        byte[] frame = helloResponse(3, 0x01);
        byte[] buf = new byte[2 + 5 + frame.length];
        buf[0] = (byte) 0xAA;
        buf[1] = (byte) 0x55;
        for (int i = 0; i < 5; i++) buf[2 + i] = (byte) 0xFF;
        System.arraycopy(frame, 0, buf, 7, frame.length);

        SerprogHandshake.HelloInfo[] out = new SerprogHandshake.HelloInfo[1];
        int idx = SerprogHandshake.findHelloFrame(buf, buf.length, out);

        assertEquals(7, idx);
        assertNotNull(out[0]);
        assertEquals(3, out[0].version);
    }

    @Test
    public void rejectsFrameWithBadChecksum() {
        byte[] frame = helloResponse(2, 0x07);
        frame[7] ^= 0x01;

        SerprogHandshake.HelloInfo[] out = new SerprogHandshake.HelloInfo[1];
        assertEquals(-1, SerprogHandshake.findHelloFrame(frame, frame.length, out));
        assertNull(out[0]);
    }

    @Test
    public void doesNotTreatAa55PayloadAsHello() {
        byte[] payload = new byte[] {
                (byte) 0xAA, 0x55, (byte) 0xAA, 0x55, 0x00, 0x11, 0x22, 0x33,
                0x44, 0x55, 0x66, 0x77, (byte) 0xAA, 0x55
        };
        SerprogHandshake.HelloInfo[] out = new SerprogHandshake.HelloInfo[1];

        assertEquals(-1, SerprogHandshake.findHelloFrame(payload, payload.length, out));
        assertNull(out[0]);
    }

    @Test
    public void detectsNakFromUnsupportedHello() {
        byte[] response = new byte[] {(byte) SerprogHandshake.S_NAK};
        assertTrue(SerprogHandshake.containsNak(response, response.length));
        assertFalse(SerprogHandshake.containsNak(new byte[] {0x06, 0x53}, 2));
    }
}
