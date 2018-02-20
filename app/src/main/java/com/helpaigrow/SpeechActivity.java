package com.helpaigrow;

import android.support.v7.app.AppCompatActivity;


public abstract class SpeechActivity extends AppCompatActivity {

    protected abstract void pauseRecognition();
    protected abstract void resumeRecognition();
    protected abstract void runCommand(int commandCode, String responseParameter, String nextCommandHintText, boolean hasTriedAllCommands);
    protected abstract void goToQuestions();

}
