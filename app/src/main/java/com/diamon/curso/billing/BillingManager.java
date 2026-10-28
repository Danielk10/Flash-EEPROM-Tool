package com.diamon.curso.billing;

import android.app.Activity;
import android.app.ActivityOptions;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.provider.Settings;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;

import com.android.billingclient.api.AcknowledgePurchaseParams;
import com.android.billingclient.api.BillingClient;
import com.android.billingclient.api.BillingClientStateListener;
import com.android.billingclient.api.BillingFlowParams;
import com.android.billingclient.api.BillingResult;
import com.android.billingclient.api.PendingPurchasesParams;
import com.android.billingclient.api.ProductDetails;
import com.android.billingclient.api.Purchase;
import com.android.billingclient.api.PurchasesUpdatedListener;
import com.android.billingclient.api.QueryProductDetailsParams;
import com.android.billingclient.api.QueryPurchasesParams;
import com.diamon.curso.R;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Gestor de facturación y compras in-app para Flash SPI Tool utilizando Google Play Billing Library 9.1.0.
 * Maneja la compra del producto permanente no consumible "remove_ads_pro"
 * (con retrocompatibilidad para "remove_ads" y "donacion_pizza") para eliminar publicidad de por vida
 * y desbloquear la suite Flash SPI Tool Pro.
 */
public class BillingManager implements PurchasesUpdatedListener {

    private static final String TAG = "BillingManager";

    /** ID del producto principal en Google Play Console para la suite Pro permanente sin anuncios. */
    public static final String PRODUCT_ID_REMOVE_ADS = "remove_ads_pro";
    /** ID alternativo para retrocompatibilidad. */
    public static final String PRODUCT_ID_REMOVE_ADS_ALT = "remove_ads";
    /** ID histórico de donación de pizza (mantenido para retrocompatibilidad total sin consumo). */
    public static final String PRODUCT_ID_PIZZA = "donacion_pizza";

    public static final String PREFS_NAME = "billing_prefs";
    public static final String KEY_ADS_REMOVED = "is_ads_removed";
    public static final String KEY_PRO_USER = "is_pro_user";
    private static final long LAUNCH_DEBOUNCE_MS = 2000L;

    /**
     * Interfaz de escucha para eventos de compra y estado de facturación.
     */
    public interface BillingListener {
        default void onAdsRemovedChanged(boolean adsRemoved) {}
        default void onProStatusChanged(boolean isProPurchased) { onAdsRemovedChanged(isProPurchased); }
        default void onPriceLoaded(String formattedPrice) {}
        default void onBillingError(String errorMessage) {}
        default void onPurchaseError(String errorMessage) { onBillingError(errorMessage); }
        default void onProductReady(String productId, String formattedPrice) { onPriceLoaded(formattedPrice); }
        default void onPurchaseSuccess(String productId) { onAdsRemovedChanged(true); }
    }

    private final Context context;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final BillingClient billingClient;
    @Nullable
    private volatile BillingListener listener;
    @Nullable
    private ProductDetails removeAdsDetails;
    private final Map<String, ProductDetails> availableProducts = new HashMap<>();
    private boolean isServiceConnected = false;
    private boolean isAdsRemovedCached = false;
    private long lastLaunchTimeMs = 0L;
    private volatile boolean destroyed = false;

    public BillingManager(@NonNull Context context, @Nullable BillingListener listener) {
        this(context, listener, null);
    }

    @VisibleForTesting
    public BillingManager(@NonNull Context context, @Nullable BillingListener listener, @Nullable BillingClient billingClient) {
        this.context = context.getApplicationContext();
        this.listener = listener;

        if (billingClient != null) {
            this.billingClient = billingClient;
        } else {
            this.billingClient = BillingClient.newBuilder(this.context)
                    .setListener(this)
                    .enablePendingPurchases(
                            PendingPurchasesParams.newBuilder()
                                    .enableOneTimeProducts()
                                    .build()
                    )
                    .build();
        }

        // Cargar estado inicial desde SharedPreferences
        isAdsRemovedCached = isAdsRemoved();

        if (billingClient == null) {
            startConnection();
        }
    }

    public void setBillingListener(@Nullable BillingListener listener) {
        this.listener = listener;
    }

    @Nullable
    public BillingListener getBillingListener() {
        return listener;
    }

    /**
     * Inicia la conexión con el servicio de Google Play Billing.
     */
    public void startConnection() {
        if (destroyed) {
            return;
        }
        if (billingClient.isReady()) {
            isServiceConnected = true;
            queryActivePurchases();
            queryProducts();
            return;
        }

        billingClient.startConnection(new BillingClientStateListener() {
            @Override
            public void onBillingSetupFinished(@NonNull BillingResult billingResult) {
                if (destroyed) {
                    billingClient.endConnection();
                    return;
                }
                if (billingResult.getResponseCode() == BillingClient.BillingResponseCode.OK) {
                    isServiceConnected = true;
                    Log.d(TAG, "Billing client connected successfully.");
                    queryActivePurchases();
                    queryProducts();
                } else {
                    isServiceConnected = false;
                    Log.w(TAG, "Billing setup failed: " + billingResult.getDebugMessage()
                            + " (Code: " + billingResult.getResponseCode() + ")");
                }
            }

            @Override
            public void onBillingServiceDisconnected() {
                isServiceConnected = false;
                Log.w(TAG, "Billing service disconnected. Will retry on next request.");
            }
        });
    }

    /**
     * Consulta los detalles de los productos in-app en Google Play.
     */
    public void queryProducts() {
        if (destroyed) {
            return;
        }
        List<QueryProductDetailsParams.Product> productList = new ArrayList<>();
        productList.add(QueryProductDetailsParams.Product.newBuilder()
                .setProductId(PRODUCT_ID_REMOVE_ADS)
                .setProductType(BillingClient.ProductType.INAPP)
                .build());
        productList.add(QueryProductDetailsParams.Product.newBuilder()
                .setProductId(PRODUCT_ID_REMOVE_ADS_ALT)
                .setProductType(BillingClient.ProductType.INAPP)
                .build());
        productList.add(QueryProductDetailsParams.Product.newBuilder()
                .setProductId(PRODUCT_ID_PIZZA)
                .setProductType(BillingClient.ProductType.INAPP)
                .build());

        QueryProductDetailsParams params = QueryProductDetailsParams.newBuilder()
                .setProductList(productList)
                .build();

        billingClient.queryProductDetailsAsync(params, (billingResult, queryResult) -> {
            if (destroyed) {
                return;
            }
            if (billingResult.getResponseCode() == BillingClient.BillingResponseCode.OK
                    && queryResult != null
                    && queryResult.getProductDetailsList() != null
                    && !queryResult.getProductDetailsList().isEmpty()) {

                ProductDetails selectedDetails = null;
                for (ProductDetails details : queryResult.getProductDetailsList()) {
                    availableProducts.put(details.getProductId(), details);
                    if (PRODUCT_ID_PIZZA.equals(details.getProductId())) {
                        selectedDetails = details;
                    } else if (selectedDetails == null && PRODUCT_ID_REMOVE_ADS.equals(details.getProductId())) {
                        selectedDetails = details;
                    } else if (selectedDetails == null && PRODUCT_ID_REMOVE_ADS_ALT.equals(details.getProductId())) {
                        selectedDetails = details;
                    }

                    if (details.getOneTimePurchaseOfferDetails() != null) {
                        String formattedPrice = details.getOneTimePurchaseOfferDetails().getFormattedPrice();
                        mainHandler.post(() -> {
                            BillingListener currentListener = listener;
                            if (!destroyed && currentListener != null) {
                                currentListener.onProductReady(details.getProductId(), formattedPrice);
                            }
                        });
                    }
                }

                if (selectedDetails == null) {
                    selectedDetails = queryResult.getProductDetailsList().get(0);
                }
                removeAdsDetails = selectedDetails;
                Log.d(TAG, "Product details loaded: " + removeAdsDetails.getName()
                        + " (" + removeAdsDetails.getProductId() + ")");

                ProductDetails.OneTimePurchaseOfferDetails offerDetails =
                        removeAdsDetails.getOneTimePurchaseOfferDetails();
                if (offerDetails != null) {
                    final String formattedPrice = offerDetails.getFormattedPrice();
                    mainHandler.post(() -> {
                        BillingListener currentListener = listener;
                        if (!destroyed && currentListener != null) {
                            currentListener.onPriceLoaded(formattedPrice);
                        }
                    });
                }
            } else {
                Log.w(TAG, "Failed to load product details: " + billingResult.getDebugMessage()
                        + " (Code: " + billingResult.getResponseCode() + ")");
            }
        });
    }

    /**
     * Retorna el precio formateado de un producto específico, con fallback.
     */
    public String getFormattedPrice(String productId, String fallbackPrice) {
        ProductDetails pd = availableProducts.get(productId);
        if (pd != null && pd.getOneTimePurchaseOfferDetails() != null) {
            return pd.getOneTimePurchaseOfferDetails().getFormattedPrice();
        }
        return fallbackPrice;
    }

    /**
     * Lanza la compra del producto Pro principal "remove_ads_pro" (o disponible).
     */
    public boolean launchPurchaseFlow(@NonNull Activity activity) {
        if (destroyed) {
            return false;
        }
        if (activity == null || activity.isFinishing() || activity.isDestroyed()) {
            Log.w(TAG, "Cannot launch purchase flow: Activity is null, finishing, or destroyed.");
            return false;
        }

        if (isRunningInTestLab(activity)) {
            Log.w(TAG, "Skipping Play Billing launch flow inside Firebase Test Lab / Play Pre-launch environment.");
            if (listener != null) {
                listener.onBillingError("In-app billing is disabled in automated test environments.");
            }
            return false;
        }

        if (!isBillingFlowCompatible()) {
            String errorMsg = "In-app billing is not available on this OS build. Please update your device.";
            Log.e(TAG, errorMsg);
            if (listener != null) {
                listener.onBillingError(errorMsg);
            }
            return false;
        }

        if (!billingClient.isReady()) {
            String errorMsg = "Billing service is not connected. Reconnecting...";
            Log.w(TAG, errorMsg);
            startConnection();
            if (listener != null) {
                listener.onBillingError(errorMsg);
            }
            return false;
        }

        if (removeAdsDetails == null) {
            String errorMsg = "Product details not available. Cannot launch purchase flow.";
            Log.w(TAG, errorMsg);
            queryProducts();
            if (listener != null) {
                listener.onBillingError(errorMsg);
            }
            return false;
        }

        long now = SystemClock.elapsedRealtime();
        if (lastLaunchTimeMs > 0L && (now - lastLaunchTimeMs) < LAUNCH_DEBOUNCE_MS) {
            Log.w(TAG, "Ignoring rapid duplicate launchPurchaseFlow call.");
            return false;
        }
        lastLaunchTimeMs = now;

        BillingFlowParams.ProductDetailsParams productDetailsParams =
                BillingFlowParams.ProductDetailsParams.newBuilder()
                        .setProductDetails(removeAdsDetails)
                        .build();

        BillingFlowParams billingFlowParams = BillingFlowParams.newBuilder()
                .setProductDetailsParamsList(Collections.singletonList(productDetailsParams))
                .build();

        final BillingResult result;
        try {
            result = billingClient.launchBillingFlow(activity, billingFlowParams);
        } catch (RuntimeException e) {
            lastLaunchTimeMs = 0L;
            String errorMsg = "Failed to launch billing flow: " + e.getMessage();
            Log.e(TAG, errorMsg, e);
            if (listener != null) {
                listener.onBillingError(errorMsg);
            }
            return false;
        }

        boolean success = result != null && result.getResponseCode() == BillingClient.BillingResponseCode.OK;
        if (!success) {
            lastLaunchTimeMs = 0L;
            if (listener != null) {
                String debugMsg = result != null ? result.getDebugMessage() : "Null BillingResult";
                listener.onBillingError("Launch billing flow failed: " + debugMsg);
            }
        }
        return success;
    }

    /**
     * Sobrecarga retrocompatible para lanzar el flujo de compra indicando el ID del producto.
     */
    public void launchPurchaseFlow(@NonNull Activity activity, @NonNull String productId) {
        if (destroyed) {
            return;
        }
        if (activity == null || activity.isFinishing() || activity.isDestroyed()) {
            Log.w(TAG, "Cannot launch purchase flow: Activity is null, finishing, or destroyed.");
            return;
        }

        if (isRunningInTestLab(activity)) {
            Log.w(TAG, "Skipping Play Billing launch flow inside Firebase Test Lab / Play Pre-launch environment.");
            if (listener != null) {
                listener.onPurchaseError("In-app billing is disabled in automated test environments.");
            }
            return;
        }

        if (!isBillingFlowCompatible()) {
            String errorMsg = "In-app billing is not available on this OS build. Please update your device.";
            Log.e(TAG, errorMsg);
            if (listener != null) {
                listener.onPurchaseError(errorMsg);
            }
            return;
        }

        if (!billingClient.isReady()) {
            startConnection();
            if (listener != null) {
                listener.onPurchaseError(context.getString(R.string.str_billing_connecting));
            }
            return;
        }

        ProductDetails pd = availableProducts.get(productId);
        if (pd == null && removeAdsDetails != null && removeAdsDetails.getProductId().equals(productId)) {
            pd = removeAdsDetails;
        }

        if (pd == null) {
            if (listener != null) {
                listener.onPurchaseError(context.getString(R.string.str_billing_not_ready));
            }
            queryProducts();
            return;
        }

        long now = SystemClock.elapsedRealtime();
        if (lastLaunchTimeMs > 0L && (now - lastLaunchTimeMs) < LAUNCH_DEBOUNCE_MS) {
            Log.w(TAG, "Ignoring rapid duplicate launchPurchaseFlow call.");
            return;
        }
        lastLaunchTimeMs = now;

        BillingFlowParams.ProductDetailsParams detailsParams =
                BillingFlowParams.ProductDetailsParams.newBuilder()
                        .setProductDetails(pd)
                        .build();

        BillingFlowParams flowParams = BillingFlowParams.newBuilder()
                .setProductDetailsParamsList(Collections.singletonList(detailsParams))
                .build();

        final BillingResult result;
        try {
            result = billingClient.launchBillingFlow(activity, flowParams);
        } catch (RuntimeException e) {
            lastLaunchTimeMs = 0L;
            Log.e(TAG, "Failed to launch billing flow", e);
            if (listener != null) {
                listener.onPurchaseError("Launch billing flow failed: " + e.getMessage());
            }
            return;
        }

        if (result == null || result.getResponseCode() != BillingClient.BillingResponseCode.OK) {
            lastLaunchTimeMs = 0L;
            String debugMsg = result != null ? result.getDebugMessage() : "Null BillingResult";
            Log.e(TAG, "Launch billing flow error: " + debugMsg);
            if (listener != null) {
                listener.onPurchaseError(debugMsg);
            }
        }
    }

    /**
     * Detecta si el dispositivo se encuentra ejecutando en Firebase Test Lab / Google Play Pre-launch Report.
     */
    private boolean isRunningInTestLab(@NonNull Context ctx) {
        try {
            String testLabSetting = Settings.System.getString(ctx.getContentResolver(), "firebase.test.lab");
            return "true".equalsIgnoreCase(testLabSetting);
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Play Billing 9.1.0 llama sin comprobación adicional a
     * {@code ActivityOptions.setPendingIntentBackgroundActivityStartMode} desde
     * {@code ProxyBillingActivity.onCreate} en Android 14+. Algunas compilaciones de
     * Android 14 no exponen ese método y el flujo de compra termina en
     * {@code NoSuchMethodError} (issue Crashlytics a6e619dc...). Comprobamos la
     * disponibilidad real del método antes de abrir el flujo para degradar a un
     * error controlado en lugar de un crash.
     */
    private static boolean isBillingFlowCompatible() {
        if (Build.VERSION.SDK_INT < 34) {
            return true;
        }
        try {
            return ActivityOptions.class
                    .getMethod("setPendingIntentBackgroundActivityStartMode", int.class)
                    .getReturnType() == ActivityOptions.class;
        } catch (NoSuchMethodException | SecurityException | LinkageError e) {
            Log.w(TAG, "ActivityOptions API unavailable for Play Billing: " + e.getMessage());
            return false;
        }
    }

    @Override
    public void onPurchasesUpdated(@NonNull BillingResult billingResult,
                                   @Nullable List<Purchase> purchases) {
        if (destroyed) {
            return;
        }
        lastLaunchTimeMs = 0L;
        int responseCode = billingResult.getResponseCode();

        if (responseCode == BillingClient.BillingResponseCode.OK && purchases != null) {
            for (Purchase purchase : purchases) {
                handlePurchase(purchase);
            }
        } else if (responseCode == BillingClient.BillingResponseCode.USER_CANCELED) {
            Log.d(TAG, "User canceled the purchase.");
        } else if (responseCode == BillingClient.BillingResponseCode.ITEM_ALREADY_OWNED) {
            Log.d(TAG, "Item already owned. Restoring purchase...");
            setAdsRemoved(true);
        } else {
            String errorMsg = "Purchase failed (" + responseCode + "): " + billingResult.getDebugMessage();
            Log.w(TAG, errorMsg);
            if (listener != null) {
                listener.onBillingError(errorMsg);
            }
        }
    }

    /**
     * Procesa una compra individual: reconoce (acknowledge) compras válidas
     * para hacerlas permanentes de por vida (NO las consume) y actualiza el estado local Pro.
     */
    public void handlePurchase(@NonNull Purchase purchase) {
        List<String> products = purchase.getProducts();
        if (products.contains(PRODUCT_ID_REMOVE_ADS)
                || products.contains(PRODUCT_ID_REMOVE_ADS_ALT)
                || products.contains(PRODUCT_ID_PIZZA)) {

            if (purchase.getPurchaseState() == Purchase.PurchaseState.PURCHASED) {
                if (!purchase.isAcknowledged()) {
                    acknowledgePurchase(purchase);
                } else {
                    setAdsRemoved(true);
                    String prodId = products.isEmpty() ? PRODUCT_ID_REMOVE_ADS : products.get(0);
                    if (listener != null) {
                        listener.onPurchaseSuccess(prodId);
                    }
                }
            } else if (purchase.getPurchaseState() == Purchase.PurchaseState.PENDING) {
                Log.d(TAG, "Purchase is pending. Pro will be unlocked once confirmed.");
            }
        }
    }

    /**
     * Reconoce una compra para hacerla permanente y evitar que Google Play la reembolse automáticamente
     * transcurridos 3 días.
     */
    private void acknowledgePurchase(@NonNull Purchase purchase) {
        AcknowledgePurchaseParams params = AcknowledgePurchaseParams.newBuilder()
                .setPurchaseToken(purchase.getPurchaseToken())
                .build();

        billingClient.acknowledgePurchase(params, billingResult -> {
            if (billingResult.getResponseCode() == BillingClient.BillingResponseCode.OK) {
                Log.d(TAG, "Purchase acknowledged successfully as permanent.");
                setAdsRemoved(true);
                String prodId = purchase.getProducts().isEmpty() ? PRODUCT_ID_REMOVE_ADS : purchase.getProducts().get(0);
                mainHandler.post(() -> {
                    BillingListener currentListener = listener;
                    if (!destroyed && currentListener != null) {
                        currentListener.onPurchaseSuccess(prodId);
                    }
                });
            } else {
                String errorMsg = "Failed to acknowledge purchase: " + billingResult.getDebugMessage();
                Log.w(TAG, errorMsg);
                if (listener != null) {
                    listener.onBillingError(errorMsg);
                }
            }
        });
    }

    /**
     * Consulta las compras activas de la cuenta para restaurar compras previas al iniciar.
     */
    public void queryActivePurchases() {
        if (destroyed) {
            return;
        }
        QueryPurchasesParams params = QueryPurchasesParams.newBuilder()
                .setProductType(BillingClient.ProductType.INAPP)
                .build();

        billingClient.queryPurchasesAsync(params, (billingResult, purchases) -> {
            if (destroyed) {
                return;
            }
            if (billingResult.getResponseCode() == BillingClient.BillingResponseCode.OK) {
                boolean hasActivePurchase = false;
                if (purchases != null) {
                    for (Purchase purchase : purchases) {
                        List<String> prods = purchase.getProducts();
                        if ((prods.contains(PRODUCT_ID_REMOVE_ADS)
                                || prods.contains(PRODUCT_ID_REMOVE_ADS_ALT)
                                || prods.contains(PRODUCT_ID_PIZZA))
                                && purchase.getPurchaseState() == Purchase.PurchaseState.PURCHASED) {
                            hasActivePurchase = true;
                            handlePurchase(purchase);
                            break;
                        }
                    }
                }
                if (!hasActivePurchase && !isAdsRemoved()) {
                    setAdsRemoved(false);
                }
            } else {
                Log.w(TAG, "Failed to query purchases: " + billingResult.getDebugMessage()
                        + " (Code: " + billingResult.getResponseCode() + ")");
            }
        });
    }

    /**
     * Actualiza el estado de anuncios eliminados y Pro en SharedPreferences y notifica al listener.
     */
    @VisibleForTesting
    public void setAdsRemoved(boolean value) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        boolean previousValue = isAdsRemoved();

        prefs.edit()
                .putBoolean(KEY_ADS_REMOVED, value)
                .putBoolean(KEY_PRO_USER, value)
                .apply();

        isAdsRemovedCached = value;

        if (value != previousValue && listener != null) {
            listener.onAdsRemovedChanged(value);
        }
    }

    /**
     * Retorna si los anuncios han sido eliminados por compra in-app permanente.
     */
    public boolean isAdsRemoved() {
        if (isAdsRemovedCached) {
            return true;
        }
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        boolean adsRemoved = prefs.getBoolean(KEY_ADS_REMOVED, false) || prefs.getBoolean(KEY_PRO_USER, false);
        if (adsRemoved) {
            isAdsRemovedCached = true;
        }
        return adsRemoved;
    }

    /**
     * Método estático para verificar rápidamente el estado de compra sin instanciar BillingManager.
     */
    public static boolean isAdsRemoved(@NonNull Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        return prefs.getBoolean(KEY_ADS_REMOVED, false) || prefs.getBoolean(KEY_PRO_USER, false);
    }

    /**
     * Alias para verificar si la compra de Flash SPI Tool Pro fue adquirida.
     */
    public boolean isProPurchased() {
        return isAdsRemoved();
    }

    public boolean isProductAvailable() {
        return removeAdsDetails != null;
    }

    @Nullable
    public ProductDetails getRemoveAdsDetails() {
        return removeAdsDetails;
    }

    @Nullable
    public String getFormattedPrice() {
        if (removeAdsDetails != null && removeAdsDetails.getOneTimePurchaseOfferDetails() != null) {
            return removeAdsDetails.getOneTimePurchaseOfferDetails().getFormattedPrice();
        }
        return null;
    }

    public boolean isServiceConnected() {
        return isServiceConnected;
    }

    @VisibleForTesting
    public void setRemoveAdsDetails(@Nullable ProductDetails details) {
        this.removeAdsDetails = details;
    }

    @VisibleForTesting
    public void setServiceConnected(boolean connected) {
        this.isServiceConnected = connected;
    }

    /**
     * Desconecta de forma segura el cliente de facturación.
     */
    public void destroy() {
        destroyed = true;
        mainHandler.removeCallbacksAndMessages(null);
        if (billingClient != null && billingClient.isReady()) {
            billingClient.endConnection();
        }
        isServiceConnected = false;
        listener = null;
    }

    /**
     * Alias retrocompatible con el ciclo de vida de actividades.
     */
    public void onDestroy() {
        destroy();
    }
}
