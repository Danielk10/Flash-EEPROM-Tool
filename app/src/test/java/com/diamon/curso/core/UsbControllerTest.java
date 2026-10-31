package com.diamon.curso.core;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

public class UsbControllerTest {

    @Test
    public void ft2232UsaElTipoCorrectoSegunElPid() {
        // Regresión del issue #101: FT232H (0x6014) no funciona con el PID
        // por defecto de ft2232_spi (FT4232H, 0x6011).
        assertEquals("ft2232_spi:type=232H",
                UsbController.resolveAutoProgrammer(0x0403, 0x6014, "FT232H", false));
        assertEquals("ft2232_spi:type=2232H",
                UsbController.resolveAutoProgrammer(0x0403, 0x6010, "FT2232H", false));
        assertEquals("ft2232_spi:type=4232H",
                UsbController.resolveAutoProgrammer(0x0403, 0x6011, "FT4232H", false));

        // Variantes FT2232H que requieren un type= especial.
        assertEquals("ft2232_spi:type=busblaster",
                UsbController.resolveAutoProgrammer(0x0403, 0x6010, "Bus Blaster v3", false));
        assertEquals("ft2232_spi:type=flyswatter",
                UsbController.resolveAutoProgrammer(0x0403, 0x6010, "Flyswatter2", false));
    }

    @Test
    public void adaptadoresSerieSeResuelvenPorNombreDeProducto() {
        assertEquals("spidriver",
                UsbController.resolveAutoProgrammer(0x0403, 0x6001, "SPIDriver", false));
        assertEquals("buspirate_spi",
                UsbController.resolveAutoProgrammer(0x0403, 0x6001, "Bus Pirate v3.6", false));
        assertEquals("developerbox",
                UsbController.resolveAutoProgrammer(0x10c4, 0xea60, "Developerbox", false));

        // Un CP2102 genérico sigue siendo un candidato a serprog.
        assertEquals("serprog",
                UsbController.resolveAutoProgrammer(0x10c4, 0xea60, "CP2102 USB to UART Bridge", false));
    }

    @Test
    public void raidenRequiereLaInterfazVendorSpecific() {
        // raiden_debug_spi no tiene tabla de PID: usa VID Google + interfaz 0xFF/0x51.
        assertEquals("raiden_debug_spi",
                UsbController.resolveAutoProgrammer(0x18d1, 0x500f, "Servo Micro", true));
        assertNull(UsbController.resolveAutoProgrammer(0x18d1, 0x500f, "Servo Micro", false));

        // Un Servo FTDI sin esa interfaz sigue usando ft2232_spi.
        assertEquals("ft2232_spi:type=google-servo",
                UsbController.resolveAutoProgrammer(0x18d1, 0x5001, "Servo", false));
    }

    @Test
    public void jlinkSeReconocePorVendorYPorPidOpenSda() {
        assertEquals("jlink_spi",
                UsbController.resolveAutoProgrammer(0x1366, 0x9999, "J-Link", false));
        assertEquals("jlink_spi",
                UsbController.resolveAutoProgrammer(0x1fc9, 0x000c, "J-Link OpenSDA", false));
        assertNull(UsbController.resolveAutoProgrammer(0x1fc9, 0x000d, "Otro", false));
    }

    @Test
    public void pidsNoSoportadosNoSeAutoSeleccionan() {
        assertNull(UsbController.resolveAutoProgrammer(0x1a86, 0x5523, "CH341 UART", false));
        assertNull(UsbController.resolveAutoProgrammer(0x0483, 0x374e, "STLINK-V3E", false));
        assertNull(UsbController.resolveAutoProgrammer(0x0483, 0xdae0, "Dediprog SF600", false));
        assertNull(UsbController.resolveAutoProgrammer(0x1234, 0x5678, "Desconocido", false));
    }

    @Test
    public void nvSmaYDispositivosUsbDirectosSeMapean() {
        assertEquals("nv_sma_spi",
                UsbController.resolveAutoProgrammer(0x0955, 0xcf11, "NVIDIA SMA", false));
        assertEquals("ch341a_spi",
                UsbController.resolveAutoProgrammer(0x1a86, 0x5512, "CH341A", false));
        assertEquals("ch347_spi",
                UsbController.resolveAutoProgrammer(0x1a86, 0x55db, "CH347T", false));
        assertEquals("dirtyjtag_spi",
                UsbController.resolveAutoProgrammer(0x1209, 0xc0cb, "DirtyJTAG 2", false));
    }

    @Test
    public void clonesUsbBlasterYDispositivosFtdiDisambiguacion() {
        // Clones de USB-Blaster basados en FTDI FT245/FT232 (0x0403:0x6001)
        assertEquals("usbblaster_spi",
                UsbController.resolveAutoProgrammer(0x0403, 0x6001, "USB-Blaster", false));
        assertEquals("usbblaster_spi",
                UsbController.resolveAutoProgrammer(0x0403, 0x6001, "Altera USB Blaster", false));

        // FT230X / FT231X (0x0403:0x6015): SPIDriver si el nombre lo indica, serprog si es UART genérico
        assertEquals("spidriver",
                UsbController.resolveAutoProgrammer(0x0403, 0x6015, "SPIDriver", false));
        assertEquals("serprog",
                UsbController.resolveAutoProgrammer(0x0403, 0x6015, "FT231X USB UART", false));

        // FT232R genérico sin nombre conocido no se fuerza para evitar conflicto
        assertNull(UsbController.resolveAutoProgrammer(0x0403, 0x6001, "FT232R USB UART", false));
    }

    @Test
    public void nuevosDispositivosSerprogSeMapeanCorrectamente() {
        // CP2104
        assertEquals("serprog",
                UsbController.resolveAutoProgrammer(0x10c4, 0xea61, "CP2104 USB to UART", false));

        // CH343, CH342, CH340 alt
        assertEquals("serprog",
                UsbController.resolveAutoProgrammer(0x1a86, 0x55d3, "CH343 USB Serial", false));
        assertEquals("serprog",
                UsbController.resolveAutoProgrammer(0x1a86, 0x55d2, "CH342 Dual Serial", false));
        assertEquals("serprog",
                UsbController.resolveAutoProgrammer(0x1a86, 0xe523, "CH340 Alt", false));

        // Espressif ESP32 CDC
        assertEquals("serprog",
                UsbController.resolveAutoProgrammer(0x303a, 0x1001, "ESP32-S3 USB JTAG/serial debug unit", false));
        assertEquals("serprog",
                UsbController.resolveAutoProgrammer(0x303a, 0x0002, "ESP32-S2 USB CDC", false));

        // Arduino CDC y SparkFun Pro Micro
        assertEquals("serprog",
                UsbController.resolveAutoProgrammer(0x2341, 0x8036, "Arduino Leonardo", false));
        assertEquals("serprog",
                UsbController.resolveAutoProgrammer(0x2341, 0x8037, "Arduino Micro", false));
        assertEquals("serprog",
                UsbController.resolveAutoProgrammer(0x2341, 0x0042, "Arduino Mega 2560 R3", false));
        assertEquals("serprog",
                UsbController.resolveAutoProgrammer(0x2341, 0x0010, "Arduino Mega 2560", false));
        assertEquals("serprog",
                UsbController.resolveAutoProgrammer(0x2341, 0x003e, "Arduino Due Native", false));
        assertEquals("serprog",
                UsbController.resolveAutoProgrammer(0x1b4f, 0x9205, "SparkFun Pro Micro 3.3V", false));
        assertEquals("serprog",
                UsbController.resolveAutoProgrammer(0x1b4f, 0x9206, "SparkFun Pro Micro 5V", false));

        // Raspberry Pi Pico stdio / debug probe
        assertEquals("serprog",
                UsbController.resolveAutoProgrammer(0x2e8a, 0x0003, "Pico CDC", false));
        assertEquals("serprog",
                UsbController.resolveAutoProgrammer(0x2e8a, 0x0009, "Raspberry Pi Debug Probe", false));
    }
}
