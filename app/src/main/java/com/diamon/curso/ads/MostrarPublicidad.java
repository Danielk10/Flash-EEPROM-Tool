package com.diamon.curso.ads;

import android.app.Activity;
import android.os.Build;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.WindowMetrics;

import androidx.annotation.NonNull;

import com.google.android.gms.ads.AdError;
import com.google.android.gms.ads.AdListener;
import com.google.android.gms.ads.AdRequest;
import com.google.android.gms.ads.AdSize;
import com.google.android.gms.ads.AdView;
import com.google.android.gms.ads.FullScreenContentCallback;
import com.google.android.gms.ads.LoadAdError;
import com.google.android.gms.ads.MobileAds;
import com.google.android.gms.ads.initialization.InitializationStatus;
import com.google.android.gms.ads.initialization.OnInitializationCompleteListener;
import com.google.android.gms.ads.interstitial.InterstitialAd;
import com.google.android.gms.ads.interstitial.InterstitialAdLoadCallback;

import java.lang.ref.WeakReference;

public class MostrarPublicidad implements Publicidad {

    private static final String TAG = "MostrarPublicidad";
    private static final String BANNER_AD_UNIT_ID = "ca-app-pub-5141499161332805/1518371626";
    private static final String INTERSTITIAL_AD_UNIT_ID = "ca-app-pub-5141499161332805/8275351662";

    // Cooldown mínimo de 3 minutos entre interstitials para proteger la experiencia del usuario
    private static final long COOLDOWN_DEFAULT_MS = 3 * 60 * 1000;

    private WeakReference<Activity> actividadRef;

    private AdView adView;

    private AdRequest adRequest;

    private InterstitialAd mInterstitialAd;

    private boolean isLoadingInterstitial = false;

    private long ultimoTiempoInterstitial = 0;

    public MostrarPublicidad(Activity actividad) {

        this.actividadRef = new WeakReference<>(actividad);

        MobileAds.initialize(
                actividad,
                new OnInitializationCompleteListener() {
                    @Override
                    public void onInitializationComplete(
                            @NonNull InitializationStatus initializationStatus) {
                    }
                });

        adView = new AdView(actividad);
        adView.setAdUnitId(BANNER_AD_UNIT_ID);
        adView.setAdSize(getAdaptiveBannerAdSize(actividad));
        adView.setAdListener(
                new AdListener() {
                    @Override
                    public void onAdLoaded() {
                        Log.d(TAG, "Adaptive Banner cargado exitosamente.");
                    }

                    @Override
                    public void onAdFailedToLoad(@NonNull LoadAdError loadAdError) {
                        Log.w(TAG, "Error cargando Adaptive Banner: " + loadAdError.getMessage());
                    }
                });
        adRequest = new AdRequest.Builder().build();
    }

    /**
     * Calcula el tamaño óptimo de Anchored Adaptive Banner según el ancho de pantalla del dispositivo.
     */
    private AdSize getAdaptiveBannerAdSize(Activity activity) {
        if (activity == null) {
            return AdSize.BANNER;
        }
        try {
            int adWidthPixels;
            DisplayMetrics displayMetrics = activity.getResources().getDisplayMetrics();
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                WindowMetrics windowMetrics = activity.getWindowManager().getCurrentWindowMetrics();
                adWidthPixels = windowMetrics.getBounds().width();
            } else {
                adWidthPixels = displayMetrics.widthPixels;
            }
            float density = displayMetrics.density;
            int adWidth = (int) (adWidthPixels / density);
            return AdSize.getCurrentOrientationAnchoredAdaptiveBannerAdSize(activity, adWidth);
        } catch (Exception e) {
            Log.e(TAG, "Error calculando adaptive banner size, fallback a estándar", e);
            return AdSize.BANNER;
        }
    }

    @Override
    public void mostrarInterstitial() {
        mostrarInterstitialConCooldown(0);
    }

    @Override
    public boolean mostrarInterstitialConCooldown() {
        return mostrarInterstitialConCooldown(COOLDOWN_DEFAULT_MS);
    }

    public boolean mostrarInterstitialConCooldown(long cooldownMs) {
        long ahora = System.currentTimeMillis();
        if (cooldownMs > 0 && (ahora - ultimoTiempoInterstitial < cooldownMs)) {
            Log.d(TAG, "Interstitial omitido por cooldown activo (" + (ahora - ultimoTiempoInterstitial) + " ms)");
            return false;
        }

        Activity actividad = actividadRef.get();
        if (mInterstitialAd != null && actividad != null && !actividad.isFinishing()) {
            ultimoTiempoInterstitial = ahora;
            mInterstitialAd.show(actividad);
            return true;
        } else {
            if (mInterstitialAd == null && !isLoadingInterstitial) {
                cargarInterstial();
            }
            return false;
        }
    }

    public void cargarInterstial() {
        if (isLoadingInterstitial || mInterstitialAd != null) {
            return;
        }

        Activity actividad = actividadRef.get();
        if (actividad == null || actividad.isFinishing())
            return;

        isLoadingInterstitial = true;
        InterstitialAd.load(
                actividad,
                INTERSTITIAL_AD_UNIT_ID,
                adRequest,
                new InterstitialAdLoadCallback() {
                    @Override
                    public void onAdLoaded(@NonNull InterstitialAd interstitialAd) {
                        mInterstitialAd = interstitialAd;
                        isLoadingInterstitial = false;

                        mInterstitialAd.setFullScreenContentCallback(
                                new FullScreenContentCallback() {
                                    @Override
                                    public void onAdDismissedFullScreenContent() {
                                        mInterstitialAd = null;
                                        // Precargar automáticamente para la siguiente oportunidad
                                        cargarInterstial();
                                    }

                                    @Override
                                    public void onAdFailedToShowFullScreenContent(@NonNull AdError adError) {
                                        Log.w(TAG, "Fallo al mostrar Interstitial: " + adError.getMessage());
                                        mInterstitialAd = null;
                                        cargarInterstial();
                                    }
                                });
                    }

                    @Override
                    public void onAdFailedToLoad(@NonNull LoadAdError loadAdError) {
                        Log.w(TAG, "Fallo al cargar Interstitial: " + loadAdError.getMessage());
                        mInterstitialAd = null;
                        isLoadingInterstitial = false;
                    }
                });
    }

    @Override
    public void botonAtrasInterstitial() {
    }

    public AdView getBanner() {
        return adView;
    }

    public void cargarBanner() {
        if (adView != null) {
            adView.loadAd(this.adRequest);
        }
    }

    public AdRequest getAdReques() {
        return this.adRequest;
    }

    public void resumenBanner() {
        if (adView != null) {
            adView.resume();
        }
    }

    public void pausarBanner() {
        if (adView != null) {
            adView.pause();
        }
    }

    public void disposeBanner() {
        if (adView != null) {
            adView.destroy();
        }
    }

    public InterstitialAd getMInterstitialAd() {
        return this.mInterstitialAd;
    }

    public void setMInterstitialAd(InterstitialAd mInterstitialAd) {
        this.mInterstitialAd = mInterstitialAd;
    }
}
