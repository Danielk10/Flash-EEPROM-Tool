package com.diamon.curso.core;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class FlashromExecutorTest {

    @Test
    public void testIssue101MacronixMultiChipLog() {
        String log = "flashrom v2.0.0-devel on Linux 4.19.157-perf-g4388af90b0da (aarch64)\n" +
                "Found Macronix flash chip \"MX25L12805D\" (16384 kB, SPI) on ft2232_spi.\n" +
                "Found Macronix flash chip \"MX25L12833F\" (16384 kB, SPI) on ft2232_spi.\n" +
                "Found Macronix flash chip \"MX25L12835F/MX25L12873F\" (16384 kB, SPI) on ft2232_spi.\n" +
                "Found Macronix flash chip \"MX25L12845E/MX25L12865E\" (16384 kB, SPI) on ft2232_spi.\n" +
                "Found Macronix flash chip \"MX25L12850F\" (16384 kB, SPI) on ft2232_spi.\n" +
                "Multiple flash chip definitions match the detected chip(s): \"MX25L12805D\", " +
                "\"MX25L12833F\", \"MX25L12835F/MX25L12873F\", \"MX25L12845E/MX25L12865E\", " +
                "\"MX25L12850F\"\n" +
                "Please specify which chip definition to use with the -c <chipname> option.\n";

        FlashromExecutor.AmbiguityInfo info = FlashromExecutor.parseOutputForAmbiguity(log);

        assertTrue("Should detect the ambiguity reported in issue #101", info.multipleChipsFound);
        assertEquals(5, info.suggestedChips.size());
        assertEquals("MX25L12805D", info.suggestedChips.get(0));
        assertEquals("MX25L12835F/MX25L12873F", info.suggestedChips.get(2));
        assertEquals("MX25L12850F", info.suggestedChips.get(4));
    }

    @Test
    public void testChipArgumentInsertedAfterProgrammer() {
        String[] args = { "-p", "ft2232_spi:type=232H", "-r", "bios.bin" };
        String[] result = FlashromExecutor.withChipArgument(args, "MX25L12835F/MX25L12873F");

        assertArrayEquals(new String[] {
                "-p", "ft2232_spi:type=232H",
                "-c", "MX25L12835F/MX25L12873F",
                "-r", "bios.bin"
        }, result);
        assertTrue(FlashromExecutor.hasChipArgument(result));
        assertFalse(FlashromExecutor.hasChipArgument(args));
    }

    @Test
    public void testChipArgumentReplacesExistingSelection() {
        String[] args = { "-p", "ch341a_spi", "-c", "OLD_CHIP", "--verify", "bios.bin" };
        String[] result = FlashromExecutor.withChipArgument(args, "W25Q128.V");

        assertArrayEquals(new String[] {
                "-p", "ch341a_spi", "-c", "W25Q128.V", "--verify", "bios.bin"
        }, result);
    }

    @Test
    public void testChipArgumentWithoutProgrammer() {
        String[] result = FlashromExecutor.withChipArgument(new String[] { "-r", "bios.bin" }, "W25Q64JV-.Q");

        assertArrayEquals(new String[] { "-c", "W25Q64JV-.Q", "-r", "bios.bin" }, result);
    }

    @Test
    public void testAmbiguityDetectionFromUserLog() {
        String log = "CH347 SPI clock set to 15MHz.\n" +
                "Found Winbond flash chip \"W25Q64BV/W25Q64CV/W25Q64FV\" (8192 kB, SPI) on ch347_spi.\n" +
                "Found Winbond flash chip \"W25Q64JV-.Q\" (8192 kB, SPI) on ch347_spi.\n" +
                "Multiple flash chip definitions match the detected chip(s): \"W25Q64BV/W25Q64CV/W25Q64FV\", \"W25Q64JV-.Q\"\n" +
                "Please specify which chip definition to use with the -c <chipname> option.\n";

        FlashromExecutor.AmbiguityInfo info = FlashromExecutor.parseOutputForAmbiguity(log);

        assertTrue("Should detect ambiguity", info.multipleChipsFound);
        assertEquals(2, info.suggestedChips.size());
        assertEquals("W25Q64BV/W25Q64CV/W25Q64FV", info.suggestedChips.get(0));
        assertEquals("W25Q64JV-.Q", info.suggestedChips.get(1));
    }

    @Test
    public void testSingleChipDetectedNotAmbiguous() {
        String log = "Found Spansion flash chip \"S25FL032P\" (4096 kB, SPI) on ch347_spi.\n" +
                "Reading flash... done.\n";

        FlashromExecutor.AmbiguityInfo info = FlashromExecutor.parseOutputForAmbiguity(log);

        assertFalse("Single chip should not trigger ambiguity", info.multipleChipsFound);
        assertTrue("Suggested chips should be empty", info.suggestedChips.isEmpty());
    }

    @Test
    public void testEmptyOrNormalLog() {
        String log = "flashrom v1.8.0-devel on Linux\n" +
                "No EEPROM/flash device found.\n";

        FlashromExecutor.AmbiguityInfo info = FlashromExecutor.parseOutputForAmbiguity(log);

        assertFalse(info.multipleChipsFound);
        assertTrue(info.suggestedChips.isEmpty());
    }
}
