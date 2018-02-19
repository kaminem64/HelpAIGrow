package com.helpaigrow;

import android.annotation.SuppressLint;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.os.AsyncTask;
import android.os.Binder;
import android.os.IBinder;
import android.text.TextUtils;
import android.util.Log;

import com.amazonaws.auth.CognitoCachingCredentialsProvider;
import com.amazonaws.regions.Regions;
import com.amazonaws.services.polly.AmazonPollyPresigningClient;
import com.amazonaws.services.polly.model.OutputFormat;
import com.amazonaws.services.polly.model.SynthesizeSpeechPresignRequest;

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
import java.util.Timer;
import java.util.TimerTask;

public class ResponseService extends Service {

    // Amazon Polly
    private static final String COGNITO_POOL_ID = "us-east-2:b0d2f207-55ba-4077-8d2a-83cab24695b3";
    private static final Regions MY_REGION = Regions.US_EAST_2;
    CognitoCachingCredentialsProvider credentialsProvider;
    private AmazonPollyPresigningClient client;
    MediaPlayer mediaPlayer;

    private String responseServerAddress;
    private AsyncTask<Void, Void, Void> speakingAgent;

    // Settings
    public static final String USERSETTINGS = "PrefsFile";

    private String conversationToken;
    private String voicePersona;
    private long responseDelay;
    private SpeechActivity activity;
    Timer timer;
    TimerTask timerTask;
    private ArrayList<String> receivedMessage;
    private AudioManager audioManager;

    /**
     * Providing a communication channel for clients
     */
    private final IBinder mBinder = new ResponseBinder();

    @Override
    public void onCreate() {
        super.onCreate();

        // Restore preferences
        final SharedPreferences settings = getSharedPreferences(USERSETTINGS, 0);
        conversationToken = settings.getString("conversationToken", "");
        voicePersona = settings.getString("voicePersona", "Joanna");

        audioManager = (AudioManager)getSystemService(Context.AUDIO_SERVICE);
        assert audioManager != null;
        audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, (audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)*3)/4 , 0);

        initPollyClient();
        setupNewMediaPlayer();
    }

    public void setActivity(SpeechActivity activity){
        this.activity = activity;
    }

    void initPollyClient() {
        // Initialize the Amazon Cognito credentials provider.
        credentialsProvider = new CognitoCachingCredentialsProvider(
                getApplicationContext(),
                COGNITO_POOL_ID,
                MY_REGION
        );

        // Create a client that supports generation of presigned URLs.
        client = new AmazonPollyPresigningClient(credentialsProvider);
    }

    private void resumeRecognition() {
        activity.resumeRecognition();
    }
    private void pauseRecognition() {
        activity.pauseRecognition();
    }

    void setupNewMediaPlayer() {
        mediaPlayer = new MediaPlayer();


        // Request audio focus for playback
        audioManager.requestAudioFocus(null, AudioManager.STREAM_MUSIC, AudioManager.AUDIOFOCUS_GAIN);

        mediaPlayer.setVolume(1,1);
        mediaPlayer.setOnCompletionListener(new MediaPlayer.OnCompletionListener() {
            @Override
            public void onCompletion(MediaPlayer mp) {
                mp.release();
                setupNewMediaPlayer();
                ResponseService.this.resumeRecognition();
            }
        });
        mediaPlayer.setOnPreparedListener(new MediaPlayer.OnPreparedListener() {
            @Override
            public void onPrepared(MediaPlayer mp) {
                ResponseService.this.pauseRecognition();
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

    @Override
    public void onDestroy() {
        mediaPlayer = null;

        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        // Returns the communication channel to the service.
        return mBinder;
    }

    public void setResponseServerAddress(String responseServerAddress) {
        this.responseServerAddress = responseServerAddress;
    }

    public void setResponseDelay(long responseDelay) {
        this.responseDelay = responseDelay;
    }

    class ResponseBinder extends Binder {
        ResponseService getService(){
            return ResponseService.this;
        }
    }
    /////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

    /**
     * Client accessible methods
     * @param text The text to be spoken
     * @param isFinished The status
     */
    public void speak(String text, boolean isFinished) {
        speakingAgent = new Speak(text, isFinished);
        speakingAgent.execute();
        this.flushReceivedMessage();

    }

    public void stopSpeaking() {
        if (speakingAgent != null) {
            speakingAgent.cancel(true);
            speakingAgent = null;
        }
        if(mediaPlayer != null) {
            mediaPlayer.stop();
            mediaPlayer.release();
            mediaPlayer = null;
        }
    }

    private String prepareQuery() {
        String text;
        ArrayList<String> messages = getReceivedMessage();
        try {
            text = TextUtils.join(" ", messages);
        } catch (Exception e) {
            text = "";
        }

        @SuppressLint("DefaultLocale") String query = String.format("conversation_token=%s&message=%s", conversationToken, text);
        return query;

    }

    public void respond() {
        new FetchResponse().execute(prepareQuery());
    }

    public void breakTheIce() {
        respond();
    }

    /**
     * SilenceCounter
     */
    public void startSilenceTimer() {
        Log.d("Location", "startSilenceTimer");
        if (this.timerTask != null) {
            this.timerTask.cancel();
            this.timerTask = null;
        }
        this.timer = new Timer();
        this.timerTask = new TimerTask() {
            @Override
            public void run() {
                respond();
            }
        };
        this.timer.schedule(this.timerTask, responseDelay);
    }

    public void killSilenceTimer() {
        Log.d("Location", "killTimer");
        if (this.timerTask != null) {
            this.timerTask.cancel();
            this.timerTask = null;
        }
    }


    /////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////


    /**
     * Getters and Setters
     */
    public ArrayList<String> getReceivedMessage() {
        return receivedMessage;
    }

    public void setReceivedMessage(ArrayList<String> receivedMessage) {
        Log.d("Location", "setReceivedMessage");
        this.receivedMessage = receivedMessage;
    }
    private void flushReceivedMessage() {
        Log.d("Location", "flushReceivedMessage");
        if(this.receivedMessage != null) {
            this.receivedMessage.clear();
        }
    }


    @SuppressLint("StaticFieldLeak")
    private class Speak extends AsyncTask<Void, Void, Void> {
        String textToRead;
        boolean isFinished;

        Speak(String textToRead, boolean isFinished) {
            this.textToRead = textToRead;
            this.isFinished = isFinished;
        }

        @Override
        protected Void doInBackground(Void... voids) {
            try {
                // Create speech synthesis request.
                SynthesizeSpeechPresignRequest synthesizeSpeechPresignRequest =
                        new SynthesizeSpeechPresignRequest()
                                // Set text to synthesize.
                                .withText(textToRead)
                                // Set voice selected by the user.
                                .withVoiceId(voicePersona)
                                // Set format to MP3.
                                .withOutputFormat(OutputFormat.Mp3)
                                .withTextType("text")
                                .withSampleRate("22050");
                // Get the presigned URL for synthesized speech audio stream.
                URL presignedSynthesizeSpeechUrl =
                        client.getPresignedSynthesizeSpeechUrl(synthesizeSpeechPresignRequest);
                Log.i("Polly", "Playing speech from presigned URL: " + presignedSynthesizeSpeechUrl);

                // Create a media player to play the synthesized audio stream.
                if (mediaPlayer.isPlaying()) {
                    setupNewMediaPlayer();
                }
                mediaPlayer.setAudioStreamType(AudioManager.STREAM_MUSIC);
                if(this.isFinished) {
                    mediaPlayer.setOnCompletionListener(new MediaPlayer.OnCompletionListener() {
                        @Override
                        public void onCompletion(MediaPlayer mp) {
                            mp.release();
                            Intent goToQuestions = new Intent(activity, QuestionsActivity.class);
                            startActivity(goToQuestions);
                        }
                    });
                }
                try {
                    // Set media player's data source to previously obtained URL.
                    mediaPlayer.setDataSource(presignedSynthesizeSpeechUrl.toString());
                } catch (IOException e) {
                    Log.e("Polly", "Unable to set data source for the media player! " + e.getMessage());
                }

                // Start the playback asynchronously (since the data source is a network stream).
                mediaPlayer.prepareAsync();
            } catch (Exception e) {
                Log.d("Polly Service", "Media player stopped because some objects are null.");
            }

            return null;
        }
    }

    @SuppressLint("StaticFieldLeak")
    private class FetchResponse extends AsyncTask<String, Integer, String> {

        @Override
        protected String doInBackground(String... strings) {
            StringBuilder result = new StringBuilder();
            try {
                String urlParameters = strings[0];
                Log.d("urlParameters", urlParameters);
                URL url = new URL(responseServerAddress);
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
                String responseText = response.getString("message");
                Log.d("ServerStat", "Got sentence");
                boolean isFinished = response.getBoolean("is_finished");
                Log.d("ServerStat", "Got is_finished");
                int responseCode = response.getInt("response_code");
                Log.d("ServerStat", "Got response code");
                if (responseCode != 0) {
                    String responseParameter = response.getString("response_parameter");
                    Log.d("ServerStat", "Got response parameter");
                    String nextCommandHintText = response.getString("next_command_hint_text");
                    Log.d("ServerStat", "Got next_command_hint_text");
                    boolean hasTriedAllCommands = response.getBoolean("has_tried_all_commands");
                    Log.d("ServerStat", "Got has_tried_all_commands");

                    activity.runCommand(responseCode, responseParameter, nextCommandHintText, hasTriedAllCommands);
                }
                if(!responseText.equals("")) speak(responseText, isFinished);
            } catch (JSONException e) {
                e.printStackTrace();
                Log.d("ServerStat", e.toString());
                speak("Please check your Internet connection.", false);
            }

        }
    }


}
