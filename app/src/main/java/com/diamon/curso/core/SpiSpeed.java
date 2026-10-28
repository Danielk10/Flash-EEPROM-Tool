package com.diamon.curso.core;

import java.util.Locale;

/**
 * Utilidades para aplicar la frecuencia SPI seleccionada por el usuario.
 *
 * Solo se aplica a los programadores cuyo backend de flashrom acepta el
 * parámetro {@code spispeed}. En esta app ese es el caso de {@code ch347_spi}
 * (CH347T y CH347F). Los comandos manuales de la consola no pasan por aquí.
 */
public final class SpiSpeed {

    public static final String PREF_KEY = "spi_speed";
    public static final String DEFAULT = "15M";
    public static final String[] OPTIONS = { "15M", "30M", "60M" };

    private static final String CH347_PROGRAMMER = "ch347_spi";

    private SpiSpeed() {
    }

    /** Devuelve el nombre base del programador, sin parámetros tras ':' o ','. */
    public static String bareProgrammer(String programmer) {
        if (programmer == null) {
            return "";
        }
        String value = programmer.trim();
        int cut = value.length();
        int colon = value.indexOf(':');
        int comma = value.indexOf(',');
        if (colon >= 0 && colon < cut) {
            cut = colon;
        }
        if (comma >= 0 && comma < cut) {
            cut = comma;
        }
        return value.substring(0, cut);
    }

    public static boolean supportsSpiSpeed(String programmer) {
        return CH347_PROGRAMMER.equals(bareProgrammer(programmer));
    }

    public static String normalize(String speed) {
        if (speed != null) {
            String value = speed.trim();
            for (String option : OPTIONS) {
                if (option.equalsIgnoreCase(value)) {
                    return option;
                }
            }
        }
        return DEFAULT;
    }

    /**
     * Añade {@code spispeed=<speed>} al programador indicado si corresponde.
     *
     * Respeta un valor explícito ya presente en el programador (por ejemplo,
     * {@code ch347_spi:spispeed=60M}) y no modifica otros backends.
     */
    public static String applyToProgrammer(String programmer, String speed) {
        if (!supportsSpiSpeed(programmer)) {
            return programmer;
        }
        if (programmer == null) {
            return null;
        }

        String value = programmer.trim();
        if (value.toLowerCase(Locale.US).contains("spispeed=")) {
            return value;
        }

        String separator = (value.indexOf(':') >= 0 || value.indexOf(',') >= 0) ? "," : ":";
        return value + separator + "spispeed=" + normalize(speed);
    }
}
