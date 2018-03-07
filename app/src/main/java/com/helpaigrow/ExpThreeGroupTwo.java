package com.helpaigrow;

import android.Manifest;
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

        circle.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View view, MotionEvent motionEvent) {
                score++;
                new Talk().start();
                scoreText.setText(String.valueOf(score));
                circle.setVisibility(View.INVISIBLE);
                return circle.performClick();
            }
        });

    }

    @Override
    protected void onStart() {
        super.onStart();
        bindSpeechService();
        bindResponseService();
        if (ActivityCompat.shouldShowRequestPermissionRationale(getActivity(), Manifest.permission.RECORD_AUDIO)) {
            showPermissionMessageDialog();
        } else {
            ActivityCompat.requestPermissions(getActivity(), new String[]{Manifest.permission.RECORD_AUDIO}, REQUEST_RECORD_AUDIO_PERMISSION);
        }
        timer = null;
        timer = new Timer();
        timer.schedule(new PeriodicTask(), 0);

        new Talk().start();
    }

    class Talk extends Thread {
        public void run() {
            synchronized (mLock) {
                while(true) {
                    if (isSpeechServiceBound && isResponseServiceBound) {
                        getActivity().mResponseService.speak("What's up?", false);
                        break;
                    }
                }
            }
        }
    }

    @Override
    protected void onStop() {
        if (timer != null) {
            timer.cancel();
            timer = null;
        }
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

        @Override
    protected void runCommand(int commandCode, String responseParameter, String nextCommandHintText, boolean hasTriedAllCommands) {

    }

    @Override
    protected void goToQuestions() {

    }

    @Override
    protected void finalizedRecognizedText(String text) {

    }

    @Override
    protected void unFinalizedRecognizedText(String text) {

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
        return null;
    }

    @Override
    protected long getResponseDelay() {
        return 0;
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
                        Log.d("M", leftMargin + " " + topMargin);
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
