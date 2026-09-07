package com.diamon.curso.billing;

import android.app.Activity;
import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import androidx.annotation.NonNull;

import com.android.billingclient.api.BillingClient;
import com.android.billingclient.api.BillingClientStateListener;
import com.android.billingclient.api.BillingFlowParams;
import com.android.billingclient.api.BillingResult;
import com.android.billingclient.api.ConsumeParams;
import com.android.billingclient.api.ConsumeResponseListener;
import com.android.billingclient.api.PendingPurchasesParams;
import com.android.billingclient.api.ProductDetails;
import com.android.billingclient.api.Purchase;
import com.android.billingclient.api.PurchasesUpdatedListener;
import com.android.billingclient.api.QueryProductDetailsParams;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class BillingManager implements PurchasesUpdatedListener {

    private static final String TAG = "BillingManager";
    public static final String PRODUCT_ID_PIZZA = "donacion_pizza";

    public interface BillingListener {
        void onProductReady(String productId, String formattedPrice);
        void onPurchaseSuccess(String productId);
        void onPurchaseError(String errorMessage);
    }

    private final Context context;
    private final BillingListener listener;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private BillingClient billingClient;
    private final Map<String, ProductDetails> availableProducts = new HashMap<>();
    private boolean isConnected = false;

    public BillingManager(Context context, BillingListener listener) {
        this.context = context.getApplicationContext();
        this.listener = listener;
        initBillingClient();
    }

    private void initBillingClient() {
        billingClient = BillingClient.newBuilder(context)
                .setListener(this)
                .enablePendingPurchases(PendingPurchasesParams.newBuilder().enableOneTimeProducts().build())
                .build();

        startConnection();
    }

    public void startConnection() {
        if (billingClient == null) return;
        billingClient.startConnection(new BillingClientStateListener() {
            @Override
            public void onBillingSetupFinished(@NonNull BillingResult billingResult) {
                if (billingResult.getResponseCode() == BillingClient.BillingResponseCode.OK) {
                    isConnected = true;
                    Log.d(TAG, "Billing client connected successfully.");
                    queryProducts();
                } else {
                    isConnected = false;
                    Log.w(TAG, "Billing setup failed: " + billingResult.getDebugMessage());
                }
            }

            @Override
            public void onBillingServiceDisconnected() {
                isConnected = false;
                Log.w(TAG, "Billing service disconnected.");
            }
        });
    }

    private void queryProducts() {
        if (!isConnected || billingClient == null) return;

        List<QueryProductDetailsParams.Product> productList = new ArrayList<>();
        productList.add(QueryProductDetailsParams.Product.newBuilder()
                .setProductId(PRODUCT_ID_PIZZA)
                .setProductType(BillingClient.ProductType.INAPP)
                .build());

        QueryProductDetailsParams params = QueryProductDetailsParams.newBuilder()
                .setProductList(productList)
                .build();

        billingClient.queryProductDetailsAsync(params, (billingResult, queryProductDetailsResult) -> {
            if (billingResult.getResponseCode() == BillingClient.BillingResponseCode.OK && queryProductDetailsResult != null) {
                List<ProductDetails> productDetailsList = queryProductDetailsResult.getProductDetailsList();
                if (productDetailsList != null) {
                    for (ProductDetails pd : productDetailsList) {
                        availableProducts.put(pd.getProductId(), pd);
                        String price = "$5.00";
                        if (pd.getOneTimePurchaseOfferDetails() != null) {
                            price = pd.getOneTimePurchaseOfferDetails().getFormattedPrice();
                        }
                        final String finalPrice = price;
                        final String finalId = pd.getProductId();
                        mainHandler.post(() -> {
                            if (listener != null) {
                                listener.onProductReady(finalId, finalPrice);
                            }
                        });
                    }
                }
            } else {
                Log.w(TAG, "Failed to query products: " + billingResult.getDebugMessage());
            }
        });
    }

    public String getFormattedPrice(String productId, String fallbackPrice) {
        ProductDetails pd = availableProducts.get(productId);
        if (pd != null && pd.getOneTimePurchaseOfferDetails() != null) {
            return pd.getOneTimePurchaseOfferDetails().getFormattedPrice();
        }
        return fallbackPrice;
    }

    public void launchPurchaseFlow(Activity activity, String productId) {
        if (!isConnected) {
            startConnection();
            if (listener != null) {
                listener.onPurchaseError("Conectando con Google Play... Intenta de nuevo en unos segundos.");
            }
            return;
        }

        ProductDetails pd = availableProducts.get(productId);
        if (pd == null) {
            if (listener != null) {
                listener.onPurchaseError("El producto no está listo o la consola aún lo está procesando.");
            }
            queryProducts();
            return;
        }

        BillingFlowParams.ProductDetailsParams detailsParams =
                BillingFlowParams.ProductDetailsParams.newBuilder()
                        .setProductDetails(pd)
                        .build();

        BillingFlowParams flowParams = BillingFlowParams.newBuilder()
                .setProductDetailsParamsList(Collections.singletonList(detailsParams))
                .build();

        BillingResult result = billingClient.launchBillingFlow(activity, flowParams);
        if (result.getResponseCode() != BillingClient.BillingResponseCode.OK) {
            Log.e(TAG, "Launch billing flow error: " + result.getDebugMessage());
            if (listener != null) {
                listener.onPurchaseError(result.getDebugMessage());
            }
        }
    }

    @Override
    public void onPurchasesUpdated(@NonNull BillingResult billingResult, List<Purchase> purchases) {
        int responseCode = billingResult.getResponseCode();
        if (responseCode == BillingClient.BillingResponseCode.OK && purchases != null) {
            for (Purchase purchase : purchases) {
                handlePurchase(purchase);
            }
        } else if (responseCode == BillingClient.BillingResponseCode.USER_CANCELED) {
            Log.i(TAG, "Compra cancelada por el usuario.");
        } else {
            Log.w(TAG, "Error en compra: " + billingResult.getDebugMessage());
            if (listener != null) {
                mainHandler.post(() -> listener.onPurchaseError(billingResult.getDebugMessage()));
            }
        }
    }

    private void handlePurchase(Purchase purchase) {
        if (purchase.getPurchaseState() == Purchase.PurchaseState.PURCHASED) {
            // Consumir el producto para permitir volver a donar en el futuro
            ConsumeParams consumeParams = ConsumeParams.newBuilder()
                    .setPurchaseToken(purchase.getPurchaseToken())
                    .build();

            billingClient.consumeAsync(consumeParams, new ConsumeResponseListener() {
                @Override
                public void onConsumeResponse(@NonNull BillingResult billingResult, @NonNull String purchaseToken) {
                    if (billingResult.getResponseCode() == BillingClient.BillingResponseCode.OK) {
                        Log.i(TAG, "Producto consumido exitosamente.");
                        mainHandler.post(() -> {
                            if (listener != null) {
                                String prodId = purchase.getProducts().isEmpty() ? PRODUCT_ID_PIZZA : purchase.getProducts().get(0);
                                listener.onPurchaseSuccess(prodId);
                            }
                        });
                    } else {
                        Log.e(TAG, "Error consumiendo producto: " + billingResult.getDebugMessage());
                    }
                }
            });
        }
    }

    public void destroy() {
        if (billingClient != null && billingClient.isReady()) {
            billingClient.endConnection();
        }
    }
}
