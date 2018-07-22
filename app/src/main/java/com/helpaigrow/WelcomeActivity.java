package com.helpaigrow;

import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.text.InputType;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

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

        experimentIdText = findViewById(R.id.experimentIdText);
        experimentIdText.setInputType(InputType.TYPE_CLASS_NUMBER);

        startExperimentButton = findViewById(R.id.startExpButton);

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
                    if(expIdString.equals("")){
                        Toast.makeText(WelcomeActivity.this, "Please enter the Experiment ID", Toast.LENGTH_SHORT).show();
                    } else {
                        int expIdInt = Integer.parseInt(expIdString);

                        // Save form before authentication
                        SharedPreferences.Editor editor = settings.edit();
                        editor.putInt("experimentId", expIdInt);
                        editor.apply();

                        //Send form to auth check
                        Intent intent = new Intent(WelcomeActivity.this, AuthActivity.class);
                        intent.putExtra("EXPERIMENT_ID", expIdInt);
                        startActivity(intent);
                    }
                } catch (NumberFormatException ignored) {
                    Toast.makeText(WelcomeActivity.this, "Experiment ID must be a number!", Toast.LENGTH_SHORT).show();
                }
            }
        });

        experimentIdText.setOnEditorActionListener(new TextView.OnEditorActionListener() {
            @Override
            public boolean onEditorAction(TextView v, int actionId, KeyEvent event) {
                boolean handled = false;
                if (actionId == EditorInfo.IME_ACTION_SEND) {
                    startExperimentButton.performClick();
                    handled = true;
                }
                return handled;
            }
        });
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.mainmenu, menu);
        MenuItem item = menu.findItem(R.id.menu_report_problem_top);
        item.setEnabled(false);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        Intent intent;
        switch (item.getItemId()) {
            case R.id.menu_report_problem:
                intent = new Intent(WelcomeActivity.this, ReportProblemActivity.class);
                startActivity(intent);
                break;
            case R.id.menu_about:
                intent = new Intent(WelcomeActivity.this, AboutActivity.class);
                startActivity(intent);
                break;
            default:
                break;
        }
        return true;
    }

//    @Override
//    public void onBackPressed() {
//        Intent intent = new Intent(Intent.ACTION_MAIN);
//        intent.addCategory(Intent.CATEGORY_HOME);
//        intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
//        startActivity(intent);
//        finish();
//        System.exit(0);
//    }
}
