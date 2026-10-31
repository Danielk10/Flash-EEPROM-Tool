package com.diamon.curso;

import org.junit.Test;
import static org.junit.Assert.*;

import com.diamon.curso.core.UsbController;

public class ExampleUnitTest {

    @Test
    public void testNeedsPtyBridge() {
        // Programadores seriales que requieren puente PTY
        assertTrue(UsbController.needsPtyBridge("serprog"));
        assertTrue(UsbController.needsPtyBridge("buspirate_spi"));
        assertTrue(UsbController.needsPtyBridge("spidriver"));

        // Programadores por USB directo o emuladores (no requieren PTY)
        assertFalse(UsbController.needsPtyBridge("ch341a_spi"));
        assertFalse(UsbController.needsPtyBridge("ch347_spi"));
        assertFalse(UsbController.needsPtyBridge("ft2232_spi"));
        assertFalse(UsbController.needsPtyBridge("ft2232_spi:type=232H"));
        assertFalse(UsbController.needsPtyBridge("dummy"));
        assertFalse(UsbController.needsPtyBridge(""));
        assertFalse(UsbController.needsPtyBridge(null));
    }

    @Test
    public void testUsbAutoMapConfigurations() {
        // Verificar que el mapeo automático de VID:PID de hardware conocido esté correcto
        assertNotNull(UsbController.USB_AUTO_MAP);

        // CH341A SPI: 0x5523 es CH341 en modo UART y no debe mapearse.
        assertEquals("ch341a_spi", UsbController.USB_AUTO_MAP.get("1a86:5512"));
        assertNull(UsbController.USB_AUTO_MAP.get("1a86:5523"));

        // CH347T / CH347F
        assertEquals("ch347_spi", UsbController.USB_AUTO_MAP.get("1a86:55db"));
        assertEquals("ch347_spi", UsbController.USB_AUTO_MAP.get("1a86:55de"));

        // FTDI: ft2232_spi necesita type=..., porque su PID por defecto es 0x6011.
        assertEquals("ft2232_spi:type=2232H", UsbController.USB_AUTO_MAP.get("0403:6010"));
        assertEquals("ft2232_spi:type=4232H", UsbController.USB_AUTO_MAP.get("0403:6011"));
        assertEquals("ft2232_spi:type=232H", UsbController.USB_AUTO_MAP.get("0403:6014"));
        assertEquals("ft2232_spi:type=4233H", UsbController.USB_AUTO_MAP.get("0403:6041"));
        assertEquals("ft2232_spi:type=tumpa", UsbController.USB_AUTO_MAP.get("0403:8a98"));
        assertEquals("ft2232_spi:type=tumpalite", UsbController.USB_AUTO_MAP.get("0403:8a99"));
        assertEquals("ft2232_spi:type=kt-link", UsbController.USB_AUTO_MAP.get("0403:bbe2"));
        assertEquals("ft2232_spi:type=jtagkey", UsbController.USB_AUTO_MAP.get("0403:cff8"));
        assertEquals("ft2232_spi:type=picotap", UsbController.USB_AUTO_MAP.get("096c:1449"));
        assertEquals("ft2232_spi:type=openmoko", UsbController.USB_AUTO_MAP.get("1457:5118"));
        assertEquals("ft2232_spi:type=arm-usb-ocd", UsbController.USB_AUTO_MAP.get("15ba:0003"));
        assertEquals("ft2232_spi:type=arm-usb-tiny", UsbController.USB_AUTO_MAP.get("15ba:0004"));
        assertEquals("ft2232_spi:type=arm-usb-tiny-h", UsbController.USB_AUTO_MAP.get("15ba:002a"));
        assertEquals("ft2232_spi:type=arm-usb-ocd-h", UsbController.USB_AUTO_MAP.get("15ba:002b"));
        assertEquals("ft2232_spi:type=google-servo", UsbController.USB_AUTO_MAP.get("18d1:5001"));
        assertEquals("ft2232_spi:type=google-servo-v2-legacy", UsbController.USB_AUTO_MAP.get("18d1:5002"));
        assertEquals("ft2232_spi:type=google-servo-v2", UsbController.USB_AUTO_MAP.get("18d1:5003"));

        // Adaptadores serie genéricos: no deben mapearse a un programador concreto.
        assertNull(UsbController.USB_AUTO_MAP.get("0403:6001"));
        assertNull(UsbController.USB_AUTO_MAP.get("0403:6015"));

        // ST-LINK V3 soportados. 0x374e está marcado BAD en flashrom.
        assertEquals("stlinkv3_spi", UsbController.USB_AUTO_MAP.get("0483:374f"));
        assertEquals("stlinkv3_spi", UsbController.USB_AUTO_MAP.get("0483:3753"));
        assertEquals("stlinkv3_spi", UsbController.USB_AUTO_MAP.get("0483:3754"));
        assertNull(UsbController.USB_AUTO_MAP.get("0483:374e"));
        assertNull(UsbController.USB_AUTO_MAP.get("0483:3748"));

        // Dediprog: solo 0xdada; 0xdae0 no está soportado.
        assertEquals("dediprog", UsbController.USB_AUTO_MAP.get("0483:dada"));
        assertNull(UsbController.USB_AUTO_MAP.get("0483:dae0"));

        // NVIDIA SMA y Arduino Uno Serprog
        assertEquals("nv_sma_spi", UsbController.USB_AUTO_MAP.get("0955:cf11"));
        assertEquals("serprog", UsbController.USB_AUTO_MAP.get("2341:0043"));
        assertEquals("serprog", UsbController.USB_AUTO_MAP.get("1a86:7523"));
    }
}
