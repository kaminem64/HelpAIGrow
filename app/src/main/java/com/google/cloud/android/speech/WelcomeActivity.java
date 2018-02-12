package com.google.cloud.android.speech;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import java.util.UUID;

public class WelcomeActivity extends AppCompatActivity {
    public static final String USERSETTINGS = "PrefsFile";



    Button startExperimentButton;
    EditText experimentIdText;
//    String trackingId;
    int experimentId;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_welcome);
        setTitle("");

        experimentIdText = (EditText) findViewById(R.id.experimentIdText);
        startExperimentButton = (Button) findViewById(R.id.startExpButton);

        // Restore preferences
        final SharedPreferences settings = getSharedPreferences(USERSETTINGS, 0);
        experimentId = settings.getInt("experimentId", 0);

        if (experimentId != 0) {
            experimentIdText.setText(String.valueOf(experimentId));
        }
        
        startExperimentButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                String expIdString = experimentIdText.getText().toString();
                try {
                        int expIdInt = Integer.parseInt(expIdString);

                        // Save form before authentication
                        SharedPreferences.Editor editor = settings.edit();
                        editor.putInt("experimentId", expIdInt);
                        editor.apply();

                        //Send form to auth check
                        Intent intent = new Intent(WelcomeActivity.this, AuthActivity.class);
                        intent.putExtra("EXPERIMENT_ID", expIdInt);
                        startActivity(intent);
//                    }
                } catch (NumberFormatException ignored) {
                    Toast.makeText(WelcomeActivity.this, "Experiment ID must be a number!", Toast.LENGTH_SHORT).show();
                }

            }
        });

    }

    @Override
    public void onBackPressed() {
        Intent intent = new Intent(Intent.ACTION_MAIN);
        intent.addCategory(Intent.CATEGORY_HOME);
        intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
        startActivity(intent);
        finish();
        System.exit(0);
    }
}
