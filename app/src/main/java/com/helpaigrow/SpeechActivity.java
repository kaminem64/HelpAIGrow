package com.helpaigrow;

import android.Manifest;
import android.content.ComponentName;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.pm.PackageManager;
import android.os.IBinder;
import android.support.annotation.NonNull;
import android.support.v4.app.ActivityCompat;
import android.support.v7.app.AppCompatActivity;
import android.text.TextUtils;
import android.util.Log;

public abstract class SpeechActivity extends AppCompatActivity implements MessageDialogFragment.Listener {

    // Constants
    protected static final String FRAGMENT_MESSAGE_DIALOG = "message_dialog";
    protected static final int REQUEST_RECORD_AUDIO_PERMISSION = 1;

    final Object mLock = new Object();

    protected boolean isIceBroken = false;
    protected boolean isSpeechServiceRunning = false;
    protected boolean isTalking = false;
    protected boolean ismApiWorking = false;
    protected boolean isSilenceTimerRunning = false;

    // Services
    protected SpeechService mSpeechService;
    protected boolean isSpeechServiceBound = false;

    protected ResponseServer responseServer;


    //Voice Recorder
    protected VoiceRecorder mVoiceRecorder;

    // Abstract Methods
    protected abstract void runCommand(int commandCode, String responseParameter, String nextCommandHintText, boolean hasTriedAllCommands);
    protected abstract void goToQuestions();
    protected abstract void finalizedRecognizedText(String text);
    protected abstract void unFinalizedRecognizedText(String text);
    protected abstract void showListeningState(boolean hearingVoice);
    protected abstract void showTalkingState();
    protected abstract void showLoadingState();
    protected abstract SpeechActivity getActivity();
    protected abstract String getResponseServerUrl();
    protected abstract long getResponseDelay();

    /////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

    /**
     * Voice Recorder
     */
    protected final VoiceRecorder.Callback mVoiceCallback = new VoiceRecorder.Callback() {

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

    protected void startVoiceRecorder() {
        if (mVoiceRecorder != null) {
            mVoiceRecorder.unSynchedStop();
            mVoiceRecorder = null;
        }
        mVoiceRecorder = new VoiceRecorder(mVoiceCallback);
        mVoiceRecorder.start();
    }

    protected void stopVoiceRecorder() {
        if (mVoiceRecorder != null) {
            mVoiceRecorder.unSynchedStop(); // we added this new method to remove "synchronized (mLock)"
            mVoiceRecorder = null;
        }
    }


    protected Runnable pauseRecognitionRunnable = new Runnable() {
        @Override
        public void run() {
            pauseRecognition();
        }
    };
    protected Runnable resumeRecognitionRunnable = new Runnable() {
        @Override
        public void run() {
            resumeRecognition();
        }
    };

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


    /**
     * Creating a SpeechListener object to listen to user's voice in this page
     */
    protected final SpeechService.Listener mSpeechServiceListener = new SpeechService.Listener() {

        @Override
        public void onSpeechRecognized(final String text, final boolean isFinal) {
            if (isFinal) {
                mVoiceRecorder.dismiss();
            }
            if (!TextUtils.isEmpty(text)) {runOnUiThread(new Runnable() { //mText != null &&
                @Override
                public void run() {
                    try {
                        if (isFinal) {
                            finalizedRecognizedText(text);
                        } else {
                            unFinalizedRecognizedText(text);
                        }
                    } catch (Exception e) {
                        Log.d("SpeechService", "Listener stopped because some objects are null.");
                    }
                }
            });
            }
        }
    };

    protected void onSpeechServiceReady(){
        startSpeechDependentService();
        showStatus(false);
    }

    protected abstract void startSpeechDependentService();

    /**
     * Showing the user that Amanda is listening
     */
    protected void showStatus(final boolean hearingVoice) {
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
                            showTalkingState();
                        } else {
                            showListeningState(hearingVoice);
                        }
                    } else {
                        if(isTalking){
                            showTalkingState();
                        } else {
                            showLoadingState();
                        }
                    }
                } else {
                    if(isTalking){
                        showTalkingState();
                    } else {
                        showLoadingState();
                    }
                }
            }
        });
    }


     /*
        Creating all needed connections to the background services
     */
    /**
     * Service Connections
     * An object from ServiceConnection Class:
     * Establishes a communication channel to the SpeechService
     */
    protected final ServiceConnection mSpeechServiceConnection = new ServiceConnection() {

        @Override
        public void onServiceConnected(ComponentName componentName, IBinder binder) {
            mSpeechService = SpeechService.from(binder);
            mSpeechService.addListener(mSpeechServiceListener);
            mSpeechService.setSpeechActivity(SpeechActivity.this);
            isSpeechServiceBound = true;
            isSpeechServiceRunning = true;
        }

        @Override
        public void onServiceDisconnected(ComponentName componentName) {
            mSpeechService = null;
            isSpeechServiceBound = false;
            isSpeechServiceRunning = false;
        }
    };


    protected void bindSpeechService() {
        // Prepare Cloud Speech API
        bindService(new Intent(getActivity(), SpeechService.class), mSpeechServiceConnection, BIND_AUTO_CREATE);
    }
    protected void unBindSpeechService() {
        if(isSpeechServiceBound) {
            try {
                // Stop Cloud Speech API
                mSpeechService.removeListener(mSpeechServiceListener);
            } catch (Exception e) {
                Log.d("onStop", "removeListener is not working");
            }
            try {
                // Stop Cloud Speech API
                getActivity().unbindService(mSpeechServiceConnection);
            } catch (Exception e) {
                Log.d("onStop", "SpeechService is already stopped");
            }
        }
        isSpeechServiceBound = false;
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

    public void showPermissionMessageDialog() {
        MessageDialogFragment
                .newInstance(getString(R.string.permission_message))
                .show(getSupportFragmentManager(), FRAGMENT_MESSAGE_DIALOG);
    }

    @Override
    public void onMessageDialogDismissed() {
        ActivityCompat.requestPermissions(getActivity(), new String[]{Manifest.permission.RECORD_AUDIO}, REQUEST_RECORD_AUDIO_PERMISSION);
    }
}
