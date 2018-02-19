package com.speech;

import android.Manifest;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.v4.app.ActivityCompat;
import android.support.v7.app.AppCompatActivity;
import android.view.View;
import android.webkit.WebView;
import android.widget.Button;
import android.widget.Toast;


public class ConsentFormActivity extends AppCompatActivity implements MessageDialogFragment.Listener {

    public static final String USERSETTINGS = "PrefsFile";
    WebView webView;
    Button acceptButton;
    Button declineButton;

    Class<?> experimentClass;

    private static final String FRAGMENT_MESSAGE_DIALOG = "message_dialog";
    private static final int REQUEST_RECORD_AUDIO_PERMISSION = 1;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_consent_form);
        setTitle("Consent Form");

        Bundle extras = getIntent().getExtras();
        assert extras != null;
        String classname = extras.getString("experimentClass");
        try {
            experimentClass = Class.forName(classname);
        } catch (ClassNotFoundException e) {
            e.printStackTrace();
            experimentClass = WelcomeActivity.class;
        }

        // Restore preferences
        SharedPreferences settings = getSharedPreferences(USERSETTINGS, 0);
        String consentFormUrl = settings.getString("consentFormUrl", "http://amandabot.xyz/consent_form/");

        acceptButton = findViewById(R.id.acceptButton);
        declineButton = findViewById(R.id.declineButton);

        webView = findViewById(R.id.consentWebView);
        webView.loadUrl(consentFormUrl);
//        String summary = "<html><body>You scored <b>192</b> points.</body></html>";
//        webView.loadDataWithBaseURL(null, summary, "text/html", "utf-8", null);

        acceptButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (ActivityCompat.shouldShowRequestPermissionRationale(ConsentFormActivity.this, Manifest.permission.RECORD_AUDIO)) {
                    showPermissionMessageDialog();
                } else {
                    ActivityCompat.requestPermissions(ConsentFormActivity.this, new String[]{Manifest.permission.RECORD_AUDIO}, REQUEST_RECORD_AUDIO_PERMISSION);
                }
            }
        });

        declineButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Toast.makeText(ConsentFormActivity.this, "You can start the experiment later if you changed your mind.", Toast.LENGTH_LONG).show();
                Intent goBackIntent = new Intent(ConsentFormActivity.this, WelcomeActivity.class);
                startActivity(goBackIntent);
            }
        });

    }
    @Override
    public void onBackPressed() {
        Intent goBackIntent = new Intent(ConsentFormActivity.this, WelcomeActivity.class);
        startActivity(goBackIntent);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        if (requestCode == REQUEST_RECORD_AUDIO_PERMISSION) {
            if (permissions.length == 1 && grantResults.length == 1 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                Toast.makeText(ConsentFormActivity.this, "Let's start!", Toast.LENGTH_SHORT).show();
                Intent startExperimentIntent = new Intent(ConsentFormActivity.this, experimentClass);
                startActivity(startExperimentIntent);
            } else if (Build.VERSION.SDK_INT >= 23 && !shouldShowRequestPermissionRationale(permissions[0])) {
                Toast.makeText(ConsentFormActivity.this, "Go to your device Settings>Apps>Amanda>Permissions and grant the permission to use this app.", Toast.LENGTH_LONG).show();
            }
            else {
                showPermissionMessageDialog();
            }
        } else {
            super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        }
    }

    private void showPermissionMessageDialog() {
        MessageDialogFragment
                .newInstance(getString(R.string.permission_message))
                .show(getSupportFragmentManager(), FRAGMENT_MESSAGE_DIALOG);
    }

    public void onMessageDialogDismissed() {
        ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.RECORD_AUDIO}, REQUEST_RECORD_AUDIO_PERMISSION);
    }
}
