package com.speech;

import android.annotation.SuppressLint;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

public class ReportProblemActivity extends AppCompatActivity {

    public static final String USERSETTINGS = "PrefsFile";
    private String reportUrl = "https://gsu.qualtrics.com/jfe/form/SV_efGJxAUE0F214aN";

    @SuppressLint("SetJavaScriptEnabled")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_report_problem);
        setTitle("Report Problems");

        Intent inputIntent = getIntent();
        String errorCode = inputIntent.getStringExtra("errorCode");

        SharedPreferences settings = getSharedPreferences(USERSETTINGS, 0);
        String userPk = settings.getString("userPk", "");
        String uniqueID = settings.getString("uniqueID", "");
        int appExperimentCode = settings.getInt("appExperimentCode", 0);
        reportUrl += "?user_id=" + userPk + "&unique_id=" + uniqueID + "&experiment_code=" + appExperimentCode + "&error_code=" + errorCode;
        try {
            WebView webView = findViewById(R.id.reportProblemWebView);
            webView.setWebViewClient(new WebViewClient());
            WebSettings webSettings = webView.getSettings();
            webSettings.setJavaScriptEnabled(true);
            webView.loadUrl(reportUrl);
        } catch(Exception e){
            Intent launchBrowser = new Intent(Intent.ACTION_VIEW, Uri.parse(reportUrl));
            startActivity(launchBrowser);
        }

    }
}
