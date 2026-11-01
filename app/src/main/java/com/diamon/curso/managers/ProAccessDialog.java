package com.diamon.curso.managers;

import android.app.Activity;
import android.app.AlertDialog;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.diamon.curso.R;
import com.diamon.curso.ads.MostrarPublicidad;
import com.diamon.curso.ads.Publicidad;
import com.diamon.curso.billing.BillingManager;

/**
 * Diálogo modal para la suite Flash SPI Tool Pro.
 * Ofrece acceso a las herramientas técnicas avanzadas y eliminación total de publicidad mediante:
 * 1. 🎬 Ver un video recompensado de AdMob para activar el Pase Libre de 12 Horas.
 * 2. 👑 Compra In-App Permanente mediante Google Play Billing.
 */
public class ProAccessDialog {

    private static final int COLOR_BG = 0xFF181824;
    private static final int COLOR_CARD = 0xFF222234;
    private static final int COLOR_ACCENT_CYAN = 0xFF00B0FF;
    private static final int COLOR_ACCENT_GOLD = 0xFFFFB300;
    private static final int COLOR_TEXT_PRIMARY = 0xFFFFFFFF;
    private static final int COLOR_TEXT_SECONDARY = 0xFFB0B0C4;
    private static final int COLOR_CHECK = 0xFF00E676;

    public interface OnProUnlockedListener {
        void onProUnlocked();
    }

    public static void show(
            @NonNull Activity activity,
            @Nullable MostrarPublicidad publicidad,
            @Nullable BillingManager billingManager,
            @NonNull ProPassManager proPassManager,
            @Nullable Runnable onProUnlocked) {

        show(activity, (Publicidad) publicidad, billingManager, proPassManager, onProUnlocked);
    }

    public static void show(
            @NonNull Activity activity,
            @Nullable Publicidad publicidad,
            @Nullable BillingManager billingManager,
            @NonNull ProPassManager proPassManager,
            @Nullable Runnable onProUnlocked) {

        if (activity.isFinishing() || activity.isDestroyed()) return;

        if (billingManager != null && billingManager.isProPurchased()) {
            new AlertDialog.Builder(activity)
                    .setTitle(activity.getString(R.string.pro_permanent_active_title))
                    .setMessage(activity.getString(R.string.pro_permanent_active_desc))
                    .setPositiveButton(R.string.str_ok, null)
                    .show();
            if (onProUnlocked != null) {
                onProUnlocked.run();
            }
            return;
        }

        if (billingManager != null && !billingManager.isProductAvailable()) {
            billingManager.queryProducts();
        }

        AlertDialog.Builder builder = new AlertDialog.Builder(activity);
        float density = activity.getResources().getDisplayMetrics().density;

        ScrollView scrollView = new ScrollView(activity);
        LinearLayout rootLayout = new LinearLayout(activity);
        rootLayout.setOrientation(LinearLayout.VERTICAL);
        rootLayout.setPadding(dp(20, density), dp(20, density), dp(20, density), dp(20, density));
        rootLayout.setBackgroundColor(COLOR_BG);

        String appName = activity.getString(R.string.app_name);

        // 1. TÍTULO CON ÍCONO
        TextView titleView = new TextView(activity);
        titleView.setText(activity.getString(R.string.pro_access_title, appName));
        titleView.setTextSize(22);
        titleView.setTypeface(null, Typeface.BOLD);
        titleView.setTextColor(COLOR_ACCENT_GOLD);
        titleView.setGravity(Gravity.CENTER);
        rootLayout.addView(titleView);

        // Subtítulo
        TextView subtitleView = new TextView(activity);
        subtitleView.setText(activity.getString(R.string.pro_access_subtitle, appName));
        subtitleView.setTextSize(13);
        subtitleView.setTextColor(COLOR_TEXT_SECONDARY);
        subtitleView.setGravity(Gravity.CENTER);
        subtitleView.setPadding(0, dp(6, density), 0, dp(16, density));
        rootLayout.addView(subtitleView);

        // 2. TARJETA DE BENEFICIOS PRO
        LinearLayout cardLayout = new LinearLayout(activity);
        cardLayout.setOrientation(LinearLayout.VERTICAL);
        cardLayout.setPadding(dp(16, density), dp(16, density), dp(16, density), dp(16, density));
        GradientDrawable cardBg = new GradientDrawable();
        cardBg.setColor(COLOR_CARD);
        cardBg.setCornerRadius(dp(12, density));
        cardBg.setStroke(dp(1, density), 0xFF35354D);
        cardLayout.setBackground(cardBg);

        String[] features = new String[]{
                activity.getString(R.string.pro_feature_antibrick_vault),
                activity.getString(R.string.pro_feature_crypto_checker),
                activity.getString(R.string.pro_feature_live_hex_editor),
                activity.getString(R.string.pro_feature_intel_gbe_mac),
                activity.getString(R.string.pro_feature_msdm_extractor),
                activity.getString(R.string.pro_feature_spi_registers),
                activity.getString(R.string.pro_feature_delta_flashing),
                activity.getString(R.string.pro_feature_zero_ads)
        };

        for (String feat : features) {
            LinearLayout row = new LinearLayout(activity);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setPadding(0, dp(4, density), 0, dp(4, density));

            TextView checkView = new TextView(activity);
            checkView.setText("✓ ");
            checkView.setTextSize(14);
            checkView.setTypeface(null, Typeface.BOLD);
            checkView.setTextColor(COLOR_CHECK);

            TextView featView = new TextView(activity);
            featView.setText(feat);
            featView.setTextSize(12.5f);
            featView.setTextColor(COLOR_TEXT_PRIMARY);

            row.addView(checkView);
            row.addView(featView);
            cardLayout.addView(row);
        }
        rootLayout.addView(cardLayout);

        // Espaciador
        View spacer = new View(activity);
        spacer.setMinimumHeight(dp(18, density));
        rootLayout.addView(spacer);

        AlertDialog dialog = builder.setView(scrollView).create();

        // 3. BOTÓN 1: VER VIDEO RECOMPENSADO (PASE 12 HORAS)
        Button btnRewarded = new Button(activity);
        btnRewarded.setText(activity.getString(R.string.pro_btn_watch_rewarded_video));
        btnRewarded.setTextSize(14);
        btnRewarded.setTypeface(null, Typeface.BOLD);
        btnRewarded.setTextColor(Color.WHITE);
        GradientDrawable btnRewardBg = new GradientDrawable();
        btnRewardBg.setColor(COLOR_ACCENT_CYAN);
        btnRewardBg.setCornerRadius(dp(8, density));
        btnRewarded.setBackground(btnRewardBg);
        btnRewarded.setPadding(dp(12, density), dp(12, density), dp(12, density), dp(12, density));

        btnRewarded.setOnClickListener(v -> {
            if (publicidad != null) {
                publicidad.mostrarRewardedAd(activity, rewardItem -> {
                    proPassManager.activarPase12Horas();
                    publicidad.ocultarBanner();
                    Toast.makeText(activity, activity.getString(R.string.pro_pass_activated_toast, appName), Toast.LENGTH_LONG).show();
                    if (onProUnlocked != null) {
                        onProUnlocked.run();
                    }
                    dialog.dismiss();
                }, () -> {
                    // Cerrado sin recompensa
                });
            } else {
                Toast.makeText(activity, activity.getString(R.string.pro_ads_not_available), Toast.LENGTH_SHORT).show();
            }
        });
        rootLayout.addView(btnRewarded);

        // Espaciador entre botones
        View btnSpacer = new View(activity);
        btnSpacer.setMinimumHeight(dp(10, density));
        rootLayout.addView(btnSpacer);

        // 4. BOTÓN 2: COMPRA PERMANENTE GOOGLE PLAY
        Button btnBuyPro = new Button(activity);
        String priceText = (billingManager != null && billingManager.getFormattedPrice() != null)
                ? " - " + billingManager.getFormattedPrice()
                : " - $5.00";
        btnBuyPro.setText(activity.getString(R.string.pro_btn_buy_permanent, appName, priceText));
        btnBuyPro.setTextSize(14);
        btnBuyPro.setTypeface(null, Typeface.BOLD);
        btnBuyPro.setTextColor(0xFF1E1E2E);
        GradientDrawable btnBuyBg = new GradientDrawable();
        btnBuyBg.setColor(COLOR_ACCENT_GOLD);
        btnBuyBg.setCornerRadius(dp(8, density));
        btnBuyPro.setBackground(btnBuyBg);
        btnBuyPro.setPadding(dp(12, density), dp(12, density), dp(12, density), dp(12, density));

        btnBuyPro.setOnClickListener(v -> {
            if (billingManager != null) {
                if (!billingManager.isProductAvailable()) {
                    billingManager.queryProducts();
                    Toast.makeText(activity, activity.getString(R.string.pro_billing_not_available), Toast.LENGTH_SHORT).show();
                    return;
                }
                boolean launched = billingManager.launchPurchaseFlow(activity);
                if (launched) {
                    dialog.dismiss();
                } else {
                    Toast.makeText(activity, activity.getString(R.string.pro_billing_not_available), Toast.LENGTH_SHORT).show();
                }
            } else {
                Toast.makeText(activity, activity.getString(R.string.pro_billing_not_available), Toast.LENGTH_SHORT).show();
            }
        });
        rootLayout.addView(btnBuyPro);

        // 5. BOTÓN 3: CANCELAR
        Button btnCancel = new Button(activity);
        btnCancel.setText(activity.getString(R.string.str_cancelar));
        btnCancel.setTextSize(13);
        btnCancel.setTextColor(COLOR_TEXT_SECONDARY);
        btnCancel.setBackgroundColor(Color.TRANSPARENT);
        btnCancel.setPadding(0, dp(10, density), 0, dp(4, density));
        btnCancel.setOnClickListener(v -> dialog.dismiss());
        rootLayout.addView(btnCancel);

        scrollView.addView(rootLayout);
        dialog.show();
    }

    private static int dp(int dp, float density) {
        return Math.round(dp * density);
    }
}
