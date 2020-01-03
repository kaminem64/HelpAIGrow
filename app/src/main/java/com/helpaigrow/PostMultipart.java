package com.helpaigrow;

import android.util.Log;

import java.io.File;
import java.util.Objects;

import okhttp3.MediaType;
import okhttp3.MultipartBody;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public final class PostMultipart {

    private String filePath;
    private String fileName;

    public PostMultipart(String filePath) {
        this.filePath = filePath;

        String[] parts = filePath.split("/");
        fileName = parts[parts.length - 1];
    }

    private final OkHttpClient client = new OkHttpClient();

    public void run() {
        RequestBody requestBody = new MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart("file", fileName, RequestBody.create(new File(filePath), MediaType.get("image/png")))
                .build();


        Request request = new Request.Builder()
                .url("http://amandabot.xyz/upload_file/")
                .post(requestBody)
                .build();

        try {
            Response response = client.newCall(request).execute();
            Log.i("PostMultipart", response + " - " + response.isSuccessful() + " - " + Objects.requireNonNull(response.body()).string());
        } catch(Exception e) {
            e.printStackTrace();
        }

    }
}