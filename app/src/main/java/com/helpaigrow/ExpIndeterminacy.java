package com.helpaigrow;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.AsyncTask;
import android.os.Bundle;
import android.support.v4.app.ActivityCompat;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.SeekBar;
import android.widget.Switch;
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
import java.util.ArrayList;

public class ExpIndeterminacy extends SpeechActivity {

    protected String responseServerUrl = "http://amandabot.xyz/assistant_response/";
    protected long responseDelay = 1000;

    // Settings
    public static final String USERSETTINGS = "PrefsFile";

    private final String WAITING_FOR_SERVER = "Thinking ...";
    private final String LISTENING_MESSAGE = "Listening ...";
    private final String TALKING = "Amanda's talking ...";

    // Containers
    private ArrayList<String> recognizedTextBuffer;

    // View references
    private ImageButton microphoneIcon;
    protected TextView recognizedText;
    private ProgressBar spinner;
    private TextView loadingWhiteTransparent;
    private Button nextButton;
    private ImageView imageView;
    private TextView bulbText;
    private int bulbNumber = 1;

    /////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
    /**
     * Creating the activity page
     */
    @SuppressLint("ClickableViewAccessibility")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_exp_indeterminacy);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        final SharedPreferences settings = getSharedPreferences(USERSETTINGS, 0);
        final String conversationToken = settings.getString("conversationToken", "");

        imageView = findViewById(R.id.lightBulb);
        bulbText = findViewById(R.id.bulbText);

        microphoneIcon = findViewById(R.id.microphoneButtonE2G1);

        spinner = findViewById(R.id.progressBarIndeterminacy);
        spinner.setVisibility(View.GONE);
        loadingWhiteTransparent = findViewById(R.id.loadingWhiteTransparentIndeterminacy);
        loadingWhiteTransparent.setVisibility(View.GONE);

        recognizedText = findViewById(R.id.recognizedTextE2G1);

        recognizedTextBuffer = new ArrayList<>();

        nextButton = findViewById(R.id.nextButtonIndeterminacy);
        nextButton.setVisibility(View.INVISIBLE);
        nextButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                @SuppressLint("DefaultLocale") String query = String.format("conversation_token=%s&message=", conversationToken);
                new FetchResponse().execute(query);
            }
        });

        responseServer = new ResponseServer(this);
        responseServer.setResponseServerAddress(getResponseServerUrl());
        responseServer.setResponseDelay(getResponseDelay());
        responseServer.setOnUtteranceStart(pauseRecognitionRunnable);
        responseServer.setOnUtteranceFinished(resumeRecognitionRunnable);
    }


    @Override
    public void onBackPressed() {
        onStop();
        Intent goBackIntent = new Intent(ExpIndeterminacy.this, WelcomeActivity.class);
        goBackIntent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(goBackIntent);
    }

    /**
     * Saving the page state in order to restore it after switching the apps or changing the app orientation
     */
    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
    }

    /**
     * Establishing the required connections to the services
     * and
     * Checking for the required permissions
     */
    @Override
    protected void onStart() {
        super.onStart();
        showStatus(false); //Shows the loading animations
        bindSpeechService();
        // Start listening to voices
    }

    @Override
    protected void startSpeechDependentService() {
        if (ActivityCompat.checkSelfPermission(getActivity(), Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
            startVoiceRecorder();
        } else if (ActivityCompat.shouldShowRequestPermissionRationale(getActivity(), Manifest.permission.RECORD_AUDIO)) {
            showPermissionMessageDialog();
        } else {
            ActivityCompat.requestPermissions(getActivity(), new String[]{Manifest.permission.RECORD_AUDIO}, REQUEST_RECORD_AUDIO_PERMISSION);
        }
        if(!isIceBroken) {
            responseServer.breakTheIce();
            isIceBroken = true;
        }
    }

    @Override
    protected void onStop() {
        super.onStop();
        // Stop listening to voice
        stopVoiceRecorder();
        // Unbind Services
        unBindSpeechService();
    }

    /////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
    @Override
    protected SpeechActivity getActivity() {
        return ExpIndeterminacy.this;
    }

    @Override
    protected String getResponseServerUrl() {
        return responseServerUrl;
    }

    @Override
    protected long getResponseDelay() {
        return responseDelay;
    }

    @Override
    protected void finalizedRecognizedText(String text) {
        recognizedText.setText(text);
        recognizedTextBuffer.add(text);
        responseServer.setReceivedMessage(recognizedTextBuffer);
        responseServer.respond();
    }

    @Override
    protected void unFinalizedRecognizedText(String text) {
        recognizedText.setText(text);
    }

    @Override
    public void goToQuestions(){
        startActivity(new Intent(ExpIndeterminacy.this, PostTestActivity.class));
    }

    @Override
    protected void saveUtterance(String utterance) {

    }

    public void runCommand(int commandCode, int fulfillment, String responseParameter, String nextCommandHintText, boolean hasTriedAllCommands, boolean commandCompleted){
        showStatus(false);
        if(commandCompleted){nextButton.setVisibility(View.VISIBLE);}
        switch (commandCode) {
            // do nothing if something is already on/off or set | or just do anything we are asked
            case 100:
                bulbNumber++;
                bulbText.setText("Light Bulb " + bulbNumber);
                if(fulfillment==1){
                    // Turn on the lights;
                    imageView.setImageResource(R.drawable.light_bulb_on);
                    new android.os.Handler().postDelayed(
                            new Runnable() {
                                public void run() {
                                    imageView.setImageResource(R.drawable.light_bulb_off);
                                }
                            },
                            2000);
                }
                break;
            case 101:
                // Turn off the lights
                //lightsSwitch.setChecked(false);
                break;
            default:
                break;
        }
    }

    protected void showListeningState(boolean hearingVoice){
        microphoneIcon.setImageResource(hearingVoice ? R.drawable.ic_mic_black_48dp : R.drawable.ic_mic_none_black_48dp);
        if(recognizedText.getText().equals("") || recognizedText.getText().equals(TALKING) || recognizedText.getText().equals(WAITING_FOR_SERVER)) {
            recognizedText.setText(LISTENING_MESSAGE);
        }
        spinner.setVisibility(View.GONE);
        loadingWhiteTransparent.setVisibility(View.GONE);
    }

    protected void showTalkingState() {
        microphoneIcon.setImageResource(R.drawable.chat);
        recognizedText.setText(TALKING);
        spinner.setVisibility(View.GONE);
        loadingWhiteTransparent.setVisibility(View.GONE);
    }

    protected void showLoadingState() {
        microphoneIcon.setImageResource(R.drawable.block_microphone);
        recognizedText.setText(WAITING_FOR_SERVER);
        spinner.setVisibility(View.VISIBLE);
        loadingWhiteTransparent.setVisibility(View.VISIBLE);
    }

    /////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
    @SuppressLint("StaticFieldLeak")
    private class FetchResponse extends AsyncTask<String, Integer, String> {

        @Override
        protected String doInBackground(String... strings) {
            StringBuilder result = new StringBuilder();
            try {
                String urlParameters = strings[0];
//                Log.d("urlParameters", urlParameters);
                URL url = new URL("http://amandabot.xyz/conversation_finished/");
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
//                Log.d("Location", "HTTP STATUS: " + String.valueOf(status));
                InputStream inputStream = conn.getInputStream();
                BufferedReader in = new BufferedReader(new InputStreamReader(inputStream, "UTF-8"));
                for (int c; (c = in.read()) >= 0;)
                    result.append((char)c);
            } catch (IOException e) {
                Log.d("Location", "IOException");
                e.printStackTrace();
            }

            return result.toString();
        }

        @Override
        protected void onPostExecute(String result) {
            super.onPostExecute(result);
            try {
                JSONObject resultJSON = new JSONObject(result);
//                Log.d("ServerStat", "Created the JSON Obj");
                JSONObject response = resultJSON.getJSONObject("response");
//                Log.d("ServerStat", "Got response");
                boolean success = response.getBoolean("success");
                Log.d("ServerStat", "Got success");
                if (success) {
                    goToQuestions();
                } else {
                    Toast.makeText(ExpIndeterminacy.this, "Server error!\nPlease try again later!", Toast.LENGTH_LONG).show();
                }
            } catch (JSONException e) {
                e.printStackTrace();
                Log.d("ServerStat", e.toString());
                Toast.makeText(ExpIndeterminacy.this, "Server error!\nPlease try again later!!!", Toast.LENGTH_LONG).show();
            }

        }
    }


}
