package com.helpaigrow;

import android.Manifest;
import android.content.Intent;
import android.graphics.Point;
import android.os.Bundle;
import android.support.v4.app.ActivityCompat;
import android.util.Log;
import android.view.Display;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.RelativeLayout;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.Random;
import java.util.Timer;
import java.util.TimerTask;

public class ExpThreeGroupTwo extends SpeechActivity {

    private RelativeLayout relLay;
    private int circleRadius;
    private Circle circle;
    private Random rnd = new Random();
    private Timer timer = new Timer();
    private TextView scoreText;
    private int score = 0;

    // Containers
    private ArrayList<String> recognizedTextBuffer;

    // View references
    protected TextView recognizedText;

    private ResponseServer responseServer;

    private Thread startGameThread;

    /////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
    /**
     * Creating the activity page
     */
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_exp_three_group_two);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        relLay = findViewById(R.id.relLay);
        circleRadius = 40;
        RelativeLayout.LayoutParams params = new RelativeLayout.LayoutParams(2*circleRadius, 2*circleRadius);
        circle = new Circle(this, circleRadius, circleRadius, circleRadius, rnd.nextInt(256), rnd.nextInt(256), rnd.nextInt(256));
        params.leftMargin = 0;
        params.topMargin = 0;
        relLay.addView(circle, params);
        scoreText = findViewById(R.id.scoreText);
        circle.setVisibility(View.INVISIBLE);

        circle.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View view, MotionEvent motionEvent) {
                score++;
                scoreText.setText(String.valueOf(score));
                circle.setVisibility(View.INVISIBLE);
                return circle.performClick();
            }
        });

        recognizedText = findViewById(R.id.recognizedTextExp3);
        recognizedTextBuffer = new ArrayList<>();

    }
    @Override
    public void onBackPressed() {
        onStop();
        Intent goBackIntent = new Intent(ExpThreeGroupTwo.this, WelcomeActivity.class);
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
        bindSpeechService();
    }

    @Override
    protected void startSpeechDependentService() {
        if (ActivityCompat.shouldShowRequestPermissionRationale(getActivity(), Manifest.permission.RECORD_AUDIO)) {
            showPermissionMessageDialog();
        } else {
            ActivityCompat.requestPermissions(getActivity(), new String[]{Manifest.permission.RECORD_AUDIO}, REQUEST_RECORD_AUDIO_PERMISSION);
        }
        responseServer = new ResponseServer(this);
        responseServer.setResponseServerAddress(getResponseServerUrl());
        responseServer.setResponseDelay(getResponseDelay());
        startGameThread = new startTheGame();
        startGameThread.start();
    }

    Runnable startGameRunnable = new Runnable() {
        @Override
        public void run() {
            responseServer.setOnUtteranceStart(pauseRecognitionRunnable);
            responseServer.setOnUtteranceFinished(new Runnable() {
                @Override
                public void run() {
                    startRecognition();
                    timer = null;
                    timer = new Timer();
                    timer.schedule(new PeriodicTask(), 0);
                }
            });
            responseServer.breakTheIce();
            isIceBroken = true;
        }
    };


    class startTheGame extends Thread {
        public void run() {
            synchronized (mLock) {
                responseServer.setOnUtteranceStart(pauseRecognitionRunnable);
                responseServer.setOnUtteranceFinished(startGameRunnable);
                responseServer.speak("Hi! In this game you need to collect points by tapping on the circles while answering questions that I ask. You shall not stop tapping on circles while answering the questions. Good luck!", false);
            }
        }
    }


    @Override
    protected void onStop() {
        if (timer != null) {
            timer.cancel();
            timer = null;
        }
        circle.setVisibility(View.INVISIBLE);
        // Stop listening to voice

        stopVoiceRecorder();
        if(responseServer != null){
            responseServer.stopSpeaking();
            responseServer = null;
        }

        // Unbind Services
        unBindSpeechService();
        super.onStop();
    }

        @Override
    protected void runCommand(int commandCode, int fulfillment, String responseParameter, String nextCommandHintText, boolean hasTriedAllCommands, boolean commandCompleted) {

    }

    @Override
    protected void goToQuestions() {

    }

    @Override
    protected void saveUtterance(String utterance) {

    }

    @Override
    protected void finalizedRecognizedText(String text) {
        recognizedText.setText(text);
        recognizedTextBuffer.add(text);
        ResponseServer responseServer = new ResponseServer(this);
        responseServer.setResponseServerAddress(getResponseServerUrl());
        responseServer.setResponseDelay(getResponseDelay());
        responseServer.setOnUtteranceStart(pauseRecognitionRunnable);
        responseServer.setOnUtteranceFinished(startRecognitionRunnable);
        responseServer.setReceivedMessage(recognizedTextBuffer);
        responseServer.respond();
    }

    @Override
    protected void unFinalizedRecognizedText(String text) {
        recognizedText.setText(text);
    }

    @Override
    protected void showListeningState(boolean hearingVoice) {

    }

    @Override
    protected void showTalkingState() {

    }

    @Override
    protected void showLoadingState() {

    }

    @Override
    protected SpeechActivity getActivity() {
        return ExpThreeGroupTwo.this;
    }

    @Override
    protected String getResponseServerUrl() {
        return "http://amandabot.xyz/game_response/";
    }

    @Override
    protected long getResponseDelay() {
        return 500;
    }

    //////////////////////////////////////////////

    private class PeriodicTask extends TimerTask {
        @Override
        public void run() {
            synchronized (this) {
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        Display display = getWindowManager().getDefaultDisplay();
                        Point size = new Point();
                        display.getSize(size);
                        int width = size.x;
                        relLay.measure(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                        int viewHeight = relLay.getMeasuredHeight();
                        int leftMargin = rnd.nextInt(width - 2 * circleRadius);
                        int topMargin = rnd.nextInt(viewHeight - 4 * circleRadius); // getStatusBarHeight() - getNavBarHeight()
//                        Log.d("M", leftMargin + " " + topMargin);
                        RelativeLayout.LayoutParams params = new RelativeLayout.LayoutParams(2 * circleRadius, 2 * circleRadius);
                        params.leftMargin = leftMargin;
                        params.topMargin = topMargin;
                        circle.recolor(rnd.nextInt(256), rnd.nextInt(256), rnd.nextInt(256));
                        circle.setLayoutParams(params);
                        circle.setVisibility(View.VISIBLE);

                        timer.schedule(new PeriodicTask(), 500);
                    }
                });
            }
        }
    }
}
