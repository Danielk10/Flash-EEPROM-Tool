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
    }
}
