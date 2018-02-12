package com.google.cloud.android.speech;

import android.annotation.SuppressLint;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.AsyncTask;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.text.Html;
import android.text.method.LinkMovementMethod;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.UUID;

public class AuthActivity extends AppCompatActivity {
    public static final String USERSETTINGS = "PrefsFile";
    String uniqueID;
//    String trackingId;
    int experimentId;
    TextView authStatusText;
    Button proceedButton;
    ImageView authStatusImage;
    ProgressBar authLoading;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_auth);
        setTitle("Authentication");

        // Restore preferences
        SharedPreferences settings = getSharedPreferences(USERSETTINGS, 0);
        uniqueID = settings.getString("uniqueID", null);

        if (uniqueID == null){
            SharedPreferences.Editor editor = settings.edit();
            editor.putString("uniqueID", UUID.randomUUID().toString());
            editor.apply();
        }

        Intent intent = getIntent();
//        trackingId = intent.getStringExtra("TRACKING_ID");
        experimentId = intent.getIntExtra("EXPERIMENT_ID", 0);

        proceedButton = (Button) findViewById(R.id.proceedButton);
        proceedButton.setEnabled(false);

        authStatusText = (TextView) findViewById(R.id.authStatusText);

        authStatusImage = (ImageView) findViewById(R.id.authStatusImage);

        authLoading = (ProgressBar) findViewById(R.id.authLoading);

        checkAuth();


    }

    @Override
    public void onBackPressed() {
        Intent goBackIntent = new Intent(AuthActivity.this, WelcomeActivity.class);
        startActivity(goBackIntent);
        finish();
    }

    void checkAuth(){ //boolean changeTrackingId
        new AuthCheck().execute(); //changeTrackingId
    }

//    DialogInterface.OnClickListener dialogClickListener = new DialogInterface.OnClickListener() {
//        @Override
//        public void onClick(DialogInterface dialog, int which) {
//            switch (which){
//                case DialogInterface.BUTTON_POSITIVE:
//                    checkAuth(true);
//                    break;
//
//                case DialogInterface.BUTTON_NEGATIVE:
//                    Toast.makeText(AuthActivity.this, "Please re-enter your tracking ID.", Toast.LENGTH_SHORT).show();
//                    Intent goBackIntent = new Intent(AuthActivity.this, WelcomeActivity.class);
//                    startActivity(goBackIntent);
//                    finish();
//                    break;
//            }
//        }
//    };



    @SuppressLint("StaticFieldLeak")
    public class AuthCheck extends AsyncTask<Boolean, Integer, String> {

        @Override
        protected String doInBackground(Boolean... booleans) {
//            boolean changeTrackingId = booleans[0];
            StringBuilder result = new StringBuilder();
            try {
                @SuppressLint("DefaultLocale") String urlParameters = String.format("unique_id=%s&experiment_id=%d",uniqueID, experimentId);
                URL url = new URL("http://amandabot.xyz/auth/");
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setReadTimeout(10000);
                conn.setConnectTimeout(15000);
                conn.setRequestMethod("POST");
                conn.setDoInput(true);
                conn.setDoOutput(true);
                OutputStream os = conn.getOutputStream();
                DataOutputStream writer = new DataOutputStream(os);
                writer.writeBytes(urlParameters);
                writer.flush();
                writer.close();
                os.close();
                int status = conn.getResponseCode();
                Log.d("Location", "HTTP STATUS: " + String.valueOf(status));
                InputStream inputStream = conn.getInputStream();
                BufferedReader in = new BufferedReader(new InputStreamReader(inputStream, "UTF-8"));
                for (int c; (c = in.read()) >= 0; )
                    result.append((char) c);
            } catch (IOException e) {
                Log.d("Location", "IOException");
                e.printStackTrace();
            }

            return result.toString();
        }


        @Override
        protected void onPostExecute(String result) {
            super.onPostExecute(result);
            authLoading.setVisibility(View.GONE);
            SharedPreferences settings = getSharedPreferences(USERSETTINGS, 0);
            try {
                JSONObject resultJSON = new JSONObject(result);
                Log.d("ServerStat", "Created the JSON Obj");
                JSONObject response = resultJSON.getJSONObject("response");
                Log.d("ServerStat", "Got response");
                boolean success = response.getBoolean("success");
                Log.d("ServerStat", "Got status");
                if(success){
                    final int groupNumber = response.getInt("group_number");
                    SharedPreferences.Editor editor = settings.edit();
                    editor.putString("conversationToken", response.getString("conversation_token"));
                    editor.putString("questionnaireUrl", response.getString("questionnaire_url"));
                    editor.putString("voicePersona", response.getString("voice_persona"));
                    editor.putString("consentFormUrl", response.getString("consent_form_url"));
                    editor.putInt("groupNumber", groupNumber);
                    editor.apply();
                    AuthActivity.this.authStatusText.setText("Authentication successful.");
                    AuthActivity.this.authStatusImage.setImageResource(R.drawable.checkmark);

                    // we can have a mapping between the codes used here and the codes on the server to create more flexibility
                    proceedButton.setOnClickListener(new View.OnClickListener() {
                        @Override
                        public void onClick(View view) {
                            Intent goToConsentFormIntent;
                            if(experimentId == 10){
                                goToConsentFormIntent = new Intent(AuthActivity.this, ConsentFormActivity.class);
                                goToConsentFormIntent.putExtra("experimentClass","com.google.cloud.android.speech.ExpOneSpeechActivity");
                            } else if(experimentId == 20) {
                                if(groupNumber == 1) {
                                    goToConsentFormIntent = new Intent(AuthActivity.this, ConsentFormActivity.class);
                                    goToConsentFormIntent.putExtra("experimentClass","com.google.cloud.android.speech.ManualCommandsActivity");
                                } else {
                                    goToConsentFormIntent = new Intent(AuthActivity.this, ConsentFormActivity.class);
                                    goToConsentFormIntent.putExtra("experimentClass","com.google.cloud.android.speech.ExpTwoSpeechActivity");
                                }
                            } else {
                                goToConsentFormIntent = new Intent(AuthActivity.this, WelcomeActivity.class);
                            }
                            startActivity(goToConsentFormIntent);
                        }
                    });
                    AuthActivity.this.proceedButton.setEnabled(true);
                }
                else if (response.getBoolean("experiment_id_error")){
                    Toast.makeText(AuthActivity.this, "Wrong experiment ID!", Toast.LENGTH_SHORT).show();
                    onBackPressed();
                }
                else if (response.getInt("days_until_next_stage") > 0){
                    String text = "You need to wait " + response.getInt("days_until_next_stage") + " days until you could do the next stage of the experiment.";
                    Toast.makeText(AuthActivity.this, text, Toast.LENGTH_LONG).show();
                    onBackPressed();
                }
//                else if (response.getBoolean("tracking_id_error")){
//                    AlertDialog.Builder builder = new AlertDialog.Builder(AuthActivity.this);
//                    builder.setMessage("Last time you entered a different tracking ID. Do you want to change it to this new one?").setPositiveButton("Yes", dialogClickListener)
//                            .setNegativeButton("No", dialogClickListener).show();
//                }
                else if (response.getBoolean("experiment_is_over")){
                    Toast.makeText(AuthActivity.this, "You have already finished this experiment. Please select another experiment.", Toast.LENGTH_LONG).show();
                    onBackPressed();
                }
                else {
                    AuthActivity.this.authStatusText.setText(Html.fromHtml("Authentication failed!\nPlease contact <a href=\"mailto:k.saffarizadeh@gmail.com\">k.saffarizadeh@gmail.com</a>"));
                    AuthActivity.this.authStatusText.setMovementMethod(LinkMovementMethod.getInstance());
                    AuthActivity.this.authStatusImage.setImageResource(R.drawable.deletesign);
                }
            } catch (JSONException e) {
                e.printStackTrace();
                Log.d("ServerStat", e.toString());
                AuthActivity.this.authStatusText.setText("Authentication failed!");
                AuthActivity.this.authStatusImage.setImageResource(R.drawable.deletesign);
            }

        }
    }
}
