package com.speech;

import android.annotation.SuppressLint;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

public class QuestionsActivity extends AppCompatActivity {
    public static final String USERSETTINGS = "PrefsFile";
    WebView webView;
    String questionnaireUrl;
    String uniqueID;
    int appExperimentCode;

    @SuppressLint("SetJavaScriptEnabled")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_questions);
        setTitle("Questions");

        SharedPreferences settings = getSharedPreferences(USERSETTINGS, 0);
        uniqueID = settings.getString("uniqueID", "");

        appExperimentCode = settings.getInt("appExperimentCode", 0);
        questionnaireUrl = settings.getString("questionnaireUrl", "");
        questionnaireUrl = questionnaireUrl + "?unique_id=" + uniqueID + "&experiment_code=" + appExperimentCode;
        try {
            webView = findViewById(R.id.questionsWebView);
            webView.setWebViewClient(new WebViewClient());
            WebSettings webSettings = webView.getSettings();
            webSettings.setJavaScriptEnabled(true);
            webView.loadUrl(questionnaireUrl);
        } catch(Exception e){
            Intent launchBrowser = new Intent(Intent.ACTION_VIEW, Uri.parse(questionnaireUrl));
            startActivity(launchBrowser);
        }
    }

    DialogInterface.OnClickListener dialogClickListener = new DialogInterface.OnClickListener() {
        @Override
        public void onClick(DialogInterface dialog, int which) {
            switch (which){
                case DialogInterface.BUTTON_POSITIVE:
                    Intent goBackIntent = new Intent(QuestionsActivity.this, WelcomeActivity.class);
                    startActivity(goBackIntent);
                    break;

                case DialogInterface.BUTTON_NEGATIVE:
                    break;
            }
        }
    };

    @Override
    public void onBackPressed() {

        AlertDialog.Builder builder = new AlertDialog.Builder(QuestionsActivity.this);
        builder.setMessage("Are you sure you want to close the questionnaire?")
                .setPositiveButton("Yes", dialogClickListener)
                .setNegativeButton("No", dialogClickListener).show();
    }


}
