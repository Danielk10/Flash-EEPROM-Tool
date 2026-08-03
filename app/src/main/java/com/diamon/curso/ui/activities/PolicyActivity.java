package com.diamon.curso.ui.activities;

import com.diamon.curso.R;

import android.os.Bundle;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import androidx.appcompat.app.AppCompatActivity;

public class PolicyActivity extends AppCompatActivity {

    private WebView webView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_policy);

        if (getSupportActionBar() != null) {
            getSupportActionBar().setTitle("Política de Privacidad");
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }

        webView = findViewById(R.id.webView);
        setupWebView();
    }

    private void setupWebView() {
        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);

        webView.setWebViewClient(new WebViewClient());
        // URL base de PTC
        webView.loadUrl("https://todoandroid.42web.io/privacy-policy.html");
    }

    @Override
    public boolean onSupportNavigateUp() {
        getOnBackPressedDispatcher().onBackPressed();
        return true;
    }
}
