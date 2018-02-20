package com.helpaigrow;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.IBinder;
import android.support.annotation.NonNull;
import android.support.v4.app.ActivityCompat;
import android.text.TextUtils;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.ImageButton;
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
//import android.view.animation.AlphaAnimation;
//import android.view.animation.Animation;

public class ExpTwoGroupOneActivity extends SpeechActivity implements MessageDialogFragment.Listener {

    Toast toastMessage;
    Switch lightsSwitch;
    Switch acSwitch;
    SeekBar acTemperature;
    TextView time;
    TextView hintText;

    // Settings
    public static final String USERSETTINGS = "PrefsFile";

    private boolean isIceBroken = false;
    private boolean isSpeechServiceRunning = false;
    private boolean isTalking = false;
    boolean ismApiWorking = false;

    private final String WAITING_FOR_SERVER = "Thinking ...";
    private final String LISTENING_MESSAGE = "Listening ...";
    private final String TALKING = "Amanda's talking ...";

    // Services
    private SpeechService mSpeechService;
    private ResponseService mResponseService;
    private boolean isResponseServiceBound = false;
    private boolean speechServiceLoaded = false;
    private boolean responseServiceLoaded = false;

    // Containers
    private ArrayList<String> recognizedTextBuffer;

    // Constants
    private static final String FRAGMENT_MESSAGE_DIALOG = "message_dialog";
    private static final int REQUEST_RECORD_AUDIO_PERMISSION = 1;

    // View references
    private ImageButton microphoneIcon;
    TextView recognizedText;
    private ProgressBar spinner;
    private TextView loadingWhiteTransparent;
    private Button nextButton;


    /**
     * Voice Recorder
     */
    private VoiceRecorder mVoiceRecorder;


    private final VoiceRecorder.Callback mVoiceCallback = new VoiceRecorder.Callback() {

        @Override
        public void onVoiceStart() {
//            Log.i("SpeechActivity", "onVoiceStart()");
            showStatus(true);
            if (mSpeechService != null) {
                mSpeechService.startRecognizing(mVoiceRecorder.getSampleRate());
            }
        }

        @Override
        public void onVoice(byte[] data, int size) {
//            Log.i("SpeechActivity", "onVoice()");
            if (mSpeechService != null) {
                mSpeechService.recognize(data, size);
            }
        }

        @Override
        public void onVoiceEnd() {
//            Log.i("SpeechActivity", "onVoiceStart()");
            showStatus(false);
            if (mSpeechService != null) {
                mSpeechService.finishRecognizing();
            }
        }

        @Override
        public void areOtherServicesReady(){
            if (mSpeechService != null) {
                mVoiceRecorder.setOtherServicesReady(mSpeechService.isServiceReady());
            }
        }

    };

    void startVoiceRecorder() {
        if (mVoiceRecorder != null) {
            mVoiceRecorder.unSynchedStop();
            mVoiceRecorder = null;
        }
        mVoiceRecorder = new VoiceRecorder(mVoiceCallback);
        mVoiceRecorder.start();
    }

    void stopVoiceRecorder() {
        if (mVoiceRecorder != null) {
            mVoiceRecorder.unSynchedStop(); // we added this new method to remove "synchronized (mLock)"
            mVoiceRecorder = null;
        }
    }
    /////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
    /**
     * Creating a SpeechListener object to listen to user's voice in this page
     */
    private final SpeechService.Listener mSpeechServiceListener = new SpeechService.Listener() {

        @Override
        public void onSpeechRecognized(final String text, final boolean isFinal) {
            if (isFinal) {
                mVoiceRecorder.dismiss();
            }
            if (!TextUtils.isEmpty(text) && isResponseServiceBound) {runOnUiThread(new Runnable() { //mText != null &&
                @Override
                public void run() {
                    try {
                        if (isFinal) {
                            recognizedText.setText(text);
                            recognizedTextBuffer.add(text);
                            mResponseService.setReceivedMessage(recognizedTextBuffer);
                            mResponseService.respond();
                        } else {
                            recognizedText.setText(text);
                        }
                    } catch (Exception e) {
                        Log.d("SpeechService", "Listener stopped because some objects are null.");
                    }
                }
            });
            }
        }
    };
    /////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
    /*
        Creating all needed connections to the background services
     */
    /**
     * Service Connections
     * An object from ServiceConnection Class:
     * Establishes a communication channel to the SpeechService
     */
    private final ServiceConnection mSpeechServiceConnection = new ServiceConnection() {

        @Override
        public void onServiceConnected(ComponentName componentName, IBinder binder) {
            mSpeechService = SpeechService.from(binder);
            mSpeechService.addListener(mSpeechServiceListener);
            microphoneIcon.setVisibility(View.VISIBLE);
            speechServiceLoaded = true;
            onPageSetupCompleted();
            isSpeechServiceRunning = true;
        }

        @Override
        public void onServiceDisconnected(ComponentName componentName) {
            mSpeechService = null;
        }

    };

    /**
     * An object from ServiceConnection Class:
     * Establishes a communication channel to the ResponseService
     */
    private final ServiceConnection mResponseServiceConnection = new ServiceConnection() {

        @Override
        public void onServiceConnected(ComponentName componentName, IBinder binder) {
            Log.d("ResponseService", "Connected");
            ResponseService.ResponseBinder responseBinder = (ResponseService.ResponseBinder) binder;
            mResponseService = responseBinder.getService();
            mResponseService.setActivity(ExpTwoGroupOneActivity.this);
            mResponseService.setResponseServerAddress("http://amandabot.xyz/assistant_response/");
            mResponseService.setResponseDelay(1000);
            responseServiceLoaded = true;
            onPageSetupCompleted();
        }

        @Override
        public void onServiceDisconnected(ComponentName componentName) {
            mResponseService = null;
        }

    };
    /////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
    /*
      Event listeners
     */

    /**
     * Creating the activity page
     */
    @SuppressLint("ClickableViewAccessibility")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_exp_two_group_one);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        final SharedPreferences settings = getSharedPreferences(USERSETTINGS, 0);
        final String conversationToken = settings.getString("conversationToken", "");

        microphoneIcon = findViewById(R.id.microphoneButtonE2G1);
        microphoneIcon.setVisibility(View.INVISIBLE);

        spinner = findViewById(R.id.progressBarE2G1);
        spinner.setVisibility(View.GONE);
        loadingWhiteTransparent = findViewById(R.id.loadingWhiteTransparentE2G1);
        loadingWhiteTransparent.setVisibility(View.GONE);

        recognizedText = findViewById(R.id.recognizedTextE2G1);

        recognizedTextBuffer = new ArrayList<>();

        lightsSwitch = findViewById(R.id.lightsSwitchE2G1);
        lightsSwitch.setClickable(false);

        acSwitch = findViewById(R.id.acSwitchE2G1);
        acSwitch.setClickable(false);

        acTemperature = findViewById(R.id.acTemperatureE2G1);

        acTemperature.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View view, MotionEvent motionEvent) {
                return true;
            }
        });

        time = findViewById(R.id.timeE2G1);

        hintText = findViewById(R.id.hintE2G1);

        nextButton = findViewById(R.id.nextButtonE2G1);
        nextButton.setVisibility(View.INVISIBLE);
        nextButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                @SuppressLint("DefaultLocale") String query = String.format("conversation_token=%s&message=", conversationToken);
                Log.d("conversationToken", query);
                new FetchResponse().execute(query);
            }
        });
    }

    @Override
    public void goToQuestions(){
        startActivity(new Intent(ExpTwoGroupOneActivity.this, QuestionsActivity.class));
    }

    public void runCommand(int commandCode, String responseParameter, String nextCommandHintText, boolean hasTriedAllCommands){
        showStatus(false);
        hintText.setText(nextCommandHintText);
        if(hasTriedAllCommands){nextButton.setVisibility(View.VISIBLE);}
        switch (commandCode) {
            // do nothing if something is already on/off or set | or just do anything we are asked
            case 100:
                // Turn on the lights;
                lightsSwitch.setChecked(true);
                if (toastMessage!= null) toastMessage.cancel();
                toastMessage = Toast.makeText(ExpTwoGroupOneActivity.this, "Lights are on", Toast.LENGTH_SHORT);
                toastMessage.show();
                break;
            case 101:
                // Turn off the lights
                lightsSwitch.setChecked(false);
                if (toastMessage!= null) toastMessage.cancel();
                toastMessage = Toast.makeText(ExpTwoGroupOneActivity.this, "Lights are off", Toast.LENGTH_SHORT);
                toastMessage.show();
                break;
            case 200:
                // Turn on AC;
                acSwitch.setChecked(true);
                if (toastMessage!= null) toastMessage.cancel();
                toastMessage = Toast.makeText(ExpTwoGroupOneActivity.this, "AC is on", Toast.LENGTH_SHORT);
                toastMessage.show();
                break;
            case 201:
                // Turn off AC;
                acSwitch.setChecked(false);
                if (toastMessage!= null) toastMessage.cancel();
                toastMessage = Toast.makeText(ExpTwoGroupOneActivity.this, "AC is off", Toast.LENGTH_SHORT);
                toastMessage.show();
                break;
            case 300:
                // Set the temp to X degrees;
                acTemperature.setProgress(Integer.parseInt(responseParameter));
                if (toastMessage!= null) toastMessage.cancel();
                String message = "Temperature is set to: " + responseParameter + "°F";
                toastMessage = Toast.makeText(ExpTwoGroupOneActivity.this, message, Toast.LENGTH_SHORT);
                toastMessage.show();
                break;
            case 400:
                // Set the alarm
                time.setText(responseParameter);
                if (toastMessage!= null) toastMessage.cancel();
                String alarmMessage = "Alarm is set for: " + responseParameter;
                toastMessage = Toast.makeText(ExpTwoGroupOneActivity.this, alarmMessage, Toast.LENGTH_SHORT);
                toastMessage.show();
                break;
            default:
                break;
        }
    }

    @Override
    public void onBackPressed() {
        onStop();
        Intent goBackIntent = new Intent(ExpTwoGroupOneActivity.this, WelcomeActivity.class);
        startActivity(goBackIntent);
    }

    public void onPageSetupCompleted() {
        if(speechServiceLoaded && responseServiceLoaded && !isIceBroken){
            mResponseService.breakTheIce();
            isIceBroken = true;
        }
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
        System.gc();
        super.onStart();
        showStatus(false);
        // Establish a connection to ResponseService
        Intent intent = new Intent(this, ResponseService.class);
        bindService(intent, mResponseServiceConnection, Context.BIND_AUTO_CREATE);
        isResponseServiceBound = true;

        // Prepare Cloud Speech API
        bindService(new Intent(this, SpeechService.class), mSpeechServiceConnection, BIND_AUTO_CREATE);

        // Start listening to voices
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
            startVoiceRecorder();
        } else if (ActivityCompat.shouldShowRequestPermissionRationale(this, Manifest.permission.RECORD_AUDIO)) {
            showPermissionMessageDialog();
        } else {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.RECORD_AUDIO}, REQUEST_RECORD_AUDIO_PERMISSION);
        }

    }

    protected void pauseRecognition(){
        try {
            // Stop listening to voice
            stopVoiceRecorder();
        }
        catch (Exception e) {
            Log.d("pauseRecognition", "SpeechService is already stopped.");
        }
        isSpeechServiceRunning = false;
        isTalking = true;
        showStatus(false);
    }
    protected void resumeRecognition(){
        try {
            // Start listening to voices
            startVoiceRecorder();
        } catch (Exception e) {
            Log.d("resumeRecognition", "SpeechService is already running.");
        }
        isSpeechServiceRunning = true;
        isTalking = false;
        showStatus(false);
    }

    @Override
    protected void onStop() {
        // Stop listening to voice
        stopVoiceRecorder();

        // Stop the connection to ResponseService
        try {
            mResponseService.stopSpeaking();
            unbindService(mResponseServiceConnection);
        } catch (Exception e) {
            Log.d("onStop", "mResponseServiceConnection is already unbindService");
        }
        mResponseService = null;
        isResponseServiceBound = false;

        try {
            // Stop Cloud Speech API
            mSpeechService.removeListener(mSpeechServiceListener);
            unbindService(mSpeechServiceConnection);
        } catch (Exception e) {
            Log.d("onStop", "SpeechService is already stopped by the ResponseService");
        }
        mSpeechService = null;
        super.onStop();
        System.gc();
    }

    /**
     * Handling all permission related tasks
     */
    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        if (requestCode == REQUEST_RECORD_AUDIO_PERMISSION) {
            if (permissions.length == 1 && grantResults.length == 1 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                startVoiceRecorder();
            } else {
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

    @Override
    public void onMessageDialogDismissed() {
        ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.RECORD_AUDIO}, REQUEST_RECORD_AUDIO_PERMISSION);
    }
    /////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
    /*
        Handling all views
     */
    /**
     * Showing the user that Amanda is listening
     */
    private void showStatus(final boolean hearingVoice) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                // We can later optimize this part but for now we want to make sure that we are handling everything
                try {
                    ismApiWorking = mSpeechService.isServiceReady();
                } catch (Exception e){
                    ismApiWorking = false;
                    isSpeechServiceRunning = false;
                }
                if (isSpeechServiceRunning) {
                    if(ismApiWorking){
                        if(isTalking){
                            microphoneIcon.setImageResource(R.drawable.chat);
                            recognizedText.setText(TALKING);
                            spinner.setVisibility(View.GONE);
                            loadingWhiteTransparent.setVisibility(View.GONE);
                        } else {
                            microphoneIcon.setImageResource(hearingVoice ? R.drawable.ic_mic_black_48dp : R.drawable.ic_mic_none_black_48dp);
                            if(recognizedText.getText().equals("") || recognizedText.getText().equals(TALKING) || recognizedText.getText().equals(WAITING_FOR_SERVER)) {
                                recognizedText.setText(LISTENING_MESSAGE);
                            }
                            spinner.setVisibility(View.GONE);
                            loadingWhiteTransparent.setVisibility(View.GONE);
                        }
                    } else {
                        if(isTalking){
                            microphoneIcon.setImageResource(R.drawable.chat);
                            recognizedText.setText(TALKING);
                            spinner.setVisibility(View.GONE);
                            loadingWhiteTransparent.setVisibility(View.GONE);
                        } else {
                            microphoneIcon.setImageResource(R.drawable.block_microphone);
                            recognizedText.setText(WAITING_FOR_SERVER);
                            spinner.setVisibility(View.VISIBLE);
                            loadingWhiteTransparent.setVisibility(View.VISIBLE);
                        }
                    }
                } else {
                    if(isTalking){
                        microphoneIcon.setImageResource(R.drawable.chat);
                        recognizedText.setText(TALKING);
                        spinner.setVisibility(View.GONE);
                        loadingWhiteTransparent.setVisibility(View.GONE);
                    } else {
                        microphoneIcon.setImageResource(R.drawable.block_microphone);
                        recognizedText.setText(WAITING_FOR_SERVER);
                        spinner.setVisibility(View.VISIBLE);
                        loadingWhiteTransparent.setVisibility(View.VISIBLE);
                    }
                }
            }
        });
    }

//    public void setUtteredText(String text){
//        recognizedText.setText(text);
//    }
//    public void fadeUtteredText() {
//        final Animation out = new AlphaAnimation(1.0f, 0.0f);
//        out.setDuration(1500);
//        out.setAnimationListener(new Animation.AnimationListener() {
//
//            @Override
//            public void onAnimationStart(Animation animation) {
//
//            }
//
//            @Override
//            public void onAnimationEnd(Animation animation) {
//                if(!recognizedText.getText().equals(TALKING)) {
//                    recognizedText.setText("");
//                }
//            }
//
//            @Override
//            public void onAnimationRepeat(Animation animation) {
//
//            }
//        });
//
//        recognizedText.setAnimation(out);
//    }

    /////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////


    @SuppressLint("StaticFieldLeak")
    private class FetchResponse extends AsyncTask<String, Integer, String> {

        @Override
        protected String doInBackground(String... strings) {
            StringBuilder result = new StringBuilder();
            try {
                String urlParameters = strings[0];
                Log.d("urlParameters", urlParameters);
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
                Log.d("Location", "HTTP STATUS: " + String.valueOf(status));
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
                Log.d("ServerStat", "Created the JSON Obj");
                JSONObject response = resultJSON.getJSONObject("response");
                Log.d("ServerStat", "Got response");
                boolean success = response.getBoolean("success");
                Log.d("ServerStat", "Got success");
                if (success) {
                    goToQuestions();
                } else {
                    Toast.makeText(ExpTwoGroupOneActivity.this, "Server error!\nPlease try again later!", Toast.LENGTH_LONG).show();
                }
            } catch (JSONException e) {
                e.printStackTrace();
                Log.d("ServerStat", e.toString());
                Toast.makeText(ExpTwoGroupOneActivity.this, "Server error!\nPlease try again later!!!", Toast.LENGTH_LONG).show();
            }

        }
    }


}
