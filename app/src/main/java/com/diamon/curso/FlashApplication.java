package com.diamon.curso;

import android.app.Application;
import android.util.Log;

import com.diamon.curso.ads.WortiseAdUnits;
import com.wortise.ads.AdSettings;
import com.wortise.ads.WortiseSdk;

/**
 * Inicializa el SDK de Wortise una sola vez al arrancar la app.
 *
 * <p>El modo de prueba se activa únicamente en debug. En release se usan los
 * bloques de producción y las peticiones son reales, así que no se deben
 * pulsar los anuncios durante las pruebas.
 */
public class FlashApplication extends Application {

    private static final String TAG = "FlashApplication";

    @Override
    public void onCreate() {
        super.onCreate();

        AdSettings.setTestEnabled(BuildConfig.DEBUG);
        WortiseSdk.initialize(this, WortiseAdUnits.APP_KEY);

        Log.d(TAG, "Wortise SDK inicializado (test=" + BuildConfig.DEBUG + ")");
    }
}
