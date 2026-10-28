package com.diamon.curso.core;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class SpiSpeedTest {

    @Test
    public void bareProgrammer_stripsParameters() {
        assertEquals("ch347_spi", SpiSpeed.bareProgrammer("ch347_spi:spispeed=30M"));
        assertEquals("ch347_spi", SpiSpeed.bareProgrammer("ch347_spi,foo=bar"));
        assertEquals("ch347_spi", SpiSpeed.bareProgrammer("  ch347_spi  "));
        assertEquals("", SpiSpeed.bareProgrammer(null));
    }

    @Test
    public void applyToProgrammer_addsSpeedForCh347() {
        assertEquals("ch347_spi:spispeed=15M", SpiSpeed.applyToProgrammer("ch347_spi", "15M"));
        assertEquals("ch347_spi:spispeed=30M", SpiSpeed.applyToProgrammer("ch347_spi", "30M"));
        assertEquals("ch347_spi:spispeed=60M", SpiSpeed.applyToProgrammer("ch347_spi", "60M"));
    }

    @Test
    public void applyToProgrammer_keepsExistingParameters() {
        assertEquals("ch347_spi:foo=bar,spispeed=30M",
                SpiSpeed.applyToProgrammer("ch347_spi:foo=bar", "30M"));
    }

    @Test
    public void applyToProgrammer_respectsExplicitSpeed() {
        assertEquals("ch347_spi:spispeed=60M",
                SpiSpeed.applyToProgrammer("ch347_spi:spispeed=60M", "15M"));
        assertEquals("ch347_spi:spispeed=7.5M",
                SpiSpeed.applyToProgrammer("ch347_spi:spispeed=7.5M", "30M"));
    }

    @Test
    public void applyToProgrammer_ignoresOtherProgrammers() {
        assertEquals("ch341a_spi", SpiSpeed.applyToProgrammer("ch341a_spi", "60M"));
        assertEquals("ft2232_spi:type=2232H", SpiSpeed.applyToProgrammer("ft2232_spi:type=2232H", "60M"));
        assertEquals("serprog:dev=/dev/pts/1", SpiSpeed.applyToProgrammer("serprog:dev=/dev/pts/1", "60M"));
        assertNull(SpiSpeed.applyToProgrammer(null, "60M"));
    }

    @Test
    public void normalize_fallsBackToSafeDefault() {
        assertEquals("15M", SpiSpeed.normalize(null));
        assertEquals("15M", SpiSpeed.normalize(""));
        assertEquals("15M", SpiSpeed.normalize("120M"));
        assertEquals("30M", SpiSpeed.normalize(" 30m "));
    }

    @Test
    public void supportsSpiSpeed_onlyForCh347() {
        assertTrue(SpiSpeed.supportsSpiSpeed("ch347_spi"));
        assertTrue(SpiSpeed.supportsSpiSpeed("ch347_spi:spispeed=30M"));
        assertFalse(SpiSpeed.supportsSpiSpeed("ch341a_spi"));
        assertFalse(SpiSpeed.supportsSpiSpeed("serprog"));
        assertFalse(SpiSpeed.supportsSpiSpeed(null));
    }
}
