package com.diamon.curso.ads;

import android.app.Activity;
import android.content.Context;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.View;
import android.view.WindowMetrics;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;

import com.diamon.curso.R;
import com.google.android.gms.ads.AdError;
import com.google.android.gms.ads.AdListener;
import com.google.android.gms.ads.AdRequest;
import com.google.android.gms.ads.AdSize;
import com.google.android.gms.ads.AdView;
import com.google.android.gms.ads.FullScreenContentCallback;
import com.google.android.gms.ads.LoadAdError;
import com.google.android.gms.ads.MobileAds;
import com.google.android.gms.ads.OnUserEarnedRewardListener;
import com.google.android.gms.ads.initialization.InitializationStatus;
import com.google.android.gms.ads.initialization.OnInitializationCompleteListener;
import com.google.android.gms.ads.interstitial.InterstitialAd;
import com.google.android.gms.ads.interstitial.InterstitialAdLoadCallback;
import com.google.android.gms.ads.rewarded.RewardedAd;
import com.google.android.gms.ads.rewarded.RewardedAdLoadCallback;

import java.lang.ref.WeakReference;

/**
 * Gestor unificado de publicidad para Flash SPI Tool.
 * Controla Banners Adaptativos, Interstitials y Rewarded Video con soporte para
 * omisión absoluta cuando el usuario tiene Pro activo (Pase 12h o Compra Permanente).
 *
 * Política de carga:
 * - El banner se pide una sola vez y se reutiliza (AdMob lo refresca solo).
 * - El interstitial y el rewarded se cargan bajo demanda, con caducidad de 60 min.
 * - Si el usuario pulsa "Ver video" y el rewarded aún no está listo, se muestra
 *   automáticamente en cuanto termina la carga.
 */
public class MostrarPublicidad implements Publicidad {

    private static final String TAG = "MostrarPublicidad";
    public static final String BANNER_AD_UNIT_ID = "ca-app-pub-5141499161332805/1518371626";
    public static final String INTERSTITIAL_AD_UNIT_ID = "ca-app-pub-5141499161332805/8275351662";
    /** ID Real AdMob oficial de producción para Rewarded Video de Flash SPI Tool: */
    public static final String REWARDED_AD_UNIT_ID = "ca-app-pub-5141499161332805/9025351389";

    // Cooldown mínimo de 3 minutos entre interstitials para proteger la experiencia del usuario.
    private static final long COOLDOWN_DEFAULT_MS = 3 * 60 * 1000;
    // Los anuncios cargados se descartan a la hora para evitar mostrarlos caducados.
    private static final long AD_TTL_MS = 60 * 60 * 1000;
    // Si el usuario pulsa "Ver video", esperamos como máximo esto a que cargue.
    private static final long REWARDED_PENDING_TIMEOUT_MS = 20 * 1000;
    // Si el interstitial se pidió pero no llegó a mostrarse, no lo mostramos mucho después.
    private static final long INTERSTITIAL_PENDING_TIMEOUT_MS = 20 * 1000;
    // Si un interstitial no se puede mostrar, reintentamos la carga más tarde.
    private static final long INTERSTITIAL_RETRY_MS = 45 * 1000;

    private static volatile boolean mobileAdsInitialized = false;

    private final WeakReference<Activity> actividadRef;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    private AdView adView;
    private final AdRequest adRequest;
    private boolean bannerLoading = false;
    private boolean bannerLoaded = false;
    private long bannerLoadedAt = 0;

    private InterstitialAd mInterstitialAd;
    private boolean isLoadingInterstitial = false;
    private long interstitialLoadedAt = 0;
    private long ultimoTiempoInterstitial = 0;
    private boolean interstitialShowPending = false;
    private long interstitialPendingSince = 0;

    private RewardedAd rewardedAd;
    private boolean isRewardedLoading = false;
    private long rewardedLoadedAt = 0;
    private boolean rewardedShowPending = false;
    private long rewardedPendingSince = 0;
    private Activity pendingRewardedActivity;
    private OnUserEarnedRewardListener pendingRewardListener;
    private Runnable pendingRewardedDismissListener;

    private AdsDisabledChecker adsDisabledChecker;
    private boolean destroyed = false;
    private boolean activityPaused = false;
    private boolean consentResolved = false;
    private boolean adsAllowed = false;
    private boolean adsReady = false;
    private boolean bannerLoadPending = false;

    public MostrarPublicidad(Activity actividad) {
        this.actividadRef = new WeakReference<>(actividad);

        requestConsent(actividad);

        adView = new AdView(actividad);
        adView.setAdUnitId(BANNER_AD_UNIT_ID);
        adView.setAdSize(getAdaptiveBannerAdSize(actividad));
        adView.setAdListener(
                new AdListener() {
                    @Override
                    public void onAdLoaded() {
                        bannerLoaded = true;
                        bannerLoadedAt = System.currentTimeMillis();
                        bannerLoading = false;
                        Log.d(TAG, "Adaptive Banner cargado exitosamente.");
                        if (isAdDisabled() && adView != null) {
                            adView.setVisibility(View.GONE);
                        }
                    }

                    @Override
                    public void onAdFailedToLoad(@NonNull LoadAdError loadAdError) {
                        bannerLoaded = false;
                        bannerLoading = false;
                        Log.w(TAG, "Error cargando Adaptive Banner: código " + loadAdError.getCode()
                                + " - " + loadAdError.getMessage());
                    }

                    @Override
                    public void onAdImpression() {
                        Log.d(TAG, "Impresión de banner registrada.");
                    }
                });
        adRequest = new AdRequest.Builder().build();

        // El rewarded NO se precarga aquí: se hace cuando el usuario abre el
        // diálogo Pro o pulsa el botón. Así no se desperdician peticiones.
    }

    private void requestConsent(Activity actividad) {
        ConsentManager.requestConsent(
                actividad,
                canRequestAds -> {
                    if (!destroyed) {
                        onConsentState(canRequestAds);
                    }
                });
    }

    /**
     * Reaplica el estado después de que el usuario cambie su decisión en
     * "Opciones de privacidad". Si concede el consentimiento, se inicializa
     * MobileAds y se cargan los anuncios pendientes.
     */
    public void onConsentChanged() {
        if (!destroyed) {
            onConsentState(ConsentManager.canRequestAds());
        }
    }

    private void onConsentState(boolean canRequestAds) {
        consentResolved = true;
        adsAllowed = canRequestAds;
        // El flujo de UMP ya terminó. Si el usuario rechazó, será
        // isAdDisabled() quien bloquee las peticiones.
        adsReady = true;

        Activity activity = actividadRef.get();
        if (activity != null && !activity.isFinishing() && !activity.isDestroyed()) {
            activity.invalidateOptionsMenu();
        }

        if (canRequestAds) {
            Activity context = activity != null ? activity : actividadRef.get();
            if (context != null) {
                initializeMobileAds(context);
            }
            if (bannerLoadPending) {
                bannerLoadPending = false;
                cargarBanner();
            }
            if (interstitialShowPending) {
                cargarInterstial();
            }
            if (rewardedShowPending) {
                cargarRewardedAd();
                schedulePendingRewardedTimeout();
            }
        } else {
            bannerLoadPending = false;
            interstitialShowPending = false;
            Runnable dismissed = pendingRewardedDismissListener;
            clearPendingRewarded();
            ocultarBanner();
            if (dismissed != null) {
                dismissed.run();
            }
        }
    }

    private static void initializeMobileAds(Context context) {
        if (mobileAdsInitialized) {
            return;
        }
        mobileAdsInitialized = true;
        MobileAds.initialize(
                context,
                new OnInitializationCompleteListener() {
                    @Override
                    public void onInitializationComplete(
                            @NonNull InitializationStatus initializationStatus) {
                        Log.d(TAG, "MobileAds inicializado.");
                    }
                });
    }

    @Override
    public void setAdsDisabledChecker(AdsDisabledChecker checker) {
        this.adsDisabledChecker = checker;
        if (isAdDisabled()) {
            ocultarBanner();
        }
    }

    public boolean isAdDisabled() {
        // Si el usuario rechazó el consentimiento, no se puede pedir publicidad.
        // Se consulta ConsentManager en cada llamada para respetar también los
        // cambios hechos desde "Opciones de privacidad".
        if (consentResolved && !ConsentManager.canRequestAds()) {
            return true;
        }
        return adsDisabledChecker != null && adsDisabledChecker.areAdsDisabled();
    }

    /**
     * Calcula el tamaño óptimo de Anchored Adaptive Banner según el ancho de pantalla del dispositivo.
     */
    @SuppressWarnings("deprecation")
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

    // --- INTERSTITIAL ---

    @Override
    public void mostrarInterstitial() {
        mostrarInterstitialConCooldown(0);
    }

    @Override
    public boolean mostrarInterstitialConCooldown() {
        return mostrarInterstitialConCooldown(COOLDOWN_DEFAULT_MS);
    }

    public boolean mostrarInterstitialConCooldown(long cooldownMs) {
        if (destroyed || isAdDisabled()) {
            Log.d(TAG, "Interstitial omitido: Acceso Pro activo / Anuncios desactivados.");
            return false;
        }

        long ahora = System.currentTimeMillis();
        if (cooldownMs > 0 && (ahora - ultimoTiempoInterstitial < cooldownMs)) {
            Log.d(TAG, "Interstitial omitido por cooldown activo (" + (ahora - ultimoTiempoInterstitial) + " ms)");
            return false;
        }

        if (isInterstitialFresh()) {
            Activity actividad = actividadRef.get();
            if (actividad != null && !actividad.isFinishing() && !actividad.isDestroyed()) {
                ultimoTiempoInterstitial = ahora;
                mInterstitialAd.show(actividad);
                return true;
            }
            return false;
        }

        // No hay anuncio fresco: se pide y se mostrará en cuanto llegue.
        // Así el primer trigger no se pierde y no se deja un anuncio cargado
        // esperando durante horas.
        Activity actual = actividadRef.get();
        if (actual == null || actual.isFinishing() || actual.isDestroyed()) {
            return false;
        }
        interstitialShowPending = true;
        interstitialPendingSince = ahora;
        if (!adsReady) {
            // El consentimiento aún se está gestionando. Se cargará y mostrará
            // en cuanto el usuario termine el formulario.
            return true;
        }
        cargarInterstial();
        return true;
    }

    public void cargarInterstial() {
        if (destroyed || !adsReady || isAdDisabled()) {
            return;
        }

        if (isLoadingInterstitial || isInterstitialFresh()) {
            return;
        }

        if (mInterstitialAd != null) {
            mInterstitialAd = null;
            interstitialLoadedAt = 0;
        }

        Activity actividad = actividadRef.get();
        if (actividad == null || actividad.isFinishing() || actividad.isDestroyed()) {
            return;
        }

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
                        interstitialLoadedAt = System.currentTimeMillis();

                        mInterstitialAd.setFullScreenContentCallback(
                                new FullScreenContentCallback() {
                                    @Override
                                    public void onAdDismissedFullScreenContent() {
                                        mInterstitialAd = null;
                                        interstitialLoadedAt = 0;
                                        scheduleInterstitialReload(INTERSTITIAL_RETRY_MS);
                                    }

                                    @Override
                                    public void onAdFailedToShowFullScreenContent(@NonNull AdError adError) {
                                        Log.w(TAG, "Fallo al mostrar Interstitial: " + adError.getMessage());
                                        mInterstitialAd = null;
                                        interstitialLoadedAt = 0;
                                        scheduleInterstitialReload(INTERSTITIAL_RETRY_MS);
                                    }

                                    @Override
                                    public void onAdShowedFullScreenContent() {
                                        Log.d(TAG, "Interstitial mostrado en pantalla completa.");
                                    }
                                });

                        if (interstitialShowPending) {
                            long edad = System.currentTimeMillis() - interstitialPendingSince;
                            interstitialShowPending = false;
                            if (edad <= INTERSTITIAL_PENDING_TIMEOUT_MS) {
                                Activity actual = actividadRef.get();
                                if (actual != null && !actual.isFinishing() && !actual.isDestroyed()) {
                                    ultimoTiempoInterstitial = System.currentTimeMillis();
                                    interstitialAd.show(actual);
                                }
                            }
                        }
                    }

                    @Override
                    public void onAdFailedToLoad(@NonNull LoadAdError loadAdError) {
                        Log.w(TAG, "Fallo al cargar Interstitial: código " + loadAdError.getCode()
                                + " - " + loadAdError.getMessage());
                        mInterstitialAd = null;
                        interstitialLoadedAt = 0;
                        isLoadingInterstitial = false;
                        interstitialShowPending = false;
                    }
                });
    }

    private boolean isInterstitialFresh() {
        return mInterstitialAd != null
                && interstitialLoadedAt > 0
                && (System.currentTimeMillis() - interstitialLoadedAt < AD_TTL_MS);
    }

    private void scheduleInterstitialReload(long delayMs) {
        if (destroyed || isAdDisabled()) {
            return;
        }
        mainHandler.postDelayed(
                () -> {
                    if (!destroyed && !activityPaused && !isAdDisabled()
                            && !isInterstitialFresh() && !isLoadingInterstitial) {
                        cargarInterstial();
                    }
                },
                delayMs);
    }

    @Override
    public void botonAtrasInterstitial() {
    }

    // --- REWARDED VIDEO ADS (PASE 12 HORAS) ---

    @Override
    public void cargarRewardedAd() {
        mainHandler.post(
                () -> {
                    try {
                        if (destroyed || !adsReady || isAdDisabled()) {
                            return;
                        }
                        if (isRewardedAdLoaded() || isRewardedLoading) {
                            return;
                        }

                        Activity actividad = actividadRef.get();
                        if (actividad == null || actividad.isFinishing() || actividad.isDestroyed()) {
                            return;
                        }

                        if (rewardedAd != null) {
                            rewardedAd = null;
                            rewardedLoadedAt = 0;
                        }

                        isRewardedLoading = true;
                        Log.d(TAG, "Iniciando carga de RewardedAd...");
                        RewardedAd.load(
                                actividad,
                                REWARDED_AD_UNIT_ID,
                                adRequest,
                                new RewardedAdLoadCallback() {
                                    @Override
                                    public void onAdLoaded(@NonNull RewardedAd ad) {
                                        rewardedAd = ad;
                                        rewardedLoadedAt = System.currentTimeMillis();
                                        isRewardedLoading = false;
                                        Log.d(TAG, "RewardedAd cargado con éxito.");
                                        showPendingRewardedIfReady();
                                    }

                                    @Override
                                    public void onAdFailedToLoad(@NonNull LoadAdError loadAdError) {
                                        rewardedAd = null;
                                        rewardedLoadedAt = 0;
                                        isRewardedLoading = false;
                                        Log.w(TAG, "Error cargando RewardedAd: código "
                                                + loadAdError.getCode() + " - "
                                                + loadAdError.getMessage());
                                        if (rewardedShowPending) {
                                            Runnable dismissed = pendingRewardedDismissListener;
                                            clearPendingRewarded();
                                            notifyRewardedLoadFailed(dismissed);
                                        }
                                    }
                                });
                    } catch (Exception e) {
                        isRewardedLoading = false;
                        Log.e(TAG, "Excepción cargando RewardedAd: " + e.getMessage(), e);
                    }
                });
    }

    public void precargarRewardedAd() {
        cargarRewardedAd();
    }

    @Override
    public boolean isRewardedAdLoaded() {
        return rewardedAd != null
                && rewardedLoadedAt > 0
                && (System.currentTimeMillis() - rewardedLoadedAt < AD_TTL_MS);
    }

    @VisibleForTesting
    public void setRewardedAd(@Nullable RewardedAd rewardedAd) {
        this.rewardedAd = rewardedAd;
        this.rewardedLoadedAt = rewardedAd != null ? System.currentTimeMillis() : 0;
    }

    @Override
    public void mostrarRewardedAd(
            Activity activity,
            OnUserEarnedRewardListener rewardListener,
            Runnable onDismissedListener) {

        Activity targetActivity =
                (activity != null && !activity.isFinishing() && !activity.isDestroyed())
                        ? activity
                        : actividadRef.get();
        if (destroyed || isAdDisabled() || targetActivity == null
                || targetActivity.isFinishing() || targetActivity.isDestroyed()) {
            if (onDismissedListener != null) {
                onDismissedListener.run();
            }
            return;
        }

        // Si el consentimiento aún no está resuelto, guardamos la intención
        // sin arrancar el temporizador: el formulario puede tardar más de 20 s
        // y no queremos cancelar el premio mientras el usuario lo lee.
        if (!adsReady) {
            pendingRewardedActivity = targetActivity;
            pendingRewardListener = rewardListener;
            pendingRewardedDismissListener = onDismissedListener;
            rewardedShowPending = true;
            rewardedPendingSince = System.currentTimeMillis();
            return;
        }

        mainHandler.post(
                () -> {
                    if (isRewardedAdLoaded()) {
                        showLoadedRewarded(targetActivity, rewardListener, onDismissedListener);
                        return;
                    }

                    // Guardamos la intención y mostramos el anuncio en cuanto cargue.
                    pendingRewardedActivity = targetActivity;
                    pendingRewardListener = rewardListener;
                    pendingRewardedDismissListener = onDismissedListener;
                    rewardedShowPending = true;
                    rewardedPendingSince = System.currentTimeMillis();
                    cargarRewardedAd();
                    schedulePendingRewardedTimeout();
                });
    }

    private void showPendingRewardedIfReady() {
        if (!rewardedShowPending || !isRewardedAdLoaded()) {
            return;
        }

        Activity activity =
                pendingRewardedActivity != null ? pendingRewardedActivity : actividadRef.get();
        OnUserEarnedRewardListener rewardListener = pendingRewardListener;
        Runnable dismissed = pendingRewardedDismissListener;
        clearPendingRewarded();

        if (activity == null || activity.isFinishing() || activity.isDestroyed()) {
            if (dismissed != null) {
                dismissed.run();
            }
            return;
        }

        showLoadedRewarded(activity, rewardListener, dismissed);
    }

    private void showLoadedRewarded(
            Activity activity,
            OnUserEarnedRewardListener rewardListener,
            Runnable onDismissedListener) {

        RewardedAd adParaMostrar = rewardedAd;
        rewardedAd = null;
        rewardedLoadedAt = 0;

        if (adParaMostrar == null) {
            if (onDismissedListener != null) {
                onDismissedListener.run();
            }
            return;
        }

        adParaMostrar.setFullScreenContentCallback(
                new FullScreenContentCallback() {
                    @Override
                    public void onAdDismissedFullScreenContent() {
                        Log.d(TAG, "RewardedAd cerrado por el usuario.");
                        if (!destroyed && !isAdDisabled()) {
                            cargarRewardedAd();
                        }
                        if (onDismissedListener != null) {
                            onDismissedListener.run();
                        }
                    }

                    @Override
                    public void onAdFailedToShowFullScreenContent(@NonNull AdError adError) {
                        Log.w(TAG, "Fallo al mostrar RewardedAd: " + adError.getMessage());
                        if (!destroyed && !isAdDisabled()) {
                            cargarRewardedAd();
                        }
                        if (onDismissedListener != null) {
                            onDismissedListener.run();
                        }
                    }

                    @Override
                    public void onAdShowedFullScreenContent() {
                        Log.d(TAG, "RewardedAd presentado en pantalla completa.");
                    }
                });

        adParaMostrar.show(
                activity,
                rewardListener != null
                        ? rewardListener
                        : reward -> Log.d(TAG, "Recompensa otorgada sin listener explícito."));
    }

    private void schedulePendingRewardedTimeout() {
        if (rewardedShowPending) {
            mainHandler.postDelayed(this::expirePendingRewarded, REWARDED_PENDING_TIMEOUT_MS);
        }
    }

    private void expirePendingRewarded() {
        if (!rewardedShowPending) {
            return;
        }
        if (System.currentTimeMillis() - rewardedPendingSince < REWARDED_PENDING_TIMEOUT_MS) {
            return;
        }

        Runnable dismissed = pendingRewardedDismissListener;
        clearPendingRewarded();
        notifyRewardedLoadFailed(dismissed);
    }

    private void notifyRewardedLoadFailed(@Nullable Runnable onDismissedListener) {
        Activity activity = actividadRef.get();
        if (activity != null && !activity.isFinishing() && !activity.isDestroyed()) {
            Toast.makeText(
                            activity,
                            activity.getString(R.string.ad_loading_rewarded_video_toast),
                            Toast.LENGTH_SHORT)
                    .show();
        }
        if (onDismissedListener != null) {
            onDismissedListener.run();
        }
    }

    private void clearPendingRewarded() {
        rewardedShowPending = false;
        pendingRewardedActivity = null;
        pendingRewardListener = null;
        pendingRewardedDismissListener = null;
        rewardedPendingSince = 0;
    }

    // --- BANNERS ---

    public AdView getBanner() {
        return adView;
    }

    public void cargarBanner() {
        if (destroyed || isAdDisabled()) {
            ocultarBanner();
            return;
        }
        if (!adsReady) {
            // El consentimiento se está gestionando; se carga al terminar.
            bannerLoadPending = true;
            return;
        }
        if (adView == null || bannerLoading) {
            return;
        }
        if (bannerLoaded) {
            if (bannerLoadedAt > 0
                    && System.currentTimeMillis() - bannerLoadedAt < AD_TTL_MS) {
                return;
            }
            bannerLoaded = false;
        }
        adView.setVisibility(View.VISIBLE);
        bannerLoading = true;
        adView.loadAd(this.adRequest);
    }

    @Override
    public void ocultarBanner() {
        mainHandler.post(
                () -> {
                    if (adView != null) {
                        adView.setVisibility(View.GONE);
                    }
                });
    }

    @Override
    public void mostrarBanner() {
        if (destroyed || isAdDisabled()) {
            ocultarBanner();
            return;
        }
        mainHandler.post(
                () -> {
                    if (adView == null) {
                        return;
                    }
                    if (!adsReady) {
                        bannerLoadPending = true;
                        return;
                    }
                    adView.setVisibility(View.VISIBLE);
                    if (bannerLoaded && bannerLoadedAt > 0
                            && System.currentTimeMillis() - bannerLoadedAt >= AD_TTL_MS) {
                        bannerLoaded = false;
                    }
                    if (!bannerLoaded && !bannerLoading) {
                        cargarBanner();
                    }
                });
    }

    public AdRequest getAdReques() {
        return this.adRequest;
    }

    @Override
    public void resumenBanner() {
        activityPaused = false;
        if (adView != null) {
            adView.resume();
        }
    }

    @Override
    public void pausarBanner() {
        activityPaused = true;
        if (adView != null) {
            adView.pause();
        }
    }

    @Override
    public void disposeBanner() {
        destroyed = true;
        mainHandler.removeCallbacksAndMessages(null);
        if (adView != null) {
            adView.destroy();
            adView = null;
        }
        mInterstitialAd = null;
        interstitialLoadedAt = 0;
        rewardedAd = null;
        rewardedLoadedAt = 0;
        clearPendingRewarded();
    }

    public InterstitialAd getMInterstitialAd() {
        return this.mInterstitialAd;
    }

    public void setMInterstitialAd(InterstitialAd mInterstitialAd) {
        this.mInterstitialAd = mInterstitialAd;
        this.interstitialLoadedAt = mInterstitialAd != null ? System.currentTimeMillis() : 0;
    }
}
