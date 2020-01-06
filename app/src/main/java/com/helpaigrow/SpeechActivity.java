package com.helpaigrow;

import android.Manifest;
import android.content.ComponentName;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.os.IBinder;
import android.support.annotation.NonNull;
import android.support.v4.app.ActivityCompat;
import android.support.v7.app.AppCompatActivity;
import android.text.TextUtils;
import android.util.Log;
import java.io.IOException;
import java.util.Objects;

public abstract class SpeechActivity extends AppCompatActivity implements MessageDialogFragment.Listener {

    // Settings
    public static final String USERSETTINGS = "PrefsFile";

    protected final String WAITING_FOR_SERVER = "Thinking ...";
    protected final String LISTENING_MESSAGE = "Listening ...";
    protected final String TALKING = "Amanda's talking ...";

    protected boolean isTimedResponse = false;

    // Constants
    protected static final String FRAGMENT_MESSAGE_DIALOG = "message_dialog";
    protected static final int REQUEST_RECORD_AUDIO_PERMISSION = 1;

    final Object mLock = new Object();
    public String uniqueID;
    public int conversationTurn;

    protected boolean isIceBroken = false;
    protected boolean isSpeechServiceRunning = false;
    protected boolean isTalking = false;
    protected boolean ismApiWorking = false;
    protected boolean isSpeechFinalized = false;

    // Services
    protected SpeechService mSpeechService;
    protected boolean isSpeechServiceBound = false;

    protected ResponseServer responseServer;


    //Voice Recorder
    protected VoiceRecorder mVoiceRecorder;

    // Media Player
    protected MediaPlayer mediaPlayer;

    // Audio Saver
    protected SaveAudio saveAudio;

    // Abstract Methods
    protected abstract void runCommand(int commandCode, int fulfillment, String responseParameter, String nextCommandHintText, boolean hasTriedAllCommands, boolean commandCompleted);

    protected abstract void goToQuestions();

    protected abstract void saveUtterance(String utterance);

    protected abstract void finalizedRecognizedText(String text);

    protected abstract void unFinalizedRecognizedText(String text);

    protected abstract void showListeningState(boolean hearingVoice);

    protected abstract void showTalkingState();

    protected abstract void showThinkingState();

    protected abstract void showLoadingState();

    protected abstract SpeechActivity getActivity();

    protected abstract String getResponseServerUrl();

    /////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

    /**
     * Silent Timer
     */
    private TimedResponse timedResponse;

    private long lastUtterance = 0;


    private class TimedResponse {
        private final int SILENT_THRESHOLD = 3000;
        private Thread timerThread;
        final Object timerLock = new Object();

        void start() {
            timerThread = new Thread(new TimedResponse.SilentTimer());
            timerThread.start();
        }

        void stop() {
            synchronized (timerLock) {
                lastUtterance = 0;
                if (timerThread != null) {
                    timerThread.interrupt();
                    timerThread = null;
                }
            }
        }

        private class SilentTimer implements Runnable {

            @Override
            public void run() {
                while (true) {
                    synchronized (timerLock) {
                        if (Thread.currentThread().isInterrupted()) {
                            break;
                        }
                        final long now = System.currentTimeMillis();
                        if (!isTalking) {
                            if (lastUtterance != 0 && (now - lastUtterance > SILENT_THRESHOLD) && isSpeechFinalized) {
                                Log.d("SpeechService", "" + (now - lastUtterance) / 1000);
                                showStatusIsThinking();
                                if (saveAudio != null) {
                                    saveAudio.closeFile();
                                    new Thread(saveAudio).start();
                                }
                                if (mSpeechService != null) {
                                    mSpeechService.finishRecognizing();
                                }
                                pauseRecognition();
                                lastUtterance = 0;
                                responseServer.respond();
                            }
                        }
                    }

                }
            }
        }
    }

    protected void startTimedResponse() {
        if (timedResponse != null) {
            timedResponse.stop();
            timedResponse = null;
        }
        timedResponse = new TimedResponse();
        timedResponse.start();
    }

    protected void stopTimedResponse() {
        if (timedResponse != null) {
            timedResponse.stop();
            timedResponse = null;
        }
    }

    /**
     * Voice Recorder
     */
    protected final VoiceRecorder.Callback mVoiceCallback = new VoiceRecorder.Callback() {

        @Override
        public void onVoiceStart() {
            showStatus(true);
            if (mSpeechService != null) {
                mSpeechService.startRecognizing(mVoiceRecorder.getSampleRate());
            }
        }

        @Override
        public void onVoice(byte[] data, int size, int channel, int sampleRate, int encoding) {
            if (mSpeechService != null) {
                mSpeechService.recognize(data, size);
            }
            if (saveAudio != null) {
                saveAudio.streamSaveFile(data, size);
            }
        }

        @Override
        public void onVoiceEnd() {
            showStatus(false);
            if (mSpeechService != null) {
                mSpeechService.finishRecognizing();
            }
        }

        @Override
        public void areOtherServicesReady() {
            if (mSpeechService != null) {
                mVoiceRecorder.setOtherServicesReady(mSpeechService.isServiceReady());
            }
        }
    };

    protected void startVoiceRecorder() {
        if (mVoiceRecorder != null) {
            mVoiceRecorder.stop();
            mVoiceRecorder = null;
        }
        mVoiceRecorder = new VoiceRecorder(mVoiceCallback);
        mVoiceRecorder.start();
    }

    protected void stopVoiceRecorder() {
        if (mVoiceRecorder != null) {
            mVoiceRecorder.stop();
            mVoiceRecorder = null;
        }
    }
    ////////////////////////////////

    protected void stopSpeaking() {
        if (responseServer != null) {
            responseServer.stopSpeaking();
            responseServer = null;
        }
    }


    protected void pauseRecognition() {
        try {
            // Stop listening to voice
            stopVoiceRecorder();
        } catch (Exception e) {
            Log.d("pauseRecognition", "stopVoiceRecorder() failed.");
        }
        try {
            // Stop listening to voice
            mSpeechService.removeListener(mSpeechServiceListener);
        } catch (Exception e) {
            Log.d("pauseRecognition", "mSpeechService.removeListener(mSpeechServiceListener) failed.");
        }
        isSpeechServiceRunning = false;
        isTalking = true;
        showStatus(false);
    }

    protected void startRecognition() {
        try {
            // Start listening to voices
            startVoiceRecorder();
        } catch (Exception e) {
            Log.d("startRecognition", "startVoiceRecorder() failed.");
        }
        try {
            String filePath = Objects.requireNonNull(getActivity().getExternalCacheDir()).getAbsolutePath() +
                    "/" + uniqueID + "_" + conversationTurn + "___" + System.currentTimeMillis() / 1000 + ".wav";
            saveAudio = new SaveAudio(mVoiceRecorder.getCHANNEL(), mVoiceRecorder.getSampleRate(), mVoiceRecorder.getENCODING(), filePath);
        } catch (Exception e) {
            e.printStackTrace();
        }
        try {
            // Start listening to voices
            mSpeechService.addListener(mSpeechServiceListener);
        } catch (Exception e) {
            Log.d("startRecognition", "mSpeechService.addListener(mSpeechServiceListener); failed.");
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
                if (mVoiceRecorder != null) {
                    mVoiceRecorder.dismiss();
                }
            }
            if (!TextUtils.isEmpty(text)) {
                runOnUiThread(new Runnable() { //mText != null &&
                    @Override
                    public void run() {
                        try {
                            if (isFinal) {
                                finalizedRecognizedText(text);
                                isSpeechFinalized = true;
                            } else {
                                unFinalizedRecognizedText(text);
                                isSpeechFinalized = false;
                            }
                            lastUtterance = System.currentTimeMillis();
                        } catch (Exception e) {
                            Log.d("SpeechService", "Listener stopped because some objects are null.");
                        }
                    }
                });
            }
        }
    };

    protected void onSpeechServiceReady() {
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
                } catch (Exception e) {
                    ismApiWorking = false;
                    isSpeechServiceRunning = false;
                }
                if (isSpeechServiceRunning) {
                    if (ismApiWorking) {
                        if (isTalking) {
                            showTalkingState();
                        } else {
                            showListeningState(hearingVoice);
                        }
                    } else {
                        if (isTalking) {
                            showTalkingState();
                        } else {
                            showLoadingState();
                        }
                    }
                } else {
                    if (isTalking) {
                        showTalkingState();
                    } else {
                        showLoadingState();
                    }
                }
            }
        });
    }

    protected void setConversationTurn(int turn){
        this.conversationTurn = turn;
    }

    protected void showStatusIsThinking() {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                showThinkingState();
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
        if (isSpeechServiceBound) {
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

    /**
     * Media player
     */

    private void setupNewMediaPlayer() {
        if(mediaPlayer != null) {
            mediaPlayer.release();
            mediaPlayer = null;
        }
        mediaPlayer = new MediaPlayer();
        mediaPlayer.setAudioStreamType(AudioManager.STREAM_MUSIC);
        mediaPlayer.setOnCompletionListener(new MediaPlayer.OnCompletionListener() {
            @Override
            public void onCompletion(MediaPlayer mp) {
                mp.release();
                setupNewMediaPlayer();
                startRecognition();
                if (isTimedResponse) {
                    startTimedResponse();
                }
            }
        });
        mediaPlayer.setOnPreparedListener(new MediaPlayer.OnPreparedListener() {
            @Override
            public void onPrepared(MediaPlayer mp) {
                if (isTimedResponse) {
                    stopTimedResponse();
                }
                pauseRecognition();
                mp.start();
            }
        });
        mediaPlayer.setOnErrorListener(new MediaPlayer.OnErrorListener() {
            @Override
            public boolean onError(MediaPlayer mp, int what, int extra) {
                return false;
            }
        });
    }

    public void stopMediaPlayer() {
        if (mediaPlayer != null) {
            try {
                mediaPlayer.stop();
                mediaPlayer.release();
                mediaPlayer = null;
            } catch (Exception ignore) {
            }
        }
    }


    public void playPollySound(String url, final String finalUtterance, boolean isFinished) {
        // Create a media player to play the synthesized audio stream.
        setupNewMediaPlayer();

        if (isFinished) {
            mediaPlayer.setOnCompletionListener(new MediaPlayer.OnCompletionListener() {
                @Override
                public void onCompletion(MediaPlayer mp) {
                    mp.release();
                    saveUtterance(finalUtterance);
                    new android.os.Handler().postDelayed(
                            new Runnable() {
                                public void run() {
                                    goToQuestions();
                                }
                            },
                            2000);
                }
            });
        }
        try {
            // Set media player's data source to previously obtained URL.
            mediaPlayer.setDataSource(url);
        } catch (IOException e) {
            Log.e("Polly", "Unable to set data source for the media player! " + e.getMessage());
        }
        // Start the playback asynchronously (since the data source is a network stream).
        mediaPlayer.prepareAsync();
    }

}