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
import com.wortise.ads.AdError;
import com.wortise.ads.AdSize;
import com.wortise.ads.WortiseSdk;
import com.wortise.ads.banner.BannerAd;
import com.wortise.ads.interstitial.InterstitialAd;
import com.wortise.ads.rewarded.RewardedAd;
import com.wortise.ads.rewarded.models.Reward;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.List;

import kotlin.Unit;

/**
 * Gestor unificado de publicidad para Flash SPI Tool sobre el SDK de Wortise.
 * Controla Banners Adaptativos, Interstitials y Rewarded Video con soporte para
 * omisión absoluta cuando el usuario tiene Pro activo (Pase 15min o Compra Permanente).
 *
 * Política de carga:
 * - El banner se pide una sola vez y Wortise lo refresca solo.
 * - El interstitial y el rewarded se cargan bajo demanda, con caducidad de 60 min.
 * - El interstitial se precarga al abrir el visor HEX; si al volver aún no ha
 *   llegado, solo se muestra dentro de los 3 s siguientes y, si no, se descarta
 *   para no aparecer por sorpresa mientras el usuario hace otra cosa.
 * - Si el usuario pulsa "Ver video" y el rewarded aún no está listo, se muestra
 *   automáticamente en cuanto termina la carga.
 */
public class MostrarPublicidad implements Publicidad {

    private static final String TAG = "MostrarPublicidad";

    // Cooldown mínimo de 3 minutos entre interstitials para proteger la experiencia del usuario.
    private static final long COOLDOWN_DEFAULT_MS = 3 * 60 * 1000;
    // Los anuncios cargados se descartan a la hora para evitar mostrarlos caducados.
    private static final long AD_TTL_MS = 60 * 60 * 1000;
    // Si el usuario pulsa "Ver video", esperamos como máximo esto a que cargue.
    private static final long REWARDED_PENDING_TIMEOUT_MS = 20 * 1000;
    // Si el interstitial se pidió pero no llegó a mostrarse, no lo mostramos
    // mucho después: un anuncio que aparece 15 s más tarde, mientras el usuario
    // ya está en otra tarea, parece un fallo de la app.
    private static final long INTERSTITIAL_PENDING_TIMEOUT_MS = 3 * 1000;
    // Si un interstitial no se puede mostrar, reintentamos la carga más tarde.
    private static final long INTERSTITIAL_RETRY_MS = 45 * 1000;

    private static volatile boolean sdkWaitRegistered = false;
    private static final List<Runnable> pendingInitCallbacks = new ArrayList<>();

    private final WeakReference<Activity> actividadRef;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    private BannerAd bannerAd;
    private boolean bannerLoading = false;
    private boolean bannerLoaded = false;
    private long bannerLoadedAt = 0;
    private int bannerRetryCount = 0;
    private static final int MAX_BANNER_RETRIES = 1;

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
    private OnRewardEarnedListener pendingRewardListener;
    private Runnable pendingRewardedDismissListener;
    private OnRewardEarnedListener activeRewardListener;
    private Runnable activeDismissListener;

    private AdsDisabledChecker adsDisabledChecker;
    private boolean destroyed = false;
    private boolean activityPaused = false;
    private boolean consentResolved = false;
    private boolean adsAllowed = false;
    private boolean adsReady = false;
    private boolean bannerLoadPending = false;

    public interface OnConsentResolvedListener {
        void onConsentResolved(boolean canRequestAds);
    }

    private OnConsentResolvedListener onConsentResolvedListener;

    public void setOnConsentResolvedListener(OnConsentResolvedListener listener) {
        this.onConsentResolvedListener = listener;
        if (consentResolved && listener != null) {
            listener.onConsentResolved(adsAllowed);
        }
    }

    public MostrarPublicidad(Activity actividad) {
        this.actividadRef = new WeakReference<>(actividad);

        requestConsent(actividad);

        bannerAd = new BannerAd(actividad);
        bannerAd.setAdSize(getAdaptiveBannerAdSize(actividad));
        bannerAd.setAdUnitId(WortiseAdUnits.BANNER);
        bannerAd.setVisibility(View.GONE);
        bannerAd.setListener(
                new BannerAd.Listener() {
                    @Override
                    public void onBannerLoaded(@NonNull BannerAd ad) {
                        bannerLoaded = true;
                        bannerLoadedAt = System.currentTimeMillis();
                        bannerLoading = false;
                        bannerRetryCount = 0;
                        Log.d(TAG, "Banner adaptativo cargado exitosamente.");
                        if (isAdDisabled()) {
                            ad.setVisibility(View.GONE);
                        } else {
                            ad.setVisibility(View.VISIBLE);
                        }
                    }

                    @Override
                    public void onBannerFailedToLoad(@NonNull BannerAd ad, @NonNull AdError error) {
                        bannerLoaded = false;
                        bannerLoading = false;
                        Log.w(TAG, "Error cargando banner: " + error.getMessage());
                        ad.setVisibility(View.GONE);
                        if (!destroyed && !activityPaused && !isAdDisabled()
                                && error != AdError.NO_FILL
                                && bannerRetryCount < MAX_BANNER_RETRIES) {
                            bannerRetryCount++;
                            mainHandler.postDelayed(() -> {
                                if (!destroyed && !activityPaused && !isAdDisabled()
                                        && !bannerLoaded && !bannerLoading) {
                                    cargarBanner();
                                }
                            }, 5000);
                        }
                    }

                    @Override
                    public void onBannerImpression(@NonNull BannerAd ad) {
                        Log.d(TAG, "Impresión de banner registrada.");
                    }
                });

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
     * "Opciones de privacidad". Si concede el consentimiento, se cargan los
     * anuncios pendientes.
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
                initializeMobileAds(context, () -> {
                    if (destroyed || isAdDisabled()) {
                        return;
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
                    if (onConsentResolvedListener != null) {
                        try {
                            onConsentResolvedListener.onConsentResolved(true);
                        } catch (Exception e) {
                            Log.e(TAG, "Error en onConsentResolvedListener", e);
                        }
                    }
                });
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
            if (onConsentResolvedListener != null) {
                try {
                    onConsentResolvedListener.onConsentResolved(false);
                } catch (Exception e) {
                    Log.e(TAG, "Error en onConsentResolvedListener", e);
                }
            }
        }
    }

    public static boolean isMobileAdsInitialized() {
        return WortiseSdk.isReady();
    }

    public static void initializeMobileAds(Context context, @Nullable Runnable onComplete) {
        if (WortiseSdk.isReady()) {
            if (onComplete != null) {
                new Handler(Looper.getMainLooper()).post(onComplete);
            }
            return;
        }

        synchronized (pendingInitCallbacks) {
            if (onComplete != null) {
                pendingInitCallbacks.add(onComplete);
            }
            if (sdkWaitRegistered) {
                return;
            }
            sdkWaitRegistered = true;
        }

        WortiseSdk.wait(
                () -> {
                    new Handler(Looper.getMainLooper())
                            .post(
                                    () -> {
                                        List<Runnable> callbacks;
                                        synchronized (pendingInitCallbacks) {
                                            sdkWaitRegistered = false;
                                            callbacks = new ArrayList<>(pendingInitCallbacks);
                                            pendingInitCallbacks.clear();
                                        }
                                        for (Runnable cb : callbacks) {
                                            try {
                                                cb.run();
                                            } catch (Exception e) {
                                                Log.e(TAG, "Error en callback de inicialización", e);
                                            }
                                        }
                                    });
                    return Unit.INSTANCE;
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
     * Calcula el tamaño óptimo de banner adaptativo anclado según el ancho de pantalla.
     */
    @SuppressWarnings("deprecation")
    private AdSize getAdaptiveBannerAdSize(Activity activity) {
        if (activity == null) {
            return AdSize.HEIGHT_50;
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
            return AdSize.getAnchoredAdaptiveBannerAdSize(activity, adWidth);
        } catch (Exception e) {
            Log.e(TAG, "Error calculando banner adaptativo, fallback a 320x50", e);
            return AdSize.HEIGHT_50;
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
                mInterstitialAd.showAd(actividad);
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

    /**
     * Precarga el intersticial sin mostrarlo. Se llama al abrir el visor HEX:
     * el usuario pasa unos segundos leyendo la memoria y, al volver, el anuncio
     * ya está listo para mostrarse sin esperas ni apariciones tardías.
     */
    public void precargarInterstitial() {
        cargarInterstial();
    }

    public void cargarInterstial() {
        if (destroyed || !adsReady || isAdDisabled()) {
            return;
        }

        if (!WortiseSdk.isReady()) {
            Activity context = actividadRef.get();
            if (context != null) {
                initializeMobileAds(context, this::cargarInterstial);
            }
            return;
        }

        if (isLoadingInterstitial || isInterstitialFresh()) {
            return;
        }

        cleanupInterstitial();

        Activity actividad = actividadRef.get();
        if (actividad == null || actividad.isFinishing() || actividad.isDestroyed()) {
            return;
        }

        isLoadingInterstitial = true;
        mInterstitialAd = new InterstitialAd(actividad, WortiseAdUnits.INTERSTITIAL);
        mInterstitialAd.setListener(interstitialListener);
        mInterstitialAd.loadAd();
    }

    private final InterstitialAd.Listener interstitialListener =
            new InterstitialAd.Listener() {
                @Override
                public void onInterstitialLoaded(@NonNull InterstitialAd ad) {
                    isLoadingInterstitial = false;
                    interstitialLoadedAt = System.currentTimeMillis();

                    if (interstitialShowPending) {
                        long edad = System.currentTimeMillis() - interstitialPendingSince;
                        interstitialShowPending = false;
                        if (edad <= INTERSTITIAL_PENDING_TIMEOUT_MS) {
                            Activity actual = actividadRef.get();
                            if (actual != null && !actual.isFinishing() && !actual.isDestroyed()) {
                                ultimoTiempoInterstitial = System.currentTimeMillis();
                                ad.showAd(actual);
                            }
                        }
                    }
                }

                @Override
                public void onInterstitialFailedToLoad(
                        @NonNull InterstitialAd ad, @NonNull AdError error) {
                    Log.w(TAG, "Fallo al cargar Interstitial: " + error.getMessage());
                    interstitialLoadedAt = 0;
                    isLoadingInterstitial = false;
                    interstitialShowPending = false;
                    if (mInterstitialAd == ad) {
                        mInterstitialAd = null;
                    }
                    ad.destroy();
                }

                @Override
                public void onInterstitialDismissed(@NonNull InterstitialAd ad) {
                    Log.d(TAG, "Interstitial cerrado por el usuario.");
                    interstitialLoadedAt = 0;
                    isLoadingInterstitial = false;
                    if (mInterstitialAd == ad) {
                        mInterstitialAd = null;
                    }
                    ad.destroy();
                    scheduleInterstitialReload(INTERSTITIAL_RETRY_MS);
                }

                @Override
                public void onInterstitialFailedToShow(
                        @NonNull InterstitialAd ad, @NonNull AdError error) {
                    Log.w(TAG, "Fallo al mostrar Interstitial: " + error.getMessage());
                    interstitialLoadedAt = 0;
                    isLoadingInterstitial = false;
                    if (mInterstitialAd == ad) {
                        mInterstitialAd = null;
                    }
                    ad.destroy();
                    scheduleInterstitialReload(INTERSTITIAL_RETRY_MS);
                }

                @Override
                public void onInterstitialShown(@NonNull InterstitialAd ad) {
                    Log.d(TAG, "Interstitial mostrado en pantalla completa.");
                }
            };

    private boolean isInterstitialFresh() {
        return mInterstitialAd != null
                && mInterstitialAd.isAvailable()
                && interstitialLoadedAt > 0
                && (System.currentTimeMillis() - interstitialLoadedAt < AD_TTL_MS);
    }

    private void cleanupInterstitial() {
        InterstitialAd ad = mInterstitialAd;
        mInterstitialAd = null;
        interstitialLoadedAt = 0;
        isLoadingInterstitial = false;
        if (ad != null) {
            ad.destroy();
        }
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

    // --- REWARDED VIDEO ADS (PASE 15 MINUTOS) ---

    @Override
    public void cargarRewardedAd() {
        mainHandler.post(
                () -> {
                    try {
                        if (destroyed || !adsReady || isAdDisabled()) {
                            return;
                        }
                        if (!WortiseSdk.isReady()) {
                            Activity context = actividadRef.get();
                            if (context != null) {
                                initializeMobileAds(context, this::cargarRewardedAd);
                            }
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
                            rewardedAd.destroy();
                            rewardedAd = null;
                            rewardedLoadedAt = 0;
                        }

                        isRewardedLoading = true;
                        Log.d(TAG, "Iniciando carga de RewardedAd...");
                        rewardedAd = new RewardedAd(actividad, WortiseAdUnits.REWARDED);
                        rewardedAd.setListener(rewardedListener);
                        rewardedAd.loadAd();
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
                && rewardedAd.isAvailable()
                && rewardedLoadedAt > 0
                && (System.currentTimeMillis() - rewardedLoadedAt < AD_TTL_MS);
    }

    @VisibleForTesting
    public void setRewardedAd(@Nullable RewardedAd rewardedAd) {
        if (this.rewardedAd != null && this.rewardedAd != rewardedAd) {
            this.rewardedAd.destroy();
        }
        this.rewardedAd = rewardedAd;
        this.rewardedLoadedAt = rewardedAd != null ? System.currentTimeMillis() : 0;
    }

    @Override
    public void mostrarRewardedAd(
            Activity activity,
            OnRewardEarnedListener rewardListener,
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
        OnRewardEarnedListener rewardListener = pendingRewardListener;
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
            OnRewardEarnedListener rewardListener,
            Runnable onDismissedListener) {

        RewardedAd adParaMostrar = rewardedAd;
        rewardedAd = null;
        rewardedLoadedAt = 0;

        if (adParaMostrar == null || !adParaMostrar.isAvailable()) {
            if (onDismissedListener != null) {
                onDismissedListener.run();
            }
            return;
        }

        activeRewardListener = rewardListener;
        activeDismissListener = onDismissedListener;
        adParaMostrar.showAd(activity);
    }

    private final RewardedAd.Listener rewardedListener =
            new RewardedAd.Listener() {
                @Override
                public void onRewardedLoaded(@NonNull RewardedAd ad) {
                    isRewardedLoading = false;
                    rewardedLoadedAt = System.currentTimeMillis();
                    Log.d(TAG, "RewardedAd cargado con éxito.");
                    showPendingRewardedIfReady();
                }

                @Override
                public void onRewardedFailedToLoad(
                        @NonNull RewardedAd ad, @NonNull AdError error) {
                    isRewardedLoading = false;
                    rewardedLoadedAt = 0;
                    if (rewardedAd == ad) {
                        rewardedAd = null;
                    }
                    Log.w(TAG, "Error cargando RewardedAd: " + error.getMessage());
                    if (rewardedShowPending) {
                        Runnable dismissed = pendingRewardedDismissListener;
                        clearPendingRewarded();
                        notifyRewardedLoadFailed(dismissed);
                    }
                }

                @Override
                public void onRewardedCompleted(
                        @NonNull RewardedAd ad, @NonNull Reward reward) {
                    OnRewardEarnedListener listener = activeRewardListener;
                    activeRewardListener = null;
                    if (listener != null) {
                        listener.onRewardEarned();
                    }
                }

                @Override
                public void onRewardedDismissed(@NonNull RewardedAd ad) {
                    Log.d(TAG, "RewardedAd cerrado por el usuario.");
                    activeRewardListener = null;
                    Runnable dismissed = activeDismissListener;
                    activeDismissListener = null;
                    ad.destroy();
                    if (!destroyed && !isAdDisabled()) {
                        cargarRewardedAd();
                    }
                    if (dismissed != null) {
                        dismissed.run();
                    }
                }

                @Override
                public void onRewardedFailedToShow(
                        @NonNull RewardedAd ad, @NonNull AdError error) {
                    Log.w(TAG, "Fallo al mostrar RewardedAd: " + error.getMessage());
                    activeRewardListener = null;
                    Runnable dismissed = activeDismissListener;
                    activeDismissListener = null;
                    ad.destroy();
                    if (!destroyed && !isAdDisabled()) {
                        cargarRewardedAd();
                    }
                    if (dismissed != null) {
                        dismissed.run();
                    }
                }

                @Override
                public void onRewardedShown(@NonNull RewardedAd ad) {
                    Log.d(TAG, "RewardedAd presentado en pantalla completa.");
                }
            };

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

    public BannerAd getBanner() {
        return bannerAd;
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
        if (!WortiseSdk.isReady()) {
            bannerLoadPending = true;
            Activity context = actividadRef.get();
            if (context != null) {
                initializeMobileAds(context, this::cargarBanner);
            }
            return;
        }
        if (bannerAd == null || bannerLoading) {
            return;
        }
        if (bannerLoaded) {
            if (bannerLoadedAt > 0
                    && System.currentTimeMillis() - bannerLoadedAt < AD_TTL_MS) {
                bannerAd.setVisibility(View.VISIBLE);
                return;
            }
            bannerLoaded = false;
        }
        bannerLoading = true;
        bannerAd.loadAd();
    }

    @Override
    public void ocultarBanner() {
        mainHandler.post(
                () -> {
                    if (bannerAd != null) {
                        bannerAd.setVisibility(View.GONE);
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
                    if (bannerAd == null) {
                        return;
                    }
                    if (!adsReady) {
                        bannerLoadPending = true;
                        return;
                    }
                    if (bannerLoaded) {
                        bannerAd.setVisibility(View.VISIBLE);
                    } else if (!bannerLoading) {
                        cargarBanner();
                    }
                });
    }

    @Override
    public void resumenBanner() {
        activityPaused = false;
        if (bannerAd != null) {
            bannerAd.resume();
        }
    }

    @Override
    public void pausarBanner() {
        activityPaused = true;
        if (bannerAd != null) {
            bannerAd.pause();
        }
    }

    @Override
    public void disposeBanner() {
        destroyed = true;
        mainHandler.removeCallbacksAndMessages(null);
        if (bannerAd != null) {
            bannerAd.destroy();
            bannerAd = null;
        }
        cleanupInterstitial();
        if (rewardedAd != null) {
            rewardedAd.destroy();
            rewardedAd = null;
        }
        rewardedLoadedAt = 0;
        activeRewardListener = null;
        activeDismissListener = null;
        clearPendingRewarded();
    }

    public InterstitialAd getMInterstitialAd() {
        return this.mInterstitialAd;
    }

    public void setMInterstitialAd(InterstitialAd mInterstitialAd) {
        if (this.mInterstitialAd != null && this.mInterstitialAd != mInterstitialAd) {
            this.mInterstitialAd.destroy();
        }
        this.mInterstitialAd = mInterstitialAd;
        this.interstitialLoadedAt = mInterstitialAd != null ? System.currentTimeMillis() : 0;
    }
}
