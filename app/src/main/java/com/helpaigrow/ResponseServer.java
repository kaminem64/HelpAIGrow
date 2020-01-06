package com.helpaigrow;

import android.annotation.SuppressLint;
import android.content.SharedPreferences;
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
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

class ResponseServer {

    private final ExecutorService executorService;
    private String finalUtterance;

    // Amazon Polly
    private static final String COGNITO_POOL_ID = "us-east-1:b0f94da1-d234-47cb-b191-3b525641ae79";
    private static final Regions MY_REGION = Regions.US_EAST_1;
    private CognitoCachingCredentialsProvider credentialsProvider;
    private static AmazonPollyPresigningClient client;

    private String responseServerAddress;
    private Thread speakingAgent;

    // Settings
    private static final String USERSETTINGS = "PrefsFile";
    private final SharedPreferences settings;

    private String conversationToken;
    private static String voicePersona;
    private SpeechActivity activity;
    private ArrayList<String> receivedMessage;



    ResponseServer(SpeechActivity activity) {

        this.activity = activity;

        // Restore preferences
        settings = activity.getSharedPreferences(USERSETTINGS, 0);
        conversationToken = settings.getString("conversationToken", "");
        voicePersona = settings.getString("voicePersona", "Joanna");

        executorService = Executors.newFixedThreadPool(10);

        initPollyClient();
    }

    private void initPollyClient() {
        // Initialize the Amazon Cognito credential provider.
        credentialsProvider = new CognitoCachingCredentialsProvider(
                this.activity.getApplicationContext(),
                COGNITO_POOL_ID,
                MY_REGION
        );

        // Create a client that supports generation of presigned URLs.
        client = new AmazonPollyPresigningClient(credentialsProvider);
    }

    void setResponseServerAddress(String responseServerAddress) {
        this.responseServerAddress = responseServerAddress;
    }

    /////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

    /**
     * Client accessible methods
     *
     * @param text       The text to be spoken
     * @param isFinished The status
     */
    void speak(String text, boolean isFinished, boolean isSSML, boolean isNeural, String region) {
        speakingAgent = new Thread(new Speak(text, isFinished, isSSML, isNeural, region));
        executorService.submit(speakingAgent);
        this.flushReceivedMessage();
    }


    void stopSpeaking() {
        if (speakingAgent != null) {
            speakingAgent.interrupt();
            speakingAgent = null;
        }
        activity.stopMediaPlayer();
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

    void respond() {
        FetchResponse task = new FetchResponse(prepareQuery());
        Future<String> future = executorService.submit(task);
        try {
            executorService.submit(new Thread(new ProcessResponse(future.get())));
        } catch (InterruptedException | ExecutionException e) {
            e.printStackTrace();
        }
    }

    /////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////


    /**
     * Getters and Setters
     */
    private ArrayList<String> getReceivedMessage() {
        return receivedMessage;
    }

    void setReceivedMessage(ArrayList<String> receivedMessage) {
        Log.d("Location", "setReceivedMessage");
        this.receivedMessage = receivedMessage;
    }

    private void flushReceivedMessage() {
        Log.d("Location", "flushReceivedMessage");
        if (this.receivedMessage != null) {
            this.receivedMessage.clear();
        }
    }

    private class Speak implements Runnable {
        String textToRead;
        boolean isFinished;
        boolean isSSML;
        boolean isNeural;
        String region;
        String textType;
        String engine;

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
        public void run() {
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
                                .withSampleRate("22050")
                                .withTextType(this.textType)
                                .withEngine(Engine.fromValue(this.engine));
                // Get the presigned URL for synthesized speech audio stream.
                URL presignedSynthesizeSpeechUrl =
                        client.getPresignedSynthesizeSpeechUrl(synthesizeSpeechPresignRequest);
                Log.i("Polly", "Received speech presigned URL: " + presignedSynthesizeSpeechUrl);

                activity.playPollySound(presignedSynthesizeSpeechUrl.toString(), finalUtterance, isFinished);
            }
            catch (Exception e) {
                Log.d("Polly Service", "Media player stopped because some objects are null.");
            }
        }
    }

    private class FetchResponse implements Callable<String> {
        String urlParameters;

        FetchResponse(String urlParameters) {
            this.urlParameters = urlParameters;
        }

        @Override
        public String call() {
            StringBuilder result = new StringBuilder();
            try {
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
                BufferedReader in = new BufferedReader(new InputStreamReader(inputStream, StandardCharsets.UTF_8));
                for (int c; (c = in.read()) >= 0; )
                    result.append((char) c);
            } catch (IOException e) {
                Log.d("Location", "IOException");
                e.printStackTrace();
            }
            return result.toString();
        }
    }

    private class ProcessResponse implements Runnable {
        String result;

        ProcessResponse(String result){
            this.result = result;
        }

        @Override
        public void run() {
            try {
                JSONObject resultJSON = new JSONObject(result);
                JSONObject response = resultJSON.getJSONObject("response");
                String responseText = response.getString("message");
                boolean isFinished = response.getBoolean("is_finished");
                boolean isSSML = response.getBoolean("is_ssml");
                boolean isNeural = response.getBoolean("is_neural");
                String region = response.getString("region");
                int conversationTurn = 0;
                try {
                    conversationTurn = response.getInt("conversation_turn");
                } catch (Exception ignored) {
                }
                final int responseCode = response.getInt("response_code");
                final int fulfillment = response.getInt("fulfillment");
                if (responseCode != 0) {
                    final boolean commandCompleted = response.getBoolean("command_completed");
                    final String responseParameter = response.getString("response_parameter");
                    final String nextCommandHintText = response.getString("next_command_hint_text");
                    final boolean hasTriedAllCommands = response.getBoolean("has_tried_all_commands");
                    // Running UI on a separate thread let's the voice to load much faster
                    activity.runOnUiThread(new Runnable() { //mText != null &&
                        @Override
                        public void run() {
                            activity.runCommand(responseCode, fulfillment, responseParameter, nextCommandHintText, hasTriedAllCommands, commandCompleted);
                        }
                    });
                }
                if (!responseText.equals("")) {
                    speak(responseText, isFinished, isSSML, isNeural, region);
                    if (conversationTurn != 0) {
                        SharedPreferences.Editor editor = settings.edit();
                        editor.putInt("conversationTurn", conversationTurn);
                        editor.apply();
                    }
                }
            } catch (JSONException e) {
                e.printStackTrace();
                Log.d("ServerStat", e.toString());
                speak("Please check your Internet connection.", false, false, false, "");
            }
        }
    }
}
