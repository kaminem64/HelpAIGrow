package com.helpaigrow;

import android.util.Log;

import com.amazonaws.http.HttpClient;

import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;

public class UploadFile {

    private String sourceFileUri;

    public UploadFile (String sourceFileUri) {
        this.sourceFileUri = sourceFileUri;
    }

    public void uploadTwo() {
        StringBuilder result = new StringBuilder();
        try {
            URL url = new URL("http://amandabot.xyz/upload_file/");
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setReadTimeout(10000);
            conn.setConnectTimeout(15000);
            conn.setRequestMethod("POST");
            conn.setDoInput(true);
            conn.setDoOutput(true);
            OutputStream os = conn.getOutputStream();
            DataOutputStream writer = new DataOutputStream(os);
            writer.writeBytes("Testing");
            writer.flush();
            writer.close();
            os.close();
            int status = conn.getResponseCode();
            Log.d("uploadTwo", "HTTP STATUS: " + String.valueOf(status));
            InputStream inputStream = conn.getInputStream();
            BufferedReader in = new BufferedReader(new InputStreamReader(inputStream, "UTF-8"));
            for (int c; (c = in.read()) >= 0;)
                result.append((char)c);
        } catch (IOException e) {
            Log.d("uploadTwo", "IOException");
            e.printStackTrace();
        }
    }


    public  void uploadAlt() {
        File file = new File(sourceFileUri);
        try {

            URL url = new URL("http://amandabot.xyz/upload_file/");
            HttpURLConnection urlConnection = (HttpURLConnection) url.openConnection();
            urlConnection.setDoInput(true); // Allow Inputs
            urlConnection.setDoOutput(true); // Allow Outputs
            urlConnection.setUseCaches(false); // Don't use a Cached Copy
            urlConnection.setRequestMethod("POST");
            urlConnection.setRequestProperty("Connection", "Keep-Alive");
            urlConnection.setRequestProperty("Content-Type", "multipart/form-data;boundary=" + "dhjsdkajshdakj");

            OutputStream out = new BufferedOutputStream(urlConnection.getOutputStream());

            BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(out, "UTF-8"));

            writer.write("Testing");
            writer.flush();
            writer.close();
            out.close();

            urlConnection.connect();

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void upload() {

        String fileName = sourceFileUri;

        HttpURLConnection conn = null;
        DataOutputStream dos = null;
        String lineEnd = "\r\n";
        String twoHyphens = "--";
        String boundary = "*****";
        int bytesRead, bytesAvailable, bufferSize;
        byte[] buffer;
        int maxBufferSize = 1 * 1024 * 1024;
        File sourceFile = new File(sourceFileUri);

        if (sourceFile.isFile()) {
            try {

                Log.i("uploadFile", "start");

                // open a URL connection to the Servlet
                FileInputStream fileInputStream = new FileInputStream(sourceFile);
                String upLoadServerUri = "http://amandabot.xyz/upload_file/";
                URL url = new URL(upLoadServerUri);

                // Open a HTTP  connection to  the URL
                conn = (HttpURLConnection) url.openConnection();
                conn.setDoInput(true); // Allow Inputs
                conn.setDoOutput(true); // Allow Outputs
                conn.setUseCaches(false); // Don't use a Cached Copy
                conn.setRequestMethod("POST");
                conn.setRequestProperty("Connection", "Keep-Alive");
                conn.setRequestProperty("Content-Type", "multipart/form-data;boundary=" + boundary);

                dos = new DataOutputStream(conn.getOutputStream());


                // create a buffer of  maximum size
                bytesAvailable = fileInputStream.available();

                bufferSize = Math.min(bytesAvailable, maxBufferSize);
                buffer = new byte[bufferSize];

                // read file and write it into form...
                bytesRead = fileInputStream.read(buffer, 0, bufferSize);

                while (bytesRead > 0) {
                    Log.i("uploadFile", "bytesRead: " + bytesRead);

                    dos.write(buffer, 0, bufferSize);
                    bytesAvailable = fileInputStream.available();
                    bufferSize = Math.min(bytesAvailable, maxBufferSize);
                    bytesRead = fileInputStream.read(buffer, 0, bufferSize);

                }

                // send multipart form data necessary after file data...
                Log.i("uploadFile", "writeBytes start");
                dos.writeBytes(lineEnd);
                dos.writeBytes(twoHyphens + boundary + twoHyphens + lineEnd);
                Log.i("uploadFile", "writeBytes end");

                // Responses from the server (code and message)
                int serverResponseCode = conn.getResponseCode();
                String serverResponseMessage = conn.getResponseMessage();

                Log.i("uploadFile", "HTTP Response is : "
                        + serverResponseMessage + ": " + serverResponseCode);

                if(serverResponseCode == 200){
                }

                //close the streams //
                fileInputStream.close();
                dos.flush();
                dos.close();

            } catch (MalformedURLException ex) {
                Log.e("UploadFileToServer", "error: " + ex.getMessage(), ex);

            } catch (Exception e) {
                e.printStackTrace();
                Log.e("UploadFileException", "Exception : "
                        + e.getMessage(), e);
            }

        } // End else block
    }
}