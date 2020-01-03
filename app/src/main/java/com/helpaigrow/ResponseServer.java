package com.helpaigrow;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.SharedPreferences;
import android.media.AudioAttributes;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.os.AsyncTask;
import android.os.Build;
import android.text.TextUtils;
import android.util.Log;
import com.amazonaws.auth.CognitoCachingCredentialsProvider;
import com.amazonaws.regions.Regions;
import com.amazonaws.services.polly.AmazonPollyPresigningClient;
import com.amazonaws.services.polly.model.OutputFormat;
import com.amazonaws.services.polly.model.Engine;
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

public class ResponseServer {

    private String finalUtterance;

    // Amazon Polly
    private static final String COGNITO_POOL_ID = "us-east-1:b0f94da1-d234-47cb-b191-3b525641ae79";
    private static final Regions MY_REGION = Regions.US_EAST_1;
    private CognitoCachingCredentialsProvider credentialsProvider;
    private AmazonPollyPresigningClient client;
    private MediaPlayer mediaPlayer;

    private String responseServerAddress;
    private AsyncTask<Void, Void, Void> speakingAgent;

    // Settings
    private static final String USERSETTINGS = "PrefsFile";

    private String conversationToken;
    private String voicePersona;
    private long responseDelay;
    private SpeechActivity activity;
    private Timer timer;
    private TimerTask timerTask;
    private ArrayList<String> receivedMessage;
    private AudioManager audioManager;

    private Runnable onUtteranceStartCallback;
    private Runnable onUtteranceFinishedCallback;

    ResponseServer(SpeechActivity activity) {

        this.activity = activity;

        // Restore preferences
        final SharedPreferences settings = activity.getSharedPreferences(USERSETTINGS, 0);
        conversationToken = settings.getString("conversationToken", "");
        voicePersona = settings.getString("voicePersona", "Joanna");

        audioManager = (AudioManager)activity.getSystemService(Context.AUDIO_SERVICE);
        assert audioManager != null;
        audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, (audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)*3)/4 , 0);

        initPollyClient();
        setupNewMediaPlayer();
    }

    public void setActivity(SpeechActivity activity){
        this.activity = activity;
    }

    private void initPollyClient() {
        // Initialize the Amazon Cognito credentials provider.
        credentialsProvider = new CognitoCachingCredentialsProvider(
                this.activity.getApplicationContext(),
                COGNITO_POOL_ID,
                MY_REGION
        );

        // Create a client that supports generation of presigned URLs.
        client = new AmazonPollyPresigningClient(credentialsProvider);
    }

    public void setOnUtteranceStart(Runnable onUtteranceStartCallback) {
        this.onUtteranceStartCallback = onUtteranceStartCallback;
    }

    public void setOnUtteranceFinished(Runnable onUtteranceFinishedCallback) {
        this.onUtteranceFinishedCallback = onUtteranceFinishedCallback;
    }

    private void onUtteranceStart() {
        this.onUtteranceStartCallback.run();
    }
    private void onUtteranceFinished() {
        this.onUtteranceFinishedCallback.run();
    }

//    private void resumeRecognition() {
//        activity.resumeRecognition();
//    }
//    private void pauseRecognition() {
//        activity.pauseRecognition();
//    }

    private void setupNewMediaPlayer() {
        mediaPlayer = new MediaPlayer();


        // Request audio focus for playback
        audioManager.requestAudioFocus(null, AudioManager.STREAM_MUSIC, AudioManager.AUDIOFOCUS_GAIN);

        mediaPlayer.setVolume(1,1);
        mediaPlayer.setOnCompletionListener(new MediaPlayer.OnCompletionListener() {
            @Override
            public void onCompletion(MediaPlayer mp) {
                mp.release();
                setupNewMediaPlayer();
                ResponseServer.this.onUtteranceFinished();
            }
        });
        mediaPlayer.setOnPreparedListener(new MediaPlayer.OnPreparedListener() {
            @Override
            public void onPrepared(MediaPlayer mp) {
                // It seems to be too late to call onUtterancesStart() here. Will move it to SilentTimer
                ResponseServer.this.onUtteranceStart();
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

    public void setResponseServerAddress(String responseServerAddress) {
        this.responseServerAddress = responseServerAddress;
    }

    public void setResponseDelay(long responseDelay) {
        this.responseDelay = responseDelay;
    }

    /////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

    /**
     * Client accessible methods
     * @param text The text to be spoken
     * @param isFinished The status
     */
    public void speak(String text, boolean isFinished, boolean isSSML, boolean isNeural, String region) {
        speakingAgent = new Speak(text, isFinished, isSSML, isNeural, region);
        speakingAgent.execute();
        this.flushReceivedMessage();
    }

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
            try {
                mediaPlayer.stop();
                mediaPlayer.release();
                mediaPlayer = null;
            } catch (Exception ignore){}
        }
    }

    private String prepareQuery() {
        String text;
        ArrayList<String> messages = getReceivedMessage();
        try {
            text = TextUtils.join(". ", messages);
        } catch (Exception e) {
            text = "";
        }
        finalUtterance = text;

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
        if (this.timer != null) {
            this.timer.cancel();
            this.timer = null;
        }
        if (this.timerTask != null) {
            this.timerTask.cancel();
            this.timerTask = null;
        }
        this.timerTask = new TimerTask() {
            @Override
            public void run() {
                Log.d("Location", "silenceTimerDone");
                // Just to double-check that speech recognition is killed
                ResponseServer.this.onUtteranceStart();
                respond();
            }
        };
        this.timer = new Timer();
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
    private ArrayList<String> getReceivedMessage() {
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
        boolean isSSML;
        boolean isNeural;
        String region;
        String textType;
        String engine;

        Speak(String textToRead, boolean isFinished) {
            this.textToRead = textToRead;
            this.isFinished = isFinished;
            this.isSSML = false;
            this.isNeural = false;
            this.region = "NA";
            textType = "text";
            engine = "standard";

        }

        Speak(String textToRead, boolean isFinished, boolean isSSML, boolean isNeural, String region) {
            this.isFinished = isFinished;
            this.isSSML = isSSML;
            this.isNeural = isNeural;
            this.region = region;
            if (isNeural) {
                engine = "neural";
                textToRead = "<amazon:domain name=\"conversational\">" + textToRead + "</amazon:domain>";
            } else {
                engine = "standard";
            }
            if (isSSML) {
                textType = "ssml";
                textToRead = "<speak>" + textToRead + "</speak>";
            } else {
                textType = "text";
            }
            this.textToRead = textToRead;
        }

        @Override
        protected Void doInBackground(Void... voids) {
            try {
                // Create speech synthesis request.
                SynthesizeSpeechPresignRequest synthesizeSpeechPresignRequest =
                        new SynthesizeSpeechPresignRequest()
                                // Set text to synthesize.
                                .withText(this.textToRead)
                                // Set voice selected by the user.
                                .withVoiceId(voicePersona)
                                // Set format to MP3.
                                .withOutputFormat(OutputFormat.Mp3)
                                .withTextType(this.textType)
                                .withEngine(Engine.fromValue(this.engine));
//                                .withSampleRate("22050");
                // Get the presigned URL for synthesized speech audio stream.
                URL presignedSynthesizeSpeechUrl =
                        client.getPresignedSynthesizeSpeechUrl(synthesizeSpeechPresignRequest);
                Log.i("Polly", "Received speech presigned URL: " + presignedSynthesizeSpeechUrl);

                // Create a media player to play the synthesized audio stream.
                if (mediaPlayer.isPlaying()) {
                    setupNewMediaPlayer();
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                    mediaPlayer.setAudioAttributes(
                            new AudioAttributes
                                    .Builder()
                                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                                    .build());
                } else {
                    mediaPlayer.setAudioStreamType(AudioManager.STREAM_MUSIC);
                }
                if(this.isFinished) {
                    mediaPlayer.setOnCompletionListener(new MediaPlayer.OnCompletionListener() {
                        @Override
                        public void onCompletion(MediaPlayer mp) {
                            mp.release();
                            activity.saveUtterance(finalUtterance);
                            activity.goToQuestions();
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

                // Send the audio here
                // String filePath = activity.getExternalCacheDir().getAbsolutePath() + "/recording.wav";


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
                boolean isSSML = response.getBoolean("is_ssml");
                Log.d("ServerStat", "Got is_ssml");
                boolean isNeural = response.getBoolean("is_neural");
                Log.d("ServerStat", "Got is_neural");
                String region = response.getString("region");
                Log.d("ServerStat", "Got region");
                int responseCode = response.getInt("response_code");
                Log.d("ServerStat", "Got response code");
                int fulfillment = response.getInt("fulfillment");
                Log.d("ServerStat", "Got fulfillment");
                if (responseCode != 0) {
                    boolean commandCompleted = response.getBoolean("command_completed");
                    Log.d("ServerStat", "Got command_completed");
                    String responseParameter = response.getString("response_parameter");
                    Log.d("ServerStat", "Got response parameter");
                    String nextCommandHintText = response.getString("next_command_hint_text");
                    Log.d("ServerStat", "Got next_command_hint_text");
                    boolean hasTriedAllCommands = response.getBoolean("has_tried_all_commands");
                    Log.d("ServerStat", "Got has_tried_all_commands");
                    activity.runCommand(responseCode, fulfillment, responseParameter, nextCommandHintText, hasTriedAllCommands, commandCompleted);
                }
                if(!responseText.equals("")) speak(responseText, isFinished, isSSML, isNeural, region);
            } catch (JSONException e) {
                e.printStackTrace();
                Log.d("ServerStat", e.toString());
                speak("Please check your Internet connection.", false);
            }
        }
    }



    @SuppressLint("StaticFieldLeak")
    private class SendFiles extends AsyncTask<String, Integer, String> {

        @Override
        protected String doInBackground(String... strings) {
            StringBuilder result = new StringBuilder();
            try {
                String urlParameters = strings[0];
                Log.d("urlParameters", urlParameters);
                URL url = new URL("http://amandabot.xyz/about/1.12/");
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
                Log.d("FetchResponseR", "HTTP STATUS: " + String.valueOf(status));
                InputStream inputStream = conn.getInputStream();
                BufferedReader in = new BufferedReader(new InputStreamReader(inputStream, "UTF-8"));
                for (int c; (c = in.read()) >= 0;)
                    result.append((char)c);
            } catch (IOException e) {
                Log.d("FetchResponseR", "IOException");
                e.printStackTrace();
            }
            return result.toString();
        }

        @Override
        protected void onPostExecute(String result) {
            super.onPostExecute(result);
        }
    }
}
