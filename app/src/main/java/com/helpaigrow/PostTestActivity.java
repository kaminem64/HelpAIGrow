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

public class PostTestActivity extends AppCompatActivity {
    public static final String USERSETTINGS = "PrefsFile";
    WebView webView;
    String postTestUrl;
    String uniqueID;
    String savedUtterance;
    int appExperimentCode;
    int groupNumber;
    int prePostGroupNumber;

    @SuppressLint("SetJavaScriptEnabled")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_questions);
        setTitle("Questions");
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            Objects.requireNonNull(getSupportActionBar()).setDisplayHomeAsUpEnabled(true);
        }

        SharedPreferences settings = getSharedPreferences(USERSETTINGS, 0);
        uniqueID = settings.getString("uniqueID", "");

        appExperimentCode = settings.getInt("appExperimentCode", 0);
        savedUtterance = settings.getString("savedUtterance", "");
        postTestUrl = settings.getString("postTestUrl", "");
        groupNumber = settings.getInt("groupNumber", 0);
        prePostGroupNumber = settings.getInt("prePostGroupNumber", 0);
        postTestUrl = postTestUrl + "?unique_id=" + uniqueID + "&experiment_code=" + appExperimentCode + "&group_number=" + groupNumber + "&pre_post_group=" + prePostGroupNumber + "&saved_utterance=" + savedUtterance;
        try {
            webView = findViewById(R.id.questionsWebView);
            webView.setWebViewClient(new WebViewClient());
            WebSettings webSettings = webView.getSettings();
            webSettings.setJavaScriptEnabled(true);
            webView.loadUrl(postTestUrl);
        } catch(Exception e){
            Intent launchBrowser = new Intent(Intent.ACTION_VIEW, Uri.parse(postTestUrl));
            startActivity(launchBrowser);
        }
    }

    DialogInterface.OnClickListener dialogClickListener = new DialogInterface.OnClickListener() {
        @Override
        public void onClick(DialogInterface dialog, int which) {
            switch (which){
                case DialogInterface.BUTTON_POSITIVE:
                    Intent goBackIntent = new Intent(PostTestActivity.this, WelcomeActivity.class);
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

        AlertDialog.Builder builder = new AlertDialog.Builder(PostTestActivity.this);
        builder.setMessage("Are you sure you want to close the questionnaire?")
                .setPositiveButton("Yes", dialogClickListener)
                .setNegativeButton("No", dialogClickListener).show();
    }

    @Override
    public boolean onSupportNavigateUp(){
        AlertDialog.Builder builder = new AlertDialog.Builder(PostTestActivity.this);
        builder.setMessage("Are you sure you want to close the questionnaire?")
                .setPositiveButton("Yes", dialogClickListener)
                .setNegativeButton("No", dialogClickListener).show();
        return true;
    }

}
