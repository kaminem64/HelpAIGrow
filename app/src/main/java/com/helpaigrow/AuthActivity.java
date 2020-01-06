package com.helpaigrow;

import android.annotation.SuppressLint;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.AsyncTask;
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
        uniqueID = settings.getString("uniqueID", "");

        if (uniqueID.equals("")){
            SharedPreferences.Editor editor = settings.edit();
            uniqueID = UUID.randomUUID().toString();
            editor.putString("uniqueID", uniqueID);
            editor.apply();
        }

        Intent intent = getIntent();
        experimentId = intent.getIntExtra("EXPERIMENT_ID", 0);

        proceedButton = findViewById(R.id.proceedButton);
        proceedButton.setVisibility(View.INVISIBLE);

        authStatusText = findViewById(R.id.authStatusText);
        authStatusText.setTextIsSelectable(true);
        authStatusText.setMovementMethod(LinkMovementMethod.getInstance());

        authStatusImage = findViewById(R.id.authStatusImage);

        authLoading = findViewById(R.id.authLoading);

        checkAuth();
    }

    @Override
    public void onBackPressed() {
        Intent goBackIntent = new Intent(AuthActivity.this, WelcomeActivity.class);
        goBackIntent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(goBackIntent);
    }

    void checkAuth(){ //boolean changeTrackingId
        new AuthCheck().execute(); //changeTrackingId
    }


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
            final Intent[] goToNextPage = new Intent[1];
            boolean success = false;
            boolean exceptionRaised = false;
            try {
                JSONObject resultJSON = new JSONObject(result);
                Log.d("ServerStat", "Created the JSON Obj");
                JSONObject response = resultJSON.getJSONObject("response");
                Log.d("ServerStat", "Got response");
                success = response.getBoolean("success");
                Log.d("ServerStat", "Got status");
                final int groupNumber = response.getInt("group_number");
                SharedPreferences.Editor editor = settings.edit();
                editor.putString("conversationToken", response.getString("conversation_token"));
                editor.putString("postTestUrl", response.getString("post_test_url"));
                editor.putString("preTestUrl", response.getString("pre_test_url"));
                editor.putString("voicePersona", response.getString("voice_persona"));
                editor.putString("consentFormUrl", response.getString("consent_form_url"));
                editor.putInt("groupNumber", groupNumber);
                editor.putInt("prePostGroupNumber", response.getInt("pre_post_group"));
                final int appExperimentCode = response.getInt("app_experiment_code");
                editor.putInt("appExperimentCode", appExperimentCode);
                String userPk = response.getString("user_pk");
                editor.putString("userPk", userPk);
                editor.apply();
                String text1;
                if(success){
                    switch(appExperimentCode){
                        case 10:
                        case 11:
                        case 12:
                            goToNextPage[0] = new Intent(AuthActivity.this, ConsentFormActivity.class);
                            goToNextPage[0].putExtra("experimentClass","com.helpaigrow.ExpOneActivity");
                            break;
                        case 20:
                            goToNextPage[0] = new Intent(AuthActivity.this, ConsentFormActivity.class);
                            goToNextPage[0].putExtra("experimentClass","com.helpaigrow.ExpTwoGroupOneActivity");
                            break;
                        case 21:
                            goToNextPage[0] = new Intent(AuthActivity.this, ConsentFormActivity.class);
                            goToNextPage[0].putExtra("experimentClass","com.helpaigrow.ExpTwoGroupTwoActivity");
                            break;
                        case 30:
                            goToNextPage[0] = new Intent(AuthActivity.this, ConsentFormActivity.class);
                            goToNextPage[0].putExtra("experimentClass","com.helpaigrow.ExpThreeGroupTwo");
                            break;
                        case 40:
                            goToNextPage[0] = new Intent(AuthActivity.this, ConsentFormActivity.class);
                            goToNextPage[0].putExtra("experimentClass","com.helpaigrow.ExpIndeterminacy");
                            break;
                        default:
                            goToNextPage[0] = new Intent(AuthActivity.this, WelcomeActivity.class);
                            break;
                    }
                    startActivity(goToNextPage[0]);
                } else if (response.getBoolean("experiment_id_error")) {
                    Toast.makeText(AuthActivity.this, "Wrong experiment ID!", Toast.LENGTH_SHORT).show();
                    onBackPressed();
                } else if (response.getInt("days_until_next_stage") > 0) {
                    String text = "You need to wait " + response.getInt("days_until_next_stage") + " days until you could do the next stage of the experiment.";
                    Toast.makeText(AuthActivity.this, text, Toast.LENGTH_LONG).show();
                    onBackPressed();
                } else if (response.getBoolean("experiment_is_over")) {
                    text1 = "You have already finished this experiment.<br/>Please select another experiment.<br/> If you are receiving this message by mistake, please report the problem.";
                    authStatusText.setText(Html.fromHtml(text1));
                    authStatusImage.setImageResource(R.drawable.deletesign);
                    goToNextPage[0] = new Intent(AuthActivity.this, ReportProblemActivity.class);
                    goToNextPage[0].putExtra("errorCode", "experiment_is_over");
                } else if (response.getBoolean("user_creation_error")) {
                    text1 = "Authentication failed!<br/>User creation error.<br/>Please report the problem.";
                    authStatusText.setText(Html.fromHtml(text1));
                    authStatusImage.setImageResource(R.drawable.deletesign);
                    goToNextPage[0] = new Intent(AuthActivity.this, ReportProblemActivity.class);
                    goToNextPage[0].putExtra("errorCode", "user_creation_error");
                } else {
                    text1 = "Authentication failed!<br/>Please report the problem.";
                    authStatusText.setText(Html.fromHtml(text1));
                    authStatusImage.setImageResource(R.drawable.deletesign);
                    goToNextPage[0] = new Intent(AuthActivity.this, ReportProblemActivity.class);
                    goToNextPage[0].putExtra("errorCode", "other_errors");

                }
            } catch (JSONException e) {
                e.printStackTrace();
                Log.d("ServerStat", e.toString());
                AuthActivity.this.authStatusText.setText(R.string.authFail);
                AuthActivity.this.authStatusImage.setImageResource(R.drawable.deletesign);
                goToNextPage[0] = new Intent(AuthActivity.this, ReportProblemActivity.class);
                goToNextPage[0].putExtra("errorCode","exception_thrown");
                exceptionRaised = true;
            }
            if(!success || exceptionRaised) {
                proceedButton.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        startActivity(goToNextPage[0]);
                    }
                });
                proceedButton.setText(R.string.oneClickProblemReport);
                proceedButton.setVisibility(View.VISIBLE);
            }

        }
    }
}
