package com.helpaigrow;

import android.annotation.SuppressLint;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import com.helpaigrow.BuildConfig;

import java.util.Objects;

public class AboutActivity extends AppCompatActivity {
    WebView webView;

    @SuppressLint("SetJavaScriptEnabled")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_about);
        setTitle("About");
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            Objects.requireNonNull(getSupportActionBar()).setDisplayHomeAsUpEnabled(true);
        }
        String versionName = BuildConfig.VERSION_NAME;
        String aboutPageUrl = "http://amandabot.xyz/about/" + versionName + "/";

        try {
            webView = findViewById(R.id.aboutWebView);
            webView.setWebViewClient(new WebViewClient());
            WebSettings webSettings = webView.getSettings();
            webSettings.setJavaScriptEnabled(true);
            webView.loadUrl(aboutPageUrl);
        } catch(Exception e){
            Intent launchBrowser = new Intent(Intent.ACTION_VIEW, Uri.parse(aboutPageUrl));
            startActivity(launchBrowser);
        }
    }


    @Override
    public void onBackPressed() {
        Intent goBackIntent = new Intent(AboutActivity.this, WelcomeActivity.class);
        goBackIntent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(goBackIntent);
    }

}
