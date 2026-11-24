package com.diamon.curso.ads;

import android.app.Activity;

import com.google.android.gms.ads.OnUserEarnedRewardListener;

/**
 * Interfaz unificada de publicidad para Flash SPI Tool.
 * Soporta Banners adaptativos, Interstitials y Rewarded Videos para el Pase Pro de 15 minutos.
 */
public interface Publicidad {

    @FunctionalInterface
    public interface AdsDisabledChecker {
        boolean areAdsDisabled();
    }

    void setAdsDisabledChecker(AdsDisabledChecker checker);

    void mostrarInterstitial();

    boolean mostrarInterstitialConCooldown();

    void botonAtrasInterstitial();

    void cargarRewardedAd();

    boolean isRewardedAdLoaded();

    void mostrarRewardedAd(Activity activity, OnUserEarnedRewardListener rewardListener, Runnable onDismissedListener);

    void ocultarBanner();

    void mostrarBanner();

    void pausarBanner();

    void resumenBanner();

    void disposeBanner();
}