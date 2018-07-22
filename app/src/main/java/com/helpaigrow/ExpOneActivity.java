package com.helpaigrow;

import android.Manifest;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.support.v4.app.ActivityCompat;
import android.support.v7.app.AlertDialog;
import android.view.View;
import android.view.WindowManager;
import android.widget.ImageButton;
import android.widget.ProgressBar;
import android.widget.TextView;
import java.util.ArrayList;
import java.util.Objects;

public class ExpOneActivity extends SpeechActivity {

    private boolean firstTimeShown = true;

    protected String responseServerUrl = "http://amandabot.xyz/response/";
    protected long responseDelay = 3000;

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

    /////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
    /**
     * Creating the activity page
     */
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_exp_one);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            Objects.requireNonNull(getSupportActionBar()).setDisplayHomeAsUpEnabled(true);
        }


        microphoneIcon = findViewById(R.id.microphoneButtonExp1);

        spinner = findViewById(R.id.progressBarExp1);
        spinner.setVisibility(View.GONE);

        loadingWhiteTransparent = findViewById(R.id.loadingWhiteTransparentExp1);
        loadingWhiteTransparent.setVisibility(View.GONE);

        recognizedText = findViewById(R.id.recognizedTextExp1);
        recognizedTextBuffer = new ArrayList<>();

        responseServer = new ResponseServer(this);
        responseServer.setResponseServerAddress(getResponseServerUrl());
        responseServer.setResponseDelay(getResponseDelay());
        responseServer.setOnUtteranceStart(pauseRecognitionRunnable);
        responseServer.setOnUtteranceFinished(resumeRecognitionRunnable);
    }


    DialogInterface.OnClickListener dialogClickListener = new DialogInterface.OnClickListener() {
        @Override
        public void onClick(DialogInterface dialog, int which) {
            switch (which){
                case DialogInterface.BUTTON_POSITIVE:
                    onStop();
                    Intent goBackIntent = new Intent(ExpOneActivity.this, WelcomeActivity.class);
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
        AlertDialog.Builder builder = new AlertDialog.Builder(ExpOneActivity.this);
        builder.setMessage("Are you sure you want to close the experiment?")
                .setPositiveButton("Yes", dialogClickListener)
                .setNegativeButton("No", dialogClickListener).show();
    }

    public void goBack() {
        onStop();
        Intent goBackIntent = new Intent(ExpOneActivity.this, WelcomeActivity.class);
        goBackIntent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(goBackIntent);
    }

    @Override
    public boolean onSupportNavigateUp(){
        AlertDialog.Builder builder = new AlertDialog.Builder(ExpOneActivity.this);
        builder.setMessage("Are you sure you want to close the experiment?")
                .setPositiveButton("Yes", dialogClickListener)
                .setNegativeButton("No", dialogClickListener).show();
        return true;
    }


    /**
     * Saving the page state in order to restore it after switching the apps or changing the app orientation
     */
    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
    }


    @Override
    protected void onStart() {
        super.onStart();
        if(!firstTimeShown) {
            goBack();
        } else {
            firstTimeShown = false;
        }
        showStatus(false);  //Shows the loading animations
        bindSpeechService();
    }

    @Override
    protected void startSpeechDependentService() {
        if (ActivityCompat.shouldShowRequestPermissionRationale(getActivity(), Manifest.permission.RECORD_AUDIO)) {
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
        // Stop speaking
        stopSpeaking();
        // Stop listening to voice
        stopVoiceRecorder();
        // Unbind Services
        unBindSpeechService();
        super.onStop();
    }

//    @Override
//    protected void onDestroy() {
//        super.onDestroy();
//        unBindSpeechService();
//    }

    /////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
    @Override
    protected SpeechActivity getActivity() {
        return ExpOneActivity.this;
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
        responseServer.startSilenceTimer();
        isSilenceTimerRunning = true;
    }

    @Override
    protected void unFinalizedRecognizedText(String text) {
        if (isSilenceTimerRunning) {
            try {
                responseServer.killSilenceTimer();
                isSilenceTimerRunning = false;
            } catch (Exception ignored) {
            }
        }
        recognizedText.setText(text);
    }

    @Override
    protected void showListeningState(boolean hearingVoice){
        microphoneIcon.setImageResource(hearingVoice ? R.drawable.ic_mic_black_48dp : R.drawable.ic_mic_none_black_48dp);
        if(recognizedText.getText().equals("") || recognizedText.getText().equals(TALKING) || recognizedText.getText().equals(WAITING_FOR_SERVER)) {
            recognizedText.setText(LISTENING_MESSAGE);
        }
        spinner.setVisibility(View.GONE);
        loadingWhiteTransparent.setVisibility(View.GONE);
    }

    @Override
    protected void showTalkingState() {
        microphoneIcon.setImageResource(R.drawable.chat);
        recognizedText.setText(TALKING);
        spinner.setVisibility(View.GONE);
        loadingWhiteTransparent.setVisibility(View.GONE);
    }

    @Override
    protected void showLoadingState() {
        microphoneIcon.setImageResource(R.drawable.block_microphone);
        recognizedText.setText(WAITING_FOR_SERVER);
        spinner.setVisibility(View.VISIBLE);
        loadingWhiteTransparent.setVisibility(View.VISIBLE);
    }

    @Override
    public void runCommand(int commandCode, String responseParameter, String nextCommandHintText, boolean hasTriedAllCommands){

    }
    @Override
    public void goToQuestions(){
        startActivity(new Intent(ExpOneActivity.this, QuestionsActivity.class));
    }
    /////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
}
