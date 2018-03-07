package com.helpaigrow;

import android.Manifest;
import android.content.Intent;
import android.os.Bundle;
import android.support.v4.app.ActivityCompat;
import android.view.View;
import android.view.WindowManager;
import android.widget.ImageButton;
import android.widget.ProgressBar;
import android.widget.TextView;
import java.util.ArrayList;

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

        microphoneIcon = findViewById(R.id.microphoneButtonExp1);

        spinner = findViewById(R.id.progressBarExp1);
        spinner.setVisibility(View.GONE);

        loadingWhiteTransparent = findViewById(R.id.loadingWhiteTransparentExp1);
        loadingWhiteTransparent.setVisibility(View.GONE);

        recognizedText = findViewById(R.id.recognizedTextExp1);

        recognizedTextBuffer = new ArrayList<>();

    }

    @Override
    public void onBackPressed() {
        onStop();
        Intent goBackIntent = new Intent(ExpOneActivity.this, WelcomeActivity.class);
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


    @Override
    protected void onStart() {
        super.onStart();
        if(!firstTimeShown) {
            onBackPressed();
        } else {
            firstTimeShown = false;
        }
        showStatus(false);
        bindSpeechService();
        bindResponseService();
        if (ActivityCompat.shouldShowRequestPermissionRationale(getActivity(), Manifest.permission.RECORD_AUDIO)) {
            showPermissionMessageDialog();
        } else {
            ActivityCompat.requestPermissions(getActivity(), new String[]{Manifest.permission.RECORD_AUDIO}, REQUEST_RECORD_AUDIO_PERMISSION);
        }
        InitializationCheck initializationCheck = new InitializationCheck();
        initializationCheck.start();
    }

    @Override
    protected void onStop() {
        // Stop listening to voice
        stopVoiceRecorder();
        // Unbind Services
        unBindResponseService();
        unBindSpeechService();
        super.onStop();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        unBindResponseService();
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
    protected long getResponseDelay() {
        return responseDelay;
    }

    @Override
    protected void finalizedRecognizedText(String text) {
        recognizedText.setText(text);
        recognizedTextBuffer.add(text);
        mResponseService.setReceivedMessage(recognizedTextBuffer);
        mResponseService.startSilenceTimer();
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
