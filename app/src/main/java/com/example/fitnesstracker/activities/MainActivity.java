package com.example.fitnesstracker.activities;

import android.os.Bundle;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.example.fitnesstracker.R;

/**
 * Temporary smoke-test screen. It will be replaced by the real dashboard in M3.
 */
public class MainActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        TextView title = findViewById(R.id.dashboardTitle);
        if (title != null) {
            title.setText(R.string.app_name);
        }
    }
}
