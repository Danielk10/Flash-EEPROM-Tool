package com.diamon.curso.ads;

import com.diamon.curso.BuildConfig;

/**
 * Identificadores de Wortise para Flash SPI Tool.
 *
 * <p>En compilaciones debug se usan los bloques de prueba oficiales para poder
 * hacer clic sin generar tráfico inválido. En release se usan los bloques
 * reales del dashboard. El modo de prueba del SDK también se activa solo en
 * debug desde {@code FlashApplication}.
 *
 * <p>Bloque extra del dashboard sin uso actualmente:
 * {@code 9b7a4602-5815-4961-be5c-ec04a74ff6ca}.
 */
public final class WortiseAdUnits {

    /** App key de Wortise (dashboard > detalles de la app). */
    public static final String APP_KEY = "e6d7e129-2243-48aa-ba56-27ce7880bfaf";

    private static final String BANNER_PRODUCTION =
            "1559e682-1e6b-4334-8762-9a590519b963";
    private static final String INTERSTITIAL_PRODUCTION =
            "c0d1bc01-0894-4f74-9369-91f083c2132e";
    private static final String REWARDED_PRODUCTION =
            "ba2b13b7-0ce6-48cb-904f-e5a29baa5f30";
    private static final String NATIVE_PRODUCTION =
            "9fd3d6e3-f002-4fb7-a4d3-11b1f5628fe7";

    public static final String BANNER =
            BuildConfig.DEBUG ? "test-banner" : BANNER_PRODUCTION;
    public static final String INTERSTITIAL =
            BuildConfig.DEBUG ? "test-interstitial" : INTERSTITIAL_PRODUCTION;
    public static final String REWARDED =
            BuildConfig.DEBUG ? "test-rewarded" : REWARDED_PRODUCTION;
    public static final String NATIVE =
            BuildConfig.DEBUG ? "test-native" : NATIVE_PRODUCTION;

    private WortiseAdUnits() {
    }
}
