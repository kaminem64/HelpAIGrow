package com.helpaigrow;

import android.annotation.SuppressLint;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Build;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import java.util.Objects;

public class PreTestActivity extends AppCompatActivity {
    public static final String USERSETTINGS = "PrefsFile";
    WebView webView;
    String preTestUrl;
    String uniqueID;
    int appExperimentCode;
    int groupNumber;
    int prePostGroupNumber;

    Class<?> experimentClass;


    @SuppressLint("SetJavaScriptEnabled")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_pre_test);
        setTitle("Questions");
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            Objects.requireNonNull(getSupportActionBar()).setDisplayHomeAsUpEnabled(true);
        }

        Bundle extras = getIntent().getExtras();
        assert extras != null;
        String className = extras.getString("experimentClass");
        try {
            experimentClass = Class.forName(className);
        } catch (ClassNotFoundException e) {
            e.printStackTrace();
            experimentClass = WelcomeActivity.class;
        }


        SharedPreferences settings = getSharedPreferences(USERSETTINGS, 0);
        uniqueID = settings.getString("uniqueID", "");

        appExperimentCode = settings.getInt("appExperimentCode", 0);
        preTestUrl = settings.getString("preTestUrl", "");
        groupNumber = settings.getInt("groupNumber", 0);
        prePostGroupNumber = settings.getInt("prePostGroupNumber", 0);
        preTestUrl = preTestUrl + "?unique_id=" + uniqueID + "&experiment_code=" + appExperimentCode + "&group_number=" + groupNumber + "&pre_post_group=" + prePostGroupNumber;
        try {
            webView = findViewById(R.id.preTestWebView);
            webView.setWebViewClient(new WebViewClient());
            webView.setWebViewClient(new WebViewClient() {
                                         public boolean shouldOverrideUrlLoading(WebView view, String url) {
                                             if (url.equals("hrupin://next_activity")) {
                                                 //Do your thing
                                                 startActivity(new Intent(getApplicationContext(), experimentClass));
                                                 return true;
                                             } else {
                                                 return false;
                                             }
                                         }
                                     });
            WebSettings webSettings = webView.getSettings();
            webSettings.setJavaScriptEnabled(true);
            webView.loadUrl(preTestUrl);
        } catch(Exception e){
            Intent launchBrowser = new Intent(Intent.ACTION_VIEW, Uri.parse(preTestUrl));
            startActivity(launchBrowser);
        }
    }

    DialogInterface.OnClickListener dialogClickListener = new DialogInterface.OnClickListener() {
        @Override
        public void onClick(DialogInterface dialog, int which) {
            switch (which){
                case DialogInterface.BUTTON_POSITIVE:
                    Intent goBackIntent = new Intent(PreTestActivity.this, WelcomeActivity.class);
                    goBackIntent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
                    startActivity(goBackIntent);
                    break;

                case DialogInterface.BUTTON_NEGATIVE:
                    break;
            }
        }
    };

    @Override
    public void onBackPressed() {

        AlertDialog.Builder builder = new AlertDialog.Builder(PreTestActivity.this);
        builder.setMessage("Are you sure you want to close the questionnaire?")
                .setPositiveButton("Yes", dialogClickListener)
                .setNegativeButton("No", dialogClickListener).show();
    }

    @Override
    public boolean onSupportNavigateUp(){
        AlertDialog.Builder builder = new AlertDialog.Builder(PreTestActivity.this);
        builder.setMessage("Are you sure you want to close the questionnaire?")
                .setPositiveButton("Yes", dialogClickListener)
                .setNegativeButton("No", dialogClickListener).show();
        return true;
    }

}
