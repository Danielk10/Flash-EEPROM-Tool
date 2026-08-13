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
        assertFalse(UsbController.needsPtyBridge("dummy"));
        assertFalse(UsbController.needsPtyBridge(""));
        assertFalse(UsbController.needsPtyBridge(null));
    }

    @Test
    public void testUsbAutoMapConfigurations() {
        // Verificar que el mapeo automático de VID:PID de hardware conocido esté correcto
        assertNotNull(UsbController.USB_AUTO_MAP);
        
        // CH341A SPI
        assertEquals("ch341a_spi", UsbController.USB_AUTO_MAP.get("1a86:5512"));
        // CH347 SPI/I2C/UART
        assertEquals("ch347_spi", UsbController.USB_AUTO_MAP.get("1a86:5523"));
        assertEquals("ch347_spi", UsbController.USB_AUTO_MAP.get("1a86:55db"));
        // Bus Pirate
        assertEquals("buspirate_spi", UsbController.USB_AUTO_MAP.get("0403:6001"));
        // Arduino Uno Serprog (VID:PID comunes de clones CH340 y oficiales)
        assertEquals("serprog", UsbController.USB_AUTO_MAP.get("2341:0043"));
        assertEquals("serprog", UsbController.USB_AUTO_MAP.get("1a86:7523"));
    }
}