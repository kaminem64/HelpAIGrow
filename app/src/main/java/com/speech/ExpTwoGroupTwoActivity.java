package com.speech;

import android.annotation.SuppressLint;
import android.app.TimePickerDialog;
import android.content.Intent;

import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.text.SimpleDateFormat;

import android.content.SharedPreferences;
import java.util.Calendar;
import android.os.AsyncTask;
import android.os.Build;
import android.os.CountDownTimer;
import android.support.annotation.RequiresApi;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.SeekBar;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.TimePicker;
import android.widget.Toast;

import org.json.JSONException;
import org.json.JSONObject;

import java.text.ParseException;

public class ExpTwoGroupTwoActivity extends AppCompatActivity {
    // Settings
    public static final String USERSETTINGS = "PrefsFile";

    private CountDownTimer countDownTimer;

    private Toast toastMessage;

    private SeekBar acTemperature;

    private TextView time;
    private Button nextButton;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_exp_two_group_two);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        setTitle("Control Desk");

        final SharedPreferences settings = getSharedPreferences(USERSETTINGS, 0);
        final String conversationToken = settings.getString("conversationToken", null);

        Switch lightsSwitch = findViewById(R.id.lightsSwitch);
        lightsSwitch.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton compoundButton, boolean isChecked) {
                if(isChecked){
                    if (toastMessage!= null) toastMessage.cancel();
                    toastMessage = Toast.makeText(ExpTwoGroupTwoActivity.this, "Lights are on", Toast.LENGTH_SHORT);
                    toastMessage.show();
                } else {
                    if (toastMessage!= null) toastMessage.cancel();
                    toastMessage = Toast.makeText(ExpTwoGroupTwoActivity.this, "Lights are off", Toast.LENGTH_SHORT);
                    toastMessage.show();
                }
            }
        });
        Switch acSwitch = findViewById(R.id.acSwitch);
        acSwitch.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton compoundButton, boolean isChecked) {
                if(isChecked){
                    if (toastMessage!= null) toastMessage.cancel();
                    toastMessage = Toast.makeText(ExpTwoGroupTwoActivity.this, "AC is on", Toast.LENGTH_SHORT);
                    toastMessage.show();
                } else {
                    if (toastMessage!= null) toastMessage.cancel();
                    toastMessage = Toast.makeText(ExpTwoGroupTwoActivity.this, "AC is off", Toast.LENGTH_SHORT);
                    toastMessage.show();
                }
            }
        });

        acTemperature = findViewById(R.id.acTemperature);
        acTemperature.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {

            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {

            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                String message = "Temperature is set to: " + acTemperature.getProgress() + "°F";
                if (toastMessage!= null) toastMessage.cancel();
                toastMessage = Toast.makeText(ExpTwoGroupTwoActivity.this, message, Toast.LENGTH_SHORT);
                toastMessage.show();
            }
        });



        nextButton = findViewById(R.id.nextButton);
        nextButton.setEnabled(false);
        nextButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                @SuppressLint("DefaultLocale") String query = String.format("conversation_token=%s", conversationToken);
                new FetchResponse().execute(query);
            }
        });

        //  initiate the edit text
        time = findViewById(R.id.time);
        // perform click event listener on edit text
        time.setOnClickListener(new View.OnClickListener() {
            @RequiresApi(api = Build.VERSION_CODES.N)
            @Override
            public void onClick(View v) {
                Calendar mcurrentTime = Calendar.getInstance();
                int hour = mcurrentTime.get(Calendar.HOUR_OF_DAY);
                int minute = mcurrentTime.get(Calendar.MINUTE);
                TimePickerDialog mTimePicker;
                mTimePicker = new TimePickerDialog(ExpTwoGroupTwoActivity.this, new TimePickerDialog.OnTimeSetListener() {
                    @Override
                    public void onTimeSet(TimePicker timePicker, int selectedHour, int selectedMinute) {
                        @SuppressLint("SimpleDateFormat") SimpleDateFormat inFormat = new SimpleDateFormat("HH:mm");
                        @SuppressLint("SimpleDateFormat") SimpleDateFormat outFormat = new SimpleDateFormat("h:mm aa");
                        String timeText = selectedHour + ":" + selectedMinute;
                        try {
                            String setTime = outFormat.format(inFormat.parse(timeText));
                            time.setText(setTime);
                            String message = "Alarm is set for: " + setTime;
                            if (toastMessage!= null) toastMessage.cancel();
                            toastMessage = Toast.makeText(ExpTwoGroupTwoActivity.this, message, Toast.LENGTH_SHORT);
                            toastMessage.show();
                        } catch (ParseException e) {
                            e.printStackTrace();
                        }
                    }
                }, hour, minute, false);//Yes 24 hour time
                mTimePicker.setTitle("Select Time");
                mTimePicker.show();

            }
        });
    }

    @Override
    protected void onStart() {
        super.onStart();
        if(countDownTimer != null){
            countDownTimer.cancel();
            countDownTimer = null;
        }
        countDownTimer = new CountDownTimer(31000, 1000) {
                @SuppressLint("SetTextI18n")
                public void onTick(long millisUntilFinished) {
                    nextButton.setText("seconds remaining: " + millisUntilFinished / 1000);
                }

                public void onFinish() {
                    nextButton.setText(R.string.proceed);
                    nextButton.setEnabled(true);
                }
            };
        countDownTimer.start();
    }

    @Override
    public void onBackPressed() {
        Intent goBackIntent = new Intent(ExpTwoGroupTwoActivity.this, WelcomeActivity.class);
        startActivity(goBackIntent);
    }


    @SuppressLint("StaticFieldLeak")
    private class FetchResponse extends AsyncTask<String, Integer, String> {

        @Override
        protected String doInBackground(String... strings) {
            StringBuilder result = new StringBuilder();
            try {
                String urlParameters = strings[0];
                URL url = new URL("http://amandabot.xyz/conversation_finished/");
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
                boolean success = response.getBoolean("success");
                Log.d("ServerStat", "Got success");
                if (success) {
                    Intent goToQuestions = new Intent(ExpTwoGroupTwoActivity.this, QuestionsActivity.class);
                    startActivity(goToQuestions);
                } else {
                    Toast.makeText(ExpTwoGroupTwoActivity.this, "Server error!\nPlease try again later!", Toast.LENGTH_LONG).show();
                }
            } catch (JSONException e) {
                e.printStackTrace();
                Log.d("ServerStat", e.toString());
                Toast.makeText(ExpTwoGroupTwoActivity.this, "Server error!\nPlease try again later!", Toast.LENGTH_LONG).show();
            }

        }
    }




}
