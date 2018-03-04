package com.helpaigrow;

import android.Manifest;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.net.Uri;
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
                if(ActivityCompat.checkSelfPermission(ConsentFormActivity.this,
                        Manifest.permission.RECORD_AUDIO)
                        != PackageManager.PERMISSION_GRANTED) {
                    showPermissionReasonsDialog();
                }
                else {
                    ActivityCompat.requestPermissions(ConsentFormActivity.this, new String[]{Manifest.permission.RECORD_AUDIO}, REQUEST_RECORD_AUDIO_PERMISSION);
                }
            }
        });

        declineButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Toast.makeText(ConsentFormActivity.this, "You can start the experiment later if you changed your mind.", Toast.LENGTH_LONG).show();
                onBackPressed();
            }
        });

    }
    @Override
    public void onBackPressed() {
        Intent goBackIntent = new Intent(ConsentFormActivity.this, WelcomeActivity.class);
        goBackIntent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
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
                AlertDialog.Builder builder;
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                    builder = new AlertDialog.Builder(ConsentFormActivity.this, android.R.style.Theme_Material_Dialog_Alert);
                } else {
                    builder = new AlertDialog.Builder(ConsentFormActivity.this);
                }
                builder.setTitle("Your Permission is Required")
                        .setMessage("To grant the permission:\n\n• Go to Permissions\n• Enable the Microphone")
                        .setCancelable(false)
                        .setPositiveButton("OPEN THE APP SETTING", new DialogInterface.OnClickListener() {
                            public void onClick(DialogInterface dialog, int id) {
                                startActivity(new Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + BuildConfig.APPLICATION_ID)));
                            }
                        })
                        .setIcon(android.R.drawable.ic_menu_set_as)
                        .show();
            }
            else {
                showPermissionReasonsDialog();
            }
        } else {
            super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        }
    }

    private void showPermissionReasonsDialog() {
        AlertDialog.Builder builder;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            builder = new AlertDialog.Builder(ConsentFormActivity.this, android.R.style.Theme_Material_Dialog_Alert);
        } else {
            builder = new AlertDialog.Builder(ConsentFormActivity.this);
        }
        builder.setTitle("Your Permission is Required")
                .setMessage("You need to use your microphone to talk to the app. So the app needs to access your microphone while you are talking to it.\nYou can remove the permission any time you wish.")
                .setPositiveButton("Grant Access", new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int which) {
                        ActivityCompat.requestPermissions(ConsentFormActivity.this, new String[]{Manifest.permission.RECORD_AUDIO}, REQUEST_RECORD_AUDIO_PERMISSION);
                    }
                })
                .setNegativeButton("I don't want to participate in the study", new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int which) {
                        onBackPressed();
                    }
                })
                .setIcon(android.R.drawable.checkbox_on_background)
                .show();
    }


    public void onMessageDialogDismissed() {
        ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.RECORD_AUDIO}, REQUEST_RECORD_AUDIO_PERMISSION);
    }
}
