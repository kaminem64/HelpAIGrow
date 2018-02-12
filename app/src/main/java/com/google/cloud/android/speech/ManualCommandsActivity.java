package com.google.cloud.android.speech;

import android.annotation.SuppressLint;
import android.app.TimePickerDialog;
import android.content.Intent;
import android.icu.text.DateFormat;
import java.text.SimpleDateFormat;
import android.icu.util.Calendar;
import android.os.Build;
import android.os.CountDownTimer;
import android.support.annotation.RequiresApi;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.SeekBar;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.TimePicker;
import android.widget.Toast;

import com.google.type.Date;

import org.w3c.dom.Text;

import java.text.ParseException;

public class ManualCommandsActivity extends AppCompatActivity {

    CountDownTimer countDownTimer;

    Toast toastMessage;

    Switch lightsSwitch;
    Switch acSwitch;
    SeekBar acTemperature;

    TextView time;
    Button nextButton;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_manual_commands);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        setTitle("Control Desk");

        lightsSwitch = (Switch) findViewById(R.id.lightsSwitch);
        lightsSwitch.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton compoundButton, boolean isChecked) {
                if(isChecked){
                    if (toastMessage!= null) toastMessage.cancel();
                    toastMessage = Toast.makeText(ManualCommandsActivity.this, "Lights are on", Toast.LENGTH_SHORT);
                    toastMessage.show();
                } else {
                    if (toastMessage!= null) toastMessage.cancel();
                    toastMessage = Toast.makeText(ManualCommandsActivity.this, "Lights are off", Toast.LENGTH_SHORT);
                    toastMessage.show();
                }
            }
        });
        acSwitch = (Switch) findViewById(R.id.acSwitch);
        acSwitch.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton compoundButton, boolean isChecked) {
                if(isChecked){
                    if (toastMessage!= null) toastMessage.cancel();
                    toastMessage = Toast.makeText(ManualCommandsActivity.this, "AC is on", Toast.LENGTH_SHORT);
                    toastMessage.show();
                } else {
                    if (toastMessage!= null) toastMessage.cancel();
                    toastMessage = Toast.makeText(ManualCommandsActivity.this, "AC is off", Toast.LENGTH_SHORT);
                    toastMessage.show();
                }
            }
        });

        acTemperature = (SeekBar) findViewById(R.id.acTemperature);
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
                toastMessage = Toast.makeText(ManualCommandsActivity.this, message, Toast.LENGTH_SHORT);
                toastMessage.show();
            }
        });



        nextButton = (Button) findViewById(R.id.nextButton);
        nextButton.setEnabled(false);

        //  initiate the edit text
        time = (TextView) findViewById(R.id.time);
        // perform click event listener on edit text
        time.setOnClickListener(new View.OnClickListener() {
            @RequiresApi(api = Build.VERSION_CODES.N)
            @Override
            public void onClick(View v) {
                Calendar mcurrentTime = Calendar.getInstance();
                int hour = mcurrentTime.get(Calendar.HOUR_OF_DAY);
                int minute = mcurrentTime.get(Calendar.MINUTE);
                TimePickerDialog mTimePicker;
                mTimePicker = new TimePickerDialog(ManualCommandsActivity.this, new TimePickerDialog.OnTimeSetListener() {
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
                            toastMessage = Toast.makeText(ManualCommandsActivity.this, message, Toast.LENGTH_SHORT);
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
                    nextButton.setText("Proceed");
                    nextButton.setEnabled(true);
                }
            };
        countDownTimer.start();
    }

    @Override
    public void onBackPressed() {
        Intent goBackIntent = new Intent(ManualCommandsActivity.this, WelcomeActivity.class);
        startActivity(goBackIntent);
        finish();
    }
}
