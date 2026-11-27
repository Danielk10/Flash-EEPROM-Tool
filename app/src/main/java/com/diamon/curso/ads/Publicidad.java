package com.diamon.curso.ads;

import android.app.Activity;

/**
 * Interfaz unificada de publicidad para Flash SPI Tool.
 * Soporta Banners adaptativos, Interstitials y Rewarded Videos para el Pase Pro de 15 minutos.
 */
public interface Publicidad {

    @FunctionalInterface
    public interface AdsDisabledChecker {
        boolean areAdsDisabled();
    }

    /** Se invoca cuando el usuario completa el video y corresponde otorgar el premio. */
    @FunctionalInterface
    public interface OnRewardEarnedListener {
        void onRewardEarned();
    }

    void setAdsDisabledChecker(AdsDisabledChecker checker);

    void mostrarInterstitial();

    boolean mostrarInterstitialConCooldown();

    void botonAtrasInterstitial();

    void cargarRewardedAd();

    boolean isRewardedAdLoaded();

    void mostrarRewardedAd(
            Activity activity,
            OnRewardEarnedListener rewardListener,
            Runnable onDismissedListener);

    void ocultarBanner();

    void mostrarBanner();

    void pausarBanner();

    void resumenBanner();

    void disposeBanner();
}
