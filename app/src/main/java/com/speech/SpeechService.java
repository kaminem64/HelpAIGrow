package com.speech;

import android.annotation.SuppressLint;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.AsyncTask;
import android.os.Binder;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.text.TextUtils;
import android.util.Log;
import android.widget.Toast;

import com.google.auth.Credentials;
import com.google.auth.oauth2.AccessToken;
import com.google.auth.oauth2.GoogleCredentials;
import com.google.cloud.speech.v1.RecognitionConfig;
import com.google.cloud.speech.v1.SpeechContext;
import com.google.cloud.speech.v1.SpeechGrpc;
import com.google.cloud.speech.v1.SpeechRecognitionAlternative;
import com.google.cloud.speech.v1.StreamingRecognitionConfig;
import com.google.cloud.speech.v1.StreamingRecognitionResult;
import com.google.cloud.speech.v1.StreamingRecognizeRequest;
import com.google.cloud.speech.v1.StreamingRecognizeResponse;
import com.google.protobuf.ByteString;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UnsupportedEncodingException;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import io.grpc.CallOptions;
import io.grpc.Channel;
import io.grpc.ClientCall;
import io.grpc.ClientInterceptor;
import io.grpc.ClientInterceptors;
import io.grpc.ManagedChannel;
import io.grpc.Metadata;
import io.grpc.MethodDescriptor;
import io.grpc.Status;
import io.grpc.StatusException;
import io.grpc.internal.DnsNameResolverProvider;
import io.grpc.okhttp.OkHttpChannelProvider;
import io.grpc.stub.StreamObserver;


public class SpeechService extends Service {

    public interface Listener {

        /**
         * Called when a new piece of text was recognized by the Speech API.
         *
         * @param text    The text.
         * @param isFinal {@code true} when the API finished processing audio.
         */
        void onSpeechRecognized(String text, boolean isFinal);

    }

    private static final String TAG = "SpeechService";

    private static final String PREFS = "SpeechService";
    private static final String PREF_ACCESS_TOKEN_VALUE = "access_token_value";
    private static final String PREF_ACCESS_TOKEN_EXPIRATION_TIME = "access_token_expiration_time";

    /** We reuse an access token if its expiration time is longer than this. */
    private static final int ACCESS_TOKEN_EXPIRATION_TOLERANCE = 30 * 60 * 1000; // thirty minutes
    /** We refresh the current access token before it expires. */
    private static final int ACCESS_TOKEN_FETCH_MARGIN = 60 * 1000; // one minute

    public static final List<String> SCOPE =
            Collections.singletonList("https://www.googleapis.com/auth/cloud-platform");
    private static final String HOSTNAME = "speech.googleapis.com";
    private static final int PORT = 443;

    private final SpeechBinder mBinder = new SpeechBinder();
    private final ArrayList<Listener> mListeners = new ArrayList<>();
    private volatile AccessTokenTask mAccessTokenTask;
    private SpeechGrpc.SpeechStub mApi;
    private static Handler mHandler;

    // Settings
    public static final String USERSETTINGS = "PrefsFile";
    private String conversationToken;

    private final StreamObserver<StreamingRecognizeResponse> mResponseObserver
            = new StreamObserver<StreamingRecognizeResponse>() {
        @Override
        public void onNext(StreamingRecognizeResponse response) {
            String text = null;

            boolean isFinal = false;
            if (response.getResultsCount() > 0) {
                final StreamingRecognitionResult result = response.getResults(0);
                isFinal = result.getIsFinal();
                if (result.getAlternativesCount() > 0) {
                    final SpeechRecognitionAlternative alternative = result.getAlternatives(0);
                    text = alternative.getTranscript();
                }
            }
            if (text != null) {
                for (Listener listener : mListeners) {
                    listener.onSpeechRecognized(text, isFinal);
                }
                // We added this to solve this error: "io.grpc.StatusRuntimeException: OUT_OF_RANGE: Exceeded maximum allowed stream duration of 65 seconds."
                if(isFinal) {
                    finishRecognizing();
                }
            }
        }

        @Override
        public void onError(Throwable t) {
            Log.e(TAG, "Error calling the API.", t);
            // Here we close the experiment if any unhandled error occurs.
            Handler handler = new Handler(Looper.getMainLooper());
            handler.post(new Runnable() {
                @Override
                public void run() {
                    Toast.makeText(getApplicationContext(), "Cannot reach the main server. Please switch to WiFi and reopen the app.", Toast.LENGTH_LONG).show();
                    startActivity(new Intent(getApplicationContext(), WelcomeActivity.class));
                }
            });

        }

        @Override
        public void onCompleted() {
            Log.i(TAG, "API completed.");
        }

    };

    private StreamObserver<StreamingRecognizeRequest> mRequestObserver;

    public static SpeechService from(IBinder binder) {
        return ((SpeechBinder) binder).getService();
    }

    @Override
    public void onCreate() {
        super.onCreate();
        final SharedPreferences settings = getSharedPreferences(USERSETTINGS, 0);
        conversationToken = settings.getString("conversationToken", null);

        mHandler = new Handler();
        fetchAccessToken();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        mHandler.removeCallbacks(mFetchAccessTokenRunnable);
        mHandler = null;
        // Release the gRPC channel.
        if (mApi != null) {
            final ManagedChannel channel = (ManagedChannel) mApi.getChannel();
            if (channel != null && !channel.isShutdown()) {
                try {
                    channel.shutdown().awaitTermination(5, TimeUnit.SECONDS);
                } catch (InterruptedException e) {
                    Log.e(TAG, "Error shutting down the gRPC channel.", e);
                }
            }
            mApi = null;
        }
    }

    public boolean isServiceReady(){
        if(mApi != null){
            final ManagedChannel channel = (ManagedChannel) mApi.getChannel();
            if (channel != null && !channel.isShutdown()) {
                return true;
            }
        }
        return false;
    }

    private void fetchAccessToken() {
        if (mAccessTokenTask != null) {
            return;
        }
        mAccessTokenTask = new AccessTokenTask();
        mAccessTokenTask.execute();
    }

    private String getDefaultLanguageCode() {
        final Locale locale = Locale.getDefault();
        final StringBuilder language = new StringBuilder(locale.getLanguage());
        final String country = locale.getCountry();
        if (!TextUtils.isEmpty(country)) {
            language.append("-");
            language.append(country);
        }
        return language.toString();
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return mBinder;
    }

    public void addListener(@NonNull Listener listener) {
        mListeners.add(listener);
    }

    public void removeListener(@NonNull Listener listener) {
        mListeners.remove(listener);
    }

    /**
     * Starts recognizing speech audio.
     *
     * @param sampleRate The sample rate of the audio.
     */
    public void startRecognizing(int sampleRate) {
        if (mApi == null) {
            Log.w(TAG, "API not ready. Ignoring the request.");
            return;
        }
        // Configure the API

        SpeechContext.Builder speechBuilder = SpeechContext.newBuilder();
        speechBuilder.addPhrases("Amanda");
        speechBuilder.addPhrases("alarm");
        speechBuilder.addPhrases("lights");
        speechBuilder.addPhrases("AC");
        speechBuilder.addPhrases("turn on");
        speechBuilder.addPhrases("turn off");
        speechBuilder.addPhrases("temperature");
        speechBuilder.addPhrases("Kambiz");

                        mRequestObserver = mApi.streamingRecognize(mResponseObserver);
        mRequestObserver.onNext(StreamingRecognizeRequest.newBuilder()
                .setStreamingConfig(StreamingRecognitionConfig.newBuilder()
                        .setConfig(RecognitionConfig.newBuilder()
                                .setLanguageCode(getDefaultLanguageCode())
                                .setEncoding(RecognitionConfig.AudioEncoding.LINEAR16)
                                .setSampleRateHertz(sampleRate)
                                .addSpeechContexts(speechBuilder)
                                .build())
                        .setInterimResults(true)
                        .setSingleUtterance(true)
                        .build())
                .build());
    }

    /**
     * Recognizes the speech audio. This method should be called every time a chunk of byte buffer
     * is ready.
     *
     * @param data The audio data.
     * @param size The number of elements that are actually relevant in the {@code data}.
     */
    public void recognize(byte[] data, int size) {
        if (mRequestObserver == null) {
            return;
        }
        // Call the streaming recognition API
        mRequestObserver.onNext(StreamingRecognizeRequest.newBuilder()
                .setAudioContent(ByteString.copyFrom(data, 0, size))
                .build());
    }

    /**
     * Finishes recognizing speech audio.
     */
    public void finishRecognizing() {
        if (mRequestObserver == null) {
            return;
        }
        mRequestObserver.onCompleted();
        mRequestObserver = null;
    }

    private class SpeechBinder extends Binder {

        SpeechService getService() {
            return SpeechService.this;
        }

    }

    private final Runnable mFetchAccessTokenRunnable = new Runnable() {
        @Override
        public void run() {
            fetchAccessToken();
        }
    };

    @SuppressLint("StaticFieldLeak")
    private class AccessTokenTask extends AsyncTask<Void, Void, AccessToken> {

        @Override
        protected AccessToken doInBackground(Void... voids) {
            Log.i("SpeechService", "AccessTokenTask doInBackground started");
            final SharedPreferences prefs =
                    getSharedPreferences(PREFS, Context.MODE_PRIVATE);
            String tokenValue = prefs.getString(PREF_ACCESS_TOKEN_VALUE, null);
            long expirationTime = prefs.getLong(PREF_ACCESS_TOKEN_EXPIRATION_TIME, -1);

            // Check if the current token is still valid for a while
            if (tokenValue != null && expirationTime > 0) {
                if (expirationTime
                        > System.currentTimeMillis() + ACCESS_TOKEN_EXPIRATION_TOLERANCE) {
                    Log.i("SpeechService", "AccessTokenTask: return new AccessToken(tokenValue, new Date(expirationTime))");
                    return new AccessToken(tokenValue, new Date(expirationTime));
                }
            }

            // Using our own server to authenticate user's access to Google Speech API
            StringBuilder result = new StringBuilder();
            URL url;
            try {
                url = new URL("http://amandabot.xyz/speech_access_token/?conversation_token="+conversationToken);
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                InputStream inputStream = conn.getInputStream();
                BufferedReader in = new BufferedReader(new InputStreamReader(inputStream, "UTF-8"));
                for (int c; (c = in.read()) >= 0;)
                    result.append((char)c);
            } catch (MalformedURLException e) {
                e.printStackTrace();
            } catch (UnsupportedEncodingException e) {
                e.printStackTrace();
            } catch (IOException e) {
                e.printStackTrace();
            }
            try {
                Log.d(TAG, result.toString());
                JSONObject resultJSON = new JSONObject(result.toString());
                JSONObject response = resultJSON.getJSONObject("response");
                boolean success = response.getBoolean("success");
                if (success) {
                    String accessToken = response.getString("access_token");
                    Date expiresIn = new Date(Long.parseLong(response.getString("expires_in")));
                    final AccessToken token = new AccessToken(accessToken, expiresIn);
                    prefs.edit()
                            .putString(PREF_ACCESS_TOKEN_VALUE, token.getTokenValue())
                            .putLong(PREF_ACCESS_TOKEN_EXPIRATION_TIME, token.getExpirationTime().getTime())
                            .apply();
                    Log.i("SpeechService", "AccessTokenTask: return token");
                    return token;
                } else {
                    Log.e(TAG, "Failed to obtain access token.");
                }
            } catch (JSONException e) {
                e.printStackTrace();
            }
            Log.i("SpeechService", "AccessTokenTask: return null");
            return null;
        }

        @Override
        protected void onPostExecute(final AccessToken accessToken) {
            Log.i("SpeechService", "AccessTokenTask onPostExecute started");
            mAccessTokenTask = null;
            final GoogleCredentials googleCredentials = new GoogleCredentials(accessToken) {
                @Override
                public AccessToken refreshAccessToken() throws IOException {
                    Log.i("SpeechService", "AccessTokenTask onPostExecute accessToken");
                    return accessToken;
                }
            }.createScoped(SCOPE);
            final ManagedChannel channel = new OkHttpChannelProvider()
                    .builderForAddress(HOSTNAME, PORT)
                    .nameResolverFactory(new DnsNameResolverProvider())
                    .intercept(new GoogleCredentialsInterceptor(googleCredentials))
                    .build();
            mApi = SpeechGrpc.newStub(channel);

            // Schedule access token refresh before it expires
            if (mHandler != null) {
                mHandler.postDelayed(mFetchAccessTokenRunnable,
                        Math.max(accessToken.getExpirationTime().getTime()
                                - System.currentTimeMillis()
                                - ACCESS_TOKEN_FETCH_MARGIN, ACCESS_TOKEN_EXPIRATION_TOLERANCE));
            }
            Log.i("SpeechService", "AccessTokenTask onPostExecute mApi and Schedule access token refresh");
        }
    }

    /**
     * Authenticates the gRPC channel using the specified {@link GoogleCredentials}.
     */
    private static class GoogleCredentialsInterceptor implements ClientInterceptor {

        private final Credentials mCredentials;

        private Metadata mCached;

        private Map<String, List<String>> mLastMetadata;

        GoogleCredentialsInterceptor(Credentials credentials) {
            mCredentials = credentials;
        }

        @Override
        public <ReqT, RespT> ClientCall<ReqT, RespT> interceptCall(
                final MethodDescriptor<ReqT, RespT> method, CallOptions callOptions,
                final Channel next) {
            return new ClientInterceptors.CheckedForwardingClientCall<ReqT, RespT>(
                    next.newCall(method, callOptions)) {
                @Override
                protected void checkedStart(Listener<RespT> responseListener, Metadata headers)
                        throws StatusException {
                    Metadata cachedSaved;
                    URI uri = serviceUri(next, method);
                    synchronized (this) {
                        Map<String, List<String>> latestMetadata = getRequestMetadata(uri);
                        if (mLastMetadata == null || mLastMetadata != latestMetadata) {
                            mLastMetadata = latestMetadata;
                            mCached = toHeaders(mLastMetadata);
                        }
                        cachedSaved = mCached;
                    }
                    headers.merge(cachedSaved);
                    delegate().start(responseListener, headers);
                }
            };
        }

        /**
         * Generate a JWT-specific service URI. The URI is simply an identifier with enough
         * information for a service to know that the JWT was intended for it. The URI will
         * commonly be verified with a simple string equality check.
         */
        private URI serviceUri(Channel channel, MethodDescriptor<?, ?> method)
                throws StatusException {
            String authority = channel.authority();
            if (authority == null) {
                throw Status.UNAUTHENTICATED
                        .withDescription("Channel has no authority")
                        .asException();
            }
            // Always use HTTPS, by definition.
            final String scheme = "https";
            final int defaultPort = 443;
            String path = "/" + MethodDescriptor.extractFullServiceName(method.getFullMethodName());
            URI uri;
            try {
                uri = new URI(scheme, authority, path, null, null);
            } catch (URISyntaxException e) {
                throw Status.UNAUTHENTICATED
                        .withDescription("Unable to construct service URI for auth")
                        .withCause(e).asException();
            }
            // The default port must not be present. Alternative ports should be present.
            if (uri.getPort() == defaultPort) {
                uri = removePort(uri);
            }
            return uri;
        }

        private URI removePort(URI uri) throws StatusException {
            try {
                return new URI(uri.getScheme(), uri.getUserInfo(), uri.getHost(), -1 /* port */,
                        uri.getPath(), uri.getQuery(), uri.getFragment());
            } catch (URISyntaxException e) {
                throw Status.UNAUTHENTICATED
                        .withDescription("Unable to construct service URI after removing port")
                        .withCause(e).asException();
            }
        }

        private Map<String, List<String>> getRequestMetadata(URI uri) throws StatusException {
            try {
                return mCredentials.getRequestMetadata(uri);
            } catch (IOException e) {
                throw Status.UNAUTHENTICATED.withCause(e).asException();
            }
        }

        private static Metadata toHeaders(Map<String, List<String>> metadata) {
            Metadata headers = new Metadata();
            if (metadata != null) {
                for (String key : metadata.keySet()) {
                    Metadata.Key<String> headerKey = Metadata.Key.of(
                            key, Metadata.ASCII_STRING_MARSHALLER);
                    for (String value : metadata.get(key)) {
                        headers.put(headerKey, value);
                    }
                }
            }
            return headers;
        }

    }

}
