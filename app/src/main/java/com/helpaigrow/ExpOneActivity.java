package com.helpaigrow;

import android.Manifest;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.media.AudioManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.support.v4.app.ActivityCompat;
import android.support.v7.app.AlertDialog;
import android.util.Log;
import android.view.View;
import android.view.WindowManager;
import android.widget.ImageButton;
import android.widget.ProgressBar;
import android.widget.TextView;

import java.io.BufferedOutputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.util.ArrayList;
import java.util.Objects;

public class ExpOneActivity extends SpeechActivity {

    private boolean firstTimeShown = true;

    protected String responseServerUrl = "http://amandabot.xyz/response/";

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

        isTimedResponse = true;

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

        AudioManager audioManager = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
        assert audioManager != null;
        audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, (audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC) * 3) / 4, 0);
        audioManager.requestAudioFocus(null, AudioManager.STREAM_MUSIC, AudioManager.AUDIOFOCUS_GAIN);
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
        builder.setMessage("Are you sure you want to close the study?")
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
        builder.setMessage("Are you sure you want to close the study?")
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
            try {
                pauseRecognition(); //Pause the recognition before Amanda gets to talk first. Otherwise on slower internet connection we might have a problem of detecting some words before the conversation starts.
            } catch (Exception ignore) {}
            showStatusIsThinking();
            responseServer.respond();
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

    @Override
    protected void onDestroy() {
        super.onDestroy();
        unBindSpeechService();
    }

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
    protected void finalizedRecognizedText(String text) {
        recognizedText.setText(text);
        recognizedTextBuffer.add(text);
        responseServer.setReceivedMessage(recognizedTextBuffer);
    }

    @Override
    protected void unFinalizedRecognizedText(String text) {
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
    protected void showThinkingState() {
        microphoneIcon.setImageResource(R.drawable.chat);
        recognizedText.setText(WAITING_FOR_SERVER);
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
    public void runCommand(int commandCode, int fulfillment, String responseParameter, String nextCommandHintText, boolean hasTriedAllCommands, boolean commandCompleted){

    }
    @Override
    public void goToQuestions(){
        startActivity(new Intent(ExpOneActivity.this, PostTestActivity.class));
    }
    @Override
    public void saveUtterance(String utterance){
        SharedPreferences settings = getSharedPreferences(USERSETTINGS, 0);
        SharedPreferences.Editor editor = settings.edit();
        editor.putString("savedUtterance", utterance);
        editor.apply();
    }
    /////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
}
